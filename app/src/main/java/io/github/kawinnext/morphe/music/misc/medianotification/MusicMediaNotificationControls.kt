/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3288
 * https://github.com/MorpheApp/morphe-patches/pull/4617
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.misc.medianotification

import android.media.session.MediaSession
import android.media.session.PlaybackState
import app.morphe.extension.music.patches.MusicMediaNotificationControlsPatch
import app.morphe.extension.shared.patches.MediaNotificationControlsPatch
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.MediaSessionSetPlaybackStateFingerprint
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

val MusicMediaNotificationControls = patch(
    name = "Media notification controls",
    description = "Adds options to disable the seekbar and previous/next buttons in the " +
            "media notification and headphone controls.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_notification_media_screen",
            // Keep the seek options right below the switch they depend on.
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_disable_notification_media_seekbar", summary = true),
                SwitchPreference("morphe_hide_notification_media_prev_next", summary = true),
                SwitchPreference("morphe_music_replace_shuffle_with_loop_button", summary = true)
            )
        )
    )

    // The playback state the app hands to its media session carries the actions of the media
    // notification and of the headphone controls, so it is filtered before the call is made.
    val setPlaybackState =
        MediaSession::class.java.getMethod("setPlaybackState", PlaybackState::class.java)
    MediaSessionSetPlaybackStateFingerprint.hookMethod(
        scopedHook(setPlaybackState) {
            before { param ->
                val state = param.args[0] as? PlaybackState ?: return@before
                param.args[0] = MediaNotificationControlsPatch.changePlaybackState(state)
            }
        }
    )

    // Shuffle and loop are custom actions of the media session. Only a few custom actions fit in
    // the media controls and shuffle is listed before loop, so the loop button only shows if the
    // shuffle action is not shown.
    ShuffleActionShownFingerprint.hookMethod {
        before {
            if (MusicMediaNotificationControlsPatch.hideShuffleAction()) it.result = false
        }
    }
}
