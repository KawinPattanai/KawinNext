/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4494
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.seekbar

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.View
import app.morphe.extension.youtube.patches.CustomSeekbarThumbPatch
import app.morphe.extension.youtube.settings.preference.CustomSeekbarThumbPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.kawinnext.morphe.youtube.layout.sponsorblock.seekbarOnDrawFingerprint
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

// The extension draws the default seekbar circle itself when it has no thumb to draw,
// and that call reaches the hooked method again while onDraw is still on the stack.
private val drawingSeekbarThumb = object : ThreadLocal<Boolean>() {
    override fun initialValue() = false
}

val customSeekbarThumbPatch = patch(
    description = "Adds an option to customize the seekbar thumb with a custom image or animated GIF."
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_custom_seekbar_thumb", summary = true),
            NonInteractivePreference(
                key = "morphe_custom_seekbar_thumb_file",
                tag = CustomSeekbarThumbPreference::class.java,
                selectable = true
            ),
            ListPreference("morphe_custom_seekbar_thumb_scale_type")
        )
    )

    ::seekbarOnDrawFingerprint.hookMethod {
        before { CustomSeekbarThumbPatch.setSeekbarView(it.thisObject as View) }
    }

    // The seekbar draws its thumb with the last of several drawCircle() calls, the earlier ones being
    // the timed markers. Hook that call and draw the thumb in its place, instead of before it,
    // so anything other patches draw there stays below the thumb.
    val thumbPaintField = ::seekbarThumbPaintField.field.apply { isAccessible = true }
    val drawCircle =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            "Landroid/view/DisplayListCanvas;->drawCircle(FFFLandroid/graphics/Paint;)V"
        else
            "Landroid/graphics/RecordingCanvas;->drawCircle(FFFLandroid/graphics/Paint;)V"
    ::seekbarOnDrawFingerprint.hookMethod(
        scopedHook(DexMethod(drawCircle).toMethod()) {
            before { param ->
                if (drawingSeekbarThumb.get()) return@before
                if (param.args[3] !== thumbPaintField.get(outerParam.thisObject)) return@before

                drawingSeekbarThumb.set(true)
                try {
                    CustomSeekbarThumbPatch.drawCircle(
                        param.thisObject as Canvas,
                        param.args[0] as Float,
                        param.args[1] as Float,
                        param.args[2] as Float,
                        param.args[3] as Paint
                    )
                } finally {
                    drawingSeekbarThumb.set(false)
                }
                param.result = null
            }
        }
    )
}
