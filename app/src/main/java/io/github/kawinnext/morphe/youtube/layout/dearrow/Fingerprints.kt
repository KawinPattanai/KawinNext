package io.github.kawinnext.morphe.youtube.layout.dearrow

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.string

/**
 * Glide RequestBuilder.into(Target, RequestListener, BaseRequestOptions, Executor).
 */
private object GlideRequestBuilderIntoFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("You must call #load() before calling #into()")
    )
)

/**
 * Glide RequestBuilder.loadGeneric(Object), which all load(...) methods use to set the image model.
 */
@StrictQuery
internal object GlideRequestBuilderLoadGenericFingerprint : Fingerprint(
    classFingerprint = GlideRequestBuilderIntoFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/Object;"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            location = MatchAfterWithin(3)
        )
    )
)
