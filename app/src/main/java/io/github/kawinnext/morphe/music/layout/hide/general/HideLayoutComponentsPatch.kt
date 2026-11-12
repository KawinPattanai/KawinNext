/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.hide.general

import android.view.View
import app.morphe.extension.music.patches.components.CommentsFilter
import app.morphe.extension.music.patches.components.LayoutComponentsFilter
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.ResourceType
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.music.misc.litho.filter.LithoFilter
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

private val findViewByIdMethod = DexMethod("Landroid/view/View;->findViewById(I)Landroid/view/View;")

private val inflateMethods = listOf(
    DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;"),
    DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;")
)

// The app has more than one player, and each of them finds its own view, so every match is hooked.
private val audioVideoToggleMethods = findMethodListDirect {
    AudioVideoSwitchPillContainerFingerprint.matchAll().map { it.method }
}

private val overlayLoopMethods = findMethodListDirect {
    OverlayQueueLoopButtonFingerprint.matchAll().map { it.method }
}

private val playbackLoopMethods = findMethodListDirect {
    PlaybackQueueLoopButtonFingerprint.matchAll().map { it.method }
}

private val overlayShuffleMethods = findMethodListDirect {
    OverlayQueueShuffleButtonFingerprint.matchAll().map { it.method }
}

private val playbackShuffleMethods = findMethodListDirect {
    PlaybackQueueShuffleButtonFingerprint.matchAll().map { it.method }
}

private val commentsInfoButtonMethod = findMethodDirect {
    InformationButtonFingerprint.match().method
}

private val chipCloudMethod = findMethodDirect {
    ChipCloudFingerprint.match().method
}

/**
 * The settings of this patch. Each of the views below is hidden by the method that finds it,
 * where the upstream patch inserts the call into the method itself. A runtime hook cannot insert
 * into a method, so the view is taken from the call the method makes instead.
 */
val HideLayoutComponents = patch(
    name = "Hide layout components",
    description = "Adds options to hide the shelves and the components of the feed and of the player."
) {
    dependsOn(LithoFilter)

    PreferenceScreen.FEED.addPreferences(
        SwitchPreference("morphe_music_hide_podcast_episode_download_button", summary = true),
        SwitchPreference("morphe_music_hide_explore_shelf"),
        SwitchPreference("morphe_music_hide_grid_shelves"),
        SwitchPreference("morphe_music_hide_filter_bar"),
        SwitchPreference("morphe_music_hide_horizontal_shelves"),
        SwitchPreference("morphe_music_hide_list_shelves"),
        SwitchPreference("morphe_music_hide_new_from_shelf"),
        SwitchPreference("morphe_music_hide_playlist_shelves"),
        SwitchPreference("morphe_music_hide_speed_dial_shelf"),
        SwitchPreference("morphe_music_hide_your_downloads_shelf")
    )

    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_music_hide_comments_info_button")
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_hide_audio_video_toggle"),
        SwitchPreference("morphe_music_hide_repeat_button"),
        SwitchPreference("morphe_music_hide_shuffle_button")
    )

    addLithoFilter(LayoutComponentsFilter())

    val audioVideoToggleId = ResourceUtils.getIdentifier(ResourceType.ID, "audio_video_switch_pill_container")
    if (audioVideoToggleId != 0) {
        ::audioVideoToggleMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == audioVideoToggleId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideAudioVideoToggle)
                        }
                    }
                }
            )
        }
    }

    val commentsInfoButtonId = ResourceUtils.getIdentifier(ResourceType.ID, "information_button")
    if (commentsInfoButtonId != 0) {
        findViewByIdMethod.hookMethod(
            scopedHook(::commentsInfoButtonMethod.member) {
                after { param ->
                    if (param.args[0] == commentsInfoButtonId) {
                        (param.result as? View)?.let(CommentsFilter::hideCommentsInfoButton)
                    }
                }
            }
        )
    }

    val overlayLoopId = ResourceUtils.getIdentifier(ResourceType.ID, "overlay_queue_loop_button_view")
    if (overlayLoopId != 0) {
        ::overlayLoopMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == overlayLoopId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideRepeatButton)
                        }
                    }
                }
            )
        }
    }

    val playbackLoopId = ResourceUtils.getIdentifier(ResourceType.ID, "playback_queue_loop_button_view")
    if (playbackLoopId != 0) {
        ::playbackLoopMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == playbackLoopId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideRepeatButton)
                        }
                    }
                }
            )
        }
    }

    val overlayShuffleId = ResourceUtils.getIdentifier(ResourceType.ID, "overlay_queue_shuffle_button_view")
    if (overlayShuffleId != 0) {
        ::overlayShuffleMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == overlayShuffleId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideShuffleButton)
                        }
                    }
                }
            )
        }
    }

    val playbackShuffleId = ResourceUtils.getIdentifier(ResourceType.ID, "playback_queue_shuffle_button_view")
    if (playbackShuffleId != 0) {
        ::playbackShuffleMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == playbackShuffleId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideShuffleButton)
                        }
                    }
                }
            )
        }
    }

    // The filter bar is inflated as a chip cloud, and the view that is inflated is the bar.
    val chipCloudLayout = ResourceUtils.getIdentifier(ResourceType.LAYOUT, "chip_cloud")
    if (chipCloudLayout != 0) {
        inflateMethods.forEach { inflateMethod ->
            inflateMethod.hookMethod(
                scopedHook(::chipCloudMethod.member) {
                    after { param ->
                        if (param.args[0] == chipCloudLayout) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideFilterBar)
                        }
                    }
                }
            )
        }
    }
}
