package io.github.kawinnext.morphe.youtube.interaction.downloads

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.patches.BaseInAppDownloadsPatch.FormatStreamInterface
import app.morphe.extension.shared.patches.BaseInAppDownloadsPatch.StreamingDataInterface
import app.morphe.extension.youtube.patches.InAppDownloadsPatch
import app.morphe.extension.shared.patches.VideoFormat
import java.lang.reflect.Field
import java.lang.reflect.Method

// เธ•เนเธเธ—เธฒเธเน€เธเธดเนเธกเธญเธดเธเน€เธ—เธญเธฃเนเน€เธเธเธเธญเธเธชเนเธงเธเธเธขเธฒเธขเนเธซเนเธเธฅเธฒเธชเธเธญเธเนเธญเธ (เธเนเธญเธกเธนเธฅเธชเธ•เธฃเธตเธก เธชเธ•เธฃเธตเธก เนเธฅเธฐ format เธเธญเธเธชเธ•เธฃเธตเธก) เนเธฅเนเธงเน€เธฃเธตเธขเธ
// เน€เธกเธเธญเธ”เธเธญเธเธญเธดเธเน€เธ—เธญเธฃเนเน€เธเธเธเธฑเนเธเน€เธเธทเนเธญเธญเนเธฒเธเธเนเธฒเธเธญเธเธชเธ•เธฃเธตเธก เธ•เธญเธเธฃเธฑเธเน€เธเธดเนเธกเธญเธดเธเน€เธ—เธญเธฃเนเน€เธเธเนเธซเนเธเธฅเธฒเธชเธ—เธตเนเนเธซเธฅเธ”เนเธฅเนเธงเนเธกเนเนเธ”เน เนเธกเธ”เธนเธฅเธเธถเธ
// เธญเนเธฒเธเธเธดเธฅเธ”เนเธเธญเธเธญเธญเธเน€เธเธเธ•เนเน€เธ”เธตเธขเธงเธเธฑเธเน€เธญเธ เนเธฅเนเธงเธชเนเธ proxy เธ—เธตเนเธชเนเธงเธเธเธขเธฒเธขเน€เธฃเธตเธขเธเนเธเนเนเธ”เนเธเนเธฒเธกเธเธญเธเน€เธเธ•เนเธ—เธเธญเธญเธเน€เธเธเธ•เนเธเธญเธเนเธญเธ

/**
 * เธเนเธฒเธ—เธตเนเธญเนเธฒเธเนเธ”เนเธเธฒเธเธญเธญเธเน€เธเธเธ•เนเธเธญเธเนเธญเธ เธเธถเนเธเธ•เนเธเธ—เธฒเธเธญเนเธฒเธเธเนเธฒเธ StreamingDataInterface, FormatStreamInterface
 * เนเธฅเธฐ VideoFormat.FormatInterface เธ—เธตเนเธ•เธฑเธงเธ•เธดเธ”เธ•เธฑเนเธเน€เธเธดเนเธกเนเธซเนเธเธฅเธฒเธชเธเธญเธเนเธญเธ
 */
