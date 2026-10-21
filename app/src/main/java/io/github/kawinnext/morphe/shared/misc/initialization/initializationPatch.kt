package io.github.kawinnext.morphe.shared.misc.initialization

import app.morphe.extension.shared.patches.InitializationPatch
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook


internal fun initializationPatch() = patch (
    description = "Prompts to restart the app on first load of a clean install",
) {
    GlobalConfigGroupFingerprint.hookMethod(scopedHook(::handleColdFingerprint.member) {
        InitializationPatch.onGlobalConfigUpdated()
    })
}
