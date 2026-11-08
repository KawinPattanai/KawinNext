/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4178
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.misc.announcements

import android.app.Activity
import app.morphe.extension.shared.patches.PatchesUpdateNoticePatch
import de.robv.android.xposed.XC_MethodReplacement
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.shared.YouTubeActivityOnCreateFingerprint
import io.github.kawinnext.patch

val patchesUpdateNoticePatch = patch(
    name = "Patches update notice",
    description = "Shows a notice on app startup when new patches with changes for the app are available."
) {
    PreferenceScreen.MISC.addPreferences(
        ListPreference("morphe_patches_update_notice")
    )

    // The extension is not part of the patched app, so the changelog scope is replaced with a hook
    // instead of the instruction edit upstream does.
    PatchesUpdateNoticePatch::class.java
        .getDeclaredMethod("getChangelogScope")
        .hookMethod(XC_MethodReplacement.returnConstant("YouTube"))

    YouTubeActivityOnCreateFingerprint.hookMethod {
        before { PatchesUpdateNoticePatch.showNoticeIfNeeded(it.thisObject as Activity) }
    }
}
