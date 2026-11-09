/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.layout.hide.ambientmode

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.anyInstruction
import io.github.kawinnext.morphe.checkCast
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string
import org.luckypray.dexkit.DexKitBridge

/** Key the app saves the state of the Ambient mode toggle of the player flyout menu by. */
internal const val AMBIENT_MODE_TOGGLE_MENU_ITEM = "menu_item_cinematic_lighting"

private const val GET_ACTION_METHOD_CALL = "Landroid/content/Intent;->getAction()Ljava/lang/String;"
private const val IS_POWER_SAVE_MODE_METHOD_CALL = "Landroid/os/PowerManager;->isPowerSaveMode()Z"
private const val POWER_SAVE_MODE_CHANGED = "android.os.action.POWER_SAVE_MODE_CHANGED"
private const val RENDERER_LOADED_STATE_FIELD = "rendererLoadedState="

/**
 * Receives the Ambient mode toggle of the player flyout menu when the app resumes and whenever
 * it changes, and saves it as the state of the menu item.
 */
private object AmbientModeToggleFingerprint : Fingerprint(
    filters = listOf(
        checkCast("Ljava/lang/Boolean;"),
        string(AMBIENT_MODE_TOGGLE_MENU_ITEM, location = MatchAfterWithin(8)),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/Boolean;"),
            returnType = "V",
            location = MatchAfterImmediately()
        ),
    )
)

/** Saves the state of a menu item of the player flyout menu, by its key. */
val ambientModeToggleMethods = findMethodListDirect {
    AmbientModeToggleFingerprint.matchAll()
        .map { it.instructionMatches.last().instruction.methodRef!! }
        .distinctBy { it.descriptor }
}

/**
 * Receives the power save mode changes of the system, which turn the Ambient mode of the app off.
 */
private object IntentActionBroadcastReceiverFingerprint : Fingerprint(
    name = "onReceive",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/content/Intent;"),
    filters = listOf(
        string(POWER_SAVE_MODE_CHANGED),
        methodCall(smali = GET_ACTION_METHOD_CALL),
        opcode(Opcode.INVOKE_DIRECT)
    )
) {
    init {
        classMatcher { superClass { descriptor("Landroid/content/BroadcastReceiver;") } }
    }
}

/**
 * YT 21.02+
 */
private object IntentActionBroadcastReceiverAlternativeFingerprint : Fingerprint(
    name = "onReceive",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/content/Intent;"),
    filters = listOf(
        methodCall(smali = GET_ACTION_METHOD_CALL),
        string(POWER_SAVE_MODE_CHANGED),
        opcode(Opcode.INVOKE_DIRECT)
    )
) {
    init {
        classMatcher { superClass { descriptor("Landroid/content/BroadcastReceiver;") } }
    }
}

/**
 * YT 21.43+ Reads the first power save mode value, for the receiver of the Ambient mode.
 */
private object IntentActionSyntheticAlternativeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        checkCast("Landroid/os/PowerManager;"),
        methodCall(smali = IS_POWER_SAVE_MODE_METHOD_CALL),
        string(POWER_SAVE_MODE_CHANGED),
    )
)

/**
 * YT 21.03 - 21.42 Reads the first power save mode value, for the receiver of the Ambient mode.
 */
private object IntentActionSyntheticAlternativeLegacyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        anyInstruction(
            string("power"),
            checkCast("Landroid/os/PowerManager;")
        ),
        methodCall(smali = IS_POWER_SAVE_MODE_METHOD_CALL),
        string(POWER_SAVE_MODE_CHANGED),
    )
)

/**
 * Methods of the app that read the power save mode of the system for the Ambient mode.
 */
val powerSaveModeReadMethods = findMethodListDirect {
    listOf(
        IntentActionBroadcastReceiverFingerprint.matchAllOrNull(),
        IntentActionBroadcastReceiverAlternativeFingerprint.matchAllOrNull(),
        IntentActionSyntheticAlternativeFingerprint.matchAllOrNull(),
        IntentActionSyntheticAlternativeLegacyFingerprint.matchAllOrNull(),
    ).filterNotNull().flatten()
        .map { it.method }
        .distinctBy { it.descriptor }
}

/**
 * The state object of the app that every one of [powerSaveModeReadMethods] reports the power save
 * mode to, as a Boolean. Which method it is, is only known by what these methods call.
 */
