/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

@file:Suppress("SpellCheckingInspection")

package io.github.kawinnext.morphe.youtube.layout.miniplayer

import io.github.kawinnext.RequireAppVersion
import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.checkCast
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.newInstance
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.string
import io.github.kawinnext.morphe.youtube.shared.SeekbarFingerprint

/**
 * Feature flag of the miniplayer that every type but DEFAULT turns on.
 *
 * The flag is read through the flag method that FeatureOverride hooks, so no fingerprint is
 * needed for it.
 */
internal const val MINIPLAYER_MODERN_FEATURE_KEY = 45622882L

/**
 * Read by the handler that closes the miniplayer right after it is shown, which is the DISABLED
 * miniplayer type.
 */
internal const val MINIPLAYER_DISABLED_FEATURE_KEY = 45657015L

private val miniplayerRoundedCornersFilters = listOf(
    methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        smali = "Landroid/view/View;->setClipToOutline(Z)V"
    ),
    methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        smali = "Landroid/view/View;->setOutlineProvider(Landroid/view/ViewOutlineProvider;)V"
    )
)

/**
 * 21.45+ rounded corners of the miniplayer.
 */
@RequireAppVersion(minVersion = "21.45.0")
internal object MiniplayerRoundedCornersFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Z", "Z"),
    filters = miniplayerRoundedCornersFilters
)

/**
 * 21.44 rounded corners of the miniplayer.
 */
@RequireAppVersion(maxVersion = "21.44.9999")
internal object MiniplayerRoundedCorners2144Fingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Z"),
    filters = miniplayerRoundedCornersFilters
)

@StrictQuery
internal object MiniplayerDragAndDropFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/view/View;", "Landroid/view/MotionEvent;"),
    filters = listOf(
        opcode(opcode = Opcode.SUB_FLOAT_2ADDR),
        opcode(opcode = Opcode.APUT, location = MatchAfterImmediately()),
        opcode(opcode = Opcode.AGET, location = MatchAfterImmediately()),
        opcode(opcode = Opcode.AGET, location = MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/view/MotionEvent;->getActionMasked()I",
            location = MatchAfterImmediately()
        ),
        opcode(opcode = Opcode.MOVE_RESULT, location = MatchAfterImmediately())
    )
)

/**
 * Drag area of the miniplayer, and its last position in the field the reposition method reads.
 */
internal object MiniplayerRectDragFieldsNameFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/graphics/Rect;",
    parameters = listOf("I", "I"),
    filters = listOf(
        opcode(opcode = Opcode.IF_GEZ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/graphics/Rect;->width()I",
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT,
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.NEG_INT,
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.GOTO,
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IGET,
            type = "I",
            location = MatchAfterImmediately()
        )
    )
)

internal object MiniplayerHorizontalRepositionFingerprint : Fingerprint(
    classFingerprint = MiniplayerRectDragFieldsNameFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/graphics/Rect;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = "Landroid/graphics/Rect;",
            name = "left"
        )
    )
)

internal object MiniplayerOffscreenRectValidatorFingerprint : Fingerprint(
    // Private in 21.44+.
    parameters = listOf("I", "I", "I"),
    filters = listOf(
        opcode(opcode = Opcode.IGET_OBJECT),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/graphics/Rect;->centerX()I",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/graphics/Rect;->centerY()I",
            location = MatchAfterWithin(3)
        ),
        newInstance(type = "Landroid/graphics/Point;", location = MatchAfterWithin(3))
    )
)

internal object MiniplayerOffscreenHandlerFingerprint : Fingerprint(
    classFingerprint = MiniplayerRectDragFieldsNameFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "I", "I", "I"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Landroid/graphics/Rect;"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Landroid/graphics/Rect;->set(IIII)V")
    )
)

internal object NextGenWatchLayoutOnInterceptTouchEventFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/watch/nextgenwatch/ui/NextGenWatchLayout;",
    name = "onInterceptTouchEvent",
    parameters = listOf("Landroid/view/MotionEvent;")
)

