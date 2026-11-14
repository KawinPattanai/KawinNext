/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

@file:Suppress("SpellCheckingInspection")

package io.github.kawinnext.morphe.youtube.layout.miniplayer

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import app.morphe.extension.youtube.patches.MinimalMiniplayerPatch
import app.morphe.extension.youtube.patches.MinimalMiniplayerPatch.MiniplayerBoundsController
import app.morphe.extension.youtube.patches.MiniplayerPatch
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.opcodeEnum
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.insertLiteralOverride
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_45_or_greater
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** The resume (Continue watching) check the show miniplayer command is skipped from. */
private val showMiniplayerResumeCheck = findMethodDirect {
    ShowMiniplayerCommandFingerprint.instructionMatches[0].instruction.methodRef!!
}

/** Last position of the miniplayer, which the reposition method lays the player out from. */
private val previousMiniplayerRectField = findFieldDirect {
    MiniplayerRectDragFieldsNameFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/** Position the offscreen handler records before it moves the miniplayer away. */
private val miniplayerRectField = findFieldDirect {
    MiniplayerOffscreenHandlerFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** The calls that hand the player the rect the video is laid out with. */
private val miniplayerVideoRectCalls = findMethodListDirect {
    MiniplayerHorizontalRepositionFingerprint.match().method.instructions.mapNotNull { instruction ->
        val reference = instruction.methodRef ?: return@mapNotNull null
        if (instruction.opcodeEnum != Opcode.INVOKE_STATIC ||
            reference.returnTypeName != "void" ||
            reference.paramTypeNames != listOf("float", "android.graphics.Rect", "android.graphics.Rect")
        ) {
            return@mapNotNull null
        }
        reference
    }
}

/** The listener that is told when the horizontal drag animation ends. */
private val miniplayerDragAnimatorEnd = findMethodDirect {
    val listenerClass = MiniplayerHorizontalDragPlaybackFingerprint
        .instructionMatches[2].instruction.methodRef!!.declaredClass!!
    listenerClass.methods!!.firstOrNull { it.name == "onAnimationEnd" }
        ?: throw IllegalStateException("No onAnimationEnd in ${listenerClass.descriptor}")
}

private val boundsControllers = WeakHashMap<Any, MiniplayerBoundsController>()

/**
 * Id of the legacy controls layout the app inflates into the miniplayer, once it has one.
 */
@Volatile
private var legacyControlsLayoutId = 0

/**
 * Ported for the targets the module is built against, 21.44 and 21.45. The shapes of the miniplayer
 * that upstream picks by version are the ones those two releases have, so a target whose miniplayer
 * is shaped differently fails this patch and keeps the app's own miniplayer.
 */
val miniplayerPatch = patch(
    name = "Miniplayer",
    description = "Adds options to change the in-app minimized player.",
) {
    dependsOn(
        VersionCheck,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_miniplayer_screen",
            sorting = Sorting.UNSORTED,
            preferences = mutableSetOf(
                ListPreference(
                    key = "morphe_miniplayer_type",
                    entriesKey = "morphe_miniplayer_type_21_29_entries",
                    entryValuesKey = "morphe_miniplayer_type_21_29_entry_values",
                ),
                SwitchPreference("morphe_miniplayer_disable_resuming", summary = true),
                SwitchPreference("morphe_miniplayer_hide_title"),
                SwitchPreference("morphe_miniplayer_swipe_up_expand", summary = true),
                SwitchPreference("morphe_miniplayer_docked", summary = true),
                SwitchPreference("morphe_miniplayer_glass", summary = true),
                SwitchPreference("morphe_miniplayer_disable_rounded_corners"),
                SwitchPreference("morphe_miniplayer_disable_drag_and_drop", summary = true),
                SwitchPreference("morphe_miniplayer_disable_horizontal_drag", summary = true),
                SwitchPreference("morphe_miniplayer_disable_horizontal_drag_playback", summary = true),
                SwitchPreference("morphe_miniplayer_disable_horizontal_reposition", summary = true),
            ),
        )
    )

    // The modern miniplayer, which every type but DEFAULT turns on, and the handler that closes
    // the miniplayer right after it is shown, which is the DISABLED type. Both flags are read
    // through the flag method that FeatureOverride hooks.
    insertLiteralOverride(MINIPLAYER_MODERN_FEATURE_KEY, MiniplayerPatch::getModernFeatureFlagsActiveOverride)
    insertLiteralOverride(MINIPLAYER_DISABLED_FEATURE_KEY, MiniplayerPatch::getMiniplayerOnCloseHandler)

    // region Disable resuming miniplayer (Continue watching)

    // Only the resume branch of the command is skipped. The command returns right after the check,
    // so the whole command is skipped, which is what the resume branch would have done.
    val resumeCheck = ::showMiniplayerResumeCheck.method

    ShowMiniplayerCommandFingerprint.hookMethod {
        before { param ->
            if (!MiniplayerPatch.disableResumingStartupMiniPlayer()) return@before

            val resuming = runCatching { resumeCheck.invoke(param.thisObject, param.args[1]) }
                .getOrNull() as? Boolean ?: return@before
            if (resuming) {
                param.result = null
            }
        }
    }

    // endregion

    // region Disable drag and drop

    MiniplayerDragAndDropFingerprint.hookMethod {
        before { param ->
            val event = param.args[1] as MotionEvent
            if (MiniplayerPatch.getMiniplayerDragAndDrop(event.actionMasked)) {
                param.result = false
            }
        }
    }

    // endregion

    // region Disable rounded corners

    // The check answers true while the rounded corners are turned off. The outline the app just set
    // is taken away, which is what skipping the call that sets it leaves behind, and the miniplayer
    // stays square until the app rounds it again.
    (if (is_21_45_or_greater) {
        MiniplayerRoundedCornersFingerprint
    } else {
        MiniplayerRoundedCorners2144Fingerprint
    }).hookMethod {
        after { param ->
            if (!MiniplayerPatch.getRoundedCorners()) return@after

            (param.args[0] as View).apply {
                clipToOutline = false
                outlineProvider = null
            }
        }
    }

    // endregion

    // region Disable horizontal drag

    // The miniplayer is never moved offscreen, so the check that allows it answers no and the
    // handler that moves it is skipped.
    MiniplayerOffscreenRectValidatorFingerprint.hookMethod {
        before { param ->
            if (MiniplayerPatch.getHorizontalDrag()) {
                param.result = false
            }
        }
    }

    // endregion

    // region Minimal miniplayer

    val previousRect = ::previousMiniplayerRectField.field.apply { isAccessible = true }
    val recordedRect = ::miniplayerRectField.field.apply { isAccessible = true }

    MiniplayerHorizontalRepositionFingerprint.let { fingerprint ->
        val reposition = fingerprint.method

        // The shape of the bar is applied on demand through the controller the extension keeps.
        // The app adds the method the controller calls to its own class, which a hook cannot do,
        // so the controller calls the reposition method of the instance it was made for.
        fingerprint.hookMethod {
            before { param ->
                val instance = param.thisObject
                val controller = synchronized(boundsControllers) {
                    boundsControllers.getOrPut(instance) {
                        val instanceRef = WeakReference(instance)
                        object : MiniplayerBoundsController {
                            override fun patch_setBounds(bounds: Rect) {
                                instanceRef.get()?.let { reposition.invoke(it, bounds) }
                            }
                        }
                    }
                }
                MinimalMiniplayerPatch.setBoundsController(controller)
            }
        }

        fingerprint.hookMethod {
            before { param ->
                val current = param.args[0] as Rect
                val last = previousRect.get(param.thisObject) as Rect
                param.args[0] = MinimalMiniplayerPatch.getMinimalBarBounds(
                    MiniplayerPatch.blockOffscreenMiniplayerHorizontalReposition(current, last)
                )
            }
        }
    }

    // The bar reshapes the rect the app lays the video out with, which is written on the rect itself
    // because the app reads it directly while it animates.
    ::miniplayerVideoRectCalls.dexMethodList.forEach { call ->
        MiniplayerHorizontalRepositionFingerprint.hookMethod(
            scopedHook(call.toMethod()) {
                after { param ->
                    MinimalMiniplayerPatch.applyVideoRect(param.args[2] as Rect)
                }
            }
        )
    }

    // The bar is moved up by the same height as YouTube's own miniplayer.
    BottomUiContainerReportHeightFingerprint.hookMethod {
        before { param ->
            MinimalMiniplayerPatch.setBottomUiHeight(param.args[0] as Int)
        }
    }

    // The legacy controls are the views the app still inflates into the miniplayer, which the bar
    // unhides and drives. The layout is the only view the constructor of the controls looks up, so
    // it is taken from that call instead of searching the view tree again.
    MiniplayerControlsFingerprint.hookMethod(
        scopedHook(
            DexMethod("Landroid/view/ViewGroup;->findViewById(I)Landroid/view/View;").toMethod()
        ) {
            after { param ->
                val controlsLayout = param.result as? ViewGroup ?: return@after
                legacyControlsLayoutId = controlsLayout.id
                MinimalMiniplayerPatch.setLegacyControls(controlsLayout)
            }
        }
    )

    // The app hides the controls while the miniplayer is not a bar, and the bar shows them instead.
    MiniplayerControlsVisibilityFingerprint.hookMethod(
        scopedHook(DexMethod("Landroid/view/ViewGroup;->setVisibility(I)V").toMethod()) {
            before { param ->
                if (param.args[0] != View.GONE) return@before

                val id = legacyControlsLayoutId
                if (id == 0 || (param.thisObject as? View)?.id != id) return@before

                param.args[0] = MinimalMiniplayerPatch.getLegacyControlsVisibility(View.GONE)
            }
        }
    )

    // YouTube sets the action button icon together with its content description, which is the exact
    // playback state at the moment it changes.
    MiniplayerSetIconsFingerprint.hookMethod {
        after { param ->
            MinimalMiniplayerPatch.setPlaybackIcon(param.args[1] as Int)
        }
    }

    // The progress bar under the glass bar is cut to the straight bottom edge of the glass.
    SeekbarOnDrawFingerprint.hookMethod {
        before { param ->
            MinimalMiniplayerPatch.clipSeekbar(param.thisObject as View, param.args[0] as Canvas)
        }
    }

    // The bar is laid out from the bounds the app hands the offscreen handler, and it leaves the
    // miniplayer where it is when the horizontal drag is turned off.
    MiniplayerOffscreenHandlerFingerprint.hookMethod {
        before { param ->
            val bounds = MinimalMiniplayerPatch.getMiniplayerBounds(
                param.args[0] as Int,
                param.args[1] as Int,
                param.args[2] as Int,
                param.args[3] as Int,
            )
            param.args[0] = bounds.left
            param.args[1] = bounds.top
            param.args[2] = bounds.right
            param.args[3] = bounds.bottom

            if (!MiniplayerPatch.getHorizontalDrag()) return@before

            // The rect the app records for the next frame is recorded here too, and then the rest
            // of the handler, which moves the miniplayer away, is skipped.
            (recordedRect.get(param.thisObject) as Rect)
                .set(bounds.left, bounds.top, bounds.right, bounds.bottom)
            param.result = null
        }
    }

    // endregion

    // region Horizontal drag

    // The playback is paused when the drag animation ends, instead of when the drag starts.
    ::miniplayerDragAnimatorEnd.hookMethod {
        before { param ->
            if (MiniplayerPatch.pausePlaybackWithHorizontalDrag()) {
                param.result = null
            }
        }
    }

    NextGenWatchLayoutOnInterceptTouchEventFingerprint.hookMethod {
        before { param ->
            MiniplayerPatch.enableOffScreenMiniplayerButtonPressed(param.args[0] as MotionEvent)
        }
    }

    // endregion

    // region Hide modern miniplayer buttons

    // The buttons are taken from the methods that hand them to the player, because the bar keeps
    // them to drive the playback and to expand or close the player.
    listOf(
        MiniplayerModernExpandButtonFingerprint,
        MiniplayerModernCloseButtonFingerprint,
    ).forEach { fingerprint ->
        fingerprint.hookMethod {
            after { param ->
                (param.result as? View)?.let(MiniplayerPatch::hideMiniplayerExpandClose)
            }
        }
    }

    MiniplayerModernActionButtonFingerprint.hookMethod {
        after { param ->
            (param.result as? View)?.let(MiniplayerPatch::hideMiniplayerActionButton)
        }
    }

    // endregion
}
