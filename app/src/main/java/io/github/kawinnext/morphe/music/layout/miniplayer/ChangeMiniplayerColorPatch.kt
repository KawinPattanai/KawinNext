/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.miniplayer

import android.view.View
import app.morphe.extension.music.patches.ChangeMiniplayerColorPatch
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.indexOfFirstInstructionOrThrow
import io.github.kawinnext.morphe.indexOfFirstInstructionReversedOrThrow
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

/** Field of the miniplayer that holds the color of its background. */
private val miniplayerColorField = findFieldDirect {
    val constructor = MiniPlayerConstructorFingerprint()
    val colorGreyIndex = constructor.indexOfFirstInstructionReversedOrThrow(methodCall(name = "getColor"))
    val colorPutIndex = constructor.indexOfFirstInstructionOrThrow(colorGreyIndex, opcode(Opcode.IPUT))
    constructor.instructions[colorPutIndex].fieldRef!!
}

/** Object the color of the album art is read from, which is passed to the toggle method. */
private val albumColorSourceField = findFieldDirect {
    SwitchToggleColorFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/** Color of the album art, read from the object of [albumColorSourceField]. */
private val albumColorField = findFieldDirect {
    SwitchToggleColorFingerprint.instructionMatches[4].instruction.fieldRef!!
}

/** Dismiss callback of the watch-while view, which the peer of the activity owns. */
private val watchWhileDismissedMethod = findMethodDirect {
    val musicActivityPeerClass = MusicActivityWidgetFingerprint.instructionMatches[1]
        .instruction.fieldRef!!.declaredClass!!.descriptor
    watchWhileDismissedFingerprint(musicActivityPeerClass).match().method
}

/**
 * Hooks the color of the miniplayer. The glass miniplayer and the glass navigation bar take the
 * color of the album art through these hooks too, so it is a patch of its own.
 */
internal val miniplayerColorHookPatch = patch {
    val miniplayerColor = ::miniplayerColorField.field.apply { isAccessible = true }
    val albumColorSource = ::albumColorSourceField.field.apply { isAccessible = true }
    val albumColor = ::albumColorField.field.apply { isAccessible = true }

    // The color the miniplayer gets from the theme.
    MiniPlayerConstructorFingerprint.hookMethod {
        after { param ->
            ChangeMiniplayerColorPatch.setDefaultMiniplayerColor(miniplayerColor.getInt(param.thisObject))
        }
    }

    // The extension decides the color of the miniplayer, and keeps the color of the album art
    // for the navigation bar and the glass.
    SwitchToggleColorParentFingerprint.hookMethod {
        before { param ->
            if (!ChangeMiniplayerColorPatch.changeMiniplayerColor()) return@before

            val colorSource = albumColorSource.get(param.args[0]) ?: return@before
            val color = ChangeMiniplayerColorPatch.setLastMiniplayerColor(albumColor.getInt(colorSource))
            miniplayerColor.setInt(param.thisObject, color)
        }
    }

    // Only the 'ytm_color_grey_12' branch of the method colors the visible tab bar, and the
    // view is published so the extension can repaint without waiting for a relayout.
    NavigationBarTabLayoutFingerprint.hookMethod(
        scopedHook(DexMethod("Landroid/view/View;->setBackgroundColor(I)V").toMember()) {
            before { param ->
                val color = param.args[0] as Int
                val navigationBar = outerParam.thisObject as? View
                    ?: param.thisObject as? View
                    ?: return@before

                ChangeMiniplayerColorPatch.registerNavigationBar(navigationBar, color)
                param.args[0] = ChangeMiniplayerColorPatch.overrideNavigationBarColor(color)
            }
        }
    )

    // The watch-while dismiss callback drops the cached color of the album art.
    ::watchWhileDismissedMethod.hookMethod {
        before { ChangeMiniplayerColorPatch.onMiniplayerDismissed() }
    }
}

val ChangeMiniplayerColor = patch(
    name = "Change miniplayer color",
    description = "Adds an option to change the miniplayer background color to match the fullscreen player."
) {
    dependsOn(miniplayerColorHookPatch)

    PreferenceScreen.addMiniplayerPreferences(
        SwitchPreference("morphe_music_change_miniplayer_color", summary = true),
        SwitchPreference("morphe_music_change_navigation_bar_color", summary = true)
    )
}
