package io.github.kawinnext.morphe.youtube.interaction.downloads

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.Utils
import app.morphe.extension.shared.patches.BaseInAppDownloadsPatch
import app.morphe.extension.shared.patches.DownloadMode
import app.morphe.extension.shared.settings.SharedYouTubeSettings
import app.morphe.extension.shared.settings.preference.ExternalDownloaderPreference
import app.morphe.extension.youtube.patches.DownloadsPatch
import app.morphe.extension.youtube.patches.InAppDownloadsPatch
import app.morphe.extension.youtube.videoplayer.ExternalDownloadButton
import app.morphe.extension.youtube.videoplayer.ShortsDownloadButton
import com.google.protobuf.MessageLite
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
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/** คลาส registry ของ protobuf ที่แอปใช้ ซึ่งเก็บทุก endpoint ที่แอปสร้าง */
private const val APP_PROTO_REGISTRY = "com.google.protobuf.ExtensionRegistryLite"

/** บิต 512 ของ ShowActionSheetCommand ที่ทำให้เมธอดแสดงแผงอ่านช่อง skipCollapsed */
private const val PICKER_EXPAND_BIT = 0x200

/**
 * คุณภาพและข้อมูลสตรีมของดาวน์โหลดที่กำลังเริ่ม
 *
 * เมธอดที่เริ่มดาวน์โหลดมีทั้งสองค่า แต่เมธอดที่ดึงความยาวสตรีมซึ่งเป็นจุดที่ยังแก้สตรีมวิดีโอได้
 * มีแค่สตรีม ค่าจึงถูกส่งต่อผ่าน thread ที่ดาวน์โหลดเริ่มขึ้น ซึ่งเรียกทั้งสองเมธอดต่อกันใน thread เดียว
 */
private class DownloadScope(val quality: Int, val streamingData: Any)

private val downloadScope = ThreadLocal<DownloadScope?>()

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

/**
 * โหมดดาวน์โหลดในแอป ซึ่งเป็นโหมดเดียวที่ส่วนขยายแทนคำสั่งและคำตอบของ endpoint ดาวน์โหลดให้
 * คำสั่งทุกตัวของแอปผ่าน hook ที่แปลงเป็น bytes ค่าตั้งจึงถูกอ่านก่อน
 */
private fun inAppDownloadMode(): Boolean =
    SharedYouTubeSettings.DOWNLOAD_MODE.get() == DownloadMode.IN_APP

/**
 * ข้อความของแอปในรูป [MessageLite] ของโมดูล ซึ่งถือ bytes ของข้อความนั้นไว้
 *
 * โมดูลกับแอปมี protobuf คนละสำเนา ออบเจกต์ของแอปจึงไม่ใช่อินสแตนซ์ของ [MessageLite] ที่ส่วนขยายรับ
 * แต่ส่วนขยายอ่านคำสั่งและคำตอบของ endpoint เป็น bytes เท่านั้น ([MessageLite.toByteArray])
 * proxy นี้จึงพอสำหรับส่งข้อความของแอปข้ามฝั่ง
 */
private fun appMessageLite(message: Any): MessageLite? {
    val bytes = message.javaClass.methods.firstOrNull {
        it.name == "toByteArray" && it.parameterTypes.isEmpty()
    }?.let { method ->
        runCatching { method.invoke(message) as? ByteArray }.getOrNull()
    } ?: return null

    return Proxy.newProxyInstance(
        MessageLite::class.java.classLoader,
        arrayOf(MessageLite::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "toByteArray" -> bytes
            "getSerializedSize" -> bytes.size
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "app message of ${bytes.size} bytes"
            else -> defaultValueOf(method.returnType)
        }
    } as MessageLite
}

/** ค่าที่ proxy คืนให้เมธอดของ [MessageLite] ที่ส่วนขยายไม่ได้เรียก */
private fun defaultValueOf(type: Class<*>): Any? = when (type) {
    Boolean::class.javaPrimitiveType -> false
    Byte::class.javaPrimitiveType -> 0.toByte()
    Char::class.javaPrimitiveType -> '\u0000'
    Short::class.javaPrimitiveType -> 0.toShort()
    Int::class.javaPrimitiveType -> 0
    Long::class.javaPrimitiveType -> 0L
    Float::class.javaPrimitiveType -> 0f
    Double::class.javaPrimitiveType -> 0.0
    else -> null
}

