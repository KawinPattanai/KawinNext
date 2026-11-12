/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import android.view.View
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import java.lang.reflect.Field

// Upstream injects one call before the native button callback dispatches a menu command, and gives
// the swipe widget of a row an interface whose bridge caches the background of its foreground
// again. A module cannot inject a call, so the callback is hooked at its only call site, and the
// two interfaces are the proxies below. As upstream does for the undo command, which it installs
// from the removal hook, the extension decides the fate of every command of ours and the app keeps
// every command that is not.

/** The tag of the app's endpoint context, which carries the row or the panel of a menu command. */
private const val ENDPOINT_TAG = "com.google.android.libraries.youtube.innertube.endpoint.tag"

/** The native button callback, which dispatches the endpoint of a menu with its context. */
private val playlistMenuDispatch = findMethodDirect {
    NativeButtonDispatchFingerprint.match().instructionMatches.single().instruction.methodRef!!
}

/** The foreground view whose background the attached swipe widget caches. */
private val swipeForeground = findFieldDirect {
    PlaylistSwipeAttachedFingerprint.match().instructionMatches.first().instruction.fieldRef!!
}

/** The cached background of that view. */
private val swipeBackground = findFieldDirect {
    PlaylistSwipeAttachedFingerprint.match().instructionMatches.last().instruction.fieldRef!!
}

/** The native update that replaces the background of the foreground of a row. */
private val rowBackgroundUpdate = findMethodDirect { PlaylistRowBackgroundFingerprint.match().method }

/** The swipe widget of a row. */
private val rowSwipe = findFieldDirect {
    PlaylistRowSwipeLayoutFingerprint.match().instructionMatches.single().instruction.fieldRef!!
}

/** The endpoint that the click listener of a row currently holds. */
private val rowEndpoint = findFieldDirect {
    PlaylistRowClickFingerprint.match().instructionMatches[4].instruction.fieldRef!!
}

/**
 * The swipe widget of a row, which stands in for the SwipeBackground interface upstream adds to the
 * class of the widget: the bridge reads the background of the foreground again, and the widget
 * draws it when the row it holds becomes removable.
 */
private class QueueSwipeBackgroundProxy(
    private val swipe: Any,
    private val foreground: Field,
    private val background: Field
) : NativePlaylistQueuePatch.SwipeBackground {
    override fun patch_refreshQueueSwipeBackground() {
        val view = bridgeField(foreground, swipe) as? View ?: return
        background.write(swipe, view.background)
    }
}

/**
 * The click listener of a row, which stands in for the RowClick interface upstream adds to the class
 * of the listener: the bridge returns the endpoint the row is bound to.
 *
 * The endpoint is read from the listener itself, at the moment the row updates its background, which
 * is the binding pass the row makes on its own view. Upstream keeps a copy of it on bind because a
 * click can decorate the endpoint of the listener afterwards, which the binding pass has not
 * reached yet.
 */
private class QueueRowClickProxy(
    private val listener: Any,
    private val endpoint: Field
) : NativePlaylistQueuePatch.RowClick {
    override fun patch_getQueueEndpoint(): Any? = bridgeField(endpoint, listener)
}

/**
 * The context of one menu command, whose tag answers as the proxy of the object it belongs to.
 *
 * Only the extension reads this map, and only for that tag, so a copy is enough: the app receives
 * its own context untouched, whether the command is consumed here or falls through to the native
 * dispatch, and the extension cannot change it.
 */
private fun ownerContext(context: Map<*, *>, owner: Any): Map<*, *> {
    val tagged = HashMap<Any?, Any?>(context.size + 1)
    context.forEach { (key, value) -> tagged[key] = value }
    tagged[ENDPOINT_TAG] = owner
    return tagged
}

/**
 * Gives the extension every menu command of ours, and consumes the commands it decides on.
 *
 * Upstream injects the call before the native dispatch and replaces the endpoint it dispatches with
 * the one the extension returns. Here the same callback is hooked: a command that the extension
 * consumes skips the native dispatch, and a command it rewrites is dispatched in its new form.
 */
internal fun PatchExecutor.installPlaylistRemoval() {
    // Upstream installs the undo command of a removal from this hook, before it observes the
    // dispatch that carries every command of the menu.
    installQueueUndo()

    ::playlistMenuDispatch.method.hookMethod {
        before { param ->
            val endpoint = param.args.getOrNull(0) ?: return@before
            val context = param.args.getOrNull(1) as? Map<*, *> ?: return@before
            val owner = queueOwnerProxy(context[ENDPOINT_TAG]) ?: return@before
            val command = queueAction("onQueueMenuCommand") {
                NativePlaylistQueuePatch.onQueueMenuCommand(
                    PlaylistQueueRenderer.NavigationEndpoint.parseFrom(appMessageBytes(endpoint)!!),
                    ownerContext(context, owner)
                )
            } ?: run {
                param.result = null
                return@before
            }

            val dispatched = appMessageBytes(endpoint) ?: return@before
            if (command.toByteArray().contentEquals(dispatched)) return@before
            param.args[0] = parseAppMessage(endpoint.javaClass, command.toByteArray())
        }
    }
}

/**
 * Keeps the background a row draws while it is swiped from replacing the one of the row that is
 * attached now.
 *
 * Upstream gives the widget an interface and calls its bridge from the background update of the row,
 * after the foreground view was loaded. Here the same update is hooked, and the widget and the click
 * listener of the row are read from the holder of that update.
 *
 * @param listener The field of a row that holds the click listener of that row.
 */
internal fun PatchExecutor.installPlaylistSwipeBackground(listener: Field) {
    val listenerField = listener
    val foreground = ::swipeForeground.field
    val background = ::swipeBackground.field
    val swipeField = ::rowSwipe.field
    val endpoint = ::rowEndpoint.field
    val swipes = ProxyCache { swipe: Any -> QueueSwipeBackgroundProxy(swipe, foreground, background) }

    ::rowBackgroundUpdate.method.hookMethod {
        after { param ->
            val holder = param.thisObject ?: return@after
            val rowListener = bridgeField(listenerField, holder) ?: return@after
            val swipe = bridgeField(swipeField, holder) ?: return@after
            queueAction("onSwipeBackgroundChanged") {
                NativePlaylistQueuePatch.onSwipeBackgroundChanged(
                    QueueRowClickProxy(rowListener, endpoint),
                    swipes.get(swipe)
                )
            }
        }
    }
}
