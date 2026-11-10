/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4324
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.playlist

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.methodCall

/**
 * Touch listener of the drag handle of a list row, which is built for the handle view.
 */
internal object DragHandleTouchListenerFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/view/View;", "L", "L", "Landroid/os/Handler;", "I"),
    filters = listOf(
        methodCall(smali = "Landroid/view/ViewConfiguration;->getScaledTouchSlop()I")
    )
)

/**
 * Litho is not obfuscated and the method overrides the framework.
 */
internal object ComponentHostInterceptTouchFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/litho/ComponentHost;",
    name = "onInterceptTouchEvent",
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;")
)

/**
 * Litho is not obfuscated and the method overrides the framework.
 */
internal object ComponentHostTouchFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/litho/ComponentHost;",
    name = "dispatchTouchEvent",
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;")
)
