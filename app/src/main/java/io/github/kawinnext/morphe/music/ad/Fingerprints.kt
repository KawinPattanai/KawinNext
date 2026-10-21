package io.github.kawinnext.morphe.music.ad

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.OpcodesFilter
import io.github.kawinnext.morphe.findMethodDirect

internal object ShowVideoAdsFingerprint : Fingerprint(
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.IGET_OBJECT,
    ),
    strings = listOf("maybeRegenerateCpnAndStatsClient called unexpectedly, but no error.")
)

val showVideoAds = findMethodDirect {
    ShowVideoAdsFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal object HideGetPremiumFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    strings = listOf("FEmusic_history", "FEmusic_offline")
)

internal object MembershipSettingsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/CharSequence;",
    parameters = listOf(),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT
    )
)
