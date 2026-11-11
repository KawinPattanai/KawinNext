/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4685
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.interaction.loop

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterAnywhere
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string

internal const val TIME_BAR_CLASS =
    "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;"

/** Seek of the music player, which the app and the media session both use. */
internal object MedialibPlayerSeekToFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("J", "L"),
    filters = listOf(
        string("MedialibPlayer.seekTo(positionMillis=%d)")
    )
)

/** Sets the scrub thumb position from the touch x coordinate. */
internal object TimeBarSetScrubPositionFingerprint : Fingerprint(
    definingClass = TIME_BAR_CLASS,
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        // Thumb center bounds.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Landroid/graphics/Rect;"
        ),
        methodCall(definingClass = "Ljava/lang/Math;", name = "max")
    )
)

/** Bounds of the thumb, which the time bar keeps for the scrub position. */
val timeBarThumbRectField = findFieldDirect {
    TimeBarSetScrubPositionFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/** Time formatter call, its toString() and the resulting label String. */
private fun timeLabelFilters(location: InstructionLocation) = arrayOf(
    methodCall(
        opcode = Opcode.INVOKE_STATIC,
        parameters = listOf("J"),
        returnType = "Ljava/lang/CharSequence;",
        location = location
    ),
    methodCall(
        definingClass = "Ljava/lang/CharSequence;",
        name = "toString",
        location = MatchAfterWithin(3)
    ),
    opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
)

/** Draw of the time bar, with the bounds of the thumb and the two time labels. */
internal object TimeBarDrawFingerprint : Fingerprint(
    definingClass = TIME_BAR_CLASS,
    name = "draw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;"),
    filters = listOf(
        // Remaining time, formatted from a negated value.
        opcode(Opcode.NEG_LONG),
        *timeLabelFilters(MatchAfterImmediately()),
        // Elapsed time.
        *timeLabelFilters(MatchAfterAnywhere())
    )
)
