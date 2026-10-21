package io.github.kawinnext.morphe.youtube.misc.playertype

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.string
import org.luckypray.dexkit.result.FieldUsingType

object PlayerTypeFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/player/YouTubePlayerViewNotForReflection;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    custom = {
        addParamType { superClass { descriptor = "Ljava/lang/Enum;" } }
    }
)

internal object ReelWatchPagerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "reel_watch_player"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterWithin(10))
    )
)

val ReelPlayerViewField = findFieldDirect {
    ReelWatchPagerFingerprint().declaredClass!!.fields.single { it.typeName.endsWith("ReelPlayerView") }
}

// 20.33 and lower class name ControlsState. 20.34+ class name is obfuscated.
internal object ControlsStateToStringFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("videoState"),
        string("isBuffering")
    )
)

internal object VideoStateEnumFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(),
    strings = listOf(
        "NEW",
        "PLAYING",
        "PAUSED",
        "RECOVERABLE_ERROR",
        "UNRECOVERABLE_ERROR",
        "ENDED"
    )
)

val videoStateFingerprint = findMethodDirect {
    val controlStateType = ControlsStateToStringFingerprint().declaredClass!!.descriptor
    val videoStateType = VideoStateEnumFingerprint().declaredClass!!.descriptor

    Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(element = controlStateType),
        filters = listOf(
            // Obfuscated parameter field name.
            fieldAccess(
                definingClass = controlStateType,
                type = videoStateType
            ),
            resourceLiteral(ResourceType.STRING, "accessibility_play"),
            resourceLiteral(ResourceType.STRING, "accessibility_pause")
        )
    )()
}

val videoStateParameterField = findFieldDirect {
    videoStateFingerprint().let { method ->
        method.usingFields.distinct().single { field ->
            // obfuscated parameter field name
            field.usingType == FieldUsingType.Read && field.field.declaredClass == method.paramTypes[0]
        }.field
    }
}