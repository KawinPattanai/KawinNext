package io.github.kawinnext.morphe.youtube.misc.verticalscroll

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode

internal object CanScrollVerticallyFingerprint : Fingerprint(
    definingClass = "Landroidx/swiperefreshlayout/widget/SwipeRefreshLayout;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->canScrollVertically(I)Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately())
    )
)

val canScrollVerticallyMethod = findMethodDirect {
    CanScrollVerticallyFingerprint.instructionMatches.first().instruction.methodRef!!
}
