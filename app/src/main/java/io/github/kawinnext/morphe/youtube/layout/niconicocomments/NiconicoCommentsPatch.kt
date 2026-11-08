/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4080
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Niconico-style live/replay comments implementation is adapted from
 * https://github.com/david419kr/niconico-yt-morphe-patches (GPLv3)
 * Feature design is based on david419kr/niconico-yt (MIT License)
 */

package io.github.kawinnext.morphe.youtube.layout.niconicocomments

import android.view.ViewGroup
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.niconicocomments.NiconicoCommentsController
import app.morphe.extension.youtube.niconicocomments.NiconicoCommentsToggleButton
import app.morphe.extension.youtube.settings.preference.NiconicoCommentsSeparatePortraitPreference
import io.github.kawinnext.R
import io.github.kawinnext.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.kawinnext.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.kawinnext.morphe.youtube.layout.sponsorblock.controlsOverlayFingerprint
import io.github.kawinnext.morphe.youtube.layout.sponsorblock.inset_overlay_view_layout
import io.github.kawinnext.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.kawinnext.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.kawinnext.morphe.youtube.misc.playercontrols.addLegacyBottomControl
import io.github.kawinnext.morphe.youtube.misc.playercontrols.initializeLegacyBottomControl
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.information.videoTimeHooks
import io.github.kawinnext.morphe.youtube.video.videoid.VideoId
import io.github.kawinnext.morphe.youtube.video.videoid.videoIdHooks
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val niconicoCommentsPatch = patch(
    name = "Niconico comments",
    description = "Adds an option to show live chat and chat replay as comments that scroll across the video."
) {
    dependsOn(
        PlayerTypeHook,
        VideoId,
        VideoInformationPatch,
        playerOverlayButtonsHook,
        LegacyPlayerControls,
    )

    fun seekBarPreference(key: String) = NonInteractivePreference(
        key = key,
        tag = SeekBarPreference::class.java,
        selectable = true
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_niconico_comments_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference(
                    "morphe_niconico_comments",
                    titleKey = "morphe_niconico_comments_screen_title",
                    summary = true
                ),
                PreferenceCategory(
                    key = "morphe_niconico_comments_appearance_category",
                    titleKey = "morphe_niconico_comments_appearance_title",
                    sorting = Sorting.UNSORTED,
                    preferences = setOf(
                        seekBarPreference("morphe_niconico_comments_font_size"),
                        seekBarPreference("morphe_niconico_comments_opacity"),
                        seekBarPreference("morphe_niconico_comments_travel_duration"),
                        seekBarPreference("morphe_niconico_comments_display_area"),
                        SwitchPreference("morphe_niconico_comments_hide_crowded", summary = true),
                        seekBarPreference("morphe_niconico_comments_max_length"),
                        seekBarPreference("morphe_niconico_comments_max_refresh_rate")
                    )
                ),
                PreferenceCategory(
                    key = "morphe_niconico_comments_portrait_category",
                    titleKey = "morphe_niconico_comments_portrait_category_title",
                    sorting = Sorting.UNSORTED,
                    preferences = setOf(
                        SwitchPreference("morphe_niconico_comments_portrait", summary = true),
                        SwitchPreference(
                            key = "morphe_niconico_comments_separate_portrait",
                            summary = true,
                            tag = NiconicoCommentsSeparatePortraitPreference::class.java
                        ),
                        seekBarPreference("morphe_niconico_comments_portrait_font_size"),
                        seekBarPreference("morphe_niconico_comments_portrait_travel_duration"),
                        seekBarPreference("morphe_niconico_comments_portrait_display_area")
                    )
                ),
                PreferenceCategory(
                    key = "morphe_niconico_comments_video_types_category",
                    titleKey = "morphe_niconico_comments_video_types_title",
                    sorting = Sorting.UNSORTED,
                    preferences = setOf(
                        SwitchPreference("morphe_niconico_comments_live_streams", summary = true),
                        SwitchPreference("morphe_niconico_comments_live_premieres", summary = true),
                        SwitchPreference("morphe_niconico_comments_ended_premieres", summary = true),
                        SwitchPreference("morphe_niconico_comments_timed_comments", summary = true),
                        SwitchPreference("morphe_niconico_comments_timed_comments_with_replay", summary = true)
                    )
                )
            )
        )
    )

    videoIdHooks.add { NiconicoCommentsController.newVideoLoaded(it) }
    videoTimeHooks.add { NiconicoCommentsController.setVideoTime(it) }

    addPlayerBottomButton(NiconicoCommentsToggleButton::initializeButton)
    addLegacyBottomControl(R.layout.morphe_niconico_comments_button)
    initializeLegacyBottomControl(
        ControlInitializer(
            R.id.morphe_niconico_comments_button_container,
            NiconicoCommentsToggleButton::initializeLegacyButton
        )
    )

    // Add the comments overlay to the same player layout as the SponsorBlock overlay.
    val controls_overlay_layout =
        ResourceUtils.getLayoutIdentifier("size_adjustable_youtube_controls_overlay")
    ::controlsOverlayFingerprint.hookMethod(
        scopedHook(
            DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;").toMember()
        ) {
            val insetOverlayViewLayout = inset_overlay_view_layout
            after { param ->
                if (param.args[0] != controls_overlay_layout) return@after
                val layout = param.result as ViewGroup
                val overlayView = layout.findViewById<ViewGroup>(insetOverlayViewLayout)
                NiconicoCommentsController.initialize(overlayView)
            }
        }
    )
}
