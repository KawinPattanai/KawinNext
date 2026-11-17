/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Runtime adapters for the spoof video streams patch.
 *
 * The patch upstream edits instructions to add an interface to a class, to add methods to a class,
 * and to call the extension from a method it cannot see. None of that can be done to the app
 * classes from an Xposed module, so each of those edits is done here with a proxy object or with
 * reflection instead.
 */

package io.github.kawinnext.morphe.youtube.misc.spoof

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.patches.MediaHttpRequest
import app.morphe.extension.shared.patches.HttpHeaderAccess
import app.morphe.extension.shared.spoof.ClientType
import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch
import app.morphe.extension.shared.spoof.requests.StreamingDataRequest
import com.google.android.libraries.youtube.media.interfaces.HttpHeader
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.ByteBuffer

/**
 * @return The field of this class with the exact type, or null.
 */
internal fun Class<*>.fieldOfType(type: Class<*>): Field? =
    declaredFields.firstOrNull { it.type == type }?.apply { isAccessible = true }

/**
 * @return The field of this class with the type of the name, for a type the module compiles
 *         against no more than a name.
 */
internal fun Class<*>.fieldOfTypeName(typeName: String): Field? =
    declaredFields.firstOrNull { it.type.name == typeName }?.apply { isAccessible = true }

/**
 * Reads a file the extension reads from the resources of the app.
 *
 * Upstream copies the scripts of the JavaScript solver into the raw resources of the app. The module
 * ships them as its own assets instead, and the lookup of a raw resource that the app does not have
 * is answered from here.
 *
 * @param name Name of the resource, as the extension names it, such as `nsigsolver/yt.solver.core.js`.
 * @return The content of the file, or null if the module has no such file.
 */
internal fun readModuleAsset(name: String): String? {
    val shortName = name.substringAfterLast('/').removeSuffix(".js")
    assets[shortName]?.let { return it }
    if (shortName in missingAssets) return null

    val content = try {
        // The module is not one of the packages the app can look up, so its assets are read from
        // the module itself instead of from a context of the module.
        java.util.zip.ZipFile(io.github.kawinnext.modulePath).use { zip ->
            listOf("assets/nsigsolver/$shortName.js", "assets/nsigsolver/$shortName.html")
                .firstNotNullOfOrNull { path ->
                    zip.getEntry(path)?.let { entry ->
                        zip.getInputStream(entry).bufferedReader().use { reader -> reader.readText() }
                    }
                }
        }
    } catch (ex: Exception) {
        Logger.printException({ "Could not read the asset: $name" }, ex)
        null
    }

    // A raw resource of the app that the module has no asset for is not looked up again.
    if (content == null) {
        missingAssets.add(shortName)
        return null
    }

    Logger.printDebug { "Read the asset of the module: $shortName (${content.length} characters)" }
    assets[shortName] = content
    return content
}

/**
 * The scripts the extension reads from the resources of the app, which are read once.
 */
private val assets = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * The names the module has no asset for, so the module is not opened for the same name again.
 */
private val missingAssets: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

/**
 * Reads a field of the object, also when the class of the object is obfuscated.
 */
internal fun readField(instance: Any, field: Field): Any? = try {
    field.get(instance)
} catch (ex: Exception) {
    Logger.printException({ "readField failure: $field" }, ex)
    null
}

/**
 * Writes a field of the object, also when the class of the object is obfuscated.
 */
internal fun writeField(instance: Any, field: Field, value: Any?): Boolean = try {
    field.set(instance, value)
    true
} catch (ex: Exception) {
    Logger.printException({ "writeField failure: $field" }, ex)
    false
}

/**
 * @return The method of this class with the name and the parameter types, or null.
 */
private fun Class<*>.methodOrNull(name: String, vararg parameters: Class<*>): Method? =
    try {
        getDeclaredMethod(name, *parameters).apply { isAccessible = true }
    } catch (_: NoSuchMethodException) {
        null
    }

