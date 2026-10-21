package io.github.kawinnext.morphe.youtube.video.audio

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string


/**
 * Media3 DefaultAudioSink, where the audio session id of a newly created AudioTrack is read.
 */
internal object AudioTrackSessionIdFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(
            string("ExoPlayer:AudioTrackReleaseThread")
        )
    ),
    filters = listOf(
        methodCall(smali = "Landroid/media/AudioTrack;->getAudioSessionId()I"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately())
    )
)
