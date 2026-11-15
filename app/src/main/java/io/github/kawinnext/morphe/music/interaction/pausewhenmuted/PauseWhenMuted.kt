/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4778
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.interaction.pausewhenmuted

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import app.morphe.extension.music.patches.PauseWhenMutedPatch
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.MediaSessionSetMetadataFingerprint
import io.github.kawinnext.morphe.shared.MediaSessionSetPlaybackStateFingerprint
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

val PauseWhenMuted = patch(
    name = "Pause when muted",
    description = "Adds an option to pause playback when the system volume is muted, " +
            "and to resume it when the volume is raised."
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_pause_when_muted", summary = true)
    )

    // The media session is needed to pause and resume YouTube Music's own player.
    // The metadata call gives the extension the session, and the playback state one tells it
    // when the app started playing again by itself, so a later manual pause is respected.
    val setMetadata = MediaSession::class.java.getMethod("setMetadata", MediaMetadata::class.java)
    MediaSessionSetMetadataFingerprint.hookMethod(
        scopedHook(setMetadata) {
            before { param ->
                PauseWhenMutedPatch.onSetMetadata(
                    param.thisObject as MediaSession,
                    param.args[0] as? MediaMetadata
                )
            }
        }
    )

    val setPlaybackState =
        MediaSession::class.java.getMethod("setPlaybackState", PlaybackState::class.java)
    MediaSessionSetPlaybackStateFingerprint.hookMethod(
        scopedHook(setPlaybackState) {
            before { param ->
                PauseWhenMutedPatch.onSetPlaybackState(param.args[0] as? PlaybackState)
            }
        }
    )
}