/**
 * @return The default instance of a message class, or null if the class has none.
 */
private fun defaultInstanceOf(messageClass: Class<*>): Any? = try {
    (messageClass.declaredFields + messageClass.fields).firstOrNull { field ->
        java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == messageClass
    }?.apply { isAccessible = true }?.get(null)
} catch (ex: Exception) {
    Logger.printException({ "Could not read the default instance of: ${messageClass.name}" }, ex)
    null
}

/**
 * @return The method the messages of the app build themselves with, or null.
 *
 * A generated message keeps one method that makes every object it needs, which is the only method
 * its class declares: the runtime calls it for the message itself, and so it cannot be removed.
 * The first parameter of the method tells which of those objects is wanted.
 */
private fun generatedDynamicMethod(messageClass: Class<*>): Method? =
    messageClass.declaredMethods.firstOrNull { method ->
        val cases = method.parameterTypes.firstOrNull()
        method.parameterTypes.size == 3 && cases != null && cases.isEnum &&
            (cases.enumConstants?.size ?: 0) >= 2
    }

/**
 * @return The parser of a message class, or null if the app has none for it.
 *
 * The messages the app parses keep no parser of their own, and a message the app never parses from
 * bytes keeps none either, such as the streaming data of a player response. The parser of every
 * message can still be read from the generated method of the message, which builds the parser.
 */
private fun messageParser(messageClass: Class<*>): Any? = try {
    val instance = defaultInstanceOf(messageClass)
    if (instance == null) {
        null
    } else {
        instance.javaClass.methods.firstOrNull {
            it.name == "getParserForType" && it.parameterTypes.isEmpty()
        }?.invoke(instance) ?: run {
            val dynamicMethod = generatedDynamicMethod(messageClass)
            val cases = dynamicMethod?.parameterTypes?.getOrNull(0)?.enumConstants
            if (dynamicMethod == null || cases == null) {
                null
            } else {
                // The parser is the last case of the method, which reads its own name.
                val parserCase = cases.lastOrNull { it is Enum<*> && it.name == "GET_PARSER" }
                    ?: cases.last()
                dynamicMethod.invoke(instance, parserCase, null, null)
            }
        }
    }
} catch (ex: Exception) {
    Logger.printException({ "Could not read the parser of: ${messageClass.name}" }, ex)
    null
}

/**
 * @return The message the parser read from its bytes, or null if it cannot read them.
 *
 * A parser keeps the ways of the runtime to read a message, and the shrinker removes the ways the
 * app does not call itself. Each way is tried, and they are found by the types they take and return
 * instead of their names, because the names of the runtime of the app are obfuscated.
 *
 * @param withExtensions If the fields the app registers as extensions of the message are wanted.
 */
private fun Any.parseBytes(
    messageClass: Class<*>,
    bytes: ByteArray,
    withExtensions: Boolean,
): Any? {
    val readers = javaClass.methods.filter { method ->
        method.returnType == messageClass && method.parameterTypes.isNotEmpty()
    }

    // The bytes are read as they are, or as a buffer of them.
    fun direct(): Any? {
        readers.firstOrNull { reader ->
            reader.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
        }?.let { return it.invoke(this, bytes) }
        readers.firstOrNull { reader ->
            reader.parameterTypes.contentEquals(arrayOf(ByteBuffer::class.java))
        }?.let { return it.invoke(this, ByteBuffer.wrap(bytes)) }
        return null
    }

    // The bytes are read as a stream, with the fields the app registered for the message.
    fun withRegistry(): Any? {
        val reader = readers.firstOrNull { reader ->
            reader.parameterTypes.size == 2 &&
                reader.parameterTypes[0] != ByteArray::class.java &&
                reader.parameterTypes[1] != ByteArray::class.java
        } ?: return null
        val input = reader.parameterTypes[0].codedInputOf(bytes) ?: return null
        val registry = reader.parameterTypes[1].registryOf(withExtensions) ?: return null
        return reader.invoke(this, input, registry)
    }

    return if (withExtensions) withRegistry() ?: direct() else direct() ?: withRegistry()
}