val powerSaveModeSinkMethod = findMethodDirect {
    val method = IntentActionBroadcastReceiverFingerprint.matchAll()
        .first { it.method.declaredClass!!.methods.size == 2 }
        .method
    val instructions = method.instructions
    val valueOfIndex = instructions.indexOfFirst {
        it.methodRef?.declaredClass?.descriptor == "Ljava/lang/Boolean;" &&
                it.methodRef?.name == "valueOf"
    }
    check(valueOfIndex >= 0) { "No Boolean valueOf found in ${method.descriptor}" }
    instructions.drop(valueOfIndex + 1)
        .first { it.methodRef != null }
        .methodRef!!
}

/**
 * Turns the background of the player to the given color when the player is laid out,
 * which is the Ambient mode seen in fullscreen.
 */
internal object SetFullScreenBackgroundColorFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/player/YouTubePlayerViewNotForReflection;",
    name = "onLayout",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I"),
    filters = listOf(
        methodCall(name = "setBackgroundColor")
    )
)

/**
 * CinematicContainerRenderer, the Ambient mode proto of the /next response.
 * The literal is its extension field number.
 */
private object CinematicContainerRendererFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    filters = listOf(
        literal(455752576L)
    )
)

/**
 * Holds the CinematicContainerRenderer of the current video and its loading state.
 */
private object CinematicContainerRendererWithStateFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("RendererWithState{$RENDERER_LOADED_STATE_FIELD")
    )
)

/**
 * Reads the presentation style (field 3) of a CinematicContainerRenderer, if it has one.
 */
private fun cinematicContainerPresentationStyleFingerprint(rendererType: String) = Fingerprint(
    returnType = "L",
    parameters = listOf(rendererType),
    filters = listOf(
        // Has-bits field.
        fieldAccess(opcode = Opcode.IGET, definingClass = rendererType, type = "I"),
        opcode(Opcode.AND_INT_LIT8, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // Presentation style field.
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = rendererType,
            type = "I",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I"),
            location = MatchAfterImmediately()
        ),
    )
)

/**
 * Loading state enum of [CinematicContainerRendererWithStateFingerprint].
 * Other enums have the same constant names, so the type is required.
 */
private fun cinematicContainerRendererLoadedStateFingerprint(stateType: String) = Fingerprint(
    definingClass = stateType,
    name = "<clinit>",
    returnType = "V",
    filters = listOf(
        string("WAITING"),
        fieldAccess(definingClass = stateType, type = stateType, opcode = Opcode.SPUT_OBJECT),
        string("LOADED"),
        fieldAccess(definingClass = stateType, type = stateType, opcode = Opcode.SPUT_OBJECT),
    )
)

private fun DexKitBridge.rendererType(): String =
    CinematicContainerRendererFingerprint.run().declaredClass!!.descriptor

private fun DexKitBridge.rendererStateType(): String =
    CinematicContainerRendererWithStateFingerprint.run()
        .findFieldFromToString(RENDERER_LOADED_STATE_FIELD).type.descriptor

/**
 * Constructor of the object that holds the renderer of the Ambient mode of the video.
 * Its parameters are the loading state and the renderer of the /next response.
 */
val ambientModeRendererConstructorMethod = findMethodDirect {
    val stateType = rendererStateType()
    CinematicContainerRendererWithStateFingerprint.run().declaredClass!!
        .findMethod { matcher { name = "<init>"; paramCount = 2 } }
        .single { it.paramTypes.first().descriptor == stateType }
}

/** Field of the renderer that holds the style of the colors of the Ambient mode. */
val ambientModeRendererStyleField = findFieldDirect {
    cinematicContainerPresentationStyleFingerprint(rendererType())
        .instructionMatches[3].instruction.fieldRef!!
}

/** The loading state of the renderer before the /next response arrives. */
val ambientModeRendererWaitingStateField = findFieldDirect {
    cinematicContainerRendererLoadedStateFingerprint(rendererStateType())
        .instructionMatches[1].instruction.fieldRef!!
}

/** The loading state of the renderer once the /next response arrived. */
val ambientModeRendererLoadedStateField = findFieldDirect {
    cinematicContainerRendererLoadedStateFingerprint(rendererStateType())
        .instructionMatches[3].instruction.fieldRef!!
}
