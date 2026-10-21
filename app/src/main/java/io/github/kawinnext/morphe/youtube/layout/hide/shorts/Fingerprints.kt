package io.github.kawinnext.morphe.youtube.layout.hide.shorts

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode

internal object ShortsExperimentalPlayerFeatureFlagFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(45677719L)
    )
)


internal object RenderNextUIFeatureFlagFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(45649743L)
    )
)

internal object DoubleTapToLikeLogicFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;"),
    filters = listOf(
        literal(255),
        methodCall("Landroid/view/MotionEvent;->getEventTime()J"),
        methodCall("Ljava/lang/Math;->hypot(DD)D"),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            location = MatchAfterWithin(25)
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterWithin(5))
    )
)

val isDoubleTapField = findFieldDirect {
    DoubleTapToLikeLogicFingerprint.instructionMatches[3].instruction.fieldRef!!
}
