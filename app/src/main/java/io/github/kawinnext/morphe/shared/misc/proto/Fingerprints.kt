package io.github.kawinnext.morphe.shared.misc.proto

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.checkCast
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.string

@StrictQuery
internal object NewElementProtoParserFingerprint : Fingerprint(
    classFingerprint = ProtoStuffReflectionFingerprint,
    accessFlags = listOf(AccessFlags.STATIC),
    parameters = listOf("L"),
    returnType = "[B",
    filters = listOf(
        checkCast("[B")
    )
)

private object ProtoStuffReflectionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    parameters = listOf(),
    returnType = "Ljava/lang/reflect/Field;",
    filters = listOf(
        string("buf"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "getDeclaredField"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "setAccessible"
        )
    )
)