internal class DownloadStreamAccess(
    /** เธเธดเธฅเธ”เน list เธเธญเธเธ—เธธเธเธชเธ•เธฃเธตเธกเธเธญเธเธงเธดเธ”เธตเนเธญ เนเธเธเนเธญเธกเธนเธฅเธชเธ•เธฃเธตเธกเธเธญเธเธงเธดเธ”เธตเนเธญ */
    private val formatStreamsField: Field,
    /** เธเธดเธฅเธ”เนเธเธญเธเธญเธญเธเน€เธเธเธ•เน format เธ—เธตเนเธชเธ•เธฃเธตเธกเธ–เธทเธญเธญเธขเธนเน */
    private val formatField: Field,
    private val itagField: Field,
    private val widthField: Field,
    private val heightField: Field,
    private val mimeTypeField: Field,
) {
    /** เธ—เธธเธเธชเธ•เธฃเธตเธกเธเธญเธเธงเธดเธ”เธตเนเธญ เธซเธฃเธทเธญ list เธงเนเธฒเธเธ–เนเธฒเธเนเธญเธกเธนเธฅเธชเธ•เธฃเธตเธกเธขเธฑเธเนเธกเนเธกเธตเธชเธ•เธฃเธตเธก */
    fun formatStreams(streamingData: Any): List<Any> =
        (formatStreamsField.get(streamingData) as? List<*>)?.filterNotNull() ?: emptyList()

    fun formatOf(stream: Any): Any? = formatField.get(stream)

    fun itagOf(format: Any?): Int = format?.let { itagField.get(it) } as? Int ?: -1

    fun widthOf(format: Any?): Int = format?.let { widthField.get(it) } as? Int ?: 0

    fun heightOf(format: Any?): Int = format?.let { heightField.get(it) } as? Int ?: 0

    fun mimeTypeOf(format: Any?): String? = format?.let { mimeTypeField.get(it) } as? String

    /** เธเธเธดเธ” mime เธเธญเธเธชเธ•เธฃเธตเธก เธเธถเนเธเธเธญเธเธงเนเธฒเธชเธ•เธฃเธตเธกเธเธตเนเน€เธเนเธเธงเธดเธ”เธตเนเธญเธซเธฃเธทเธญเน€เธชเธตเธขเธ */
    fun mimeTypeOfStream(stream: Any): String? = mimeTypeOf(formatOf(stream))

    fun isVideoStream(stream: Any): Boolean = mimeTypeOfStream(stream)?.startsWith("video/") == true

    /** เธเนเธญเธเธงเธฒเธกเธเธญเธเธชเธ•เธฃเธตเธกเธชเธณเธซเธฃเธฑเธ log เธเธถเนเธเธเธญเธเธเธธเธ“เธ เธฒเธเนเธฅเธฐ codec เธเธญเธเธชเธ•เธฃเธตเธก */
    fun describe(stream: Any): String {
        val format = formatOf(stream)
        return "itag ${itagOf(format)} ${widthOf(format)}x${heightOf(format)} ${codecOf(format)}"
    }

    /** codec เธเธญเธเธชเธ•เธฃเธตเธก เน€เธเนเธ "vp9" เธซเธฃเธทเธญ "av01.0.12M.08" เธเธถเนเธเธญเธขเธนเนเนเธเธเธเธดเธ” mime เธซเธฅเธฑเธ codecs= */
    fun codecOf(format: Any?): String {
        val mimeType = mimeTypeOf(format) ?: return ""
        val codecsStart = mimeType.indexOf("codecs=\"")
        if (codecsStart < 0) return ""

        val codecStart = codecsStart + CODECS_PREFIX_LENGTH
        return mimeType.substring(codecStart).substringBefore('"')
    }

    private companion object {
        const val CODECS_PREFIX_LENGTH = 8 // เธเธงเธฒเธกเธขเธฒเธงเธเธญเธ codecs="
    }
}

/**
 * เธชเธ•เธฃเธตเธกเธเธญเธเนเธญเธเธซเธเธถเนเธเธชเธ•เธฃเธตเธก เนเธเธฃเธนเธเธ—เธตเนเธชเนเธงเธเธเธขเธฒเธขเธญเนเธฒเธ format เธเธญเธเธกเธฑเธเนเธ”เน
 */
internal class DownloadFormatStreamProxy(
    val appStream: Any,
    private val access: DownloadStreamAccess,
) : FormatStreamInterface {
    override fun patch_getItag(): Int = access.itagOf(access.formatOf(appStream))

    override fun patch_getWidth(): Int = access.widthOf(access.formatOf(appStream))

    override fun patch_getHeight(): Int = access.heightOf(access.formatOf(appStream))

    override fun patch_getMimeType(): String? = access.mimeTypeOf(access.formatOf(appStream))

    // เธชเนเธงเธเธเธขเธฒเธขเธญเนเธฒเธเธเนเธฒเธเธตเนเน€เธเธเธฒเธฐเธ•เธญเธเนเธ—เธเธชเธ•เธฃเธตเธกเน€เธชเธตเธขเธเนเธซเนเธ•เธฃเธเธเธฑเธ container เธเธญเธเธงเธดเธ”เธตเนเธญ เธเธถเนเธเธขเธฑเธเนเธกเนเธเธญเธฃเนเธ•
    override fun patch_getXtags(): String? = null
}

