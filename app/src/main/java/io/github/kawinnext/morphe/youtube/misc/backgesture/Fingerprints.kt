package io.github.kawinnext.morphe.youtube.misc.backgesture

import io.github.kawinnext.RequireAppVersion
import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE


internal object YouTubeMainActivityOnBackPressedFingerprint : Fingerprint(
    definingClass = YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onBackPressed",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_SUPER,
            name = "onBackPressed"
        ),
        opcode(Opcode.RETURN_VOID)
    )
)

@RequireAppVersion(minVersion = "20.40.00")
internal object PredictiveGesturesOnBackInvokedFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        name = "onBackCancelled",
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(),
        filters = listOf(
            literal(0),
            opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                type = "Ljava/lang/Object;",
                location = MatchAfterWithin(5)
            ),
            literal(-1, location = MatchAfterWithin(10)),
        )
    ),
    name = "onBackInvoked"
)
