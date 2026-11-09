/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.layout.hide.ambientmode

import android.view.View
import android.view.ViewGroup
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.ambientlight.AmbientLightController
import app.morphe.extension.youtube.patches.AmbientModePatch
import app.morphe.extension.youtube.settings.Settings
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.shared.layout.theme.lithoColorHookPatch
import io.github.kawinnext.morphe.shared.layout.theme.lithoDrawableColorHook
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.insertLiteralOverride
import io.github.kawinnext.morphe.youtube.layout.sponsorblock.controlsOverlayFingerprint
import io.github.kawinnext.morphe.youtube.layout.sponsorblock.inset_overlay_view_layout
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.information.videoTimeHooks
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

/** Server flags that turn the Ambient mode of the watch page on. */
private const val AMBIENT_MODE_FEATURE_FLAG = 45376186L
private const val AMBIENT_MODE_FEATURE_FLAG_ALTERNATIVE = 45372814L

/**
 * Server experiment of the new watch page that replaces the Ambient mode with a black background.
 */
private const val AMBIENT_MODE_BLACK_BACKGROUND_FEATURE_FLAG = 45767512L

/**
 * CinematicContainerRenderer presentation style DYNAMIC_BLURRED:
 * the background takes the colors of the video.
 */
private const val PRESENTATION_STYLE_DYNAMIC_BLURRED = 5

val ambientModePatch = patch(
    name = "Ambient mode",
    description = "Adds Enhanced Ambient mode and options to force Ambient mode on, " +
            "bypass its power saving restrictions, and disable it entirely or in fullscreen.",
) {
    dependsOn(
        VersionCheck,
        PlayerTypeHook,
        VideoInformationPatch,
        lithoColorHookPatch { is_21_30_or_greater },
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_ambient_mode_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_enhanced_ambient_mode", summary = true),
                SwitchPreference("morphe_enhanced_ambient_mode_readable_text", summary = true),
                SwitchPreference("morphe_bypass_ambient_mode_restrictions", summary = true),
                SwitchPreference("morphe_disable_ambient_mode"),
                SwitchPreference("morphe_disable_fullscreen_ambient_mode"),
            )
        )
    )

    // Enhanced Ambient mode lights the page from the video, and makes the Litho backgrounds
    // the page is seen through take the colors of the video.
    lithoDrawableColorHook { drawable, color -> AmbientLightController.getLithoColor(drawable, color) }

    // The light follows the video time, and stops while the toggle of the flyout menu turns
    // the Ambient mode of the app off.
    videoTimeHooks.add(AmbientLightController::setVideoTime)

    // The flyout menu saves the state of each of its items with these methods.
    ::ambientModeToggleMethods.dexMethodList.forEach { toggleMethod ->
        toggleMethod.hookMethod {
            before { param ->
                if (param.args[0] != AMBIENT_MODE_TOGGLE_MENU_ITEM) return@before
                AmbientLightController.setAmbientModeToggle(param.args[1] as? Boolean)
            }
        }
    }

    // Add the light to the same player layout as the SponsorBlock overlay.
    val controls_overlay_layout =
        ResourceUtils.getLayoutIdentifier("size_adjustable_youtube_controls_overlay")
    ::controlsOverlayFingerprint.hookMethod(
        scopedHook(
            DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;").toMember()
        ) {
            val insetOverlayViewLayout = inset_overlay_view_layout
            after { param ->
                if (param.args[0] != controls_overlay_layout) return@after
                val layout = param.result as ViewGroup
                val overlayView = layout.findViewById<ViewGroup>(insetOverlayViewLayout) ?: return@after
                AmbientLightController.initialize(overlayView)
            }
        }
    )

    // Each of these flags is on if the Ambient mode is not disabled in the Morphe settings.
    insertLiteralOverride(AMBIENT_MODE_FEATURE_FLAG) { AmbientModePatch.disableAmbientMode(it) }
    insertLiteralOverride(AMBIENT_MODE_FEATURE_FLAG_ALTERNATIVE) { AmbientModePatch.disableAmbientMode(it) }

    // The black background experiment turns the Ambient mode off, whatever the other flags say.
    insertLiteralOverride(AMBIENT_MODE_BLACK_BACKGROUND_FEATURE_FLAG) { false }

    // The /next response can have a static color style, so the background does not change
    // with the video. Override what the server decides, unless the Ambient mode is disabled.
    val rendererConstructor = ::ambientModeRendererConstructorMethod.method
    val rendererOptionalType = rendererConstructor.parameterTypes[1]
    val optionalIsPresent = rendererOptionalType.getMethod("isPresent")
    val optionalGet = rendererOptionalType.getMethod("get")
    val optionalOf = rendererOptionalType.getMethod("of", Any::class.java)
    val optionalEmpty = rendererOptionalType.getMethod("empty")
    val styleField = ::ambientModeRendererStyleField.field
    val waitingState = ::ambientModeRendererWaitingStateField.field
    val loadedState = ::ambientModeRendererLoadedStateField.field

    rendererConstructor.hookMethod {
        before { param ->
            val optional = param.args[1] ?: return@before
            // Nothing is decided before the /next response arrives.
            if (param.args[0] != waitingState.get(null)) {
                if (!AmbientModePatch.disableAmbientMode(true)) {
                    // The Ambient mode is disabled, so the renderer of the server is not used either.
                    param.args[1] = optionalEmpty.invoke(null)
                } else if (optionalIsPresent.invoke(optional) == true) {
                    val renderer = optionalGet.invoke(optional) ?: return@before
                    styleField.setInt(renderer, PRESENTATION_STYLE_DYNAMIC_BLURRED)
                    param.args[1] = optionalOf.invoke(null, renderer)
                }
            }
            // The renderer of the /next response is the state the watch page waited for.
            if (optionalIsPresent.invoke(param.args[1]) == true) param.args[0] = loadedState.get(null)
        }
    }

    // Battery saver turns the Ambient mode of the app off. The bypass reports that the system
    // is not in power save mode to the state object of the Ambient mode.
    val powerSaveModeSink = ::powerSaveModeSinkMethod.method
    ::powerSaveModeReadMethods.dexMethodList.forEach { readMethod ->
        readMethod.hookMethod(
            scopedHook(powerSaveModeSink) {
                before { param ->
                    if (Settings.BYPASS_AMBIENT_MODE_RESTRICTIONS.get()) param.args[0] = false
                }
            }
        )
    }

    // The Ambient mode seen in fullscreen is only the background color of the player.
    SetFullScreenBackgroundColorFingerprint.hookMethod(
        scopedHook(View::class.java.getDeclaredMethod("setBackgroundColor", Int::class.javaPrimitiveType)) {
            before { param ->
                param.args[0] = AmbientModePatch.getFullScreenBackgroundColor(param.args[0] as Int)
            }
        }
    )
}
