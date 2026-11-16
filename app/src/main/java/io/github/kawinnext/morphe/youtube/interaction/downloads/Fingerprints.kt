package io.github.kawinnext.morphe.youtube.interaction.downloads

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.anyInstruction
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.string

internal object OfflineVideoEndpointFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Ljava/util/Map;",
        "L",
        "Ljava/lang/String", // VideoId
        "L",
    ),
    filters = listOf(
        anyInstruction(
            string("Unsupported Offline Video Action: "), // 21.14 and lower
            string("Unsupported Offline Video Action: %s") // 21.15+
        )
    ),
    custom = {
        addUsingString("Unsupported Offline Video Action: ")
    }
)

/**
 * คลาสที่เก็บแผนที่ของ format type ไปยังความสูงและไปยังป้ายชื่อคุณภาพของหน้าตั้งค่าดาวน์โหลด
 * แอปมีป้ายชื่อของแอปเองถึง 1080p เท่านั้น สตริงของ 1080p จึงเป็นตัวยืนยันคลาส
 * (เวอร์ชันที่เพิ่ม 1440p/2160p ยังอ้างสตริงตัวนี้อยู่)
 */
internal object FormatTypeMapsFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "offline_video_quality_1080p")
    )
)

/**
 * ป้ายชื่อของคุณภาพหนึ่งๆ ในหน้าตั้งค่าดาวน์โหลด หรือ -1 ถ้าคุณภาพนั้นไม่มีป้ายชื่อของแอป
 * (1440p/2160p เป็นคุณภาพที่แอปไม่มีป้ายชื่อ ส่วนขยายจึงให้สตริงของตัวเอง)
 */
internal object FormatTypeLabelFingerprint : Fingerprint(
    classFingerprint = FormatTypeMapsFingerprint,
    returnType = "I",
    parameters = listOf("L")
)

/**
 * เมธอดที่แสดง bottom sheet ของ ShowActionSheetCommand
 * ถ้าบิต 512 ของคำสั่งถูกตั้งไว้ ช่องที่อ่านต่อจากบิตนั้นบอกให้ข้ามสถานะย่อและเปิดแผ่นเต็ม
 */
internal object ShowActionSheetCommandFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("ShowActionSheetCommand needs to provided."),
        literal(512, opcodes = listOf(Opcode.AND_INT_LIT16)),
        fieldAccess(opcode = Opcode.IGET_BOOLEAN)
    )
)

/**
 * เมธอดที่สร้าง ShowActionSheetCommand ของแผงเลือกคุณภาพตอนดาวน์โหลด
 * ตัวเลือกของแผงมาจาก raw resource ของแอป ซึ่งมีเฉพาะในแอป ไม่ใช่ของส่วนขยาย
 */
internal object DownloadQualityPickerCommandFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        resourceLiteral(ResourceType.RAW, "download_options_picker_footer_element_android"),
        // เขียนบิตของ footer ลงคำสั่ง ซึ่งเป็นช่องบิตเดียวกับที่ [ShowActionSheetCommandFingerprint] อ่าน
        fieldAccess(opcode = Opcode.IPUT, type = "I")
    )
)

/** คลาสที่เก็บค่าตั้งของดาวน์โหลด ซึ่งมี SharedPreferences ของแอปอยู่ข้างใน */
internal object OfflineSettingsFingerprint : Fingerprint(
    filters = listOf(
        string("[Offline] Failed to set hasTransfersForOffline.")
    )
)

/**
 * ตัวสร้างค่าตั้งของดาวน์โหลด รับความสูงสูงสุดของคุณภาพที่ให้ดาวน์โหลดมาเป็นพารามิเตอร์
 * แล้วตัดคุณภาพที่สูงกว่าออก (จอ 1080p จึงดาวน์โหลดได้ถึง 1080p)
 */
internal object OfflineSettingsConstructorFingerprint : Fingerprint(
    classFingerprint = OfflineSettingsFingerprint,
    name = "<init>",
    parameters = listOf(
        "Landroid/content/SharedPreferences;",
        "L",
        "I", // ความสูงสูงสุดของคุณภาพที่ให้ดาวน์โหลด
        "L",
        "L"
    ),
    filters = listOf(
        // คัดลอก format type ที่ผ่านการกรองแล้วลง immutable list
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/util/Collection;")
        )
    )
)
