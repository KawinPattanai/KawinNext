/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/1842
 * https://github.com/MorpheApp/morphe-patches/pull/4768
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.sponsorblock

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string

private const val MUSIC_TIME_BAR_CLASS =
    "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;"

/** Draw of the seekbar of the compact and mini player, over which the segments are drawn. */
internal object MusicTimeBarDrawFingerprint : Fingerprint(
    definingClass = MUSIC_TIME_BAR_CLASS,
    name = "draw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;")
)

/** Measure of the seekbar, which sets the bounds of the track the segments are drawn on. */
internal object MusicTimeBarOnMeasureFingerprint : Fingerprint(
    definingClass = MUSIC_TIME_BAR_CLASS,
    name = "onMeasure",
    returnType = "V"
)

/** Bounds of the track of the seekbar, which the last bounds the measure reads. */
val musicTimeBarRectField = findFieldDirect {
    MusicTimeBarOnMeasureFingerprint().instructions
        .last { instruction -> instruction.fieldRef?.type?.descriptor == "Landroid/graphics/Rect;" }
        .fieldRef!!
}

private const val REMOVE_OFFLINE_TRACK_STRING = "Couldn't delete single track through orchestration: %s"

/**
 * The endpoint that saves a song or a video for offline playback.
 * The call that saves the object has the video ID of the object.
 */
internal object MusicOfflineVideoEndpointFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    strings = listOf("Object is not an offlineable video: %s"),
    filters = listOf(
        methodCall(
            opcodes = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_VIRTUAL),
            parameters = listOf("Ljava/lang/String;", "L", "L", "L"),
            returnType = "V"
        )
    )
)

/** Saves the song or the video of a video ID for offline playback. */
val musicOfflineVideoSaveMethod = findMethodDirect {
    MusicOfflineVideoEndpointFingerprint.instructionMatches.first().instruction.methodRef!!
}

/**
 * Removes a saved song or a video, after any prompt is confirmed.
 */
internal object MusicRemoveOfflineTrackFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf(
        "Ljava/lang/String;", // Video ID
        "Ljava/lang/String;"
    ),
    filters = listOf(
        string(REMOVE_OFFLINE_TRACK_STRING)
    )
)

/**
 * Removes a saved song or a video with its own copy of the code of [MusicRemoveOfflineTrackFingerprint].
 * Returns early without removing it if the app cannot remove it right now.
 */
internal object MusicRemoveOfflineTrackActionFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"), // Video ID
    filters = listOf(
        opcode(Opcode.RETURN_VOID), // Early return.
        string(REMOVE_OFFLINE_TRACK_STRING)
    )
)
