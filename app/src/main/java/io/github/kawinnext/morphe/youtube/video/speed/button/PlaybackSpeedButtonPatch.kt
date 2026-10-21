package io.github.kawinnext.morphe.youtube.video.speed.button

import app.morphe.extension.youtube.videoplayer.PlaybackSpeedDialogButton
import io.github.kawinnext.R
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.kawinnext.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.kawinnext.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.kawinnext.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.kawinnext.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.kawinnext.morphe.youtube.misc.playercontrols.addLegacyBottomControl
import io.github.kawinnext.morphe.youtube.misc.playercontrols.initializeLegacyBottomControl
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.information.userSelectedPlaybackSpeedHook
import io.github.kawinnext.morphe.youtube.video.information.videoSpeedChangedHook
import io.github.kawinnext.morphe.youtube.video.speed.custom.CustomPlaybackSpeed
import io.github.kawinnext.patch

val PlaybackSpeedButton = patch(
    description = "Adds the option to display playback speed dialog button in the video player.",
) {
    dependsOn(
        CustomPlaybackSpeed,
        LegacyPlayerControls,
        playerOverlayButtonsHook,
        VideoInformationPatch,
    )

    addPlayerOverlayPreferences(
        SwitchPreference("morphe_playback_speed_dialog_button", summary = true),
    )

    addPlayerBottomButton(PlaybackSpeedDialogButton::initializeButton)

    addLegacyBottomControl(R.layout.morphe_playback_speed_dialog_button)
    initializeLegacyBottomControl(
        ControlInitializer(
            R.id.morphe_playback_speed_dialog_button_container,
            PlaybackSpeedDialogButton::initializeLegacyButton
        )
    )

    videoSpeedChangedHook.add { PlaybackSpeedDialogButton.videoSpeedChanged(it) }
    userSelectedPlaybackSpeedHook.add { PlaybackSpeedDialogButton.videoSpeedChanged(it) }
}

