package io.github.kawinnext.morphe.youtube.layout.sponsorblock

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.OpcodesFilter
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceMappings
import io.github.kawinnext.morphe.youtube.shared.SeekbarFingerprint

internal object AppendTimeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;"
    ),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.IGET_OBJECT,
        Opcode.IGET_OBJECT,
        Opcode.CHECK_CAST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT,
    ),
)

val SponsorBarRect = findFieldDirect {
    val clazz = SeekbarFingerprint().declaredClass!!
    clazz.findMethod {
        matcher {
            addInvoke {
                name = "invalidate"
                paramTypes("android.graphics.Rect")
            }
        }
    }.single().usingFields.last { it.field.typeName == "android.graphics.Rect" }.field
}

val seekbarOnDrawFingerprint = findMethodDirect {
    SeekbarFingerprint().declaredClass!!.findMethod {
        matcher {
            name = "onDraw"
        }
    }.single()
}

val inset_overlay_view_layout get() = resourceMappings["id", "inset_overlay_view_layout"]

val controlsOverlayFingerprint = findMethodDirect {
    findMethod {
        matcher {
            addUsingNumber(inset_overlay_view_layout)
            paramCount = 0
            returnType = "void"
        }
    }.single()
}

internal object AdProgressTextViewVisibilityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/Object;"
        ),
        opcode(opcode = Opcode.CHECK_CAST, location = MatchAfterWithin(4)),
    ),
    custom = {
        addInvoke {
            descriptor =
                "Lcom/google/android/libraries/youtube/ads/player/ui/AdProgressTextView;->setVisibility(I)V"
        }
    }
)

val AdProgressTextField = findFieldDirect {
    AdProgressTextViewVisibilityFingerprint.instructionMatches[0].instruction.fieldRef!!
}
