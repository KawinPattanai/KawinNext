/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4659
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.autoplay

import app.morphe.extension.youtube.patches.AutoplayControlPatch
import app.morphe.extension.youtube.patches.DisablePlaylistAutoplayPatch
import app.morphe.extension.youtube.videoplayer.AutoplayButton
import io.github.kawinnext.morphe.shared.misc.settings.preference.InputType
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.TextPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.kawinnext.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.kawinnext.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.navigationIntentHook
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.patch

/**
 * ปุ่มและกฎของวิดีโอถัดไปแทน Autoplay ของ YouTube: เพลย์ลิสต์และคิวเล่นต่อไป มิกซ์หยุดตามจำนวน
 * ที่ตั้งไว้ และวิดีโอที่แนะนำไม่เล่นเอง
 *
 * กฎอยู่ใน [AutoplayControlPatch] ฝั่งส่วนขยาย ซึ่ง upstream เรียกจากการตรวจ navigation intent
 * ของแพตช์ "Disable playlist autoplay" แพตช์นั้นยังไม่ถูกพอร์ตในโมดูลนี้ จึงติดตั้งการตรวจนั้น
 * จากแพตช์นี้แทน (เป็นจุดเดียวที่เรียก `shouldSkipPlaylistAutoplay`)
 */
val autoplayControlPatch = patch(
    name = "Autoplay control",
    description = "Adds a player button and a rule for the next video: playlists and queues go on, " +
            "a Mix stops after a number of videos, a suggested video never plays."
) {
    dependsOn(
        playerOverlayButtonsHook,
        VideoInformationPatch,
    )

    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_autoplay_control", summary = true),
            ListPreference("morphe_autoplay_mode"),
            TextPreference("morphe_autoplay_mix_stop_after", inputType = InputType.NUMBER),
        )
    )

    // ปุ่มข้อความของโหมดอยู่แถวล่างของตัวเล่น ข้างปุ่มความเร็วและคุณภาพ
    // (ปุ่มจะไม่ถูกสร้างถ้าผู้ใช้เปิด "Restore old player buttons")
    addPlayerBottomButton(AutoplayButton::initializeButton)

    // มิกซ์ส่วนใหญ่ฟังอยู่เบื้องหลัง ซึ่ง hook วิดีโอปกติไม่ถูกเรียก จึงใช้ hook ของ background play
    // การเรียกซ้ำสำหรับวิดีโอเดิมไม่เป็นไร เพราะกฎนับเฉพาะวิดีโอที่ยังไม่เคยเห็น
    BackgroundPlayVideoIdFingerprint.hookMethod {
        before {
            val videoId = it.args[0] as? String ?: return@before
            AutoplayControlPatch.newVideoLoaded(videoId)
        }
    }

    // กฎต้องรู้ว่า YouTube กำลังเล่นวิดีโอเดิมซ้ำ (โหมดเล่นซ้ำ 2) ซึ่งไม่ใช่การไปวิดีโอถัดไป
    NativeNavigationStateFingerprint.hookMethod {
        before { DisablePlaylistAutoplayPatch.setRepeatMode(it.args[2] as Int) }
    }

    // การตรวจ intent ก่อนที่แอปจะเริ่มนำทางไปวิดีโอถัดไป คืน true เพื่อยกเลิกการนำทาง
    navigationIntentHook { intent ->
        DisablePlaylistAutoplayPatch.shouldSkipPlaylistAutoplay(intent)
    }
}
