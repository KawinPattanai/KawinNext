package io.github.kawinnext.morphe.shared

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StringComparisonType
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.string

internal object FormatStreamModelToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string(" isDefaultAudioTrack="),
        string(" audioTrackId="),
        string(" audioTrackDisplayName="),
        string(" width="),
        string(" height="),
        // formatStreamModelFormatField.
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "L"),
        string("FormatStream(itag="),
        string(" mimeType=")
    )
)

internal object MediaSessionSetMetadataFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = "Landroid/media/session/MediaSession;",
            name = "setMetadata",
            parameters = listOf("Landroid/media/MediaMetadata;")
        )
    )
)

internal object SpannableStringBuilderFingerprint : Fingerprint(
    returnType = "Ljava/lang/CharSequence;",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "Ljava/lang/String;",
            parameters = emptyList()
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            smali = "Landroid/text/SpannableString;->valueOf(Ljava/lang/CharSequence;)Landroid/text/SpannableString;"
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "V",
            parameters = listOf(
                "Landroid/text/SpannableString;",
                "Ljava/lang/Object;",
                "I",
                "Z",
                "I"
            )
        ),
        string(
            "Failed to set PB Style Run Extension in TextComponentSpec.",
            comparison = StringComparisonType.STARTS_WITH
        )
    )
)

val spannableStringBuilderGetSpannedMethod = findMethodDirect {
    SpannableStringBuilderFingerprint.instructionMatches[0].instruction.methodRef!!
}
