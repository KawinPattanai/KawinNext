package io.github.kawinnext.morphe.youtube.interaction.downloads

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.anyInstruction
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.indexOfFirstInstruction
import io.github.kawinnext.morphe.indexOfFirstInstructionReversedOrThrow
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcodeEnum
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.shared.FormatStreamModelToStringFingerprint
import io.github.kawinnext.morphe.string
import io.github.kawinnext.morphe.writeRegister
import io.github.kawinnext.morphe.youtube.shared.VideoStreamingDataToStringFingerprint
import org.luckypray.dexkit.result.MethodData

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

/**
 * เมธอดที่เติมความยาวของสตรีมให้สตรีมที่เซิร์ฟเวอร์ไม่ได้ให้ความยาวมา
 * ส่วนขยายเรียกเมธอดนี้กับสตรีมที่มันบังคับคุณภาพให้ ก่อนคืนสตรีมนั้นให้แอป
 */
internal object FetchContentLengthFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        string("fetchContentLengthIfNecessary failed")
    )
)

/**
 * เมธอดที่เลือกสตรีมของหนึ่งคุณภาพของการดาวน์โหลด ทั้งสตรีมวิดีโอและสตรีมเสียง
 * แผงเลือกคุณภาพเรียกเมธอดนี้ทีละแถว และแอปจะเลือกคุณภาพที่ต่ำกว่าถ้าตรวจของอุปกรณ์
 * หรือของเซิร์ฟเวอร์ไม่ผ่าน ซึ่งเป็นการตรวจชุดเดียวกับการเล่นวิดีโอ
 */
internal object DownloadStreamSelectorFingerprint : Fingerprint(
    classFingerprint = FetchContentLengthFingerprint,
    returnType = "L",
    parameters = listOf(
        "I", // ความสูงของคุณภาพ
        "I", // ความสูงสูงสุดที่ยอมให้เลือก
        "I", // คุณภาพของเสียง
        "L", // ข้อมูลสตรีมของวิดีโอ
        "Z", // เลือกสตรีมเสียง
        "L"  // ค่าตั้งของผู้เล่น
    )
)

/**
 * ตัวสร้างข้อมูลสตรีมของวิดีโอ ซึ่งเก็บ list ของทุกสตรีมของวิดีโอเป็นอย่างแรก
 */
internal object StreamingDataFormatStreamsFingerprint : Fingerprint(
    classFingerprint = VideoStreamingDataToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/util/List;"
        )
    )
)

/**
 * เมธอดที่เริ่มดาวน์โหลด ซึ่งเลือกสตรีมของวิดีโอและของเสียงอีกรอบด้วยการตรวจชุดเดียวกับ
 * แผงเลือกคุณภาพ แล้วจับคู่สตรีมทั้งสองเข้าด้วยกัน
 */
internal object DownloadTransferStreamsFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf(
        "I", // ความสูงของคุณภาพ
        "I", // คุณภาพของเสียง
        "Ljava/lang/String;", // แทร็กเสียง
        "Ljava/lang/String;", // รหัสวิดีโอ
        "L", // ข้อมูลสตรีมของวิดีโอ
        "L", // ค่าตั้งของผู้เล่น
        "L",
        "Z"
    ),
    filters = listOf(
        // เลือกสตรีมของวิดีโอและของเสียง
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("L", "L", "L")
        ),
        // จับคู่สตรีมของวิดีโอกับของเสียง
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("L", "L"),
            location = MatchAfterWithin(30)
        ),
        string("Stream pair could not be found.")
    )
)

/**
 * ฟิลด์ของออบเจกต์ format ที่สตรีมถืออยู่ ค่าของสตรีมอ่านผ่านออบเจกต์นี้
 */
internal val formatStreamFormatField = findFieldDirect {
    FormatStreamModelToStringFingerprint.instructionMatches[5].instruction.fieldRef!!
}

/**
 * ฟิลด์ list ของทุกสตรีมของวิดีโอในข้อมูลสตรีม ซึ่งตัวสร้างเก็บเป็นอย่างแรก
 */
internal val videoFormatStreamsField = findFieldDirect {
    StreamingDataFormatStreamsFingerprint.instructionMatches.first().instruction.fieldRef!!
}

private const val STRING_BUILDER_CLASS = "Ljava/lang/StringBuilder;"

/** คำสั่งที่เขียนค่าลง register ซึ่งค่าที่ถูกเขียนอาจเป็นค่าที่ต่อสตริง */
private val moveOpcodes = setOf(
    Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
    Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16,
    Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16
)