private object MiniplayerModernViewParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("player_overlay_modern_mini_player_controls")
    )
)

internal object MiniplayerModernCloseButtonFingerprint : Fingerprint(
    classFingerprint = MiniplayerModernViewParentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "modern_miniplayer_close"),
        checkCast("Landroid/widget/ImageView;")
    )
)

internal object MiniplayerModernExpandButtonFingerprint : Fingerprint(
    classFingerprint = MiniplayerModernViewParentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "modern_miniplayer_expand"),
        checkCast("Landroid/widget/ImageView;")
    )
)

internal object MiniplayerModernActionButtonFingerprint : Fingerprint(
    classFingerprint = MiniplayerModernViewParentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "modern_miniplayer_overlay_action_button"),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterWithin(5))
    )
)

/**
 * 21.17+ sets the icon of the miniplayer action button together with its content description,
 * which is the playback state at the moment it changes.
 */
internal object MiniplayerSetIconsFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "Landroid/graphics/drawable/Drawable;",
        filters = listOf(
            resourceLiteral(ResourceType.DIMEN, "shadow_icon_offset_x"),
            resourceLiteral(ResourceType.DIMEN, "shadow_icon_offset_y")
        )
    ),
    returnType = "V",
    parameters = listOf("Landroid/graphics/drawable/Drawable;", "I"),
    filters = listOf(
        methodCall(smali = "Landroid/widget/ImageView;->setImageDrawable(Landroid/graphics/drawable/Drawable;)V")
    )
)

/**
 * 21.43+
 *
 * Matches the resume (Continue watching) check of the show miniplayer command.
 */
internal object ShowMiniplayerCommandFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(
            literal(164817L),
            literal(121253L)
        )
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L", "Ljava/util/Map;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            definingClass = "this",
            returnType = "Z"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT,
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.IF_EQZ,
            location = MatchAfterImmediately()
        )
    )
)

internal object MiniplayerControlsFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "controls_layout"),
        checkCast("Landroid/view/ViewGroup;", location = MatchAfterWithin(5))
    )
)

internal object MiniplayerControlsVisibilityFingerprint : Fingerprint(
    classFingerprint = MiniplayerControlsFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = $$"Landroid/view/ViewGroup;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;"
        ),
        literal(8),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/view/ViewGroup;->setVisibility(I)V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Reports the height the snackbars and the other bottom messages take, for every frame they slide.
 * The first call passes it on, once every check has allowed it. YouTube moves its own miniplayer
 * up by it.
 */
internal object BottomUiContainerReportHeightFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/common/ui/bottomui/BottomUiContainer;",
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(parameters = listOf("L", "I"), returnType = "V")
    )
)

/**
 * The progress bar under the glass bar is cut to the straight bottom edge of the glass.
 *
 * Upstream also anchors this to the rounded rectangle the seekbar draws, which matches nothing on
 * the 21.44 and 21.45 targets, so the draw method of the seekbar is matched by its own class.
 */
internal object SeekbarOnDrawFingerprint : Fingerprint(
    classFingerprint = SeekbarFingerprint,
    name = "onDraw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;")
)

/**
 * 21.30+
 *
 * Sets up the animator that answers the horizontal drag of the miniplayer. Its listener is the
 * place the playback is paused from.
 */
@StrictQuery
internal object MiniplayerHorizontalDragPlaybackFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = $$"Landroid/animation/ValueAnimator;->addUpdateListener(Landroid/animation/ValueAnimator$AnimatorUpdateListener;)V",
        ),
        opcode(
            opcode = Opcode.NEW_INSTANCE,
            location = MatchAfterWithin(4)
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            location = MatchAfterWithin(4)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = $$"Landroid/animation/ValueAnimator;->addListener(Landroid/animation/Animator$AnimatorListener;)V",
            location = MatchAfterWithin(4)
        ),
        opcode(
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(4)
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "Ljava/lang/Object;",
            location = MatchAfterWithin(4)
        )
    )
)
