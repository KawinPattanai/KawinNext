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
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.literal
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

/**
 * A bit of the music shelf item bit field set when the item is a search history entry.
 */
private const val MUSIC_SHELF_ITEM_SEARCH_HISTORY_BIT = 0x8000000L

/**
 * A bit of the music shelf item bit field set when the item is a suggested search.
 */
private const val MUSIC_SHELF_ITEM_SUGGESTED_SEARCH_BIT = 0x4000000L

/**
 * Returns the renderers of a music shelf. Each shelf item holds one renderer, selected by a bit of
 * the item bit field.
 */
internal object MusicShelfItemsFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        literal(MUSIC_SHELF_ITEM_SEARCH_HISTORY_BIT),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(6)),
        literal(MUSIC_SHELF_ITEM_SUGGESTED_SEARCH_BIT),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(6))
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

/**
 * Renders the search suggestions response of the search page. Before a query is typed, the response
 * holds the search history shelves, the entry point buttons and the 'You may also like' shelf.
 * Matches the section list being handed to the list controller, right before the loading spinner is
 * updated.
 */
internal object SearchSuggestionsRenderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf(
        "renderSuggestions",
        "Search Suggestions Response has Section List but also more than one renderer."
    ),
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("L"),
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "V",
            parameters = listOf("L"),
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Lcom/google/android/apps/youtube/music/ui/components/loading/LoadingFrameLayout;",
            location = MatchAfterImmediately()
        )
    )
)
