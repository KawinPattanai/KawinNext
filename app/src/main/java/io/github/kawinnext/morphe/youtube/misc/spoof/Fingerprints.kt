/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Ported from app/morphe/patches/shared/misc/spoof/Fingerprints.kt and
 * app/morphe/patches/shared/misc/request/Fingerprints.kt of morphe-patches.
 */

package io.github.kawinnext.morphe.youtube.misc.spoof

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.parameters
import io.github.kawinnext.morphe.strings

internal const val MEDIA_INTERFACES = "Lcom/google/android/libraries/youtube/media/interfaces/"

internal const val HTTP_REQUEST_CLASS_TYPE = "${MEDIA_INTERFACES}HttpRequest;"
internal const val HTTP_HEADER_CLASS_TYPE = "${MEDIA_INTERFACES}HttpHeader;"

internal const val STREAMING_DATA_OUTER_CLASS =
    "Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass\$StreamingData;"

private const val NET_FETCH_CPP_PROXY =
    "com.google.android.libraries.youtube.media.interfaces.NetFetchCallbacks\$CppProxy"
private const val HTTP_REQUEST_CLASS_NAME = "com.google.android.libraries.youtube.media.interfaces.HttpRequest"
private const val HTTP_HEADER_CLASS_NAME = "com.google.android.libraries.youtube.media.interfaces.HttpHeader"

/**
 * Called by native code when a media response asks the player to load the player response again.
 */
internal object ReloadPlayerResponseFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Lcom/google/android/apps/youtube/proto/streaming/ReloadPlayerResponseOuterClass\$ReloadPlayerResponse;"
    ),
) {
    init {
        classMatcher {
            superClass { descriptor("${MEDIA_INTERFACES}PlaybackControllerCallbacks;") }
        }
    }
}

private object CreateStreamingDataParentFingerprint : Fingerprint(
    strings = listOf("Invalid playback type; streaming data is not playable")
)

/**
 * Creates the streaming data and the player config of a video.
 */
internal object CreateStreamingDataFingerprint : Fingerprint(
    classFingerprint = CreateStreamingDataParentFingerprint,
    name = "<init>",
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = STREAMING_DATA_OUTER_CLASS),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = STREAMING_DATA_OUTER_CLASS,
            location = MatchAfterWithin(7)
        ),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(opcode = Opcode.IF_NEZ, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.AND_INT_LIT8),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(5)),
    ),
)

/**
 * Builds the data source of a video stream, which holds the url, the http method and the post body.
 */
internal object BuildMediaDataSourceFingerprint : Fingerprint(
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = "this", opcode = Opcode.IPUT_OBJECT, type = "Landroid/net/Uri;"),
        fieldAccess(definingClass = "this", opcode = Opcode.IPUT, type = "I"),
        fieldAccess(definingClass = "this", opcode = Opcode.IPUT_OBJECT, type = "[B"),
        opcode(Opcode.RETURN_VOID),
    ),
    // 21.37+ added the url of the manifest before the http method.
    custom = {
        parameters(
            "Landroid/net/Uri;",
            "J",
            "Ljava/lang/String;",
            "I",
            "[B",
            "Ljava/util/Map;",
            "J",
            "J",
            "Ljava/lang/String;",
            "I",
            "Ljava/lang/Object;",
        )
    }
)

/**
 * Builds the video format line of the stats for nerds panel.
 */
internal val nerdsStatsVideoFormatBuilderMethod = findMethodDirect {
    findMethod {
        matcher {
            addEqString("codecs=\"")
            returnType = "java.lang.String"
        }
    }.filter { method ->
        // 21.42+ has a mime type codecs parser with the same string.
        method.paramTypes.firstOrNull()?.descriptor != "Ljava/lang/String;"
    }.single()
}

/**
 * Constructor of the enum that tells how the player fetches its media streams.
 */
internal object MediaFetchEnumConstructorFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(
        "ENABLED",
        "DISABLED_FOR_PLAYBACK",
        "DISABLED_BY_SABR_STREAMING_URI",
    ),
)

/**
 * Builds a Cronet url request, which every network request of the app is made with.
 */
internal object CronetUrlRequestBuildFingerprint : Fingerprint(
    name = "build",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Lorg/chromium/net/ExperimentalUrlRequest;",
    parameters = listOf(),
    filters = listOf(
        methodCall(returnType = "Lorg/chromium/net/ExperimentalUrlRequest;")
    ),
) {
    init {
        classMatcher {
            superClass { descriptor("Lorg/chromium/net/ExperimentalUrlRequest\$Builder;") }
        }
    }
}

internal object CronetUrlRequestSetHttpMethodFingerprint : Fingerprint(
    classFingerprint = CronetUrlRequestBuildFingerprint,
    name = "setHttpMethod",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Lorg/chromium/net/ExperimentalUrlRequest\$Builder;",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_OBJECT, definingClass = "this", type = "Ljava/lang/String;")
    ),
)

