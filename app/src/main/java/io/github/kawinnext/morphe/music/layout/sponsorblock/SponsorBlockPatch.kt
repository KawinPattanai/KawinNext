/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/1842
 * https://github.com/MorpheApp/morphe-patches/pull/4768
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.sponsorblock

import android.graphics.Canvas
import android.graphics.Rect
import app.morphe.extension.music.sponsorblock.MusicSponsorBlockConfig
import app.morphe.extension.shared.sponsorblock.ui.SponsorBlockAboutPreference
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.kawinnext.morphe.music.video.information.musicVideoIdHooks
import io.github.kawinnext.morphe.music.video.information.musicVideoInformationPatch
import io.github.kawinnext.morphe.music.video.information.musicVideoTimeHooks
import io.github.kawinnext.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.TextPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.kawinnext.morphe.youtube.layout.sponsorblock.categoryPreference
import io.github.kawinnext.patch

val MusicSponsorBlock = patch(
    name = "SponsorBlock",
    description = "Adds options to enable and configure SponsorBlock, which can skip non-music segments.",
) {
    dependsOn(
        musicVideoInformationPatch
    )

    PreferenceScreen.SPONSORBLOCK.addPreferences(
        SwitchPreference("morphe_sb_enabled", summary = true),
        SwitchPreference("morphe_sb_toast_on_skip", summary = true),

        PreferenceCategory(
            key = "morphe_sb_diff_segments",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                categoryPreference("morphe_sb_sponsor_color"),
                categoryPreference("morphe_sb_selfpromo_color"),
                categoryPreference("morphe_sb_interaction_color"),
                categoryPreference("morphe_sb_intro_color"),
                categoryPreference("morphe_sb_outro_color"),
                categoryPreference("morphe_sb_preview_color"),
                categoryPreference("morphe_sb_hook_color"),
                categoryPreference("morphe_sb_filler_color"),
                categoryPreference("morphe_sb_music_offtopic_color")
            )
        ),

        PreferenceCategory(
            key = "morphe_sb_general",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_sb_offline_segments", summary = true),
                SwitchPreference("morphe_sb_toast_on_connection_error", summary = true),
                TextPreference("morphe_sb_api_url")
            )
        ),

        screenInfoPreferenceCategory(
            key = "morphe_sb_about",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_sb_about_api",
                    titleKey = null,
                    tag = SponsorBlockAboutPreference::class.java,
                    selectable = true
                )
            )
        )
    )

    // Configure the shared SponsorBlock API and clear the state of the track that played.
    MusicActivityOnCreateFingerprint.hookMethod {
        before {
            MusicSponsorBlockConfig.initialize()
        }
    }

    // Drives the skip-segment logic of the track that plays.
    musicVideoTimeHooks.add { time -> MusicSponsorBlockConfig.setVideoTime(time) }

    // Triggers the SponsorBlock API fetch when a new track loads.
    musicVideoIdHooks.add { videoId -> MusicSponsorBlockConfig.setCurrentVideoId(videoId) }

    // Save the segments of downloaded songs and videos.
    ::musicOfflineVideoSaveMethod.hookMethod {
        before {
            val videoId = it.args[0] as? String ?: return@before
            MusicSponsorBlockConfig.offlineVideoAdded(videoId)
        }
    }

    // Remove the segments of songs and videos that are removed from the downloads.
    MusicRemoveOfflineTrackFingerprint.hookMethod {
        before {
            val videoId = it.args[0] as? String ?: return@before
            MusicSponsorBlockConfig.offlineVideoRemoved(videoId)
        }
    }

    MusicRemoveOfflineTrackActionFingerprint.hookMethod {
        after {
            val videoId = it.args[0] as? String ?: return@after
            MusicSponsorBlockConfig.offlineVideoRemoved(videoId)
        }
    }

    // Segment markers over the seekbar of the compact and mini player.
    val rectField = ::musicTimeBarRectField.field.apply { isAccessible = true }

    MusicTimeBarDrawFingerprint.hookMethod {
        after {
            val canvas = it.args[0] as? Canvas ?: return@after
            MusicSponsorBlockConfig.setSeekbarRectangle(rectField.get(it.thisObject) as? Rect)
            MusicSponsorBlockConfig.drawSegmentTimeBars(canvas)
        }
    }
}