/**
 * format เธเธญเธเธชเธ•เธฃเธตเธกเธซเธเธถเนเธเธชเธ•เธฃเธตเธก เธเธถเนเธเนเธซเนเธเนเธฒเธ—เธตเนเนเธญเธเธเธดเธกเธเนเนเธ toString เธเธญเธเธชเธ•เธฃเธตเธก
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
 * เธเนเธญเธกเธนเธฅเธชเธ•เธฃเธตเธกเธเธญเธเธงเธดเธ”เธตเนเธญ เนเธเธฃเธนเธเธ—เธตเนเธชเนเธงเธเธเธขเธฒเธขเธญเนเธฒเธเธ—เธธเธเธชเธ•เธฃเธตเธกเธเธญเธเธงเธดเธ”เธตเนเธญเนเธ”เน
 */
internal class DownloadStreamingDataProxy(
    private val appStreamingData: Any,
    private val access: DownloadStreamAccess,
) : StreamingDataInterface {
    override fun patch_getFormatStreams(): List<FormatStreamInterface> =
        access.formatStreams(appStreamingData).map { DownloadFormatStreamProxy(it, access) }
}

/**
 * เธเธฅเธฒเธชเธเธญเธเนเธญเธเธ—เธตเนเน€เธฅเธทเธญเธเธชเธ•เธฃเธตเธกเธเธญเธเธเธธเธ“เธ เธฒเธ เนเธเธฃเธนเธเธ—เธตเนเธชเนเธงเธเธเธขเธฒเธขเน€เธฃเธตเธขเธเน€เธกเธเธญเธ”เธเธญเธเธกเธฑเธเนเธ”เน
 *
 * เธชเนเธงเธเธเธขเธฒเธขเน€เธฃเธตเธขเธเน€เธกเธเธญเธ”เน€เธฅเธทเธญเธเธชเธ•เธฃเธตเธกเธเธญเธเนเธญเธเน€เธญเธ เนเธฅเนเธงเธเธฑเธเธเธฑเธเธเธธเธ“เธ เธฒเธเธเธญเธเธชเธ•เธฃเธตเธกเธ—เธตเนเธกเธฑเธเธเธทเธเธกเธฒ เธ•เนเธเธ—เธฒเธเน€เธเธฅเธตเนเธขเธเธเธทเนเธญ
 * เน€เธกเธเธญเธ”เน€เธ”เธดเธกเธเธญเธเนเธญเธเนเธงเนเนเธซเนเธชเนเธงเธเธเธขเธฒเธขเน€เธฃเธตเธขเธ เธ•เธญเธเธฃเธฑเธเน€เธเธฅเธตเนเธขเธเธเธทเนเธญเน€เธกเธเธญเธ”เธ—เธตเนเนเธซเธฅเธ”เนเธฅเนเธงเนเธกเนเนเธ”เน เน€เธกเธเธญเธ”เน€เธ”เธดเธกเธเธถเธเธ–เธนเธเน€เธฃเธตเธขเธ
 * เนเธเนเธฅเนเธงเนเธ”เธข hook เธ—เธตเนเนเธ”เนเธเธฅเธฅเธฑเธเธเนเธกเธฒเนเธฅเนเธง proxy เธเธตเนเธเธทเธเธเธฅเธฅเธฑเธเธเนเธเธฑเนเธเนเธ—เธเธเธฒเธฃเน€เธฃเธตเธขเธเธเนเธณ
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
        streamingData: StreamingDataInterface?,
        audio: Boolean,
        playerConfig: Any?,
    ): FormatStreamInterface? =
        pickedStream?.let { DownloadFormatStreamProxy(it, access) }

    override fun patch_fetchContentLength(
        stream: FormatStreamInterface,
    ): FormatStreamInterface {
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
 * เธเธธเธ”เธเธตเธ”เธเธญเธเธชเนเธงเธเธเธขเธฒเธข: เธชเธ•เธฃเธตเธกเธเธญเธเธซเธเธถเนเธเธเธธเธ“เธ เธฒเธเธ—เธตเนเนเธเธเน€เธฅเธทเธญเธเธเธธเธ“เธ เธฒเธเธเธญเธเธ”เธฒเธงเธเนเนเธซเธฅเธ”เธเธณเธฅเธฑเธเธชเธฃเนเธฒเธ
 *
 * เนเธเธเธ—เธดเนเธเธเธธเธ“เธ เธฒเธเธ—เธตเนเธชเธ•เธฃเธตเธกเธเธญเธเธกเธฑเธเน€เธเนเธเธชเธ•เธฃเธตเธกเน€เธ”เธตเธขเธงเธเธฑเธเธเธธเธ“เธ เธฒเธเธ—เธตเนเธ•เนเธณเธเธงเนเธฒ เนเธฅเธฐเนเธญเธเน€เธฅเธทเธญเธเธเธธเธ“เธ เธฒเธเธ—เธตเนเธ•เนเธณเธเธงเนเธฒเธ–เนเธฒ
 * เธ•เธฃเธงเธเธเธญเธเธญเธธเธเธเธฃเธ“เนเธซเธฃเธทเธญเธเธญเธเน€เธเธดเธฃเนเธเน€เธงเธญเธฃเนเนเธกเนเธเนเธฒเธ เธชเนเธงเธเธเธขเธฒเธขเธเธถเธเธเธทเธเธชเธ•เธฃเธตเธกเธเธญเธเธเธธเธ“เธ เธฒเธเธเธฑเนเธเนเธ—เธ เธเธถเนเธเธเธธเธ“เธ เธฒเธเธ—เธตเนเธชเธนเธเธเธงเนเธฒ
 * 1080p เธเธฑเธเธเธฑเธเนเธ”เนเน€เธกเธทเนเธญเธเธนเนเนเธเนเน€เธเธดเธ”เธเนเธฒเธ•เธฑเนเธเนเธงเน
 *
 * @return เธชเธ•เธฃเธตเธกเธเธญเธเนเธญเธเธ—เธตเนเธเธงเธฃเนเธเน เธซเธฃเธทเธญ null เธ–เนเธฒเนเธกเนเธกเธตเธชเธ•เธฃเธตเธกเธเธญเธเธเธธเธ“เธ เธฒเธเธเธฑเนเธ
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
 * เธเธธเธ”เธเธตเธ”เธเธญเธเธชเนเธงเธเธเธขเธฒเธข: เธชเธ•เธฃเธตเธกเธงเธดเธ”เธตเนเธญเธเธญเธเธเธฒเธฃเธ”เธฒเธงเธเนเนเธซเธฅเธ”เธ—เธตเนเธเธณเธฅเธฑเธเน€เธฃเธดเนเธก
 *
 * เธเธฒเธฃเธ”เธฒเธงเธเนเนเธซเธฅเธ”เน€เธฅเธทเธญเธเธชเธ•เธฃเธตเธกเธเธญเธเธกเธฑเธเธญเธตเธเธฃเธญเธเธ”เนเธงเธขเธเธฒเธฃเธ•เธฃเธงเธเธเธธเธ”เน€เธ”เธตเธขเธงเธเธฑเธเนเธเธเน€เธฅเธทเธญเธเธเธธเธ“เธ เธฒเธ เนเธฅเธฐเนเธ”เนเธชเธ•เธฃเธตเธกเธเธญเธเธเธธเธ“เธ เธฒเธ
 * เธ—เธตเนเธ•เนเธณเธเธงเนเธฒเน€เธกเธทเนเธญเธ•เธฃเธงเธเนเธกเนเธเนเธฒเธ เธชเนเธงเธเธเธขเธฒเธขเธเธถเธเธเธทเธเธชเธ•เธฃเธตเธกเธเธญเธเธเธธเธ“เธ เธฒเธเธ—เธตเนเธเธนเนเนเธเนเน€เธฅเธทเธญเธเนเธ—เธ
 *
 * @return เธชเธ•เธฃเธตเธกเธเธญเธเนเธญเธเธ—เธตเนเธเธงเธฃเนเธเน เธเธถเนเธเน€เธเนเธเธชเธ•เธฃเธตเธกเน€เธ”เธดเธกเธ–เนเธฒเนเธกเนเธ•เนเธญเธเธเธฑเธเธเธฑเธ
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
