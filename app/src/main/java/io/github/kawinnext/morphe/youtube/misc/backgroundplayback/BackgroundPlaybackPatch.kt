package io.github.kawinnext.morphe.youtube.misc.backgroundplayback

import app.morphe.extension.youtube.patches.BackgroundPlaybackPatch
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.insertLiteralOverride
import io.github.kawinnext.morphe.youtube.misc.playercontrols.disableNewPlayerControlsFeatureFlag
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_20_29_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_20_49_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_15_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_21_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_23_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_36_or_greater
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.video.information.onCreateHook
import io.github.kawinnext.hookMethod
import io.github.kawinnext.patch
import java.lang.reflect.Method

private var backgroundPlaybackSettingInstance: Any? = null
private var backgroundPlaybackSettingGetter: Method? = null

/**
 * Reads the background playback setting of the account the same way the app does, so the extension
 * knows whether playback may resume when the app returns to the foreground.
 * <p>
 * The upstream patch adds the method that answers this to the class of the setting. A module cannot
 * add a method to a class of the app, so the setting object is kept here and read again.
 */
private object BackgroundPlaybackAccountSetting : BackgroundPlaybackPatch.BackgroundPlaybackSetting {
    override fun patch_isBackgroundPlaybackAllowed(): Boolean {
        val instance = backgroundPlaybackSettingInstance ?: return true
        val getter = backgroundPlaybackSettingGetter ?: return true

        return runCatching { getter.invoke(instance) as? Boolean ?: true }.getOrDefault(true)
    }
}

val BackgroundPlayback = patch(
    name = "Remove background playback restrictions",
    description = "Removes restrictions on background playback, including playing kids videos in the background.",
) {

    dependsOn(VersionCheck)

    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("morphe_shorts_disable_background_playback")
    )

    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_remove_background_playback_restrictions"),
        ListPreference("morphe_auto_pause_on_screen_lock")
    )

    onCreateHook.add(BackgroundPlaybackPatch::initialize)

    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("morphe_shorts_disable_background_playback"),
    )

    BackgroundPlaybackManagerFingerprint.hookMethod {
        after {
            it.result = BackgroundPlaybackPatch.isBackgroundPlaybackAllowed(it.result as Boolean)
        }
    }

    ::backgroundPlaybackManagerShortsFingerprint.dexMethodList.forEach {
        it.hookMethod {
            after {
                it.result =
                    BackgroundPlaybackPatch.isBackgroundShortsPlaybackAllowed(it.result as Boolean)
            }
        }
    }

    // Enable background playback option in YouTube settings
    val backgroundPlaybackAllowedMatch = if (is_21_23_or_greater)
        ::backgroundPlaybackSettingsSubFingerprint
    else
        ::backgroundPlaybackSettingsLegacySubFingerprint
    backgroundPlaybackAllowedMatch.hookMethod(returnConstant(true))

    // Prevents playback from resuming if it was interrupted from the notification
    // and the app was subsequently brought to the foreground.
    if (is_21_15_or_greater) {
        insertLiteralOverride(45770945L, BackgroundPlaybackPatch::isAutomaticForegroundPlaybackAllowed)

        // The setting object is kept as it is read, and the extension reads its value again.
        backgroundPlaybackSettingGetter = ::backgroundPlaybackSettingMethod.member as? Method
        ::backgroundPlaybackSettingMethod.hookMethod {
            after { param ->
                val instance = param.thisObject ?: return@after
                backgroundPlaybackSettingInstance = instance
                BackgroundPlaybackPatch.setBackgroundPlaybackSetting(BackgroundPlaybackAccountSetting)
            }
        }
    }

    // Prevents playback from pausing when the overlay video settings is invoked.
    if (is_20_49_or_greater) {
        insertLiteralOverride(45741823L, BackgroundPlaybackPatch::isAutomaticPlaybackPauseInFlyout)
    }

    // Force allowing background play for Shorts.
    insertLiteralOverride(45415425, true)

    // Force allowing background play for videos labeled for kids.
    KidsBackgroundPlaybackPolicyControllerFingerprint.hookMethod(returnConstant(Unit))

    // Fix PiP buttons not working after locking/unlocking device screen.
    if (!is_21_21_or_greater) {
        insertLiteralOverride(45638483L)
    }

    if (is_20_29_or_greater && !is_21_36_or_greater) {
        // Client flag that interferes with background playback of some video types.
        // Exact purpose is not clear and it's used in ~ 100 locations.
        // Flag cannot be forced off with 21.36+ or the player seekbar is missing.
        //
        // Edit: This override may not be needed and only 45752335L override might be needed.
        insertLiteralOverride(45698813L)
    }

    // If NewPlayerTypeEnumFeatureFlagFingerprint is overridden then must also
    // force off new player control flags otherwise player has no buttons visible.
    disableNewPlayerControlsFeatureFlag()
}