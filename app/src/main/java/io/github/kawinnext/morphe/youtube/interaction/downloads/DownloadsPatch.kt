package io.github.kawinnext.morphe.youtube.interaction.downloads

import app.morphe.extension.shared.Utils
import app.morphe.extension.shared.settings.preference.ExternalDownloaderPreference
import app.morphe.extension.youtube.patches.DownloadsPatch
import app.morphe.extension.youtube.patches.InAppDownloadsPatch
import app.morphe.extension.youtube.videoplayer.ExternalDownloadButton
import app.morphe.extension.youtube.videoplayer.ShortsDownloadButton
import io.github.kawinnext.R
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.setExtensionIsPatchIncluded
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.TextPreference
import io.github.kawinnext.morphe.youtube.layout.flyout.flyoutPatch
import io.github.kawinnext.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.kawinnext.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.kawinnext.morphe.youtube.misc.playercontrols.addTopControl
import io.github.kawinnext.morphe.youtube.misc.playercontrols.initializeTopControl
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.patch

/** บิต 512 ของ ShowActionSheetCommand ที่ทำให้เมธอดแสดงแผงอ่านช่อง skipCollapsed */
private const val PICKER_EXPAND_BIT = 0x200

/** ช่องบิตของคำสั่ง ซึ่ง [DownloadQualityPickerCommandFingerprint] เขียนและเมธอดแสดงแผงอ่าน */
private val pickerExpandBitField = findFieldDirect {
    DownloadQualityPickerCommandFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/** ช่อง skipCollapsed ของคำสั่ง ซึ่งคลาสเดียวกับช่องบิตข้างบน */
private val pickerSkipCollapsedField = findFieldDirect {
    ShowActionSheetCommandFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/**
 * หาคำสั่ง ShowActionSheetCommand ที่ [DownloadQualityPickerCommandFingerprint] สร้าง
 * เมธอดที่ fingerprint จับได้คืนคำสั่งที่ถูกห่อด้วยออบเจกต์ของแอปอีกชั้น (ตัวห่อแบบ Optional ของแอป
 * ซึ่งชื่อเมธอดของมันต่างกันในแต่ละเวอร์ชัน) จึงอ่านจากฟิลด์ของตัวห่อแทนการเดาชื่อเมธอด
 *
 * ถ้าหาคำสั่งไม่เจอ คืน null ซึ่งปล่อยให้แผงเปิดแบบเดิมของแอป
 */
private fun pickerCommandOf(result: Any?, commandType: Class<*>): Any? {
    if (result == null) return null
    if (commandType.isInstance(result)) return result

    return runCatching {
        result.javaClass.declaredFields.firstNotNullOfOrNull { field ->
            field.isAccessible = true
            field.get(result)?.takeIf(commandType::isInstance)
        }
    }.getOrNull()
}

/**
 * ป้ายชื่อของคุณภาพที่แอปไม่มี (1440p/2160p) เป็นสตริงของโมดูล แอปจึงหาสตริงด้วย id นี้ได้
 * เฉพาะตอนที่รีซอร์สของโมดูลถูกโหลดเข้า Resources ของ activity แล้ว
 * ถ้ายังโหลดไม่ถึง ปล่อยป้ายชื่อเดิมของแอปไว้ ไม่ให้แอปโยน Resources.NotFoundException
 */
private fun appCanResolveLabel(label: Int): Boolean =
    runCatching { Utils.getResources().getString(label) }.isSuccess

val Downloads = patch(
    name = "Downloads",
    description = "Adds support to download videos with an external downloader app " +
            "using the in-app download button or a video player action button, " +
            "and offers the download qualities up to 2160p.",
) {
    dependsOn(
        LegacyPlayerControls,
        VideoInformationPatch,
        // เมนูของวิดีโอที่แอปมีรายการดาวน์โหลดอยู่แล้ว ถูกตรวจจากส่วนขยายของแพตช์ flyout
        flyoutPatch,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_external_downloader_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_external_downloader", summary = true),
                SwitchPreference("morphe_external_downloader_action_button", summary = true),
                // รายการดาวน์โหลดที่ส่วนขยายเติมให้เมนูของวิดีโอ ซึ่งแอปไม่มีรายการให้
                SwitchPreference("morphe_feed_download_flyout_button", summary = true),
                TextPreference(
                    "morphe_external_downloader_name",
                    tag = ExternalDownloaderPreference::class.java
                ),
            ),
        ),
    )

    addTopControl(
        R.layout.morphe_external_download_button,
        R.id.morphe_external_download_button,
        R.id.morphe_external_download_button
    )

    initializeTopControl(
        ControlInitializer(
            R.id.morphe_external_download_button,
            ExternalDownloadButton::initializeLegacyButton
        )
    )

    // ส่วนขยายเติมรายการดาวน์โหลดให้เมนูของ Shorts และของวิดีโอในฟีดที่แอปไม่มีรายการให้
    // (เช่น วิดีโอที่ให้เฉพาะสมาชิก Premium ดาวน์โหลด) FlyoutUtils เรียกเมธอดนี้ก่อนเติมรายการ
    setExtensionIsPatchIncluded(ShortsDownloadButton::class.java)

    // แอปเก็บความสูงสูงสุดของคุณภาพที่ให้ดาวน์โหลดไว้ตอนสร้างค่าตั้งของดาวน์โหลด
    // แล้วตัดคุณภาพที่สูงกว่าออก จอ 1080p จึงดาวน์โหลดได้ถึง 1080p
    // ส่วนขยายเปิดให้ทุกจอเห็นถึง 2160p ก่อนที่รายการคุณภาพจะถูกกรอง
    OfflineSettingsConstructorFingerprint.hookMethod {
        before { param ->
            param.args[2] = InAppDownloadsPatch.getMaxDownloadQualityHeight(param.args[2] as Int)
        }
    }

    // แอปมีป้ายชื่อของคุณภาพถึง 1080p ส่วนคุณภาพที่สูงกว่านั้นคืน -1
    // ส่วนขยายให้สตริงของตัวเอง (Quad HD / 4K) เมื่อแอปหาสตริงของโมดูลเจอ
    FormatTypeLabelFingerprint.hookMethod {
        after { param ->
            val label = InAppDownloadsPatch.getDownloadQualityLabel(param.args[0])
            if (label != 0 && appCanResolveLabel(label)) param.result = label
        }
    }

    // แผงเลือกคุณภาพตอนดาวน์โหลดเปิดมาในสถานะย่อ ซึ่งคุณภาพที่เพิ่มขึ้นมาทำให้คุณภาพต่ำสุดตกออกนอกแผง
    // ส่วนขยายจึงสั่งให้แผงเปิดเต็มตอนที่คำสั่งของแผงถูกสร้าง
    val expandBitField = ::pickerExpandBitField.field.apply { isAccessible = true }
    val skipCollapsedField = ::pickerSkipCollapsedField.field.apply { isAccessible = true }
    if (expandBitField.declaringClass != skipCollapsedField.declaringClass) {
        throw IllegalStateException(
            "Unexpected command class: ${skipCollapsedField.declaringClass.name}"
        )
    }
    val pickerCommandType = expandBitField.declaringClass

    DownloadQualityPickerCommandFingerprint.hookMethod {
        after { param ->
            val command = pickerCommandOf(param.result, pickerCommandType) ?: return@after

            skipCollapsedField.setBoolean(command, InAppDownloadsPatch.expandDownloadQualityPicker())
            expandBitField.setInt(command, expandBitField.getInt(command) or PICKER_EXPAND_BIT)
        }
    }

    OfflineVideoEndpointFingerprint.hookMethod {
        before {
            if (DownloadsPatch.inAppDownloadButtonOnClick(it.args[2] as String)) it.result = null
        }
    }
}
