package io.github.kawinnext.morphe.youtube.misc.engagement

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.youtube.shared.EngagementPanelControllerFingerprint

internal object EngagementPanelUpdateFingerprint : Fingerprint(
    classFingerprint = EngagementPanelControllerFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Z"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/app/Activity;"
        )
    )
)

val panelInitFingerprint = findMethodDirect {
    panelClass().findMethod {
        matcher {
            name = "<init>"
        }
    }.single()
}

val panelIdField = findFieldDirect {
    panelClass().fields.single { it.typeName == "java.lang.String" }
}

val panelClass = findClassDirect {
    EngagementPanelControllerFingerprint.instructionMatches[3].instruction.classRef!!
}