/**
 * The app's own implementation of the media request interface, which native code calls to start a request.
 * Matched by its superclass, because the parameters of the query are compared by name only there.
 */
private object NetFetchStartFetchTaskParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "${MEDIA_INTERFACES}NetFetchTask;",
    parameters = listOf("${MEDIA_INTERFACES}HttpRequest;", "${MEDIA_INTERFACES}NetFetchCallbacks;"),
) {
    init {
        classMatcher {
            superClass { descriptor("${MEDIA_INTERFACES}NetFetch;") }
        }
    }
}

internal val netFetchStartFetchTaskMethod = findMethodDirect {
    NetFetchStartFetchTaskParentFingerprint.matchAll()
        // The native implementation forwards the request instead of starting it.
        .single { match ->
            match.method.declaredClass?.descriptor?.endsWith("\$CppProxy;") == false
        }
        .method
}

/**
 * Receives each chunk of a media response before native code reads it.
 */
internal object NetFetchResponseChunkFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/nio/ByteBuffer;"),
    custom = { declaredClass(NET_FETCH_CPP_PROXY) },
)

/**
 * The media request of the app, whose fields are obfuscated.
 */
internal object HttpRequestConstructorFingerprint : Fingerprint(
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    custom = { declaredClass(HTTP_REQUEST_CLASS_NAME) },
)

/**
 * Sets the name of the header, and then its value.
 */
internal object HttpHeaderConstructorFingerprint : Fingerprint(
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_OBJECT, definingClass = "this", type = "Ljava/lang/String;"),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, definingClass = "this", type = "Ljava/lang/String;"),
    ),
    custom = { declaredClass(HTTP_HEADER_CLASS_NAME) },
)

/**
 * Fields of the player response that [CreateStreamingDataFingerprint] builds.
 *
 * The streaming data of the app is replaced with the streams of the spoofed client, so the fields
 * that hold the streaming data, the video details and the player config are needed.
 */
internal val streamingDataVideoDetailsField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches[5].instruction.fieldRef!!
}

internal val streamingDataSetterField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal val streamingDataGetterField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches[0].instruction.fieldRef!!
}

internal val playerConfigSetterField = findFieldDirect {
    CreateStreamingDataFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/**
 * Fields of the data source of a video stream.
 */
internal val mediaDataSourceUrlField = findFieldDirect {
    BuildMediaDataSourceFingerprint.instructionMatches[0].instruction.fieldRef!!
}

internal val mediaDataSourceMethodField = findFieldDirect {
    BuildMediaDataSourceFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal val mediaDataSourceBodyField = findFieldDirect {
    BuildMediaDataSourceFingerprint.instructionMatches[2].instruction.fieldRef!!
}

/**
 * Fields of the builder of every network request, which are changed to request plain streams
 * as GET requests without a body.
 */
internal val cronetUrlField = findFieldDirect {
    val match = CronetUrlRequestBuildFingerprint.match()
    match.method.declaredClass!!.fields.first { it.type.descriptor == "Ljava/lang/String;" }
}

internal val cronetUploadField = findFieldDirect {
    val match = CronetUrlRequestBuildFingerprint.match()
    match.method.declaredClass!!.fields.first {
        it.type.descriptor == "Lorg/chromium/net/UploadDataProvider;"
    }
}

internal val cronetHttpMethodField = findFieldDirect {
    CronetUrlRequestSetHttpMethodFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/**
 * The value of the media fetch enum that turns SABR off, written by the constructor of the enum.
 */
internal val sabrDisabledValueField = findFieldDirect {
    val match = MediaFetchEnumConstructorFingerprint.run()
    val enumClass = match.declaredClass!!.descriptor
    val instructions = match.instructions
    val sabrString = instructions.indexOfFirst {
        Opcode.fromInt(it.opcode) == Opcode.CONST_STRING &&
            it.string == "DISABLED_BY_SABR_STREAMING_URI"
    }
    check(sabrString >= 0) { "The SABR value of the media fetch enum is gone" }
    (sabrString until instructions.size).firstNotNullOf { index ->
        val instruction = instructions[index]
        if (Opcode.fromInt(instruction.opcode) == Opcode.SPUT_OBJECT) {
            instruction.fieldRef?.takeIf { it.type.descriptor == enumClass }
        } else {
            null
        }
    }
}

/**
 * The method that decides if the player fetches the streams of a video with SABR.
 */
internal val mediaFetchDecisionMethod = findMethodDirect {
    val enumClass = MediaFetchEnumConstructorFingerprint.run().declaredClass!!.descriptor
    findMethod {
        matcher { returnType = enumClass.removePrefix("L").removeSuffix(";").replace('/', '.') }
    }.single { method ->
        // The player config and the streaming data of the video.
        method.paramTypes.size == 2 &&
            method.instructions.any { Opcode.fromInt(it.opcode) == Opcode.SGET_OBJECT } &&
            method.instructions.any { Opcode.fromInt(it.opcode) == Opcode.RETURN_OBJECT }
    }
}
