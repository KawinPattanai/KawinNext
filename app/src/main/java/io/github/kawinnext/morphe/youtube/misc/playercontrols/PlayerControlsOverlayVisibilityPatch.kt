package io.github.kawinnext.morphe.youtube.misc.playercontrols

import app.morphe.extension.youtube.patches.PlayerControlsVisibilityHookPatch
import io.github.kawinnext.patch

val PlayerControlsOverlayVisibility = patch {
    PlayerControlsVisibilityEntityModelInit.hookMethod {
        val getPlayerControlsVisibilityMethod =
            PlayerControlsVisibilityEntityModelFingerprint.method
        after {
            PlayerControlsVisibilityHookPatch.setPlayerControlsVisibility(
                getPlayerControlsVisibilityMethod(it.thisObject) as Enum<*>?
            )
        }
    }
}