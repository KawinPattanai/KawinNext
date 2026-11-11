/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.video.information

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.string

/**
 * The player class that exposes a seek method, which logs the position it seeks to.
 */
internal object VideoEndFingerprint : Fingerprint(
    parameters = listOf("J", "L"),
    filters = listOf(
        string("currentPositionMs."),
        string(";seekTimeUs.")
    )
)

/**
 * The method called with the player response model when a new track loads.
 * Parameters are (playerResponseModel, videoId). Music 9.24 also has a boolean.
 */
internal object VideoIdFingerprint : Fingerprint(
    parameters = listOf("L", "Ljava/lang/String;"),
    filters = listOf(
        string("Null initialPlayabilityStatus")
    )
)
