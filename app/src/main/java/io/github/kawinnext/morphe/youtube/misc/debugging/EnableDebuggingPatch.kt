package io.github.kawinnext.morphe.youtube.misc.debugging

import io.github.kawinnext.morphe.shared.misc.debugging.enableDebuggingPatch
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.misc.playservice.is_20_40_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_20_41_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen

val EnableDebugging = enableDebuggingPatch(
    hookIndexedBooleanFeatureFlag = { is_21_42_or_greater },
    hookStringFeatureFlag = { true },
    // 20.40 has changes not worth supporting.
    hookLongFeatureFlag = { !is_20_40_or_greater || is_20_41_or_greater },
    hookDoubleFeatureFlag = { !is_20_40_or_greater || is_20_41_or_greater },
    preferenceScreen = PreferenceScreen.MISC,
    additionalDebugPreferences = listOf(
        SwitchPreference(
            "morphe_debug_protobuffer",
            summary = true
        )
    )
)
