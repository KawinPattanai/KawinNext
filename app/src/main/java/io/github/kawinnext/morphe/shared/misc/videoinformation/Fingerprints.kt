package io.github.kawinnext.morphe.shared.misc.videoinformation

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.string

/**
 * Matches method {androidx.media3.exoplayer.ExoPlayerImpl.setPlaybackParameters(PlaybackParameters p1)}
 */
val playbackParametersSetterFingerprint = findMethodDirect {
    Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(PlaybackParametersToStringFingerprint().declaredClass!!.descriptor),
        custom = {
            declaredClass {
                addInterface { descriptor = "Landroidx/media3/exoplayer/ExoPlayer;" }
            }
        }
    )()
}


/**
 * Matches method {androidx.media3.common.PlaybackParameters}.toString()
 */
internal object PlaybackParametersToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(definingClass = "this", opcode = Opcode.IGET, type = "F"),
        string("PlaybackParameters(speed=%.2f, pitch=%.2f)")
    )
)