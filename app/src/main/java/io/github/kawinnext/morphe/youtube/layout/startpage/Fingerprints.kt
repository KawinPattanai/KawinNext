package io.github.kawinnext.morphe.youtube.layout.startpage

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
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
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/lang/String;"),
            location = MatchAfterImmediately()
        ),
        // Sets the has bit of the browse id, which is 0x400 in 21.47+ and 0x200 before.
        opcode(Opcode.OR_INT_LIT16),
        opcode(Opcode.IPUT, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        )
    )
)

val browserIdProtoBuilder = findMethodDirect {
    BrowseIdFingerprint.instructionMatches[1].instruction.methodRef!!
}