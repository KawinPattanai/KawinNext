/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.fullscreen.QueueFullscreenRenderer
import app.morphe.extension.youtube.patches.queue.QueueFullscreenActions
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream adds two bridge methods to the controller of the fullscreen quick actions, one that
// parses the model the extension builds and one that renders it again, and injects one call at the
// point the controller takes a model. A module cannot add a method to a class of the app, so the two
// bridges are the proxy below and that call is the hooked setter.

/** The container of the quick actions, which the controller stores when it binds its view. */
private val quickActionsContainer = findFieldDirect {
    QuickActionsContainerFingerprint.match().instructionMatches.last().instruction.fieldRef!!
}

/** The controller of the quick actions, of which that container is a field. */
private val quickActionsController = findClassDirect { quickActionsContainer().declaredClass!! }

private fun DexKitBridge.quickActionsControllerType(): String = quickActionsController().descriptor

/** The callback that accepts the quick actions model of the video that is played. */
private val quickActionsCallback = findMethodDirect {
    quickActionsModelCallbackFingerprint(quickActionsControllerType()).match().method
}

/** The setter that callback uses to hand the model to the presenter of the actions. */
private val quickActionsSetter = findMethodDirect {
    quickActionsModelCallbackFingerprint(quickActionsControllerType()).match()
        .instructionMatches.last().instruction.methodRef!!
}

/** The native reset of the rendered actions, which also releases the cached component tree. */
private val quickActionsReset = findMethodDirect {
    quickActionsResetFingerprint(quickActionsControllerType(), quickActionsSetter()).match().method
}

/** The dimensions the controller measured last, which the native binding reads again. */
private val quickActionsDimensions = findFieldDirect {
    quickActionsSizeFingerprint(quickActionsControllerType()).match()
        .instructionMatches[1].instruction.fieldRef!!
}

/** The emitter of those dimensions, which runs the native measuring and binding again. */
private val quickActionsEmit = findMethodDirect {
    quickActionsSizeFingerprint(quickActionsControllerType()).match()
        .instructionMatches[2].instruction.methodRef!!
}

/** The dimensions the subject holds now, which is the value the controller re-emits. */
private val quickActionsCurrentDimensions = findMethodDirect {
    Fingerprint(
        definingClass = quickActionsDimensions().type.descriptor,
        parameters = emptyList(),
        returnType = "Ljava/lang/Object;",
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    )()
}

/**
 * The controller of the quick actions, which stands in for the two bridge methods upstream adds to
 * its class: the model the extension builds is parsed with the app's own schema, and the refresh
 * repeats the calls the controller itself makes when it takes a model.
 *
 * The model crosses as the extension's own copy of it, which is what a bridge method of an installed
 * patch would hand over, so the refresh turns it back into a message of the app before the setter
 * of the controller stores it.
 */
private class FullscreenControllerProxy(
    private val controller: Any,
    private val modelType: Class<*>,
    private val reset: Method,
    private val setter: Method,
    private val dimensions: Field,
    private val currentDimensions: Method,
    private val emit: Method
) : QueueFullscreenActions.Controller {
    override fun patch_parseQuickActions(bytes: ByteArray): Any = parseAppMessage(modelType, bytes)

    override fun patch_refreshQuickActions(model: Any?) {
        val stored = appMessageBytes(model)?.let { parseAppMessage(modelType, it) } ?: return
        // The native reset also releases the presenter's cached component tree. Merely resetting the
        // rendered flag reuses the previous button layout. Its lifecycle argument is unused; null is
        // safe for this UI-only reset.
        reset.call(controller, null)
        setter.call(controller, stored)
        val subject = bridgeField(dimensions, controller) ?: return
        val measured = bridgeCall(currentDimensions, subject) ?: return
        emit.call(subject, measured)
    }
}

/**
 * Lets the extension add the playlist action to the quick actions of the fullscreen player.
 *
 * Upstream injects the call at the top of the native setter, so a model the extension builds is what
 * the native setter then stores, keeping its own null handling, its visibility policy and its other
 * action bindings. Here the setter is hooked the same way, with the proxy of its controller.
 */
internal fun PatchExecutor.installFullscreenPlaylist() {
    val modelType = ::quickActionsSetter.method.parameterTypes.single()
    val reset = ::quickActionsReset.method
    val setter = ::quickActionsSetter.method
    val dimensions = ::quickActionsDimensions.field
    val currentDimensions = ::quickActionsCurrentDimensions.method
    val emit = ::quickActionsEmit.method
    val proxies = ProxyCache { controller: Any ->
        FullscreenControllerProxy(
            controller, modelType, reset, setter, dimensions, currentDimensions, emit
        )
    }

    ::quickActionsSetter.method.hookMethod {
        before { param ->
            val controller = param.thisObject ?: return@before
            val model = param.args.getOrNull(0)

            // The extension reads the model with its own schema, so it is handed its own copy of the
            // same message. A null model is the reset path, which the extension records as well.
            val carried = model?.let { app ->
                queueAction("parseQuickActions") {
                    bridgeMessage(app) { bytes -> QueueFullscreenRenderer.Renderer.parseFrom(bytes) }
                }
            }
            val replaced = queueAction("onModelLoaded") {
                QueueFullscreenActions.onModelLoaded(proxies.get(controller), carried)
            } ?: return@before

            // Only a model the extension built, which is a message of the app again, replaces the one
            // the native setter received. Its own copy is not stored in the app.
            if (replaced !== carried) param.args[0] = replaced
        }
    }
}