/**
 * แปลง [bytes] ที่ส่วนขยายสร้างกลับเป็นข้อความของแอปคลาสเดียวกับ [like]
 *
 * endpoint ของคำสั่งดาวน์โหลดเป็นฟิลด์ที่แอปลงทะเบียนไว้ใน registry ของ protobuf
 * การอ่านจึงต้องใช้ registry ของแอปเอง ไม่อย่างนั้น endpoint จะกลายเป็นฟิลด์ที่ไม่รู้จัก
 * แล้ว router ของแอปหาตัวจัดการของคำสั่งไม่เจอ (ต้นทางอ่านด้วย registry เดียวกันนี้)
 *
 * ถ้าแอปไม่มีทางอ่านด้วย registry คืน null ซึ่งปล่อยคำตอบเดิมของแอปไว้ ไม่ให้หน้าขาย Premium หาย
 */
private fun parseAppMessage(like: Any, bytes: ByteArray): Any? {
    val type = like.javaClass
    val registryType = runCatching {
        Class.forName(APP_PROTO_REGISTRY, false, type.classLoader)
    }.getOrNull()
    val registryInfo = registryType?.let { registryClass ->
        val registry = runCatching {
            registryClass.getMethod("getGeneratedRegistry").invoke(null)
        }.getOrNull()
        if (registry == null) null else registryClass to registry
    }
    val parser = runCatching { type.getMethod("getParserForType").invoke(like) }.getOrNull()

    // ทางเดียวกับที่ต้นทางใช้: parser ของข้อความอ่านพร้อม registry ของแอป
    // ซึ่งเป็นทางเดียวที่ endpoint ที่แอปลงทะเบียนไว้ยังถูกอ่าน (ทางที่เหลืออ่านได้แต่ไม่มี endpoint)
    val parsed = (if (registryInfo != null) {
        val (registryClass, registry) = registryInfo
        parser?.invokeParse(type, arrayOf(ByteArray::class.java, registryClass), bytes, registry)
    } else {
        null
    }) ?: parser?.invokeParse(type, arrayOf(ByteArray::class.java), bytes)
        ?: type.invokeStaticParse(bytes)
    if (parsed != null) return parsed

    Logger.printInfo { "Downloads: the app cannot read the replacement of ${type.name}" }
    return null
}

/**
 * เรียกเมธอดของ [this] ที่อ่าน bytes เป็นข้อความของแอปคลาส [type]
 *
 * แอปเปลี่ยนชื่อเมธอดของ protobuf ไว้ในแต่ละเวอร์ชัน (เช่น `l([B, registry)`) จึงหาจากชนิดของ
 * พารามิเตอร์และของผลลัพธ์ ไม่ใช่จากชื่อเมธอด
 */
private fun Any.invokeParse(
    type: Class<*>,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?,
): Any? = runCatching {
    javaClass.methods.firstOrNull {
        !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(parameterTypes)
    }?.invoke(this, *args)?.takeIf(type::isInstance)
}.getOrNull()

