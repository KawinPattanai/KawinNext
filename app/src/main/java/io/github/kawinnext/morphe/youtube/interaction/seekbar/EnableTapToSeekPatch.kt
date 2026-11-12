package io.github.kawinnext.morphe.youtube.interaction.seekbar

import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import app.morphe.extension.youtube.patches.TapToSeekPatch
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

val enableTapToSeekPatch = patch(
    description = "Adds an option to enable tap to seek on the seekbar of the video player.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        SwitchPreference("morphe_tap_to_seek", summary = true),
    )

    // Find the required methods to tap the seekbar.
    val startSeek = ::tapSeekStartMethod.method
    val updateSeek = ::tapSeekUpdateMethod.method
    val pointField = ::tapSeekPointField.field
    val getPoint = pointField.type.getMethod("get")

    TapToSeekFingerprint.hookMethod(scopedHook(::tapSeekTouchDownMethod.method) {
        after {
            if (it.hasThrowable() || it.thisObject !== outerParam.thisObject ||
                (outerParam.args[0] as MotionEvent).action != MotionEvent.ACTION_DOWN ||
                !TapToSeekPatch.tapToSeekEnabled()) return@after
            val point = getPoint(pointField.get(outerParam.thisObject)) as Point
            startSeek(outerParam.thisObject, point.x)
            updateSeek(outerParam.thisObject, point.x)
        }
    })

    // การแตะจะเปิด scrub ตั้งแต่ finger แตะ time bar และปิดตอน finger ปล่อย
    // แต่ time bar ไม่รับ touch ขณะที่มันถูก disable (เช่นตอนที่ยังไม่รู้ความยาววิดีโอ)
    // scrub ที่เปิดจาก tap จึงค้างอยู่ และ player ก็ยังทำตัวเหมือนถูก scrub อยู่
    // ปุ่ม "More videos" ใน fullscreen จึงกดไม่ติดจนกว่าจะหมุนจอ
    // upstream เติมโค้ดนี้ไว้ที่ต้นเมธอดด้วย addInstructionsWithLabels(index 0)
    // โมดูลนี้ไม่แพตช์ bytecode จึงใช้ before-hook ที่ตำแหน่งเดียวกันแทน
    // แล้วปิด scrub ด้วยเมธอดเดียวกับที่ time bar ใช้ตอน controls ถูกซ่อน
    // upstream เรียกสองเมธอดนี้ด้วย invoke-virtual จากในคลาสเดียวกัน ซึ่งไม่จำเป็นต้องเป็น public
    // จึงเปิด accessible ไว้เพื่อไม่ให้เรียกไม่ถึงตอนรันจริง
    val isScrubbing = ::timeBarIsScrubbingMethod.method.apply { isAccessible = true }
    val completeScrub = ::timeBarCompleteScrubMethod.method.apply { isAccessible = true }

    TimeBarCompleteHiddenScrubFingerprint.hookMethod {
        before {
            val timeBar = it.thisObject as? View ?: return@before
            val event = it.args[0] as? MotionEvent ?: return@before
            // ยืนยันให้ครบเงื่อนไขเดียวกับ upstream ก่อนปิด scrub
            if (!TapToSeekPatch.shouldCompleteScrub(timeBar, event)) return@before
            if (isScrubbing(timeBar) != true) return@before
            completeScrub(timeBar)
        }
    }
}
