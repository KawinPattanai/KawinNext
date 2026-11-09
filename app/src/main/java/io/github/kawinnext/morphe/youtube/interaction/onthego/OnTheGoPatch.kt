/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4511
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.onthego

import app.morphe.extension.shared.Utils
import app.morphe.extension.youtube.patches.onthego.OnTheGoFilter
import app.morphe.extension.youtube.patches.onthego.OnTheGoPatch
import app.morphe.extension.youtube.patches.utils.FlyoutUtils
import app.morphe.extension.youtube.settings.Settings
import io.github.kawinnext.morphe.setExtensionIsPatchIncluded
import io.github.kawinnext.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.kawinnext.morphe.shared.misc.proto.hookElement
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.layout.flyout.flyoutPatch
import io.github.kawinnext.morphe.youtube.misc.commandrouter.commandRouterExecuteCommandMethod
import io.github.kawinnext.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.misc.proto.elementProtoParserHookPatch
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.patch
import java.lang.reflect.Field
import java.lang.reflect.Method

// The commands of the app are messages of the app, which is not the protobuf of the module:
// the module repackages its own copy under another name, so the command cannot be cast to it.
// The panel is opened from the bytes of the command instead, which are the only thing the
// extension needs from it.
private val onTheGoUpsellRequest = runCatching {
    OnTheGoPatch::class.java.getDeclaredField("upsellRequest").apply { isAccessible = true }
}.getOrNull()

private val onTheGoSheetShow = runCatching {
    Class.forName(
        "app.morphe.extension.youtube.patches.onthego.OnTheGoSheet",
        false,
        OnTheGoPatch::class.java.classLoader
    ).getDeclaredMethod("show").apply { isAccessible = true }
}.getOrNull()

private fun isOnTheGoCommand(command: Any): Boolean {
    val show = onTheGoSheetShow ?: return false
    val request = onTheGoUpsellRequest?.get(null) as? ByteArray ?: return false
    val bytes = runCatching {
        command.javaClass.getMethod("toByteArray").invoke(command) as? ByteArray
    }.getOrNull() ?: return false

    if (FlyoutUtils.byteIndexOf(bytes, request) < 0) return false

    Utils.runOnMainThread { show.invoke(null) }
    return true
}

// The app cannot be made to implement the interfaces the extension is handed at runtime,
// so an object of the module stands in for the app object and calls its setter, or reads its field.
private class OnTheGoStateProxy(
    private val state: Any,
    private val setter: Method
) : OnTheGoPatch.OnTheGoState {
    override fun patch_setOnTheGoActive(active: Boolean) {
        setter.invoke(state, active)
    }
}

private class SeekLengthProxy(
    private val length: Any,
    private val field: Field,
    private val toMillis: Method
) : OnTheGoPatch.SeekLength {
    override fun patch_getSeekLengthMilliseconds(): Long =
        toMillis.invoke(field.get(length)) as Long
}

val onTheGoPatch = patch(
    name = "On-the-go player",
    description = "Adds an option to open a panel with large playback controls " +
            "from the On-the-go item of the video player menu."
) {
    dependsOn(
        elementProtoParserHookPatch,
        VideoInformationPatch,
        PlayerTypeHook,
        LithoFilter,
        flyoutPatch,
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_on_the_go", summary = true)
    )

    // The menu item of an account without Premium has the request of the upsell,
    // which is saved to find the command of the menu item when it runs.
    hookElement { bytes -> bytes?.let(OnTheGoPatch::onElementLoaded) }

    // Some accounts get a video player menu without the On-the-go item,
    // so the item is added to the menu if the filter finds no item of the app.
    setExtensionIsPatchIncluded(OnTheGoPatch::class.java)
    addLithoFilter(OnTheGoFilter())

    // Commands of Elements components, such as of the menu item, are run by the command router.
    ::commandRouterExecuteCommandMethod.hookMethod {
        before { param ->
            if (!Settings.ON_THE_GO.get()) return@before

            val command = param.args[0] ?: return@before
            if (isOnTheGoCommand(command)) param.result = null
        }
    }

    // While On-the-go is active, the lock screen and the notification show seek buttons,
    // which the app turns on through the state object of the player.
    val stateClass = "L" + OnTheGoSkipToNextActionConstructorFingerprint.method
        .parameterTypes.first().name.replace('.', '/') + ";"
    val stateSetter = onTheGoStateSetterFingerprint(stateClass).method
    onTheGoStateConstructorFingerprint(stateClass).hookMethod {
        after { OnTheGoPatch.setOnTheGoState(OnTheGoStateProxy(it.thisObject, stateSetter)) }
    }

    // The seek buttons of the panel use the double tap to seek length of the app settings.
    val seekLength = ::seekLengthField.field.apply { isAccessible = true }
    val seekLengthToMillis = ::seekLengthToMillisMethod.method
    SeekLengthConstructorFingerprint.hookMethod {
        after {
            OnTheGoPatch.setSeekLength(SeekLengthProxy(it.thisObject, seekLength, seekLengthToMillis))
        }
    }
}
