package io.github.kawinnext.morphe.youtube.layout.startpage

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.string


internal object IntentActionFingerprint : Fingerprint(
    parameters = listOf("Landroid/content/Intent;"),
    filters = listOf(
        string("has_handled_intent")
    )
)

@StrictQuery
internal object BrowseIdFingerprint : Fingerprint(
    returnType = "L",

    //parameters() // 20.30 and earlier is no parameters = listOf(.  20.31+ parameter is L.),
    filters = listOf(
        string("FEwhat_to_watch"),
        methodCall(
            parameters = listOf("Ljava/lang/String;"),
            location = MatchAfterWithin(3)
        ),
        literal(512),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "Ljava/lang/String;")
    )
)

val browserIdProtoBuilder = findMethodDirect {
    BrowseIdFingerprint.instructionMatches[1].instruction.methodRef!!
}