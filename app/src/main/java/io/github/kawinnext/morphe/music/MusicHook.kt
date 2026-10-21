package io.github.kawinnext.morphe.music

import io.github.kawinnext.ExtensionResourceHook
import io.github.kawinnext.morphe.music.ad.HideAds
import io.github.kawinnext.morphe.music.audio.exclusiveaudio.EnableExclusiveAudioPlayback
import io.github.kawinnext.morphe.music.layout.upgradebutton.HideUpgradeButton
import io.github.kawinnext.morphe.music.misc.backgroundplayback.BackgroundPlayback
import io.github.kawinnext.morphe.music.misc.debugging.EnableDebugging
import io.github.kawinnext.morphe.music.misc.privacy.SanitizeSharingLinks
import io.github.kawinnext.morphe.music.misc.settings.SettingsHook
import io.github.kawinnext.morphe.shared.misc.CheckRecycleBitmapMediaSession

val YTMusicPatches = arrayOf(
    ExtensionResourceHook,
    BackgroundPlayback,
    HideUpgradeButton,
    HideAds,
    EnableExclusiveAudioPlayback,
    CheckRecycleBitmapMediaSession,
    EnableDebugging,
    SanitizeSharingLinks,
    SettingsHook
)