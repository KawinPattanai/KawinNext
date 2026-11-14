/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4757
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.video.quality

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchFirst
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall

/**
 * A quality of the quality menu of the player.
 * The class name is not obfuscated in the targets before 21.01.
 */
internal object VideoQualityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(
        "I", // Resolution.
        "L",
        "Ljava/lang/String;", // Human-readable resolution: "480p", "1080p Premium", etc.
        "Z",
        "L"
    )
)

/**
 * Sets the qualities of the quality menu and the index of the quality playing.
 * Called when the format of the video stream changes, with the 'Auto' quality first.
 */
internal fun getVideoQualityMenuSetQualitiesFingerprint(videoQualityType: String) = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("[$videoQualityType", "I", "Z"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "[$videoQualityType",
            location = MatchFirst()
        ),
        fieldAccess(
            opcode = Opcode.IPUT,
            definingClass = "this",
            type = "I",
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * The item click listener of the quality menu,
 * which sets the resolution of the selected quality with the listener of the menu.
 */
internal fun getVideoQualityItemOnClickFingerprint(videoQualityType: String) = Fingerprint(
    name = "onItemClick",
    returnType = "V",
    parameters = listOf(
        "Landroid/widget/AdapterView;",
        "Landroid/view/View;",
        "I",
        "J"
    ),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = videoQualityType,
            type = "I"
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("I"),
            returnType = "V",
            location = MatchAfterImmediately()
        )
    )
)

val videoQualityMenuSetQualitiesMethod = findMethodDirect {
    getVideoQualityMenuSetQualitiesFingerprint(
        VideoQualityFingerprint().declaredClass!!.descriptor
    )()
}

val videoQualityItemOnClickMethod = findMethodDirect {
    getVideoQualityItemOnClickFingerprint(
        VideoQualityFingerprint().declaredClass!!.descriptor
    )()
}

/** Resolution of a quality, which the click listener of the quality menu reads. */
val videoQualityResolutionField = findFieldDirect {
    getVideoQualityItemOnClickFingerprint(
        VideoQualityFingerprint().declaredClass!!.descriptor
    ).match().instructionMatches.first().instruction.fieldRef!!
}

/** Method the listener of the quality menu sets a quality with. */
val videoQualitySetQualityMethod = findMethodDirect {
    getVideoQualityItemOnClickFingerprint(
        VideoQualityFingerprint().declaredClass!!.descriptor
    ).match().instructionMatches[1].instruction.methodRef!!
}
