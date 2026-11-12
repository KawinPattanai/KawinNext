/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.hide.general

import io.github.kawinnext.SkipTest
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral

/**
 * The player view that holds the audio/video switch. The app has more than one player, and the
 * test suite expects a single match, so it is skipped there; the hook installs on every match.
 */
@SkipTest
internal object AudioVideoSwitchPillContainerFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "audio_video_switch_pill_container"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "findViewById"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/**
 * The method that inflates the filter bar of the search page as a chip cloud.
 */
internal object ChipCloudFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "chip_cloud"),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/**
 * The method that builds the info button of the comments.
 */
internal object InformationButtonFingerprint : Fingerprint(
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "information_button"),
        opcode(Opcode.CHECK_CAST)
    )
)

/** Matched more than once, as the overlay and the playback player both have one. */
@SkipTest
internal object OverlayQueueLoopButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "overlay_queue_loop_button_view"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "findViewById"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/** Matched more than once, as the overlay and the playback player both have one. */
@SkipTest
internal object OverlayQueueShuffleButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "overlay_queue_shuffle_button_view"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "findViewById"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/** Matched more than once, as the overlay and the playback player both have one. */
@SkipTest
internal object PlaybackQueueLoopButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "playback_queue_loop_button_view"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "findViewById"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/** Matched more than once, as the overlay and the playback player both have one. */
@SkipTest
internal object PlaybackQueueShuffleButtonFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "playback_queue_shuffle_button_view"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "findViewById"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)
