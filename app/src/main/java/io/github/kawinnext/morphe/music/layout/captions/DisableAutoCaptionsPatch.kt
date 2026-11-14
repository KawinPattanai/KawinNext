/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4773
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.captions

import app.morphe.extension.music.patches.DisableAutoCaptionsPatch
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.patch

val DisableAutoCaptions = patch(
    name = "Disable auto captions",
    description = "Adds an option to disable captions from being automatically enabled.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_disable_auto_captions", summary = true)
    )

    val reasonField = ::setCaptionTrackReasonField.field.apply { isAccessible = true }

    // The setter applies the track of the event, or the default track of the caption tracks of the
    // video when the event has no track or has the option to disable captions. The patched app
    // replaces both values before they are used, which a hook of a method cannot do. The setter is
    // skipped instead, so the captions are not enabled automatically and no track is applied.
    SetCaptionTrackFingerprint.hookMethod {
        before {
            val reason = runCatching { reasonField.getInt(it.args[0]) }.getOrDefault(0)
            if (DisableAutoCaptionsPatch.disableAutoCaptions(false, reason)) {
                it.result = null
            }
        }
    }
}
