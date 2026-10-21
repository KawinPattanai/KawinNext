package io.github.kawinnext.morphe.youtube.video.quality

import app.morphe.extension.youtube.videoplayer.VideoQualityDialogButton
import io.github.kawinnext.R
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.kawinnext.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.kawinnext.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.kawinnext.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.kawinnext.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.kawinnext.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.kawinnext.morphe.youtube.misc.playercontrols.addLegacyBottomControl
import io.github.kawinnext.morphe.youtube.misc.playercontrols.initializeLegacyBottomControl
import io.github.kawinnext.patch

val VideoQualityDialogButtonPatch = patch(
    description = "Adds the option to display video quality dialog button in the video player.",
) {
    dependsOn(
        RememberVideoQuality,
        LegacyPlayerControls,
        playerOverlayButtonsHook
    )

    addPlayerOverlayPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_video_quality_dialog_button", summary = true),
            SwitchPreference("morphe_video_quality_dialog_button_resolution", summary = true)
        )
    )
    addPlayerBottomButton(VideoQualityDialogButton::initializeButton)

    addLegacyBottomControl(R.layout.morphe_video_quality_dialog_button_container)
    initializeLegacyBottomControl(
        ControlInitializer(
            R.id.morphe_video_quality_dialog_button_container,
            VideoQualityDialogButton::initializeLegacyButton
        )
    )
}