/**
 * เมธอดของคลาสสตรีมที่ toString เรียกเพื่อพิมพ์ค่าของ [label] เช่น " width=" พิมพ์ค่าที่เมธอด
 * ที่คืนความกว้างคืนมา
 *
 * ต้นทางอ่านความกว้าง ความสูง ชนิด mime และ itag ของสตรีมผ่านเมธอดที่ toString เรียก แล้วเพิ่ม
 * อินเทอร์เฟซที่อ่านฟิลด์ให้คลาสของ format ตอนรันเพิ่มอินเทอร์เฟซให้คลาสที่โหลดแล้วไม่ได้ โมดูลจึง
 * เดินตามสตริงมาถึงเมธอดเดียวกันนี้ แล้วอ่านฟิลด์ที่เมธอดนั้นอ่าน
 *
 * คำสั่งของ toString ไม่ได้เรียงตามสตริง เพราะคอมไพเลอร์เรียกเมธอดทั้งหมดไว้ก่อนแล้วจึงต่อสตริง
 * การหาจึงเดินย้อนจากคำสั่งที่ต่อสตริง ไปหาคำสั่งที่เขียนค่าลง register ตัวเดียวกับที่ถูกต่อ
 */
private fun MethodData.findMethodFromToString(label: String): MethodData {
    val stringIndex = indexOfFirstInstruction { string?.contains(label) == true }
    check(stringIndex >= 0) { "toString does not print '$label'" }
    val stringRegister = instructions[stringIndex].register(0)

    // คำสั่งที่ต่อสตริงนี้เข้าไปใน StringBuilder
    val stringUsageIndex = indexOfFirstInstruction(stringIndex) {
        methodRef?.declaredClass?.descriptor == STRING_BUILDER_CLASS &&
                registerOrNull(1) == stringRegister
    }
    check(stringUsageIndex >= 0) { "toString does not append '$label' with a StringBuilder" }

    // คำสั่งต่อสตริงตัวถัดไป ซึ่งรับค่าที่พิมพ์ต่อจากสตริงนี้
    val valueUsageIndex = indexOfFirstInstruction(stringUsageIndex + 1) {
        methodRef?.declaredClass?.descriptor == STRING_BUILDER_CLASS && methodRef?.name == "append"
    }
    check(valueUsageIndex >= 0) { "toString does not append a value for '$label'" }

    val valueInstruction = instructions[valueUsageIndex]
    var valueRegister = valueInstruction.registerOrNull(1) ?: valueInstruction.register(0)
    var valueSetIndex = indexOfFirstInstructionReversedOrThrow(valueUsageIndex - 1) {
        valueRegister == writeRegister
    }

    // ผลของเมธอดอาจถูกย้ายไปอีก register ก่อนถูกต่อ จึงย้อนตาม register ไปจนถึงเมธอดที่ให้ค่านั้น
    while (true) {
        val opcode = instructions[valueSetIndex].opcodeEnum
        if (opcode == Opcode.MOVE_RESULT || opcode == Opcode.MOVE_RESULT_WIDE ||
            opcode == Opcode.MOVE_RESULT_OBJECT
        ) {
            valueSetIndex--
        }

        val instruction = instructions[valueSetIndex]
        instruction.methodRef?.let { return it }

        if (instruction.registerCount == 2 && instruction.opcodeEnum in moveOpcodes) {
            valueRegister = instruction.register(1)
            valueSetIndex = indexOfFirstInstructionReversedOrThrow(valueSetIndex - 1) {
                valueRegister == writeRegister
            }
            continue
        }
        error("toString does not call a method for '$label': $instruction")
    }
}

/**
 * ฟิลด์ของออบเจกต์ format ที่เก็บค่านี้ ซึ่งเป็นคำสั่งอ่านฟิลด์ตัวที่สองของเมธอดที่ [label] เรียก
 * (คำสั่งแรกอ่านฟิลด์ format ของสตรีม)
 */
private fun formatValueField(label: String) = findFieldDirect {
    FormatStreamModelToStringFingerprint()
        .findMethodFromToString(label)
        .instructions
        .mapNotNull { it.fieldRef }
        .last()
}

/** ฟิลด์ itag ของ format */
internal val formatStreamItagField = formatValueField("itag=")

/** ฟิลด์ความกว้างของ format */
internal val formatStreamWidthField = formatValueField(" width=")

/** ฟิลด์ความสูงของ format */
internal val formatStreamHeightField = formatValueField(" height=")

/** ฟิลด์ชนิด mime ของ format */
internal val formatStreamMimeTypeField = formatValueField(" mimeType=")