/**
 * @return The stream of coded bytes of this class, or null if the class has no way to make one.
 */
private fun Class<*>.codedInputOf(bytes: ByteArray): Any? = methods.firstOrNull { method ->
    java.lang.reflect.Modifier.isStatic(method.modifiers) && method.returnType == this &&
        method.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
}?.invoke(null, bytes)

/**
 * @return The registry of extensions of this class, or null if the class has no way to make one.
 *
 * @param generated If the registry of the fields the app registered is wanted, and not the empty one.
 */
private fun Class<*>.registryOf(generated: Boolean): Any? {
    val factories = methods.filter { method ->
        java.lang.reflect.Modifier.isStatic(method.modifiers) && method.returnType == this &&
            method.parameterTypes.isEmpty()
    }
    val factory = if (generated) {
        factories.firstOrNull { it.name.contains("Generated") } ?: factories.firstOrNull()
    } else {
        factories.firstOrNull { it.name.contains("Empty") } ?: factories.firstOrNull()
    }
    return factory?.invoke(null)
}

/**
 * @return The methods of this class and of the classes it extends.
 */
private fun Class<*>.allMethods(): List<Method> {
    val methods = mutableListOf<Method>()
    var type: Class<*>? = this
    while (type != null) {
        methods += type.declaredMethods
        type = type.superclass
    }
    return methods
}

/**
 * Reads the bytes of a message with the method the runtime keeps for every message of the app,
 * which takes the default instance of the message and its bytes.
 *
 * The messages the app never parses from bytes, such as the streaming data of a player response,
 * keep no parse method of their own, but the runtime parses the messages of the app with this one.
 *
 * @param withExtensions If the fields the app registers as extensions of the message are wanted.
 * @return The message, or null if the runtime has no such method for the message.
 */
private fun parseWithDefaultInstance(
    messageClass: Class<*>,
    bytes: ByteArray,
    withExtensions: Boolean,
): Any? {
    val instance = defaultInstanceOf(messageClass) ?: return null
    val parseMethods = instance.javaClass.allMethods().filter { method ->
        java.lang.reflect.Modifier.isStatic(method.modifiers) &&
            method.name == "parseFrom" &&
            method.parameterTypes.size in 2..3 &&
            method.parameterTypes[0].isAssignableFrom(instance.javaClass) &&
            method.parameterTypes[1] == ByteArray::class.java
    }
    // The message is read with the fields the app registered for it when that way is kept, and the
    // ways of the runtime are used in the other order when the other one is the one that is left.
    val parseFrom = if (withExtensions) {
        parseMethods.firstOrNull { it.parameterTypes.size == 3 }
            ?: parseMethods.firstOrNull { it.parameterTypes.size == 2 }
    } else {
        parseMethods.firstOrNull { it.parameterTypes.size == 2 }
            ?: parseMethods.firstOrNull { it.parameterTypes.size == 3 }
    } ?: return null

    val registryType = parseFrom.parameterTypes.getOrNull(2)
    if (registryType != null) {
        val registry = registryType.registryOf(generated = withExtensions) ?: return null
        return parseFrom.invoke(null, instance, bytes, registry)
    }
    return parseFrom.invoke(null, instance, bytes)
}

/**
 * Parses a message with the proto class of the app.
 *
 * The bytes the extension returns are the bytes of a message of the app, so they must be parsed
 * with the class of the app and not with the class of the extension.
 *
 * The app is built with the proto runtime of the full library, and the shrinker removes the static
 * parsers of the messages the app builds itself but never parses from bytes, such as the streaming
 * data of a player response. The method the runtime parses the messages of the app with is kept, so
 * a message whose class has no parser is parsed with it.
 *
 * @return The parsed message, or null if the class cannot parse.
 */
