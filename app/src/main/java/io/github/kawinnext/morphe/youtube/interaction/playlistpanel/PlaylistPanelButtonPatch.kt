/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4662
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.playlistpanel

import android.view.ViewGroup
import android.widget.ImageView
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.videoplayer.PlaylistPanelButton
import io.github.kawinnext.R
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.layout.buttons.overlay.PlayerOverlayButtonsSettings
import io.github.kawinnext.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.kawinnext.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.kawinnext.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.kawinnext.morphe.youtube.misc.playercontrols.addTopControl
import io.github.kawinnext.morphe.youtube.misc.playercontrols.initializeTopControl
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.patch

/** ไอคอนเพลย์ลิสต์ของแอปเอง ซึ่ง upstream ใช้เป็นไอคอนของปุ่มนี้ (ไม่มีการคัดลอกไอคอน) */
private const val PLAYLIST_ICON = "yt_outline_experimental_playlist_vd_theme_24"

/**
 * ปุ่มในตัวเล่นที่เปิดแผงเพลย์ลิสต์ด้วยคำสั่งเดียวกับ quick action ของเพลย์ลิสต์ใต้แถบเลื่อน
 * ปุ่มจะแสดงเฉพาะตอนที่เล่นเพลย์ลิสต์อยู่ และซ่อนตามปุ่มอื่นของตัวเล่น
 */
val playlistPanelButtonPatch = patch(
    name = "Playlist button",
    description = "Adds an option to display a button in the video player that opens the playlist panel."
) {
    dependsOn(
        LegacyPlayerControls,
        PlayerOverlayButtonsSettings,
        VideoInformationPatch,
    )

    addPlayerOverlayPreferences(
        SwitchPreference("morphe_playlist_panel_button", summary = true),
    )

    // ปุ่มวิ่งคำสั่งของแอป จึงต้องมี command router ของ activity ให้ส่วนขยายก่อน
    installCommandRouter()

    // ปุ่มอยู่แถวบนของตัวเล่น ข้างปุ่มของแอปเอง (ตำแหน่งเดียวกับที่ upstream ใส่ในเลย์เอาต์ของแอป)
    addTopControl(
        R.layout.morphe_playlist_panel_button,
        R.id.morphe_playlist_panel_button,
        R.id.morphe_playlist_panel_button
    )

    initializeTopControl(
        ControlInitializer(R.id.morphe_playlist_panel_button) { controlsView ->
            setPlaylistIcon(controlsView)
            PlaylistPanelButton.initializeLegacyButton(controlsView)
        }
    )
}

/**
 * ตั้งไอคอนของปุ่มจาก drawable ของแอป ซึ่งเลย์เอาต์ของโมดูลอ้างตรงไม่ได้
 * ถ้าเวอร์ชันของแอปไม่มีไอคอนนี้ ปุ่มยังอยู่แต่ไม่มีรูป (การกดยังเปิดแผงได้)
 */
private fun setPlaylistIcon(controlsView: ViewGroup) {
    val button = controlsView.findViewById<ImageView>(R.id.morphe_playlist_panel_button) ?: return
    val icon = ResourceUtils.getDrawableIdentifier(PLAYLIST_ICON)
    if (icon == 0) {
        Logger.printDebug { "Playlist button: the icon $PLAYLIST_ICON is not in this build" }
        return
    }
    button.setImageResource(icon)
}
