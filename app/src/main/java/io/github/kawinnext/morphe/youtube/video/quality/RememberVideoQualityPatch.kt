package io.github.kawinnext.morphe.youtube.video.quality

import app.morphe.extension.youtube.patches.playback.quality.RememberVideoQualityPatch
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.insertLiteralOverride
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.shared.VideoQualityReceiver
import io.github.kawinnext.morphe.youtube.shared.VideoQualityChangedFingerprint
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.information.VideoQualityProxy
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import `j$`.util.Optional

val RememberVideoQuality = patch {
    dependsOn(
        VideoInformationPatch,
        PlayerTypeHook,
    )

    settingsMenuVideoQualityGroup.addAll(
        listOf(
            ListPreference(
                key = "morphe_video_quality_default_mobile",
                entriesKey = "morphe_video_quality_default_entries",
                entryValuesKey = "morphe_video_quality_default_entry_values"
            ),
            ListPreference(
                key = "morphe_video_quality_default_wifi",
                entriesKey = "morphe_video_quality_default_entries",
                entryValuesKey = "morphe_video_quality_default_entry_values"
            ),
            SwitchPreference("morphe_remember_video_quality_last_selected", summary = true),

            ListPreference(
                key = "morphe_shorts_quality_default_mobile",
                entriesKey = "morphe_shorts_quality_default_entries",
                entryValuesKey = "morphe_shorts_quality_default_entry_values",
            ),
            ListPreference(
                key = "morphe_shorts_quality_default_wifi",
                entriesKey = "morphe_shorts_quality_default_entries",
                entryValuesKey = "morphe_shorts_quality_default_entry_values"
            ),
            SwitchPreference("morphe_remember_shorts_quality_last_selected", summary = true),
            SwitchPreference("morphe_remember_video_quality_last_selected_toast", summary = true)
        )
    )

    ::PlaybackStartParametersInit.hookMethod {
        val initialResolutionField = ::InitialResolutionField.field
        after {
            val oldValue = initialResolutionField.get(it.thisObject)
            val newValue = RememberVideoQualityPatch.getInitialVideoQuality(oldValue as Optional<*>?)
            initialResolutionField.set(it.thisObject, newValue)
        }
    }

    insertLiteralOverride(45408049L, RememberVideoQualityPatch::useInitialVideoQuality)

    val advancedMenu = ::advancedVideoQualitySettingField.field.get(null)
    ::getVideoQualitySettingMethod.hookMethod {
        before {
            if (RememberVideoQualityPatch.useAdvancedVideoQualitySetting()) {
                it.result = advancedMenu
            }
        }
    }

    ::getVideoQualityConstraintMethod.hookMethod {
        val fixedConstraint = ::fixedVideoQualityConstraintMethod.method
        after {
            val quality = RememberVideoQualityPatch.getLastSelectedVideoQuality()
            if (quality >= 0 && it.result != null) {
                it.result = fixedConstraint(it.result, quality)
            }
        }
    }

    VideoQualityItemOnClickFingerprint.hookMethod {
        before { param ->
            RememberVideoQualityPatch.userChangedShortsQuality(param.args[2] as Int)
        }
    }

    VideoQualityChangedFingerprint.hookMethod(scopedHook(::VideoQualityReceiver.member) {
        before { param ->
            RememberVideoQualityPatch.userChangedQuality(VideoQualityProxy(param.args[0]))
        }
    })

    // If this flag is enabled, Shorts restart whenever the quality changes.
    // The restart uses the initial quality of the Short, so the quality selected by the user is lost.
    insertLiteralOverride(45387052)
    insertLiteralOverride(45399743)
}