internal fun parseProtoMessage(messageClass: Class<*>, bytes: ByteArray): Any? {
    return try {
        // The parser of the class itself, which the app keeps for the messages it parses.
        messageClass.methodOrNull("parseFrom", ByteArray::class.java)?.let { parseFrom ->
            return parseFrom.invoke(null, bytes)
        }

        // The parse method of the runtime, which takes the default instance of the message.
        parseWithDefaultInstance(messageClass, bytes, withExtensions = false)?.let { return it }

        // The parser of the generated method of the message, which the app also keeps.
        messageParser(messageClass)?.parseBytes(messageClass, bytes, withExtensions = false)
            ?.let { return it }

        // A message without a parser is parsed with its builder.
        messageClass.methodOrNull("newBuilder")?.let { newBuilder ->
            val builder = newBuilder.invoke(null) ?: return null
            val mergeFrom = builder.javaClass.methods.firstOrNull {
                it.name == "mergeFrom" &&
                    it.parameterTypes.isNotEmpty() &&
                    it.parameterTypes[0] == ByteArray::class.java
            } ?: return null
            val merged = mergeFrom.invoke(builder, bytes) ?: builder
            val build = merged.javaClass.methods.firstOrNull {
                it.name == "build" && it.parameterTypes.isEmpty()
            } ?: return null
            return build.invoke(merged)
        }

        // Nothing of the class parses the bytes, and the reason is worth knowing: the player keeps
        // the streams of the app, which are not the streams that were fetched for it.
        Logger.printDebug {
            "Could not parse a message of: ${messageClass.name}, default instance: " +
                (defaultInstanceOf(messageClass) != null) +
                ", methods: " + messageClass.declaredMethods.joinToString { method -> method.name }
        }
    } catch (ex: Exception) {
        Logger.printException({ "Could not parse a message of: ${messageClass.name}" }, ex)
    }
    return null
}

/**
 * Parses a message with the proto class of the app, and with the fields the app registers as
 * extensions of the message.
 *
 * The player config holds such fields, such as the exo player config and the android media library
 * config. A message parsed without them has none of them, and the player fails to play the streams.
 * The config of the app is left as it is instead of being replaced with a message that lost them.
 *
 * @return The parsed message, or null if the class cannot parse its bytes with the extensions.
 */
internal fun parseProtoMessageWithRegistry(messageClass: Class<*>, bytes: ByteArray): Any? {
    return try {
        parseWithDefaultInstance(messageClass, bytes, withExtensions = true)
            ?: messageParser(messageClass)?.parseBytes(messageClass, bytes, withExtensions = true)
    } catch (ex: Exception) {
        Logger.printException(
            { "Could not parse a message with its extensions: ${messageClass.name}" }, ex
        )
        null
    }
}

/**
 * @return The bytes of the proto message, or null if it cannot be serialized.
 */
internal fun protoMessageToBytes(message: Any): ByteArray? = try {
    message.javaClass.methods.firstOrNull {
        it.name == "toByteArray" && it.parameterTypes.isEmpty()
    }?.invoke(message) as ByteArray?
} catch (ex: Exception) {
    Logger.printException({ "Could not serialize a message of: ${message.javaClass.name}" }, ex)
    null
}

/**
 * The media request of the app, exposed to the extension.
 *
 * Upstream adds the [MediaHttpRequest] interface to the class of the request and adds the methods
 * of the interface to it. The methods are added here instead, and read the fields of the request.
 *
 * @param createRequest Builds a request with the constructor of the app from the url, the headers
 *                      and the body of a request of the app.
 */
