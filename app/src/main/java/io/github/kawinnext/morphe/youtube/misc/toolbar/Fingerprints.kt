package io.github.kawinnext.morphe.youtube.misc.toolbar

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral

val toolbarIconEnumMethod = findMethodDirect {
    ToolBarButtonFingerprint.match().instructionMatches[2].instruction.methodRef!!
}

val toolbarImageViewField = findFieldDirect {
    ToolBarButtonFingerprint.match().instructionMatches[6].instruction.fieldRef!!
}

internal object ToolBarButtonFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "menu_item_view"),
        methodCall(smali = "Landroid/view/MenuItem;->setShowAsAction(I)V"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "L",
            parameters = listOf("I")
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "I",
            parameters = listOf("L")
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/widget/ImageView;",
            location = MatchAfterWithin(6)
        ),
        methodCall(
            definingClass = "Landroid/content/res/Resources;",
            name = "getDrawable",
            location = MatchAfterWithin(8)
        ),
        methodCall(
            definingClass = "Landroid/widget/ImageView;",
            name = "setImageDrawable",
            location = MatchAfterWithin(4)
        )
    )
)
