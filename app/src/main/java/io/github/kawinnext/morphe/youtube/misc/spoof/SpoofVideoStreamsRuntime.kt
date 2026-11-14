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
import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch
import com.google.android.libraries.youtube.media.interfaces.HttpHeader
import java.lang.reflect.Field
import java.lang.reflect.Method

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

    return try {
        // The module is not one of the packages the app can look up, so its assets are read from
        // the module itself instead of from a context of the module.
        java.util.zip.ZipFile(io.github.kawinnext.modulePath).use { zip ->
            listOf("assets/nsigsolver/$shortName.js", "assets/nsigsolver/$shortName.html")
                .firstNotNullOfOrNull { path ->
                    zip.getEntry(path)?.let { entry ->
                        zip.getInputStream(entry).bufferedReader().use { reader -> reader.readText() }
                    }
                }
                ?.also { assets[shortName] = it }
        }
    } catch (ex: Exception) {
        Logger.printException({ "Could not read the asset: $name" }, ex)
        null
    }
}

/**
 * The scripts the extension reads from the resources of the app, which are read once.
 */
private val assets = java.util.concurrent.ConcurrentHashMap<String, String>()

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
 * Parses a message with the proto class of the app.
 *
 * The bytes the extension returns are the bytes of a message of the app, so they must be parsed
 * with the class of the app and not with the class of the extension.
 *
 * @return The parsed message, or null if the class cannot parse.
 */
internal fun parseProtoMessage(messageClass: Class<*>, bytes: ByteArray): Any? {
    return try {
        messageClass.methodOrNull("parseFrom", ByteArray::class.java)?.let { parseFrom ->
            return parseFrom.invoke(null, bytes)
        }

        // A message without a parseFrom method is parsed with its builder.
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
 *
 * @return The parsed message, or null if the class cannot parse.
 */
internal fun parseProtoMessageWithRegistry(messageClass: Class<*>, bytes: ByteArray): Any? {
    return try {
        val defaultInstanceField = messageClass.declaredFields.firstOrNull { field ->
            java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == messageClass
        } ?: return parseProtoMessage(messageClass, bytes)
        defaultInstanceField.isAccessible = true
        val defaultInstance = defaultInstanceField.get(null)
            ?: return parseProtoMessage(messageClass, bytes)

        val builder = defaultInstance.javaClass.methods.firstOrNull {
            it.name == "createBuilder" && it.parameterTypes.isEmpty()
        }?.invoke(defaultInstance) ?: return parseProtoMessage(messageClass, bytes)

        val registryClass = messageClass.classLoader
            ?.loadClass("com.google.protobuf.ExtensionRegistryLite")
            ?: return parseProtoMessage(messageClass, bytes)
        val registry = registryClass.getMethod("getGeneratedRegistry").invoke(null)

        val mergeFrom = builder.javaClass.methods.firstOrNull { method ->
            method.name == "mergeFrom" && method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == ByteArray::class.java &&
                method.parameterTypes[1] == registryClass
        } ?: return parseProtoMessage(messageClass, bytes)

        val merged = mergeFrom.invoke(builder, bytes, registry) ?: builder
        val build = merged.javaClass.methods.firstOrNull {
            it.name == "build" && it.parameterTypes.isEmpty()
        } ?: return parseProtoMessage(messageClass, bytes)

        build.invoke(merged)
    } catch (ex: Exception) {
        Logger.printException(
            { "Could not parse a message with its extensions: ${messageClass.name}" }, ex
        )
        parseProtoMessage(messageClass, bytes)
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
    override fun patch_getHeaders(): ArrayList<HttpHeader>? =
        readField(request, headersField) as ArrayList<HttpHeader>?

    override fun patch_getUrl(): String? = readField(request, urlField) as String?

    override fun patch_getBody(): ByteArray? = readField(request, bodyField) as ByteArray?

    override fun patch_withBodyAndHeaders(
        body: ByteArray?,
        headers: ArrayList<HttpHeader>?,
    ): MediaHttpRequest = MediaRequestProxy(
        createRequest(request, patch_getUrl(), headers, body),
        urlField,
        bodyField,
        headersField,
        createRequest,
    )
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

    fun apply(instance: Any, playerProto: Any?) {
        try {
            if (!SpoofVideoStreamsPatch.isSpoofingEnabled() || playerProto == null) return

            val details = readField(instance, videoDetails) ?: return
            val videoId = readField(details, this.videoId) as? String ?: return

            val stream = SpoofVideoStreamsPatch.getStreamingData(videoId, playerProto) ?: return
            val parsed = parseProtoMessage(playerProto.javaClass, stream) ?: return
            val streamingData = readField(parsed, getStreamingData) ?: return
            writeField(instance, setStreamingData, streamingData)

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
