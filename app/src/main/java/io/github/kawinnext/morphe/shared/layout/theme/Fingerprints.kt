/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package io.github.kawinnext.morphe.shared.layout.theme

import io.github.kawinnext.RequireAppVersion
import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.methodCall

@RequireAppVersion(minVersion = "21.30.000")
@StrictQuery
internal object LithoOnBoundsChangeFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(),
        filters = listOf(
            methodCall(smali = $$"Landroid/graphics/Path;->addRoundRect(Landroid/graphics/RectF;[FLandroid/graphics/Path$Direction;)V"),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Landroid/graphics/Path;"
            )
        )
    ),
    name = "onBoundsChange",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/graphics/Rect;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Landroid/graphics/Paint",
            location = MatchAfterWithin(10)
        ),
        methodCall(
            smali = "Landroid/graphics/Paint;->setColor(I)V",
            location = MatchAfterImmediately()
        )
    )
)

// YT 21.29, Music 9.29 and older.
@RequireAppVersion(maxVersion = "21.30.000")
@StrictQuery
internal object LithoOnBoundsChangeLegacyFingerprint : Fingerprint(
    name = "onBoundsChange",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/graphics/Rect;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Landroid/graphics/Path;"
        ),

        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Landroid/graphics/Paint",
            location = MatchAfterWithin(10)
        ),
        methodCall(
            smali = "Landroid/graphics/Paint;->setColor(I)V",
            location = MatchAfterImmediately()
        )
    )
)

@get:RequireAppVersion(minVersion = "21.30.000")
val lithoPaintField = findFieldDirect {
    LithoOnBoundsChangeFingerprint.instructionMatches[0].instruction.fieldRef!!
}

@get:RequireAppVersion(maxVersion = "21.30.000")
val lithoPaintLegacyField = findFieldDirect {
    LithoOnBoundsChangeLegacyFingerprint.instructionMatches[1].instruction.fieldRef!!
}
