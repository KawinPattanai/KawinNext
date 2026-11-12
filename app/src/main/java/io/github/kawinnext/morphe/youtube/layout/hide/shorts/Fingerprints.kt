package io.github.kawinnext.morphe.youtube.layout.hide.shorts

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral
import org.luckypray.dexkit.result.FieldData

/**
 * Reads from the configuration of a Shorts page whether the navigation bar is hidden.
 * The first instruction match is the field of the navigation bar settings in the configuration,
 * and the fourth one is the mode of the navigation bar.
 */
@StrictQuery
internal object ShortsNavigationBarModeFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(
            resourceLiteral(ResourceType.LAYOUT, "reel_watch_fragment")
        )
    ),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET, type = "I", location = MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I"),
            returnType = "I",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
        opcode(Opcode.ADD_INT_LIT8, location = MatchAfterImmediately())
    )
)

/**
 * Every read of the navigation bar mode of a Shorts page, such as by the Shorts pager and by
 * the page of the Short. The third instruction match is the static field that holds the
 * configuration the mode is read from.
 *
 * upstream สร้าง fingerprint นี้ในแพตช์จาก field reference สองตัวของ
 * [ShortsNavigationBarModeFingerprint] แล้วใช้ matchAll() กับทุกจุดที่อ่านโหมด
 */
internal fun shortsNavigationBarModeReadFingerprint(
    navigationBarField: FieldData,
    navigationBarModeField: FieldData
) = Fingerprint(
    filters = listOf(
        fieldAccess(reference = navigationBarField, opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(
            reference = navigationBarModeField,
            opcode = Opcode.IGET,
            location = MatchAfterImmediately()
        )
    )
)

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
