package io.github.kawinnext.morphe.music.layout.upgradebutton

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fingerprint

internal val pivotBarConstructorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    returns("V")
    parameters("L", "Z")
    opcodes(
        Opcode.INVOKE_INTERFACE,
        Opcode.GOTO,
        Opcode.IPUT_OBJECT,
        Opcode.RETURN_VOID
    )
}
