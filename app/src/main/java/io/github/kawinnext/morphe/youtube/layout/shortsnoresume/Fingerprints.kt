package io.github.kawinnext.morphe.youtube.layout.shortsnoresume

import io.github.kawinnext.RequireAppVersion
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StringComparisonType
import io.github.kawinnext.morphe.checkCast
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string

@RequireAppVersion("21.03.00")
internal object UserWasInShortsEvaluateAnchorFingerprint: Fingerprint(
    returnType = "Z",
    filters = listOf(
        literal(1073815471),
        literal(1073815469)
    )
)

/**
 * 21.41+
 */
@RequireAppVersion("21.41.000")
internal object UserWasInShortsEvaluateFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "Z", "Z", "L", "Z")
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "L", "L", "L", "L", "Ljava/lang/String;"),
            location = InstructionLocation.MatchAfterWithin(50)
        )
    )
)

/**
 * 21.30 - 21.40
 */
@RequireAppVersion("21.30.000", "21.41.000")
internal object UserWasInShortsEvaluateLegacy2130Fingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "Z", "Z", "L", "Z")
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "L", "L", "L", "L", "L", "Ljava/lang/String;"),
            location = InstructionLocation.MatchAfterWithin(50)
        )
    )
)

/**
 * 21.03 - 21.29
 */
@RequireAppVersion(minVersion="21.03.00", maxVersion = "21.30.000")
internal object UserWasInShortsEvaluateLegacy2103Fingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "Z", "Z", "L", "Z")
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "L", "L", "L", "L", "I"),
            location = InstructionLocation.MatchAfterWithin(50)
        )
    )
)

/**
 * 20.02+
 */
@RequireAppVersion("20.02.00", "21.03.00")
internal object UserWasInShortsListenerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        checkCast("Ljava/lang/Boolean;"),
        methodCall(smali = "Ljava/lang/Boolean;->booleanValue()Z", location = InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        string("ShortsStartup SetUserWasInShortsListener", StringComparisonType.CONTAINS, InstructionLocation.MatchAfterWithin(30))
    )
)
