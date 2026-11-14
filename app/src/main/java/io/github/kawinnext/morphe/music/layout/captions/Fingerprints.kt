/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4773
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.captions

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.InstructionLocation.MatchFirst
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string

/**
 * The caption track manager of the player, which knows if the captions are enabled.
 */
internal object SubtitleManagerClassFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("L", "Landroid/view/accessibility/CaptioningManager;"),
    filters = listOf(
        fieldAccess("Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;"),
        methodCall("Landroid/view/accessibility/CaptioningManager;->isEnabled()Z"),
    )
)

/**
 * Sets the caption track of a caption track event.
 *
 * The event holds the reason the track is set, which is 2 when the captions are enabled
 * automatically after the caption tracks of a video are loaded.
 * If the track is the option to disable captions, or if there is no track,
 * the default track of the caption tracks is used if there is one.
 */
internal object SetCaptionTrackFingerprint : Fingerprint(
    classFingerprint = SubtitleManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        // Reason the track is set.
        fieldAccess(
            opcode = Opcode.IGET,
            type = "I",
            location = MatchFirst()
        ),
        string(""),
        // If the track is the option to disable captions.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Z"
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // If the caption tracks of the video have a default track.
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            location = MatchAfterWithin(10)
        ),
    )
)

/** Reason the caption track is set, which the event of the setter holds. */
val setCaptionTrackReasonField = findFieldDirect {
    SetCaptionTrackFingerprint.instructionMatches.first().instruction.fieldRef!!
}