internal class MediaRequestProxy(
    val request: Any,
    private val urlField: Field,
    private val bodyField: Field,
    private val headersField: Field,
    private val createRequest: (source: Any, url: String?, headers: ArrayList<*>?, body: ByteArray?) -> Any,
) : MediaHttpRequest {

    @Suppress("UNCHECKED_CAST")
    override fun patch_getHeaders(): ArrayList<HttpHeader>? {
        val headers = readField(request, headersField) as ArrayList<HttpHeader>?
        // The extension reads the name of a header from the fields of its class, because the module
        // cannot add an interface to a class of the app the way the patch of upstream does. The
        // class is read once, from a header the app built, so the fields come from the class itself
        // and not from a guess about the order it writes them in.
        if (!HttpHeaderAccess.isResolved()) {
            headers?.firstOrNull()?.let { header ->
                val strings = header.javaClass.declaredFields.filter { it.type == String::class.java }
                if (strings.size >= 2) {
                    runCatching { HttpHeaderAccess.setFields(strings[0], strings[1]) }
                }
            }
        }
        return headers
    }

    override fun patch_getUrl(): String? = readField(request, urlField) as String?

    override fun patch_getBody(): ByteArray? = readField(request, bodyField) as ByteArray?

    override fun patch_withBodyAndHeaders(
        body: ByteArray?,
        headers: ArrayList<HttpHeader>?,
    ): MediaHttpRequest {
        val url = patch_getUrl()
        // A request that leaves without a url of its own fails on the player side with
        // ERR_INVALID_URL, and the player retries until the video gives up, which the user sees as a
        // spinner that never ends. The request of the app is handed back untouched instead.
        // The app reads every header it is handed, and a request it builds from a null list fails
        // later with "Attempt to invoke getClass() on a null object reference", which the player
        // reports as a stream that never loads.
        if (url.isNullOrBlank() || !url.contains("://") || headers == null) {
            return this
        }
        // The player reads fvip and mn out of every googlevideo url it is handed and fails on a url
        // that does not carry them, which it reports as a stream that never loads. A spoofed url
        // without them is left alone and the request of the app is used as it is.
        if (url.contains("googlevideo.com") && (!url.contains("fvip=") || !url.contains("mn="))) {
            return this
        }
        return MediaRequestProxy(
            createRequest(request, url, headers, body),
            urlField,
            bodyField,
            headersField,
            createRequest,
        )
    }
}

/**
 * Builds the fields of the media request of the app once, and the way to copy a request.
 *
 * @param constructor Creates a request of the app, in the order the constructor takes its parameters.
 */
internal class MediaRequestFactory(
    requestClass: Class<*>,
    private val constructor: (source: Any, url: String?, headers: ArrayList<*>?, body: ByteArray?) -> Any,
) {
    private val urlField = requestClass.fieldOfType(String::class.java)
        ?: error("No url field in: ${requestClass.name}")
    private val bodyField = requestClass.fieldOfType(ByteArray::class.java)
        ?: error("No body field in: ${requestClass.name}")
    private val headersField = requestClass.fieldOfType(ArrayList::class.java)
        ?: error("No headers field in: ${requestClass.name}")

    fun wrap(request: Any): MediaRequestProxy =
        MediaRequestProxy(request, urlField, bodyField, headersField, constructor)

    /**
     * The request the extension returns, as an instance of the class of the app.
     */
    fun unwrap(proxy: MediaHttpRequest): Any = (proxy as? MediaRequestProxy)?.request ?: error(
        "The extension returned a request that is not a proxy of the app request"
    )
}

/**
 * If the streams of the spoofed client were written into the player response of the app.
 *
 * A client is fetched before the player response of a video is built, and the streams it returned
 * can still not be written into that response, such as when the response of the app does not parse
 * the bytes of the client. The player then keeps the streams of the app, and the hooks that change
 * how the app requests its streams must not act as if it had the streams of the client: the app
 * requests SABR streams with an address without a host, which the player retries without end.
 */
@Volatile
internal var spoofedStreamsApplied: Boolean = false

