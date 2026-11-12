/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findMethodDirect

// Upstream injects the extension decision before the menu sheet dispatches the command it was given,
// installs the picker of the owned queue for the endpoint of that command, and jumps to the dismissal
// of the sheet when the extension consumes the command. A module cannot inject into the middle of a
// method, so the command callback the sheet calls is hooked instead: a consumed command leaves the
// native dispatch without running, and the sheet then dismisses itself on its own, which is the
// dismissal the upstream jump reaches.

/** The tag of the app's endpoint context, which carries the row or the panel of a menu command. */
private const val ENDPOINT_TAG = "com.google.android.libraries.youtube.innertube.endpoint.tag"

/** The command callback the button of a row hands its command to. */
private val playlistButtonDispatch = findMethodDirect {
    NativeButtonDispatchFingerprint.match().instructionMatches.single().instruction.methodRef!!
}

/** The endpoint callback of the menu sheet, which its installer sets before the sheet is looked up. */
private var playlistMenuCallback: String? = null

/** The command callback the menu sheet of the header and of the rows dispatches through. */
private val playlistMenuCommand = findMethodDirect {
    val callback = playlistMenuCallback
        ?: error("Native playlist queue: the endpoint callback of the menu sheet is missing")
    playlistMenuDispatchFingerprint(callback).match()
        .instructionMatches[1].instruction.methodRef!!
}

/**
 * Watches the shared header and row service-menu callback.
 *
 * The callback already validates and logs the command before this point, and a local queue action
 * then calls the same dismissal the native path uses. Unowned endpoints and other commands continue
 * through the original call.
 *
 * @param callbackType The descriptor of the endpoint callback the sheet of the menu dispatches
 * through, which the row hook reads from the menu it opens.
 */
internal fun PatchExecutor.installPlaylistMenuDispatch(callbackType: String) {
    playlistMenuCallback = callbackType
    val command = ::playlistMenuCommand.method
    check(command.parameterCount == 2 && command.parameterTypes[1] == Map::class.java) {
        "Native playlist queue: the menu sheet dispatches another command than an endpoint and its context"
    }

    // The picker of the owned queue is opened from the endpoint of this very command.
    installQueueSavePicker()

    // The button of a row hands its command to the same callback, and the removal hook of the module
    // already decides every command of ours there. Deciding one command twice would consume it twice,
    // so the sheet only gets its own hook when it dispatches through another callback.
    if (command == ::playlistButtonDispatch.method) return

    command.hookMethod {
        before { param ->
            val endpoint = param.args.getOrNull(0) ?: return@before
            val context = param.args.getOrNull(1) as? Map<*, *> ?: return@before
            val owner = queueOwnerProxy(context[ENDPOINT_TAG]) ?: return@before
            // The extension reads the owner of the command from the context, and the app receives
            // its own context untouched, whether the command is consumed here or falls through.
            val tagged = HashMap<Any?, Any?>(context.size + 1).apply {
                putAll(context)
                put(ENDPOINT_TAG, owner)
            }
            val consumed = queueAction("onQueueMenuCommand") {
                NativePlaylistQueuePatch.onQueueMenuCommand(
                    PlaylistQueueRenderer.NavigationEndpoint.parseFrom(appMessageBytes(endpoint)!!),
                    tagged
                )
            } ?: run {
                param.result = null
                return@before
            }

            val dispatched = appMessageBytes(endpoint) ?: return@before
            if (consumed.toByteArray().contentEquals(dispatched)) return@before
            param.args[0] = parseAppMessage(endpoint.javaClass, consumed.toByteArray())
        }
    }
}
