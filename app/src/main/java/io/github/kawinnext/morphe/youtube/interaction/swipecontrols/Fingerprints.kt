package io.github.kawinnext.morphe.youtube.interaction.swipecontrols

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode

@StrictQuery
internal object PlayerOverlayContainerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = $$"Landroid/view/ViewGroup$LayoutParams;",
    parameters = listOf(),
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        literal(-1),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this"
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("I", "I", "Z")
        )
    ),
    custom = {
        addUsingField { type = "boolean" }
    }
)

val PlayerOverlayNameField = findFieldDirect {
    PlayerOverlayContainerFingerprint().declaredClass!!.fields.first { it.typeName == "java.lang.String" }
}