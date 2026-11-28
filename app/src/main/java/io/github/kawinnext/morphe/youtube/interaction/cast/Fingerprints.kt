/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.cast

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.literal

/**
 * When a different video is selected while casting, the flag disconnects the Cast session
 * and plays the video on the phone, unless the video is from the queue of the TV.
 *
 * The flag is read through the flag method that FeatureOverride hooks, so the id is the key the
 * override is registered with, and the fingerprint proves the read is still in the app.
 */
internal const val CAST_DISCONNECT_ON_VIDEO_SELECT_FEATURE_FLAG = 45693138L

internal object CastDisconnectOnVideoSelectFeatureFlagFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        literal(CAST_DISCONNECT_ON_VIDEO_SELECT_FEATURE_FLAG)
    )
)

/**
 * When the player is closed or minimized while casting, the flag disconnects the Cast session.
 * The flag is also read by the minimize button of the player, which then shows a close icon.
 *
 * More than one method reads the flag, so no single-method fingerprint can name it: upstream
 * overrides every method the flag is read in, and here the read is overridden by the id, which
 * covers all of them at once.
 */
internal const val CAST_DISCONNECT_ON_PLAYER_CLOSE_FEATURE_FLAG = 45798912L

/**
 * Picks where the player goes when swiped down.
 * While casting, the player is dismissed into the You tab instead of minimized.
 */
internal const val CAST_DISMISS_PLAYER_ON_SWIPE_FEATURE_FLAG = 45783248L

internal object CastSwipeDownTargetFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
    parameters = listOf("I"),
    filters = listOf(
        literal(CAST_DISMISS_PLAYER_ON_SWIPE_FEATURE_FLAG),
        literal(4096L)
    )
)
