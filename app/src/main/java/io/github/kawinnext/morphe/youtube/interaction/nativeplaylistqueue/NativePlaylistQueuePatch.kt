/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.morphe.setExtensionIsPatchIncluded
import io.github.kawinnext.morphe.shared.misc.proto.hookElement
import io.github.kawinnext.morphe.youtube.layout.flyout.flyoutPatch
import io.github.kawinnext.morphe.youtube.misc.auth.authHookPatch
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_15_or_greater
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.videoid.VideoId
import io.github.kawinnext.morphe.youtube.video.videoid.videoIdHooks
import io.github.kawinnext.patch

/**
 * Adds a temporary video queue using YouTube's playlist interface and playback controls.
 * Used by Add to queue when the queue type is set to the native queue.
 * <p>
 * The upstream patch injects into a patched APK in one pass. Here each hook is a separate file,
 * and the interfaces the upstream patch adds to the classes of the app are module side proxies.
 */
val nativePlaylistQueuePatch = patch(
    name = "Native playlist queue",
    description = "Adds a temporary video queue using YouTube's playlist interface and playback controls."
) {
    dependsOn(
        authHookPatch,
        flyoutPatch,
        VideoInformationPatch,
        VideoId,
        PlayerTypeHook,
        VersionCheck,
    )

    // 20.x builds the native playlist queue differently.
    // Add to queue then does not offer the native queue type.
    if (!is_21_15_or_greater) return@patch

    // Same order as upstream. The picker of the queue save, the undo of a removal, the playback
    // engine and the notification of the watch history are installed by the hooks below, which are
    // the callers upstream installs them from.
    installQueueFlyout()
    installPlaylistCardSource()
    installPlaylistPages()
    installPlaylistPaging()
    installPlaylistPanel()
    installPlaylistTitle()
    installRatingCapture()
    installPlaylistRemoval()
    installFullscreenPlaylist()

    hookElement { bytes -> NativePlaylistQueuePatch.onElementLoaded(bytes) }
    videoIdHooks.add(NativePlaylistQueuePatch::onVideoChanged)

    setExtensionIsPatchIncluded(NativePlaylistQueuePatch::class.java)
}
