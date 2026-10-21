package io.github.kawinnext.morphe.youtube.layout.player.buttons

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral

internal object ExploderUIFullscreenButtonFingerprint : Fingerprint(
    classFingerprint = ExploderUIFullscreenButtonParentFingerprint,
    filters = listOf(
        resourceLiteral(ResourceType.ID, "fullscreen_button"),
        methodCall(smali = "Landroid/view/View;->findViewById(I)Landroid/view/View;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately())
    )
)

internal object ExploderUIFullscreenButtonParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "time_bar_live_label")
    )
)
