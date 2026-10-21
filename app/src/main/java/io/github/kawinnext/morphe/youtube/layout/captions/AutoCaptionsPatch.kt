package io.github.kawinnext.morphe.youtube.layout.captions

import app.morphe.extension.youtube.patches.AutoCaptionsPatch
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.youtube.insertLiteralOverride
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_20_26_or_greater
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.shared.hookVideoStarted
import io.github.kawinnext.morphe.youtube.shared.startVideoInformerPatch
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.information.onCreateHook
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

val AutoCaptions = patch(
    name = "Auto captions",
    description = "Adds an option to disable captions from being automatically enabled.",
) {
    dependsOn(
        VersionCheck,
        startVideoInformerPatch,
        VideoInformationPatch
    )

    PreferenceScreen.PLAYER.addPreferences(
        if (is_20_26_or_greater) {
            ListPreference("morphe_auto_captions_style")
        } else {
            ListPreference(
                key = "morphe_auto_captions_style",
                entriesKey = "morphe_auto_captions_style_legacy_entries",
                entryValuesKey = "morphe_auto_captions_style_legacy_entry_values"
            )
        }
    )

    SubtitleManagerFingerprint.hookMethod(scopedHook(::subtitleTrackIsDisableOptionMethod.method) {
        after {
            if (outerParam.getObjectExtra("autoCaptionsHandled") == true) return@after
            outerParam.setObjectExtra("autoCaptionsHandled", true)
            it.result = AutoCaptionsPatch.disableAutoCaptions(it.result as Boolean)
        }
    })

    onCreateHook.add { AutoCaptionsPatch.newVideoStarted(it) }

    hookVideoStarted(AutoCaptionsPatch::videoInformationLoaded)

    // Disable mute auto captions feature flag.
    if (is_20_26_or_greater) {
        insertLiteralOverride(45692436L, AutoCaptionsPatch::disableMuteAutoCaptions)
    }
}
