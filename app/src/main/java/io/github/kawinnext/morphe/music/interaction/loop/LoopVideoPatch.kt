/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4685
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.interaction.loop

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import app.morphe.extension.music.patches.LoopVideoPatch
import de.robv.android.xposed.XC_MethodHook
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.music.video.information.musicVideoIdHooks
import io.github.kawinnext.morphe.music.video.information.musicVideoInformationPatch
import io.github.kawinnext.morphe.music.video.information.musicVideoTimeHooks
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.hookMethod
import io.github.kawinnext.patch

val musicLoopVideoPatch = patch(
    name = "A-B loop",
    description = "Adds an option to repeat a section of a track, set by dragging on the time bar."
) {
    dependsOn(musicVideoInformationPatch)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_ab_loop", summary = true)
    )

    musicVideoIdHooks.add(LoopVideoPatch::videoIdChanged)
    musicVideoTimeHooks.add(LoopVideoPatch::videoTimeChanged)

    // Seeks of the player and of the media session are kept inside the loop range.
    MedialibPlayerSeekToFingerprint.hookMethod {
        before {
            it.args[0] = LoopVideoPatch.clampSeekPosition(it.args[0] as Long)
        }
    }

    // The scrub thumb cannot be dragged out of the loop range.
    TimeBarSetScrubPositionFingerprint.hookMethod {
        before {
            it.args[0] = LoopVideoPatch.clampSeekX(it.args[0] as Float)
        }
    }

    val thumbRect = ::timeBarThumbRectField.field.apply { isAccessible = true }

    TimeBarDrawFingerprint.hookMethod {
        after {
            val canvas = it.args[0] as? Canvas ?: return@after
            val rect = thumbRect.get(it.thisObject) as? Rect ?: return@after
            LoopVideoPatch.drawLoopMarkers(canvas, rect)
        }
    }

    // The time bar of the app does not handle touches, so the touch handling of the view is
    // taken for the time bar only and handed to the extension to set the loop points.
    val timeBarClass = TimeBarDrawFingerprint.method.declaringClass
    View::class.java.getDeclaredMethod("onTouchEvent", MotionEvent::class.java).hookMethod(
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.thisObject?.javaClass != timeBarClass) return
                LoopVideoPatch.onTouchEvent(param.thisObject as View, param.args[0] as MotionEvent)
            }
        }
    )
}
