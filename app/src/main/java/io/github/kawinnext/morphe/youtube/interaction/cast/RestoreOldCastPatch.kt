/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5441
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.cast

import app.morphe.extension.youtube.patches.RestoreOldCastPatch
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.insertLiteralOverride
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_28_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch

/**
 * The three flags the app decides about the Cast session by are turned off while the setting is on,
 * which leaves the session connected. The flags are read through the flag method that FeatureOverride
 * hooks, so each read is overridden by its id and no hook of a reading method is needed.
 */
val restoreOldCastPatch = patch(
    name = "Restore old Cast behavior",
    description = "Adds an option to keep the Cast session connected when selecting a different video " +
            "or closing the player, so videos keep playing on the TV."
) {
    dependsOn(
        VersionCheck,
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_restore_old_cast_behavior", summary = true)
    )

    // The app disconnects the Cast session and plays the selected video on the phone unless the
    // video is from the queue of the TV.
    insertLiteralOverride(
        CAST_DISCONNECT_ON_VIDEO_SELECT_FEATURE_FLAG,
        RestoreOldCastPatch::disconnectCastOnVideoSelect
    )

    // From 21.30 the player decides by this flag whether a swipe down dismisses it into the You tab
    // or minimizes it, which is the shape the fingerprint above still has in the targets.
    if (is_21_30_or_greater) {
        insertLiteralOverride(
            CAST_DISMISS_PLAYER_ON_SWIPE_FEATURE_FLAG,
            RestoreOldCastPatch::dismissPlayerOnSwipeWhileCasting
        )
    }

    // From 21.28 the flag is read both when the player is closed and when it is minimized,
    // and the minimize button of the player shows a close icon because of it.
    if (is_21_28_or_greater) {
        insertLiteralOverride(
            CAST_DISCONNECT_ON_PLAYER_CLOSE_FEATURE_FLAG,
            RestoreOldCastPatch::disconnectCastOnPlayerClose
        )
    }
}
