/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.PlayerControls
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream gives the native controls that hold the previous and next buttons the PlayerControls
// interface, injects the extension decision at the point each of them compares its computed
// eligibility with the cached one, and injects it at the press callbacks before the native
// delegate or history branch runs. A module cannot add a member to a class of the app, so the
// interface is the proxy below, the eligibility is replaced at the native capability call that
// feeds it, and the callbacks are hooked at entry.

/** The playlist navigator the controls of the playlist are created with. */
private fun DexKitBridge.playlistNavigatorType(): String =
    PlaylistControlsCreationFingerprint.match().instructionMatches[4].instruction.classRef!!.descriptor

/** The native request of one skip direction, which the controls dispatch. */
private fun DexKitBridge.playerControlRequest(direction: String): FieldData {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    val intent = playlistNavigationIntentFingerprint(enum, direction).match()
        .instructionMatches.last().instruction.fieldRef!!
    return playlistNavigationSingletonFingerprint(nativeQueueRequestClass(this).descriptor, intent)
        .match().instructionMatches.last().instruction.fieldRef!!
}

/** The eligibility check of the controls, one per direction, which caches what it computes. */
private val playerControlEligibility = findMethodListDirect {
    val navigator = playlistNavigatorType()
    listOf("NEXT", "PREVIOUS").map { direction ->
        playlistControlAvailabilityFingerprint(navigator, playerControlRequest(direction))
            .match().method
    }
}

/** The navigator field of the controls. */
private val playerControlsNavigator = findFieldDirect {
    val navigator = playlistNavigatorType()
    playlistControlAvailabilityFingerprint(navigator, playerControlRequest("NEXT"))
        .match().instructionMatches[4].instruction.fieldRef!!
}

/** The delegate field the controls hand a skip to while a watch screen is open. */
private val playerControlsDelegate = findFieldDirect {
    val navigator = playlistNavigatorType()
    playlistControlAvailabilityFingerprint(navigator, playerControlRequest("NEXT"))
        .match().instructionMatches.first().instruction.fieldRef!!
}

/** The constructor that keeps the navigator of the playlist. */
private val playerControlsConstructor = findMethodDirect {
    val navigator = playlistNavigatorType()
    val controls = playlistControlAvailabilityFingerprint(
        navigator, playerControlRequest("NEXT")
    ).match().method.declaredClass!!.descriptor
    playlistPlayerControlsConstructorFingerprint(controls, navigator).match().method
}

/** The press callback of each direction, which logs first and dispatches after. */
private val playerControlPresses = findMethodListDirect {
    val navigator = playlistNavigatorType()
    val availability = playlistControlAvailabilityFingerprint(
        navigator, playerControlRequest("NEXT")
    ).match()
    val controls = availability.method.declaredClass!!.descriptor
    val delegate = availability.instructionMatches.first().instruction.fieldRef!!
    listOf("NEXT", "PREVIOUS").map { direction ->
        playlistControlPressFingerprint(controls, delegate, playerControlRequest(direction))
            .match().method
    }
}

/** The native capability check of the navigator, which both eligibility checks evaluate. */
private val playerControlsCapability = findMethodDirect {
    playlistServiceCanNavigateFingerprint(
        playlistNavigatorType(), nativeQueueRequestClass(this).descriptor
    ).match().method
}

/** The direction of the Next control in the native order of the two checks. */
private const val NEXT_DIRECTION = 0

/**
 * The controls of the native player, which stand in for the PlayerControls interface upstream adds
 * to their class: one bridge reads the navigator they keep, the other recomputes both eligibility
 * values, which runs the two native checks again on every refresh.
 */
private class PlayerControlsProxy(
    private val controls: Any,
    private val navigator: Field,
    private val eligibility: List<Method>
) : PlayerControls {
    override fun patch_getNavigator(): Any? = bridgeField(navigator, controls)

    override fun patch_refreshNavigation() {
        eligibility.forEach { bridgeCall(it, controls) }
    }
}

/**
 * Lets the owned queue keep its native skip buttons enabled and working.
 *
 * Upstream injects the extension decision into the register each eligibility check compares with
 * its cached value, and returns before the native press when the extension navigated. Here the
 * navigator capability call that feeds that register is hooked inside the check that makes it, so
 * the extension decides the computed value before the native comparison, and each press callback is
 * hooked at entry.
 *
 * @param navigatorType The playlist navigator that the controls hold.
 * @param enumType The enum of the navigation intents of a request.
 * @param wrapperType The request class of one navigation.
 */
internal fun PatchExecutor.installPlaylistPlayerControls(
    navigatorType: Class<*>,
    enumType: Class<*>,
    wrapperType: Class<*>
) {
    val navigator = ::playerControlsNavigator.field
    check(navigator.type == navigatorType) {
        "Native playlist queue: the player controls hold a navigator of another class"
    }
    check(::playerControlsDelegate.field.declaringClass == ::playerControlsConstructor.method.declaringClass) {
        "Native playlist queue: the player controls of the eligibility check are another class"
    }
    check(NavigationIntentEnumFingerprint.declaredClass == enumType) {
        "Native playlist queue: the player controls were given another navigation enum"
    }
    check(::nativeQueueRequestClass.clazz == wrapperType) {
        "Native playlist queue: the player controls were given another navigation request"
    }

    val eligibility = ::playerControlEligibility.dexMethodList.map { it.toMethod() }
    val controls = ProxyCache { instance: Any ->
        PlayerControlsProxy(instance, navigator, eligibility)
    }
    val presses = ::playerControlPresses.dexMethodList.map { it.toMethod() }
    val capability = ::playerControlsCapability.method

    ::playerControlsConstructor.method.hookMethod {
        after { param ->
            val instance = param.thisObject ?: return@after
            queueAction("onPlayerControlsCreated") {
                NativePlaylistQueuePatch.onPlayerControlsCreated(controls.get(instance))
            }
        }
    }

    eligibility.forEachIndexed { direction, check ->
        // The extension replaces the value the native check computed, which is the register the
        // upstream injection rewrites just before the cached value is compared with it.
        check.hookMethod(scopedHook(capability) {
            after { param ->
                val original = param.result as? Boolean ?: return@after
                val instance = outerParam.thisObject ?: return@after
                param.result = queueAction("playerControlAvailability") {
                    NativePlaylistQueuePatch.playerControlAvailability(
                        controls.get(instance), direction == NEXT_DIRECTION, original
                    )
                } ?: original
            }
        })
    }

    presses.forEachIndexed { direction, press ->
        press.hookMethod {
            before { param ->
                val instance = param.thisObject ?: return@before
                // A skip the extension navigated must not fall into the history or restart branch.
                val navigated = queueAction("playerControlPress") {
                    if (direction == NEXT_DIRECTION) {
                        NativePlaylistQueuePatch.onNextPressed(controls.get(instance))
                    } else {
                        NativePlaylistQueuePatch.onPreviousPressed(controls.get(instance))
                    }
                } ?: false
                if (navigated) param.result = null
            }
        }
    }
}
