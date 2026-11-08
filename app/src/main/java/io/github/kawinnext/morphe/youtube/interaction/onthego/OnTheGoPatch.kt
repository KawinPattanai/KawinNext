/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4511
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.onthego

import app.morphe.extension.youtube.patches.onthego.OnTheGoFilter
import app.morphe.extension.youtube.patches.onthego.OnTheGoPatch
import com.google.protobuf.MessageLite
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
            if (OnTheGoPatch.onCommand(param.args[0] as MessageLite)) param.result = null
        }
    }
}
