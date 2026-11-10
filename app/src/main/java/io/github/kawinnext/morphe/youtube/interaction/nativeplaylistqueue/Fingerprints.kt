/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4404
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.anyInstruction
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.string

/** The native batch request, not the modern panel's single-video parameter. */
internal object QueueSaveRequestFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        string("playlist/get_add_to_playlist")
    )
)

/** Response callback owned by the picker request; the renderer discriminator is stable. */
internal object QueueSaveResponseFingerprint : Fingerprint(
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "V",
    filters = listOf(
        opcode(Opcode.CHECK_CAST),
        literal(88978004L)
    )
)

/** Native edit callback error diagnostics identify the same request's success listener. */
internal object QueueSaveEditErrorFingerprint : Fingerprint(
    filters = listOf(
        string("Error editing the playlist")
    )
)

// The navigation descriptor lookup is not found by the log strings of the patch set on
// 21.44.535: both the descriptor text and the null text fail to match. It needs an anchor
// from the queue class once the queue fingerprints are found.

/** Rich playlist rows, as opposed to the compact/offline binders. */
internal object PlaylistRichRowConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        resourceLiteral(type = ResourceType.ID, name = "playlist_panel_video_item")
    )
)

/** The rich row's swipe layout getter. */
internal object PlaylistRowSwipeLayoutFingerprint : Fingerprint(
    classFingerprint = PlaylistRichRowConstructorFingerprint,
    returnType = "Lcom/google/android/libraries/youtube/common/ui/swipelayout/SwipeLayout;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            type = "Lcom/google/android/libraries/youtube/common/ui/swipelayout/SwipeLayout;",
            opcode = Opcode.IGET_OBJECT
        )
    )
)

/** Native selection updates replace the swipe foreground's background. */
internal object PlaylistRowBackgroundFingerprint : Fingerprint(
    classFingerprint = PlaylistRichRowConstructorFingerprint,
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        fieldAccess(type = "Landroid/view/View;", opcode = Opcode.IGET_OBJECT),
        methodCall(
            smali = "Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V",
            location = MatchAfterImmediately()
        ),
        methodCall(
            smali = "Landroid/widget/TextView;->setTextColor(I)V",
            location = MatchAfterWithin(8)
        )
    )
)

/** SwipeLayout is an unobfuscated native widget; attachment caches its foreground drawable. */
internal object PlaylistSwipeAttachedFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/common/ui/swipelayout/SwipeLayout;",
    name = "onAttachedToWindow",
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        fieldAccess(type = "Landroid/view/View;", opcode = Opcode.IPUT_OBJECT),
        methodCall(
            smali = "Landroid/view/View;->getBackground()Landroid/graphics/drawable/Drawable;",
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            type = "Landroid/graphics/drawable/Drawable;",
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterWithin(2)
        )
    )
)

/** The native binder configures the drag handle's touch target from resources. */
internal object PlaylistDragBinderFingerprint : Fingerprint(
    classFingerprint = PlaylistRichRowConstructorFingerprint,
    returnType = "V",
    filters = listOf(
        resourceLiteral(type = ResourceType.DIMEN, name = "playlist_drag_handle_touch_padding_start")
    )
)

internal object PlaylistDragStartFingerprint : Fingerprint(
    classFingerprint = PlaylistDragBinderFingerprint,
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        anyInstruction(opcode(Opcode.IF_EQ), opcode(Opcode.IF_NE)),
        fieldAccess(
            type = "Landroid/view/View;",
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(1)
        ),
        methodCall(
            smali = "Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V",
            location = MatchAfterWithin(1)
        )
    )
)

internal object PlaylistDragEndFingerprint : Fingerprint(
    classFingerprint = PlaylistDragBinderFingerprint,
    returnType = "V",
    parameters = listOf("L", "L", "I", "I"),
    filters = listOf(
        anyInstruction(opcode(Opcode.IF_EQ), opcode(Opcode.IF_NE)),
        methodCall(
            definingClass = "this",
            opcode = Opcode.INVOKE_DIRECT,
            parameters = emptyList(),
            returnType = "V",
            location = MatchAfterWithin(1)
        )
    )
)

internal object PlaylistDragUnbindFingerprint : Fingerprint(
    classFingerprint = PlaylistDragBinderFingerprint,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->setOnTouchListener(Landroid/view/View\$OnTouchListener;)V"),
        methodCall(smali = "Landroid/view/View;->setOnLongClickListener(Landroid/view/View\$OnLongClickListener;)V")
    )
)
