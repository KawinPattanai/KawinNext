/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4328
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.buttons.navigation

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.morphe.extension.music.patches.MusicGlassNavigationBar
import app.morphe.extension.music.patches.NavigationBarPatch
import app.morphe.extension.music.settings.preference.MusicGlassNavigationBarPreference
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import io.github.kawinnext.morphe.music.misc.debugging.insertLiteralOverride
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

// The extension hides the buttons and applies the glass to the tabs of the bar it is shown the tab of,
// and those calls read the visibility of views again, which reaches the hooked method once more.
private val tabLayoutRunning = object : ThreadLocal<Boolean>() {
    override fun initialValue() = false
}

val NavigationBar = patch(
    name = "Navigation bar",
    description = "Adds options to hide the navigation bar, its labels or its buttons, " +
            "and to show the navigation bar as glass."
) {
    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_music_navigation_bar_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_hide_navigation_bar_home_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_samples_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_explore_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_library_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_upgrade_button"),
                SwitchPreference("morphe_music_hide_navigation_bar"),
                SwitchPreference("morphe_music_hide_navigation_bar_labels"),
                SwitchPreference("morphe_music_navigation_bar_glass", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_navigation_bar_glass_opacity",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                SwitchPreference("morphe_music_navigation_bar_glass_light", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_navigation_bar_glass_height",
                    titleKey = "morphe_music_navigation_bar_glass_size_title",
                    summaryKey = "morphe_music_navigation_bar_glass_size_summary",
                    tag = MusicGlassNavigationBarPreference::class.java,
                    selectable = true
                )
            )
        )
    )

    // The navigation bar reads the visibility of the tab view it has just built,
    // and that view is the one the buttons and the glass background are applied to.
    TabLayoutTextFingerprint.hookMethod(
        scopedHook(DexMethod("Landroid/view/View;->getVisibility()I").toMember()) {
            before { param ->
                if (tabLayoutRunning.get()) return@before
                val tabView = param.thisObject as? View ?: return@before
                val label = tabView.findViewById<TextView>(MusicGlassNavigationBar.BUTTON_LABEL)
                    ?: return@before

                tabLayoutRunning.set(true)
                try {
                    NavigationBarPatch.hideNavigationButton(tabView)
                    NavigationBarPatch.hideNavigationLabel(label)
                    MusicGlassNavigationBar.navigationTabCreated(tabView)
                } finally {
                    tabLayoutRunning.set(false)
                }

                // Keep the button centered in the tab when the label is hidden, as the app does.
                val icon = tabView.findViewById<View>(MusicGlassNavigationBar.BUTTON_ICON)
                (icon?.layoutParams as? LinearLayout.LayoutParams)?.let { layoutParams ->
                    layoutParams.weight = 0.5f
                    icon.layoutParams = layoutParams
                }
            }
        }
    )

    // The tab layout reads the enum of the tab it is building with a static call.
    TabLayoutTextFingerprint.hookMethod(
        scopedHook(::tabLayoutNavigationEnumMethod.member) {
            after { param -> NavigationBarPatch.setLastAppNavigationEnum(param.result as? Enum<*>) }
        }
    )

    // The glass navigation bar uses the floating miniplayer of the app.
    insertLiteralOverride(45823962L, MusicGlassNavigationBar::useFloatingMiniplayer)

    // The pages are laid out behind the glass bar, down to the bottom of the screen.
    WatchWhileLayoutOnMeasureFingerprint.hookMethod(
        scopedHook(::watchWhileLayoutContentHeightCall.member) {
            before { param ->
                param.args[2] = MusicGlassNavigationBar.getContentHeight(
                    param.args[0] as View,
                    param.args[2] as Int
                )
            }
        }
    )
}