/**
 * ตัวบ่งชี้ว่าเป็นไลฟ์สดหรือพรีเมียร์ ซึ่งเซิร์ฟเวอร์ใส่ไว้ใน url ของสตรีม
 *
 * upstream อ่านตัวบ่งชี้นี้จาก server abr url ของไคลเอนต์ที่สปูฟ เพื่อสลับไคลเอนต์ให้ไลฟ์
 * ทางนี้ไม่มีคลาสของ player response ที่อ่านฟิลด์นั้นได้ จึงอ่านเป็นไบต์ของข้อความแล้วหาตัวบ่งชี้
 * ในไบต์ของ player response แทน ซึ่งได้ผลเหมือนกันเพราะ url ถูกเก็บเป็นข้อความทั้งก้อนในไบต์
 */
private val liveStreamMarkers = arrayOf("yt_live_broadcast", "yt_premiere_broadcast")

/**
 * @return ถ้า player response นี้เป็นของไลฟ์สดหรือของพรีเมียร์
 */
private fun isLiveStreamResponse(playerResponse: ByteArray?): Boolean {
    if (playerResponse == null || playerResponse.isEmpty()) return false
    val text = String(playerResponse, Charsets.ISO_8859_1)
    return liveStreamMarkers.any { marker -> text.contains(marker) }
}

/**
 * ไคลเอนต์ที่ใช้สปูฟไลฟ์สด ซึ่งเป็นไคลเอนต์ที่ upstream สลับให้ไลฟ์อยู่แล้ว
 *
 * ไคลเอนต์ที่ขอสตรีมแบบ SABR ส่งสตรีมของไลฟ์ที่ผู้เล่นเล่นไม่ได้ เซิร์ฟเวอร์ตอบเป็น 403 ผู้เล่นจึง
 * ค้างรอสตรีมที่เล่นไม่ได้ TV_DASH เป็นไคลเอนต์เดียวกับ TV_SABR แต่ขอสตรีมแบบ DASH ซึ่งเล่นไลฟ์ได้
 * ไคลเอนต์นี้ไม่ปรากฏในรายการไคลเอนต์ให้ผู้ใช้เลือก จึงถูกใช้กับไลฟ์เท่านั้น
 */
private val liveStreamClient = ClientType.TV_DASH

/**
 * จำนวนไลฟ์ที่ไคลเอนต์ให้สตรีมมาไม่ได้ติดกัน
 *
 * ไลฟ์ที่เล่นได้เพราะสตรีมของแอปยังไม่เสียหาย การลองไคลเอนต์ที่ใช้ไม่ได้ซ้ำกับไลฟ์ทุกตัวมีแต่ทำให้
 * ไลฟ์เริ่มช้า เพราะต้องรอไคลเอนต์ที่ตอบไม่ได้จนหมดเวลา ไลฟ์ที่ไคลเอนต์ให้สตรีมมาได้จะล้างค่านี้
 * เพราะเซิร์ฟเวอร์ตอบไลฟ์ด้วย "The page needs to be reloaded" ได้เป็นครั้งคราว ซึ่งไม่ควรปิด
 * การสปูฟไลฟ์ไปตลอดทั้งโปรเซส
 */
@Volatile
private var liveStreamClientFailures: Int = 0

/**
 * จำนวนไลฟ์ที่ไคลเอนต์ให้สตรีมมาไม่ได้ติดกัน ก่อนจะเลิกลองกับไลฟ์ตัวถัดไป
 */
private const val MAX_LIVE_STREAM_CLIENT_FAILURES = 2

/**
 * Replaces the streaming data and the player config of a player response with the ones of the
 * spoofed client.
 *
 * Upstream adds a method to the class of the player response that does this and calls it from the
 * constructor. The constructor is hooked here instead, and its fields are read and written.
 */
