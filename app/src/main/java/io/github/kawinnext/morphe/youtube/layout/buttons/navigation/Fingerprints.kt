package io.github.kawinnext.morphe.youtube.layout.buttons.navigation

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.OpcodesFilter
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.resourceLiteral

internal object CreatePivotBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf(
        "Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;",
        "Landroid/widget/TextView;",
        "Ljava/lang/CharSequence;",
    ),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_VIRTUAL,
        Opcode.RETURN_VOID,
    ),
)

/**
 * Creates the container of the navigation bar and the slim status bar.
 */
internal object InitializeBottomBarContainerFingerprint : Fingerprint(
    name = "run",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "bottom_bar_container"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = $$"Landroid/view/View;->addOnLayoutChangeListener(Landroid/view/View$OnLayoutChangeListener;)V"
        )
    )
)

internal object AutoHideNavigationBarOnFeedScrollingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/support/v7/widget/RecyclerView;", "I", "I"),
    filters = listOf(
        methodCall("Landroid/view/ViewConfiguration;->get(Landroid/content/Context;)Landroid/view/ViewConfiguration;"),
        methodCall(
            smali = "Landroid/view/ViewConfiguration;->getScaledTouchSlop()I",
            location = MatchAfterWithin(5)
        )
    )
)

internal object AutoHideNavigationBarOnDismissMiniplayerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        literal(2),
        methodCall(
            smali = "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Ljava/util/Set;->iterator()Ljava/util/Iterator;",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Ljava/util/Iterator;->hasNext()Z",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Ljava/util/Iterator;->next()Ljava/lang/Object;",
            location = MatchAfterWithin(5)
        )
    )
)
