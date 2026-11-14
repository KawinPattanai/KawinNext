/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4757
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.video.quality

import android.widget.AdapterView
import app.morphe.extension.music.patches.RememberVideoQualityPatch
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.music.video.information.musicVideoIdHooks
import io.github.kawinnext.morphe.music.video.information.musicVideoInformationPatch
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.kawinnext.patch
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * A quality of the quality menu, which the extension reads the resolution of.
 * The patched app adds a method to the quality class instead, which a hook cannot do.
 */
private class VideoQualityAdapter(
    private val quality: Any?,
    private val resolutionField: Field,
) : RememberVideoQualityPatch.VideoQualityInterface {
    override fun patch_getResolution(): Int {
        val quality = quality ?: return -1
        return runCatching { resolutionField.getInt(quality) }.getOrDefault(-1)
    }
}

/**
 * The quality menu, which the extension sets a quality with the same listener the menu sets the
 * quality selected by the user with.
 */
private class VideoQualityMenuAdapter(
    private val menu: Any,
    private val listenerField: Field,
    private val setQualityMethod: Method,
) : RememberVideoQualityPatch.VideoQualityMenuInterface {
    override fun patch_setQuality(resolution: Int) {
        val listener = runCatching { listenerField.get(menu) }.getOrNull() ?: return
        runCatching { setQualityMethod.invoke(listener, resolution) }
    }
}

/**
 * The quality of an item of the quality menu. The menu holds the items in an adapter, and an item
 * that is not a quality itself holds one.
 */
private fun qualityOf(item: Any?, qualityClass: Class<*>): Any? {
    if (item == null) return null
    if (qualityClass.isInstance(item)) return item
    return item.javaClass.declaredFields.firstNotNullOfOrNull { field ->
        runCatching {
            field.isAccessible = true
            field.get(item)
        }.getOrNull()?.takeIf { qualityClass.isInstance(it) }
    }
}

val RememberVideoQuality = patch(
    name = "Remember video quality",
    description = "Adds options to set default video qualities and remember the last selected video quality.",
) {
    dependsOn(
        musicVideoInformationPatch
    )

    PreferenceScreen.VIDEO.addPreferences(
        noTitleUnsortedPreferenceCategory(
            ListPreference(
                key = "morphe_video_quality_default_wifi",
                entriesKey = "morphe_video_quality_default_entries",
                entryValuesKey = "morphe_video_quality_default_entry_values"
            ),
            ListPreference(
                key = "morphe_video_quality_default_mobile",
                entriesKey = "morphe_video_quality_default_entries",
                entryValuesKey = "morphe_video_quality_default_entry_values"
            ),
            SwitchPreference("morphe_remember_video_quality_last_selected", summary = true),
            SwitchPreference("morphe_remember_video_quality_last_selected_toast", summary = true)
        )
    )

    val qualityClass = VideoQualityFingerprint.declaredClass
    val resolutionField = ::videoQualityResolutionField.field.apply { isAccessible = true }

    // The menu sets a quality with its own click listener, the same way the app sets the quality
    // selected by the user.
    val setQualityMethod = ::videoQualitySetQualityMethod.method.apply { isAccessible = true }
    val listenerField = ::videoQualityMenuSetQualitiesMethod.method.declaringClass.declaredFields
        .firstOrNull { field -> field.type == setQualityMethod.declaringClass }
        ?.apply { isAccessible = true }
        ?: error("No listener of ${setQualityMethod.declaringClass.name} in the quality menu")

    musicVideoIdHooks.add(RememberVideoQualityPatch::newVideoStarted)

    // Set the default quality when the qualities of a new video are set in the quality menu.
    ::videoQualityMenuSetQualitiesMethod.hookMethod {
        before {
            val qualities = it.args[0] as? Array<*> ?: return@before
            val index = it.args[1] as? Int ?: return@before
            val menu = it.thisObject ?: return@before

            val adapters: Array<RememberVideoQualityPatch.VideoQualityInterface> =
                Array(qualities.size) { position ->
                    VideoQualityAdapter(qualities[position], resolutionField)
                }

            it.args[1] = RememberVideoQualityPatch.setVideoQuality(
                adapters,
                VideoQualityMenuAdapter(menu, listenerField, setQualityMethod),
                index
            )
        }
    }

    // Remember the quality selected by the user.
    ::videoQualityItemOnClickMethod.hookMethod {
        after {
            val parent = it.args[0] as? AdapterView<*> ?: return@after
            val position = it.args[2] as? Int ?: return@after
            val item = runCatching { parent.getItemAtPosition(position) }.getOrNull()
            val quality = qualityOf(item, qualityClass) ?: return@after
            RememberVideoQualityPatch.userChangedQuality(
                runCatching { resolutionField.getInt(quality) }.getOrDefault(-2)
            )
        }
    }
}