internal class StreamingDataFields(
    private val videoDetails: Field,
    private val videoId: Field,
    private val setStreamingData: Field,
    private val getStreamingData: Field,
    private val setPlayerConfig: Field,
    private val playerConfigClass: Class<*>,
) {
    /**
     * The length of the video the streams are really of, when it is not the video the app plays.
     */
    private val videoLength = videoDetails.type.declaredFields
        .singleOrNull { it.type == Long::class.javaPrimitiveType }
        ?.apply { isAccessible = true }

    /**
     * ปล่อยให้ผู้เล่นใช้สตรีมของแอปเอง และบอกส่วนอื่นของส่วนขยายว่าวิดีโอนี้ไม่ได้ใช้สตรีมที่สปูฟ
     *
     * ใช้เมื่อไคลเอนต์ที่ขอสตรีมให้ไลฟ์ให้สตรีมมาไม่ได้ สตรีมของแอปเป็นสตรีมที่ผู้เล่นเล่นได้
     * ไลฟ์จึงยังเล่นได้เหมือนก่อนแพตช์
     *
     * ส่วนอื่นอ่านค่านี้ไปตัดสินว่าจะปิด SABR หรือจะตัด post body ของคำขอสตรีม ซึ่งต้องไม่ทำกับ
     * สตรีมของแอป ไม่งั้นไลฟ์สดที่ไม่ได้สปูฟก็เล่นไม่ได้
     */
    private fun keepAppStreams(videoId: String) {
        StreamingDataRequest.setPlayerClient(videoId, null)
        // เก็บเป็น log ระดับ info เพื่อให้เห็นใน logcat ได้โดยไม่ต้องเปิด debug logging
        Logger.printInfo { "Keeping the streams of the app for the live stream: $videoId" }
    }

    /**
     * ขอสตรีมของไลฟ์สดด้วยไคลเอนต์ [liveStreamClient] แล้วเขียนลงใน player response ของแอป
     *
     * ใช้ทางขอสตรีมของส่วนขยายที่มีอยู่ คือ [StreamingDataRequest.fetchRequestForDownload] ซึ่งขอ
     * สตรีมของวิดีโอเดียวด้วยไคลเอนต์ที่กำหนดและไม่เก็บผลลงแคช ไคลเอนต์ที่ผู้ใช้เลือกไว้จึงยังใช้กับ
     * วิดีโอปกติ และการสลับไคลเอนต์ไม่กระทบวิดีโออื่นที่ขอสตรีมพร้อมกัน
     *
     * @return ถ้าไคลเอนต์ให้สตรีมที่เขียนลงใน player response ได้
     */
    private fun spoofLiveStreamWithLiveClient(
        instance: Any,
        playerProto: Any,
        videoId: String,
    ): Boolean {
        val stream = StreamingDataRequest.fetchRequestForDownload(
            videoId,
            listOf(liveStreamClient),
            liveStreamClient,
        ).getStream()
        if (stream == null) {
            Logger.printInfo {
                "No playable streams from ${liveStreamClient.name} for the live stream: $videoId"
            }
            // ไคลเอนต์ให้สตรีมมาไม่ได้ เช่นตัวแก้ลายเซ็น JavaScript ของเครื่องใช้ไม่ได้
            // ไลฟ์ตัวถัดไปยังลองอยู่จนกว่าจะล้มติดกันหลายตัว แล้วจึงเลิก เพื่อไม่ให้รอไคลเอนต์ที่
            // ใช้ไม่ได้ทุกครั้งที่เปิดไลฟ์
            liveStreamClientFailures++
            return false
        }

        val parsed = parseProtoMessage(playerProto.javaClass, stream.streamingData()) ?: return false
        val streamingData = readField(parsed, getStreamingData) ?: return false
        spoofedStreamsApplied = writeField(instance, setStreamingData, streamingData)
        if (!spoofedStreamsApplied) return false

        // ผู้เล่นเลือก SABR จากไคลเอนต์ของสตรีมที่ได้ และส่วนอื่นของส่วนขยายอ่านค่านี้ไปตัดสินว่าจะ
        // ปิด SABR หรือจะตัด post body ของคำขอสตรีม
        StreamingDataRequest.setPlayerClient(videoId, liveStreamClient)
        // เก็บเป็น log ระดับ info เพื่อให้เห็นใน logcat ได้โดยไม่ต้องเปิด debug logging
        Logger.printInfo {
            "Live stream given the streams of client ${liveStreamClient.name} " +
                "(${liveStreamClient.friendlyName}): $videoId"
        }
        liveStreamClientFailures = 0
        return true
    }

    fun apply(instance: Any, playerProto: Any?) {
        try {
            if (!SpoofVideoStreamsPatch.isSpoofingEnabled() || playerProto == null) return

            // The streams of the app stay in the player response until the streams of the client
            // are written into it, and the hooks that change how the app requests its streams read
            // this to know whose streams the player has.
            spoofedStreamsApplied = false

            val details = readField(instance, videoDetails) ?: return
            val videoId = readField(details, this.videoId) as? String ?: return

            // ไลฟ์สดและพรีเมียร์ถูกสปูฟด้วยไคลเอนต์ที่รองรับไลฟ์ ไม่ใช่ไคลเอนต์ที่ผู้ใช้เลือก
            // เซิร์ฟเวอร์ตอบสตรีมของไคลเอนต์ที่ผู้ใช้เลือกด้วย 403 ผู้เล่นจึงค้างรอสตรีมที่เล่นไม่ได้
            // จนกว่าจะปิดแอป ตรวจจาก player response ของแอปก่อน เพื่อไม่ต้องขอสตรีมของไคลเอนต์อื่นมาทิ้ง
            val appStreams = readField(playerProto, getStreamingData)?.let(::protoMessageToBytes)
            if (isLiveStreamResponse(appStreams)) {
                if (liveStreamClientFailures < MAX_LIVE_STREAM_CLIENT_FAILURES &&
                    spoofLiveStreamWithLiveClient(instance, playerProto, videoId)
                ) {
                    return
                }
                keepAppStreams(videoId)
                return
            }

            val stream = SpoofVideoStreamsPatch.getStreamingData(videoId, playerProto) ?: return

            // player response ของแอปอาจไม่มีตัวบ่งชี้ทั้งที่วิดีโอเป็นไลฟ์ เช่นเมื่อแอปขอสตรีมด้วย SABR
            // อย่างเดียว ตัวบ่งชี้ใน player response ของไคลเอนต์จึงถูกตรวจซ้ำอีกชั้น แล้วสลับไคลเอนต์
            // ให้ไลฟ์ก่อนที่สตรีมที่เล่นไม่ได้จะถูกเขียนลงใน player response
            if (isLiveStreamResponse(stream)) {
                if (liveStreamClientFailures < MAX_LIVE_STREAM_CLIENT_FAILURES &&
                    spoofLiveStreamWithLiveClient(instance, playerProto, videoId)
                ) {
                    return
                }
                keepAppStreams(videoId)
                return
            }

            val parsed = parseProtoMessage(playerProto.javaClass, stream) ?: return
            val streamingData = readField(parsed, getStreamingData) ?: return
            spoofedStreamsApplied = writeField(instance, setStreamingData, streamingData)
            Logger.printDebug {
                "Streaming data of $videoId given to the player: $spoofedStreamsApplied"
            }

            // The replacement streams can be of another video, such as an album track played as
            // its song version, and then the app keeps the timeline of the music video.
            val length = SpoofVideoStreamsPatch.getVideoLengthSeconds(videoId)
            if (length > 0 && videoLength != null) {
                videoLength.setLong(details, length)
            }

            val appConfig = readField(playerProto, setPlayerConfig)
            val config = SpoofVideoStreamsPatch.getPlayerConfig(
                videoId,
                appConfig?.let { protoMessageToBytes(it) },
            ) ?: return
            val playerConfig = parseProtoMessageWithRegistry(playerConfigClass, config) ?: return
            writeField(playerProto, setPlayerConfig, playerConfig)
        } catch (ex: Exception) {
            Logger.printException({ "Could not replace the streaming data of the player" }, ex)
        }
    }
}
