/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import android.view.View
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.RepeatHeader
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_20_or_greater
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream gives the native playlist header the RepeatHeader interface, injects the shuffle
// command at the fallback action of its button and the bind notification at every return of the
// method that binds a panel. A module cannot add a member to a class of the app, so the interface is
// the proxy below, the fallback action is hooked at entry, and the binder is observed after it ran.

/** The compact playlist header, which holds the repeat and shuffle controls of the queue. */
private fun DexKitBridge.repeatHeaderType(): String =
    PlaylistRepeatBindingFingerprint.match().method.declaredClass!!.descriptor

/** The binder that renders the repeat and shuffle state of a bound panel. */
private val repeatBinder = findMethodDirect { PlaylistRepeatBindingFingerprint.match().method }

/** The native renderer of the repeat control, which takes the mode and the shuffle flag. */
private val repeatRenderState = findMethodDirect {
    PlaylistRepeatBindingFingerprint.match().instructionMatches.last().instruction.methodRef!!
}

/** The binder both the repeat state and the repeat capabilities of the header run in. */
private val repeatCapabilities = findMethodDirect {
    PlaylistRepeatCapabilitiesFingerprint.match().method
}

/** The navigator field of the header. */
private val repeatNavigator = findFieldDirect {
    playlistHeaderNavigatorFingerprint(repeatHeaderType()).match()
        .instructionMatches.single().instruction.fieldRef!!
}

/** The panel field of the header, which names the panel it is bound to. */
private val repeatPanel = findFieldDirect {
    playlistHeaderPanelFingerprint(PlaylistRepeatBindingFingerprint.match().method).match()
        .instructionMatches.single().instruction.fieldRef!!
}

/** The repeat button of the header. */
private val repeatView = findFieldDirect {
    PlaylistHeaderRepeatViewFingerprint.match().instructionMatches.last().instruction.fieldRef!!
}

/** The shuffle button of the header. */
private val repeatShuffleView = findFieldDirect {
    PlaylistShuffleViewFingerprint.match().instructionMatches.last().instruction.fieldRef!!
}

/** The two capability flags of the repeat control: repeat all and repeat one. */
private val repeatFlags = findFieldListDirect {
    val header = repeatHeaderType()
    PlaylistRepeatCapabilitiesFingerprint.match().instructionMatches
        .mapNotNull { it.instruction.fieldRef }
        .filter { it.type.descriptor == "Z" && it.declaredClass!!.descriptor == header }
}

/** The listener the binder stores and restores when it shows the shuffle button. */
private val repeatShuffleListener = findFieldDirect {
    playlistShuffleListenerFingerprint(
        PlaylistRepeatBindingFingerprint.match().method, repeatShuffleView(this)
    ).match().instructionMatches[2].instruction.fieldRef!!
}

/** The fallback action of the shuffle button, which toggles the native shuffler. */
private val repeatShufflePress = findMethodDirect {
    playlistShufflePressFingerprint(repeatNavigator(this), repeatShuffleView(this)).match().method
}

/**
 * The native playlist header, which stands in for the RepeatHeader interface upstream adds to its
 * class: the bridges read the navigator and the bound panel, enable the repeat control of an owned
 * queue, and render a mode through the native renderer of the binder.
 */
private class RepeatHeaderProxy(
    private val header: Any,
    private val navigator: Field,
    private val panel: Field,
    private val repeatView: Field,
    private val shuffleView: Field,
    private val flags: List<Field>,
    private val renderState: Method,
    private val shuffleListener: Field?
) : RepeatHeader {
    override fun patch_getNavigator(): Any? = bridgeField(navigator, header)

    override fun patch_getPanel(): Any? = bridgeField(panel, header)?.let(::queuePanelProxy)

    override fun patch_enableRepeat() {
        flags.forEach { it.write(header, true) }
        (bridgeField(repeatView, header) as? View)?.isEnabled = true
    }

    override fun patch_setRepeatMode(mode: Int) {
        // Reuses the selected and loop-one drawables of the native repeat control.
        renderState.call(header, mode, false)
    }

    override fun patch_setShuffleAvailable(available: Boolean) {
        val view = bridgeField(shuffleView, header) as? View ?: return
        view.isEnabled = available
        // A native binder can hide an unsupported shuffle action and clear its listener.
        view.visibility = View.VISIBLE
        view.isSelected = false
        shuffleListener?.let { view.setOnClickListener(bridgeField(it, header) as? View.OnClickListener) }
    }
}

/**
 * Keeps the repeat control of the native header and turns the shuffle of the owned queue into its
 * own command.
 *
 * Upstream injects the extension decision at the fallback action of the shuffle button, which only a
 * header bound to the panel of the owned queue consumes, and notifies the bound header at every
 * return of the binder. The native listener and the account playlist branch stay untouched, and the
 * notifications of the binder are read after it finished, which is what the cloned returns of the
 * upstream injection observe.
 *
 * @param navigatorType The playlist navigator that the header holds.
 */
internal fun PatchExecutor.installPlaylistRepeat(navigatorType: Class<*>) {
    val navigator = ::repeatNavigator.field
    val panel = ::repeatPanel.field
    val repeat = ::repeatView.field
    val shuffle = ::repeatShuffleView.field
    val flags = ::repeatFlags.fieldList
    val binder = ::repeatBinder.method
    val render = ::repeatRenderState.method
    val listener = if (is_21_20_or_greater) ::repeatShuffleListener.field else null

    check(navigator.type == navigatorType) {
        "Native playlist queue: the playlist header holds a navigator of another class"
    }
    check(navigator.declaringClass == ::repeatPanel.field.declaringClass) {
        "Native playlist queue: the playlist header fields belong to different classes"
    }
    check(::repeatCapabilities.method == binder) {
        "Native playlist queue: the repeat capabilities and state have different binders"
    }
    check(flags.size == 2 && flags.toSet().size == 2) {
        "Native playlist queue: the repeat capabilities are not two flags of the playlist header"
    }
    check(shuffle.declaringClass == navigator.declaringClass) {
        "Native playlist queue: the shuffle control belongs to another header than the repeat control"
    }

    val proxies = ProxyCache { header: Any ->
        RepeatHeaderProxy(header, navigator, panel, repeat, shuffle, flags, render, listener)
    }

    /** The header of a pressed shuffle button, which the listener either is or keeps. */
    fun pressHeader(press: Any?): Any? {
        if (press == null) return null
        if (navigator.declaringClass.isInstance(press)) return press
        return press.javaClass.declaredFields.firstNotNullOfOrNull { field ->
            field.apply { isAccessible = true }.get(press)
                ?.takeIf(navigator.declaringClass::isInstance)
        }
    }

    ::repeatShufflePress.method.hookMethod {
        before { param ->
            val header = pressHeader(param.thisObject) ?: return@before
            val consumed = queueAction("onQueueShuffle") {
                NativePlaylistQueuePatch.onQueueShuffle(proxies.get(header))
            } ?: false
            if (consumed) param.result = null
        }
    }

    binder.hookMethod {
        after { param ->
            val header = param.thisObject ?: return@after
            queueAction("onRepeatHeaderBound") {
                NativePlaylistQueuePatch.onRepeatHeaderBound(proxies.get(header))
            }
        }
    }
}
