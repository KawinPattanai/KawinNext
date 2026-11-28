/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package io.github.kawinnext.morphe.youtube.misc.loadvideo

import app.morphe.extension.youtube.patches.LoadVideoPatch
import app.morphe.extension.youtube.patches.LoadVideoPatch.InlinePlaybackInterface
import app.morphe.extension.youtube.patches.LoadVideoPatch.PlayerInterface
import io.github.kawinnext.createProxy
import io.github.kawinnext.patch

internal val loadVideoHookPatch = patch(
    description = "Hooks the player so the extension can dismiss it and open videos."
) {
    val dismissPlayer = ::dismissPlayerMethod.method

    // The extension keeps only the dismiss on the player now: it opens a video with the app's own
    // intent (LoadVideoPatch.openUrl), so the player no longer hands an intent parcelable back and
    // the main activity adapter is gone with it.
    ::playerControllerConstructor.hookMethod {
        after {
            LoadVideoPatch.initialize(it.thisObject.createProxy { reference ->
                object : PlayerInterface {
                    override fun patch_dismissPlayer() {
                        reference.get()?.let { player -> dismissPlayer(player) }
                    }
                }
            })
        }
    }

    // Once the player is dismissed, a video in the feed below it can start playing
    // and take the place of the video opened next, so the extension blocks it.
    val blockInlinePlayback = BlockInlinePlaybackFingerprint.method
    val unblockInlinePlayback = UnblockInlinePlaybackFingerprint.method
    ::inlinePlaybackConstructor.hookMethod {
        after {
            LoadVideoPatch.initializeInlinePlayback(it.thisObject.createProxy { reference ->
                object : InlinePlaybackInterface {
                    override fun patch_blockInlinePlayback(reason: Int) {
                        reference.get()?.let { controller -> blockInlinePlayback(controller, reason) }
                    }

                    override fun patch_unblockInlinePlayback(reason: Int) {
                        reference.get()?.let { controller -> unblockInlinePlayback(controller, reason) }
                    }
                }
            })
        }
    }

    BackButtonFinishActivityOnNewVideoIntentFingerprint.hookMethod {
        before { it.result = null }
    }
}
