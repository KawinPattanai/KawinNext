/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2100
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.kawinnext.morphe.youtube.misc.chapters

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.checkCast
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral

internal object TimelineMarkerFingerprint : Fingerprint (
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf("TimelineMarker[title=",  ", startMillis=", ", endMillis=")
)

internal fun getTimelineMarkersArrayFingerprint(timelineMarkerClassName: String) = object : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "[$timelineMarkerClassName",
    parameters = listOf("L"),
    filters = listOf(
        checkCast("[$timelineMarkerClassName")
    )
) {}

@StrictQuery
internal object HeatMapPeakPointFingerprint : Fingerprint (
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Lj$/util/Optional;->isPresent()Z"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT,
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Lj$/util/Optional;",
            location = MatchAfterWithin(2)
        ),
        resourceLiteral(
            ResourceType.DIMEN,
            "ic_marker_decoration_size",
            location = MatchAfterWithin(37)
        )
    )
)

val timelineMarkersArrayMethod = findMethodDirect {
    getTimelineMarkersArrayFingerprint(TimelineMarkerFingerprint().declaredClass!!.descriptor)()
}

val timelineMarkersToArrayMethod = findMethodDirect {
    timelineMarkersArrayMethod().instructions.last { it.methodRef?.name == "toArray" }.methodRef!!
}

val timelineMarkerStartMillisField = findFieldDirect {
    TimelineMarkerFingerprint().findFieldFromToString("startMillis=")
}

val timelineMarkerEndMillisField = findFieldDirect {
    TimelineMarkerFingerprint().findFieldFromToString("endMillis=")
}

val timelineMarkerTitleField = findFieldDirect {
    TimelineMarkerFingerprint().findFieldFromToString("title=")
}

val heatMapPeakPointIsPresentMethod = findMethodDirect {
    HeatMapPeakPointFingerprint.instructionMatches.first().instruction.methodRef!!
}
