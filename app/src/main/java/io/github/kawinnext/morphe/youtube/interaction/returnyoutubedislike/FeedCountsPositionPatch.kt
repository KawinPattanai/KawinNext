/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4776
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.returnyoutubedislike

import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.youtube.layout.returnyoutubedislike.returnYouTubeDislikePatch
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch

/**
 * ตำแหน่งของยอด like และอัตราส่วนถูกใจบน thumbnail ของวิดีโอในฟีด ซึ่ง upstream เพิ่มมาใน #4776
 * ("แสดงยอด like ข้างจำนวนการดู")
 *
 * ตัววาดยอด like อยู่ใน `FeedEngagementCountsPatch` ของส่วนขยาย (sourceSets ของ app คอมไพล์
 * `morphe-patches/extensions/youtube` เข้ามาอยู่แล้ว) และถูกเรียกจาก `FeedVideoCardsPatch.onComponentHostLayout`
 * ที่ [io.github.kawinnext.morphe.youtube.misc.feedcards.feedVideoCardsHookPatch] ต่อสายไว้ให้แล้ว
 * แพตช์นี้จึงมีหน้าที่อย่างเดียว: ประกาศ ListPreference ของหน้าจอ Return YouTube Dislike ให้เขียนค่าลง
 * `Settings.RYD_FEED_COUNTS_POSITION` ด้วย key เดียวกับที่ส่วนขยายอ่าน (`morphe_ryd_feed_counts_position`)
 *
 * ค่าที่เก็บเป็นชื่อ enum ของ upstream ตรง ๆ (NEXT_TO_VIEW_COUNT / MIDDLE_RIGHT / TOP_LEFT) เพราะ
 * entry values มาจาก `morphe_ryd_feed_counts_position_entry_values` ใน addresources ของ upstream
 * และ `CustomDialogListPreference` เขียนค่าลง Setting ตามสตริงของ entry value
 */
val feedCountsPositionPatch = patch(
    name = "Feed thumbnail like position",
    description = "Adds an option to choose where the like count of the videos in the feed is shown " +
            "on their thumbnails, or next to their view count."
) {
    // ตัวเลือกนี้เป็นของหน้าจอ Return YouTube Dislike และไม่มีความหมายเมื่อแพตช์นั้นปิดอยู่
    dependsOn(returnYouTubeDislikePatch)

    PreferenceScreen.RETURN_YOUTUBE_DISLIKE.addPreferences(
        ListPreference("morphe_ryd_feed_counts_position")
    )
}
