package io.github.kawinnext.morphe.youtube.interaction.downloads

import app.morphe.extension.shared.Logger
import app.morphe.extension.youtube.patches.InAppDownloadsPatch
import app.morphe.extension.youtube.patches.VideoFormat
import java.lang.reflect.Field
import java.lang.reflect.Method

// ต้นทางเพิ่มอินเทอร์เฟซของส่วนขยายให้คลาสของแอป (ข้อมูลสตรีม สตรีม และ format ของสตรีม) แล้วเรียก
// เมธอดของอินเทอร์เฟซนั้นเพื่ออ่านค่าของสตรีม ตอนรันเพิ่มอินเทอร์เฟซให้คลาสที่โหลดแล้วไม่ได้ โมดูลจึง
// อ่านฟิลด์ของออบเจกต์เดียวกันเอง แล้วส่ง proxy ที่ส่วนขยายเรียกใช้ได้ข้ามขอบเขตแทนออบเจกต์ของแอป

/**
 * ค่าที่อ่านได้จากออบเจกต์ของแอป ซึ่งต้นทางอ่านผ่าน StreamingDataInterface, FormatStreamInterface
 * และ VideoFormat.FormatInterface ที่ตัวติดตั้งเพิ่มให้คลาสของแอป
 */
internal class DownloadStreamAccess(
    /** ฟิลด์ list ของทุกสตรีมของวิดีโอ ในข้อมูลสตรีมของวิดีโอ */
    private val formatStreamsField: Field,
    /** ฟิลด์ของออบเจกต์ format ที่สตรีมถืออยู่ */
    private val formatField: Field,
    private val itagField: Field,
    private val widthField: Field,
    private val heightField: Field,
    private val mimeTypeField: Field,
) {
    /** ทุกสตรีมของวิดีโอ หรือ list ว่างถ้าข้อมูลสตรีมยังไม่มีสตรีม */
    fun formatStreams(streamingData: Any): List<Any> =
        (formatStreamsField.get(streamingData) as? List<*>)?.filterNotNull() ?: emptyList()

    fun formatOf(stream: Any): Any? = formatField.get(stream)

    fun itagOf(format: Any?): Int = format?.let { itagField.get(it) } as? Int ?: -1

    fun widthOf(format: Any?): Int = format?.let { widthField.get(it) } as? Int ?: 0

    fun heightOf(format: Any?): Int = format?.let { heightField.get(it) } as? Int ?: 0

    fun mimeTypeOf(format: Any?): String? = format?.let { mimeTypeField.get(it) } as? String

    /** ชนิด mime ของสตรีม ซึ่งบอกว่าสตรีมนี้เป็นวิดีโอหรือเสียง */
    fun mimeTypeOfStream(stream: Any): String? = mimeTypeOf(formatOf(stream))

    fun isVideoStream(stream: Any): Boolean = mimeTypeOfStream(stream)?.startsWith("video/") == true

    /** ข้อความของสตรีมสำหรับ log ซึ่งบอกคุณภาพและ codec ของสตรีม */
    fun describe(stream: Any): String {
        val format = formatOf(stream)
        return "itag ${itagOf(format)} ${widthOf(format)}x${heightOf(format)} ${codecOf(format)}"
    }

    /** codec ของสตรีม เช่น "vp9" หรือ "av01.0.12M.08" ซึ่งอยู่ในชนิด mime หลัง codecs= */
    fun codecOf(format: Any?): String {
        val mimeType = mimeTypeOf(format) ?: return ""
        val codecsStart = mimeType.indexOf("codecs=\"")
        if (codecsStart < 0) return ""

        val codecStart = codecsStart + CODECS_PREFIX_LENGTH
        return mimeType.substring(codecStart).substringBefore('"')
    }

    private companion object {
        const val CODECS_PREFIX_LENGTH = 8 // ความยาวของ codecs="
    }
}

/**
 * สตรีมของแอปหนึ่งสตรีม ในรูปที่ส่วนขยายอ่าน format ของมันได้
 */
internal class DownloadFormatStreamProxy(
    val appStream: Any,
    private val access: DownloadStreamAccess,
) : InAppDownloadsPatch.FormatStreamInterface {
    override fun patch_getFormat(): VideoFormat.FormatInterface =
        DownloadFormatProxy(access.formatOf(appStream), access)

    // ส่วนขยายอ่านค่านี้เฉพาะตอนแทนสตรีมเสียงให้ตรงกับ container ของวิดีโอ ซึ่งยังไม่พอร์ต
    override fun patch_getXtags(): String? = null
}

/**
 * format ของสตรีมหนึ่งสตรีม ซึ่งให้ค่าที่แอปพิมพ์ใน toString ของสตรีม
 */
internal class DownloadFormatProxy(
    private val appFormat: Any?,
    private val access: DownloadStreamAccess,
) : VideoFormat.FormatInterface {
    override fun patch_getMimeType(): String? = access.mimeTypeOf(appFormat)

    override fun patch_getWidth(): Int = access.widthOf(appFormat)

    override fun patch_getHeight(): Int = access.heightOf(appFormat)

    override fun patch_getItag(): Int = access.itagOf(appFormat)
}

/**
 * ข้อมูลสตรีมของวิดีโอ ในรูปที่ส่วนขยายอ่านทุกสตรีมของวิดีโอได้
 */