/** เมธอดอ่าน bytes ของคลาสข้อความเอง ซึ่งมีเฉพาะบางเวอร์ชัน */
private fun Class<*>.invokeStaticParse(bytes: ByteArray): Any? = runCatching {
    methods.firstOrNull {
        Modifier.isStatic(it.modifiers) &&
            it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
    }?.invoke(null, bytes)?.takeIf(this::isInstance)
}.getOrNull()

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
                // บังคับคุณภาพ 1440p และ 4K ของดาวน์โหลด แม้แอปตัดสินว่าอุปกรณ์นี้เล่นคุณภาพนั้นไม่ได้
                SwitchPreference("morphe_download_force_4k", summary = true),
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

    // คำสั่งที่ router ได้รับจากเซิร์ฟเวอร์เป็นคำสั่งที่เปิดหน้าขาย Premium (บัญชีที่ไม่ใช่ Premium)
    // คำสั่งนั้นถูกแทนด้วยคำสั่งที่บันทึกวิดีโอจริง ก่อนที่ router จะหาตัวจัดการของคำสั่ง
    // คำสั่งทุกตัวของแอปผ่าน hook นี้ ค่าตั้งจึงถูกอ่านก่อนแปลงเป็น bytes
    ResolveCommandFingerprint.hookMethod {
        before { param ->
            if (!inAppDownloadMode()) return@before
            val command = param.args[0] ?: return@before
            val message = appMessageLite(command) ?: return@before
            val bytes = InAppDownloadsPatch.replaceDownloadActionCommand(message) ?: return@before
            val replacement = parseAppMessage(command, bytes) ?: return@before

            param.args[0] = replacement
            Logger.printDebug { "Downloads: replaced the download action command" }
        }
    }

    // คำตอบของ endpoint ที่เปิดหน้าขาย Premium ซึ่งเป็นคำตอบของการกดคุณภาพในแผงดาวน์โหลด
    // ถูกแทนด้วยคำตอบที่สั่งให้บันทึกวิดีโอ คำสั่งของ endpoint อ่านจากฟิลด์ของตัวรับคำตอบ
    val upsellCommandField = ::offlineUpsellCommandField.field.apply { isAccessible = true }
    OfflineUpsellResponseFingerprint.hookMethod {
        before { param ->
            if (!inAppDownloadMode()) return@before
            val response = param.args[0] ?: return@before
            val command = runCatching { upsellCommandField.get(param.thisObject) }.getOrNull()
                ?: return@before
            val commandMessage = appMessageLite(command) ?: return@before
            val responseMessage = appMessageLite(response) ?: return@before
            val bytes = BaseInAppDownloadsPatch.replaceOfflineUpsell(commandMessage, responseMessage)
            if (bytes == null) {
                // คำตอบที่ส่วนขยายไม่แทน (เช่น คำตอบที่ไม่มี renderer ของการขาย Premium)
                Logger.printDebug { "Downloads: kept the offline upsell response of the app" }
                return@before
            }
            val replacement = parseAppMessage(response, bytes) ?: return@before

            param.args[0] = replacement
            Logger.printDebug { "Downloads: replaced the offline upsell response" }
        }
    }

    // ค่าของสตรีมที่ส่วนขยายอ่าน ซึ่งต้นทางอ่านผ่านอินเทอร์เฟซที่ตัวติดตั้งเพิ่มให้คลาสของแอป
    val streamAccess = DownloadStreamAccess(
        formatStreamsField = ::videoFormatStreamsField.field,
        formatField = ::formatStreamFormatField.field,
        itagField = ::formatStreamItagField.field,
        widthField = ::formatStreamWidthField.field,
        heightField = ::formatStreamHeightField.field,
        mimeTypeField = ::formatStreamMimeTypeField.field,
    )
    val fetchContentLengthMethod = FetchContentLengthFingerprint.method

    // แผงเลือกคุณภาพเรียกเมธอดนี้ทีละคุณภาพ เพื่อเลือกสตรีมของวิดีโอและของเสียง
    // คุณภาพที่สูงกว่า 1080p ซึ่งตรวจของอุปกรณ์หรือของเซิร์ฟเวอร์ไม่ผ่าน ถูกบังคับด้วยสตรีมของคุณภาพนั้น
    // เมื่อผู้ใช้เปิดค่าตั้ง (แอปจะได้สตรีมของคุณภาพที่ต่ำกว่าแทน และแผงทิ้งคุณภาพนั้นไป)
    DownloadStreamSelectorFingerprint.hookMethod {
        after { param ->
            // สตรีมเสียงไม่ต้องบังคับคุณภาพ
            if (param.args[4] as Boolean) return@after
            val streamingData = param.args[3] ?: return@after

            val forcedStream = forceDownloadStream(
                selector = param.thisObject,
                pickedStream = param.result,
                quality = param.args[0] as Int,
                maxHeight = param.args[1] as Int,
                audioQuality = param.args[2] as Int,
                streamingData = streamingData,
                playerConfig = param.args[5],
                access = streamAccess,
                fetchContentLength = fetchContentLengthMethod,
            )
            if (forcedStream != null) param.result = forcedStream
        }
    }

    // การดาวน์โหลดเริ่มขึ้น: จำคุณภาพและข้อมูลสตรีมไว้ให้ hook ที่ดึงความยาวสตรีม
    DownloadTransferStreamsFingerprint.hookMethod {
        before { param ->
            val streamingData = param.args[4] ?: return@before
            val quality = param.args[0] as Int
            downloadScope.set(DownloadScope(quality, streamingData))
            Logger.printDebug { "Downloads: download started, quality=${quality}p" }
        }
        after { downloadScope.remove() }
    }

    // การดาวน์โหลดเลือกสตรีมของวิดีโออีกรอบด้วยการตรวจชุดเดียวกับแผงเลือกคุณภาพ แล้วดึงความยาวของ
    // สตรีมก่อนจับคู่กับสตรีมเสียง จุดนี้จึงเป็นจุดสุดท้ายที่ยังแทนสตรีมวิดีโอได้ และแอปจะดึงความยาว
    // ให้สตรีมที่แทนเข้าไปเอง ซึ่งเป็นขั้นเดียวกับที่ต้นทางแทนสตรีมก่อนจับคู่
    FetchContentLengthFingerprint.hookMethod {
        before { param ->
            val scope = downloadScope.get() ?: return@before
            val stream = param.args[0] ?: return@before
            if (!streamAccess.isVideoStream(stream)) return@before
            downloadScope.remove()

            val forcedStream = forceDownloadVideoStream(
                stream = stream,
                quality = scope.quality,
                streamingData = scope.streamingData,
                access = streamAccess,
            )
            if (forcedStream == null || forcedStream === stream) return@before

            Logger.printInfo {
                "Downloads: forced ${scope.quality}p video stream, was ${streamAccess.describe(stream)}"
            }
            param.args[0] = forcedStream
        }
    }
}
