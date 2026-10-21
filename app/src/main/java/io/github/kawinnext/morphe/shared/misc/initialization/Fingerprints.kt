package io.github.kawinnext.morphe.shared.misc.initialization

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.indexOfFirstInstructionReversed
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.string

internal object GlobalConfigGroupFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Ljava/util/concurrent/locks/ReentrantLock;->lock()V"
        ),
        string(string = "com.google.android.libraries.youtube.innertube.cold_stored_timestamp"),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            name = "putLong"
        )
    )
)

val handleColdFingerprint = findMethodDirect {
    val method = GlobalConfigGroupFingerprint()
    val matches = GlobalConfigGroupFingerprint.matchOrNull(method)?.instructionMatches!!
    val str_index = matches[2].instruction.index

    val index = method.indexOfFirstInstructionReversed (str_index) {
        this.opcode == Opcode.INVOKE_STATIC.opCode
    }

    method.instructions[index].methodRef!!
}