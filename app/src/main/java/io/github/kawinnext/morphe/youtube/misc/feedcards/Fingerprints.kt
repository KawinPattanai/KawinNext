/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3758
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.misc.feedcards

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.shared.misc.litho.relayout.COMPONENT_HOST_CLASS

/**
 * Litho is not obfuscated, and the method overrides the framework,
 * so this matches by name on every supported app version.
 */
internal object FeedCardComponentHostLayoutFingerprint : Fingerprint(
    definingClass = COMPONENT_HOST_CLASS,
    name = "onLayout",
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I")
)

/**
 * Binds a compact video, such as the videos of the watch history, which are not drawn by Litho.
 */
internal object CompactVideoBindFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Ljava/lang/Object;"),
    filters = listOf(
        // Renderer of the video.
        opcode(Opcode.CHECK_CAST),
        resourceLiteral(type = ResourceType.INTEGER, name = "compact_renderer_title_max_lines"),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "[B"),
        methodCall(returnType = "Landroid/text/Spanned;"),
        // Sets the title.
        methodCall(
            parameters = listOf("Ljava/lang/CharSequence;"),
            returnType = "V",
            location = MatchAfterWithin(2)
        )
    )
)

/**
 * Sets the title of a compact video, with the view of the title in a field of its class.
 */
val compactVideoTitleSetterMethod = findMethodDirect {
    CompactVideoBindFingerprint.instructionMatches.last().instruction.methodRef!!
}

/**
 * Renderer of a compact video, which keeps the video id in its only string field.
 */
val compactVideoRendererClass = findClassDirect {
    CompactVideoBindFingerprint.instructionMatches.first().instruction.classRef!!
}

val compactVideoIdField = findFieldDirect {
    CompactVideoBindFingerprint.instructionMatches.first().instruction.classRef!!
        .fields.single { it.typeName == "java.lang.String" }
}

val compactVideoTitleViewField = findFieldDirect {
    CompactVideoBindFingerprint.instructionMatches.last().instruction.methodRef!!
        .declaredClass!!.fields.first { it.typeName == "android.widget.TextView" }
}
