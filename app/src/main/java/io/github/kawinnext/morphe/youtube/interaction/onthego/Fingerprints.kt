/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4570
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.onthego

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.newInstance
import io.github.kawinnext.morphe.string

/**
 * Name of the "Skip to next" media session action, which is only shown while On-the-go is active.
 * Matches the getter of the action name.
 */
internal object OnTheGoSkipToNextActionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("com.google.android.youtube.SKIP_TO_NEXT")
    )
)

/**
 * Constructor of the On-the-go media session action, whose first parameter is the state object.
 */
internal object OnTheGoSkipToNextActionConstructorFingerprint : Fingerprint(
    classFingerprint = OnTheGoSkipToNextActionFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    name = "<init>"
)

/**
 * Constructor of the app object that keeps the seek length, which is set from the double tap
 * to seek length of the app settings. The second parameter is the seek length, if it is set
 * from elsewhere.
 */
internal object SeekLengthConstructorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("L", "Lj\$/util/Optional;"),
    filters = listOf(
        literal(10),
        methodCall(name = "ofSeconds", location = MatchAfterWithin(1)),
        methodCall(
            smali = "Lj\$/util/Optional;->orElse(Ljava/lang/Object;)Ljava/lang/Object;",
            location = MatchAfterWithin(2)
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(3)
        )
    )
)

/** Field of the app object that keeps the seek length. */
val seekLengthField = findFieldDirect {
    SeekLengthConstructorFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/** Turns the seek length of the field into milliseconds, with the type the length is built from. */
val seekLengthToMillisMethod = findMethodDirect {
    SeekLengthConstructorFingerprint.instructionMatches[1].instruction.methodRef!!
        .declaredClass!!.findMethod { matcher { name = "toMillis" } }.single()
}

/**
 * Constructor of the object that keeps the On-the-go state of the app.
 */
internal fun onTheGoStateConstructorFingerprint(stateClass: String) = Fingerprint(
    definingClass = stateClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    name = "<init>",
    filters = listOf(
        methodCall(smali = "Ljava/lang/Object;-><init>()V"),
        newInstance("Ljava/util/concurrent/atomic/AtomicBoolean;")
    )
)

/**
 * Sets if On-the-go is active.
 */
internal fun onTheGoStateSetterFingerprint(stateClass: String) = Fingerprint(
    definingClass = stateClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(smali = "Ljava/util/concurrent/atomic/AtomicBoolean;->set(Z)V"),
        methodCall(
            smali = "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;",
            location = MatchAfterWithin(4)
        )
    )
)
