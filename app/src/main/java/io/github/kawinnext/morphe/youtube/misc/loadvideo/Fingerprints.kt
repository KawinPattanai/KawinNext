/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package io.github.kawinnext.morphe.youtube.misc.loadvideo

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.OpcodesFilter
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.parameters

internal object MiniAppOpenYtContentCommandEndpointFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        // Matches the same method but different app versions have different strings in different ordering.
        custom = {
            addAnyOf {
                addEqString("InvalidProtocolBufferException while decoding MiniAppMetadata for MiniAppOpenYTContentCommand: ")
            }
            // 21.15+
            addAnyOf {
                addEqString("InvalidProtocolBufferException while decoding MiniAppWebToNativeParams for MiniAppOpenYTContentCommand: ")
            }
        }
    ),
    returnType = "V",
    filters = listOf(
        opcode(Opcode.CHECK_CAST),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "V",
            parameters = listOf(),
            location = MatchAfterImmediately()
        )
    ),
    strings = listOf("no error message"),
    custom = {
        addAnyOf {
            not { accessFlags(AccessFlags.STATIC.modifier) }
            parameters("L")
        }
        addAnyOf { parameters("L", "Ljava/util/Map;") } // 21.30+
    }
)

internal object OpenNewVideoIntentParcelableFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("Landroid/content/Intent;"),
    strings = listOf(
        "android.intent.extra.inventory_identifier",
        "http",
        "vnd.youtube",
        "No video id in the Uri: "
    )
)

internal object BackButtonFinishActivityOnNewVideoIntentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_OBJECT,
        Opcode.CHECK_CAST,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CHECK_CAST,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IPUT_BOOLEAN,
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CHECK_CAST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IPUT_BOOLEAN
    )
)

private const val ACCESSIBILITY_STATE_CHANGE_LISTENER =
    $$"Landroid/view/accessibility/AccessibilityManager$AccessibilityStateChangeListener;"

/**
 * Adds a reason that blocks playback in feeds.
 */
internal object BlockInlinePlaybackFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;"),
        methodCall(smali = "Ljava/util/Set;->add(Ljava/lang/Object;)Z")
    ),
    custom = {
        declaredClass { addInterface { descriptor = ACCESSIBILITY_STATE_CHANGE_LISTENER } }
    }
)

/**
 * Removes a reason that blocks playback in feeds.
 */
internal object UnblockInlinePlaybackFingerprint : Fingerprint(
    classFingerprint = BlockInlinePlaybackFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;"),
        methodCall(smali = "Ljava/util/Set;->remove(Ljava/lang/Object;)Z")
    )
)

val inlinePlaybackConstructor = findMethodDirect {
    BlockInlinePlaybackFingerprint().declaredClass!!.methods.single { it.name == "<init>" }
}

val dismissPlayerMethod = findMethodDirect {
    MiniAppOpenYtContentCommandEndpointFingerprint.instructionMatches.last().instruction.methodRef!!
}

val playerControllerConstructor = findMethodDirect {
    dismissPlayerMethod().declaredClass!!.methods.single { it.name == "<init>" }
}