internal class DownloadStreamingDataProxy(
    private val appStreamingData: Any,
    private val access: DownloadStreamAccess,
) : InAppDownloadsPatch.StreamingDataInterface {
    override fun patch_getFormatStreams(): List<InAppDownloadsPatch.FormatStreamInterface> =
        access.formatStreams(appStreamingData).map { DownloadFormatStreamProxy(it, access) }
}

/**
 * คลาสของแอปที่เลือกสตรีมของคุณภาพ ในรูปที่ส่วนขยายเรียกเมธอดของมันได้
 *
 * ส่วนขยายเรียกเมธอดเลือกสตรีมของแอปเอง แล้วบังคับคุณภาพของสตรีมที่มันคืนมา ต้นทางเปลี่ยนชื่อ
 * เมธอดเดิมของแอปไว้ให้ส่วนขยายเรียก ตอนรันเปลี่ยนชื่อเมธอดที่โหลดแล้วไม่ได้ เมธอดเดิมจึงถูกเรียก
 * ไปแล้วโดย hook ที่ได้ผลลัพธ์มาแล้ว proxy นี้คืนผลลัพธ์นั้นแทนการเรียกซ้ำ
 */
internal class DownloadStreamSelectorProxy(
    private val appSelector: Any,
    private val pickedStream: Any?,
    private val access: DownloadStreamAccess,
    private val fetchContentLength: Method,
) : InAppDownloadsPatch.DownloadStreamSelector {
    override fun patch_selectStream(
        quality: Int,
        maxHeight: Int,
        audioQuality: Int,
        streamingData: InAppDownloadsPatch.StreamingDataInterface?,
        audio: Boolean,
        playerConfig: Any?,
    ): InAppDownloadsPatch.FormatStreamInterface? =
        pickedStream?.let { DownloadFormatStreamProxy(it, access) }

    override fun patch_fetchContentLength(
        stream: InAppDownloadsPatch.FormatStreamInterface,
    ): InAppDownloadsPatch.FormatStreamInterface {
        val appStream = (stream as? DownloadFormatStreamProxy)?.appStream ?: return stream
        val fetched = try {
            fetchContentLength.invoke(appSelector, appStream)
        } catch (exception: Exception) {
            Logger.printException({ "Downloads: fetch content length failure" }, exception)
            null
        }
        return if (fetched == null || fetched === appStream) {
            stream
        } else {
            DownloadFormatStreamProxy(fetched, access)
        }
    }
}

/**
 * จุดฉีดของส่วนขยาย: สตรีมของหนึ่งคุณภาพที่แผงเลือกคุณภาพของดาวน์โหลดกำลังสร้าง
 *
 * แผงทิ้งคุณภาพที่สตรีมของมันเป็นสตรีมเดียวกับคุณภาพที่ต่ำกว่า และแอปเลือกคุณภาพที่ต่ำกว่าถ้า
 * ตรวจของอุปกรณ์หรือของเซิร์ฟเวอร์ไม่ผ่าน ส่วนขยายจึงคืนสตรีมของคุณภาพนั้นแทน ซึ่งคุณภาพที่สูงกว่า
 * 1080p บังคับได้เมื่อผู้ใช้เปิดค่าตั้งไว้
 *
 * @return สตรีมของแอปที่ควรใช้ หรือ null ถ้าไม่มีสตรีมของคุณภาพนั้น
 */
internal fun forceDownloadStream(
    selector: Any,
    pickedStream: Any?,
    quality: Int,
    maxHeight: Int,
    audioQuality: Int,
    streamingData: Any,
    playerConfig: Any?,
    access: DownloadStreamAccess,
    fetchContentLength: Method,
): Any? = try {
    InAppDownloadsPatch.selectDownloadStream(
        DownloadStreamSelectorProxy(selector, pickedStream, access, fetchContentLength),
        quality,
        maxHeight,
        audioQuality,
        DownloadStreamingDataProxy(streamingData, access),
        false,
        playerConfig,
    ).let { (it as? DownloadFormatStreamProxy)?.appStream }
} catch (exception: Exception) {
    Logger.printException({ "Downloads: select download stream failure" }, exception)
    null
}

/**
 * จุดฉีดของส่วนขยาย: สตรีมวิดีโอของการดาวน์โหลดที่กำลังเริ่ม
 *
 * การดาวน์โหลดเลือกสตรีมของมันอีกรอบด้วยการตรวจชุดเดียวกับแผงเลือกคุณภาพ และได้สตรีมของคุณภาพ
 * ที่ต่ำกว่าเมื่อตรวจไม่ผ่าน ส่วนขยายจึงคืนสตรีมของคุณภาพที่ผู้ใช้เลือกแทน
 *
 * @return สตรีมของแอปที่ควรใช้ ซึ่งเป็นสตรีมเดิมถ้าไม่ต้องบังคับ
 */
internal fun forceDownloadVideoStream(
    stream: Any?,
    quality: Int,
    streamingData: Any,
    access: DownloadStreamAccess,
): Any? = try {
    InAppDownloadsPatch.selectDownloadVideoStream(
        stream?.let { DownloadFormatStreamProxy(it, access) },
        quality,
        DownloadStreamingDataProxy(streamingData, access),
    ).let { (it as? DownloadFormatStreamProxy)?.appStream ?: stream }
} catch (exception: Exception) {
    Logger.printException({ "Downloads: select download video stream failure" }, exception)
    stream
}
