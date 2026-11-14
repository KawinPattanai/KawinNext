/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Ported from app/morphe/patches/youtube/misc/spoof/SpoofVideoStreamsPatch.kt and
 * app/morphe/patches/shared/misc/spoof/SpoofVideoStreamsPatch.kt of morphe-patches.
 */

package io.github.kawinnext.morphe.youtube.misc.spoof

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch as SpoofVideoStreams
import app.morphe.extension.shared.spoof.requests.StreamingDataRequest
import app.morphe.extension.youtube.patches.spoof.SpoofVideoStreamsPatch as YouTubeSpoofVideoStreams
import app.morphe.extension.youtube.settings.preference.SpoofVideoStreamsMwebSignInPreference
import app.morphe.extension.youtube.settings.preference.SpoofVideoStreamsSideEffectsPreference
import de.robv.android.xposed.XC_MethodReplacement
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.shared.misc.debugging.experimentFlagUtilFingerprint
import io.github.kawinnext.morphe.shared.misc.request.BuildRequestFingerprint
import io.github.kawinnext.morphe.shared.misc.request.requestUrlMethod
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE
import io.github.kawinnext.patch
import org.luckypray.dexkit.wrap.DexMethod
import java.nio.ByteBuffer
import java.util.ArrayList

/**
 * Feature flags that are turned off while the streams are spoofed, because they make the player
 * wait for a stream it cannot play.
 *
 * Upstream rewrites the read of each flag in the method it is read in. The flags are read through
 * the accessors of the experiment flag utility class, so the read is overridden there instead.
 */
/**
 * Name the extension uses for the client of a video whose streams were not spoofed.
 */
private const val NO_SPOOFED_CLIENT = "Unknown"

/**
 * @return If the streams of another client were given to the player.
 *
 * The requests the app makes for its own streams must be left as they are. The client the user chose
 * can also fail to fetch its streams, such as when the JavaScript solver of the client is not usable,
 * and then the app plays its own streams and playback must still work.
 */
private fun areStreamsSpoofed(): Boolean = try {
    SpoofVideoStreams.isSpoofingEnabled() &&
        StreamingDataRequest.getLastSpoofedClientName() != NO_SPOOFED_CLIENT
} catch (ex: Exception) {
    false
}

private fun featureFlagOverrides(): Map<Long, (Boolean) -> Boolean> = mapOf(
    // Fix the current time of a live stream of an Apple client.
    45355374L to SpoofVideoStreams::fixHLSCurrentTime,
    // Feature flags that interfere with the spoofed streams.
    45640404L to SpoofVideoStreams::useMediaSessionFeatureFlag,
    45665455L to SpoofVideoStreams::usePlaybackStartFeatureFlag,
    45638126L to SpoofVideoStreams::useReelItemWatchResponseFeatureFlag,
)

val spoofVideoStreamsPatch = patch(
    name = "Spoof video streams",
    description = "Adds options to spoof the client video streams to fix playback."
) {
    PreferenceScreen.MISC.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_spoof_video_streams_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference(
                    key = "morphe_spoof_video_streams",
                    titleKey = "morphe_spoof_video_streams_screen_title",
                    summary = true
                ),
                ListPreference("morphe_spoof_video_streams_client_type"),
                NonInteractivePreference(
                    // Requires a key and title but the actual text is chosen at runtime.
                    key = "morphe_spoof_video_streams_about",
                    summaryKey = null,
                    tag = SpoofVideoStreamsSideEffectsPreference::class.java,
                ),
                NonInteractivePreference(
                    key = "morphe_spoof_video_streams_sign_in_mweb_about",
                    tag = SpoofVideoStreamsMwebSignInPreference::class.java,
                    selectable = true,
                ),
                SwitchPreference("morphe_spoof_video_streams_av1", summary = true),
                ListPreference("morphe_spoof_video_streams_player_js_variant"),
                SwitchPreference("morphe_spoof_video_streams_stats_for_nerds"),
            )
        )
    )

    // The scripts of the JavaScript solver are raw resources of the app upstream, which the module
    // cannot add, so a raw resource the app does not have is read from the assets of the module.
    ResourceUtils::class.java
        .getDeclaredMethod("getRawResource", String::class.java)
        .hookMethod {
            after { param ->
                if (param.result == null) {
                    (param.args.getOrNull(0) as? String)?.let { name ->
                        param.result = readModuleAsset(name)
                    }
                }
            }
        }

    // The extension is not patched, so the flag it reads to know the patch is included is set here.
    // The hook is installed before the class is initialized, and the class initializes with it.
    SpoofVideoStreams::class.java
        .getDeclaredMethod("isPatchIncluded")
        .hookMethod(XC_MethodReplacement.returnConstant(true))

    // The client to request the streams with is chosen on every start of the app.
    DexMethod("$YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE->onCreate(Landroid/os/Bundle;)V")
        .toMethod()
        .hookMethod {
            before {
                try {
                    YouTubeSpoofVideoStreams.setClientOrderToUse()
                } catch (ex: Exception) {
                    Logger.printException({ "Could not set the client to spoof" }, ex)
                }
            }
        }

    // region Fetch the streams of the video while the app requests the video itself.
    run {
        val builder = BuildRequestFingerprint.dexMethod
        val bodyIndex = builder.paramTypeNames.indexOfFirst { it == "[B" || it == "byte[]" }
        val mapIndex = builder.paramTypeNames.indexOf("java.util.Map")
        if (bodyIndex < 0 || mapIndex < 0) {
            Logger.printException(
                { "The request builder has no body, the streams of the videos are not fetched" },
                NoSuchFieldException(builder.paramTypeNames.toString())
            )
        } else {
            val urlMethod = ::requestUrlMethod.method
            builder.hookMethod {
                before { param ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val headers = param.args[mapIndex] as? Map<String, String>
                        SpoofVideoStreams.fetchStreams(
                            urlMethod(param.args[0]) as? String,
                            headers.orEmpty(),
                            param.args[bodyIndex] as? ByteArray,
                        )
                    } catch (ex: Exception) {
                        Logger.printException({ "fetchStreams failure" }, ex)
                    }
                }
            }
        }
    }
    // endregion

    // The app loads the video again when a media response asks for the player response again.
    ReloadPlayerResponseFingerprint.hookMethod {
        after { SpoofVideoStreams.onReloadPlayerResponse() }
    }

    // region Name the client in the media requests of the clients that use SABR, and follow the
    // website session of the Mobile Web client.
    run {
        val constructor = HttpRequestConstructorFingerprint.constructor
        val requestClass = HttpRequestConstructorFingerprint.declaredClass
        val methodField = requestClass.fieldOfTypeName(
            "com.google.android.libraries.youtube.media.interfaces.HttpMethod"
        )
        val flagField = requestClass.fieldOfType(Boolean::class.javaPrimitiveType!!)
        val headersField = requestClass.fieldOfType(ArrayList::class.java)
        if (methodField == null || flagField == null || headersField == null) {
            Logger.printException(
                { "The media request has no method, flag or headers, its requests are left as they are" },
                NoSuchFieldException(requestClass.name)
            )
        } else {
            val factory = MediaRequestFactory(requestClass) { source, url, headers, body ->
                constructor.newInstance(
                    url,
                    headers ?: readField(source, headersField),
                    body,
                    readField(source, methodField),
                    readField(source, flagField),
                )
            }

            ::netFetchStartFetchTaskMethod.hookMethod {
                before { param ->
                    try {
                        val request = param.args.getOrNull(0) ?: return@before
                        val proxy = factory.wrap(request)
                        val replacement =
                            SpoofVideoStreams.onMediaRequest(proxy, param.args.getOrNull(1))
                        if (replacement !== proxy) {
                            param.args[0] = factory.unwrap(replacement)
                        }
                    } catch (ex: Exception) {
                        Logger.printException({ "onMediaRequest failure" }, ex)
                    }
                }
            }
        }

        NetFetchResponseChunkFingerprint.hookMethod {
            before { param ->
                try {
                    SpoofVideoStreams.onMediaResponseChunk(
                        param.thisObject,
                        param.args.getOrNull(0) as? ByteBuffer,
                    )
                } catch (ex: Exception) {
                    Logger.printException({ "onMediaResponseChunk failure" }, ex)
                }
            }
        }
    }
    // endregion

    // region Replace the streaming data and the player config with the ones of the client.
    run {
        val streamingData = StreamingDataFields(
            videoDetails = ::streamingDataVideoDetailsField.field,
            videoId = ::streamingDataVideoDetailsField.type
                .getDeclaredField("c").apply { isAccessible = true },
            setStreamingData = ::streamingDataSetterField.field,
            getStreamingData = ::streamingDataGetterField.field,
            setPlayerConfig = ::playerConfigSetterField.field,
            playerConfigClass = ::playerConfigSetterField.type,
        )

        CreateStreamingDataFingerprint.hookMethod {
            after { param -> streamingData.apply(param.thisObject, param.args.getOrNull(0)) }
        }
    }
    // endregion

    // Remove the body of the playback requests, which is made for the streams of the app.
    run {
        val uriField = ::mediaDataSourceUrlField.field
        val methodField = ::mediaDataSourceMethodField.field
        val bodyField = ::mediaDataSourceBodyField.field

        BuildMediaDataSourceFingerprint.hookMethod {
            after { param ->
                try {
                    if (!areStreamsSpoofed()) return@after
                    val dataSource = param.thisObject
                    val uri = readField(dataSource, uriField) ?: return@after
                    val method = readField(dataSource, methodField) as? Int ?: return@after
                    val body = readField(dataSource, bodyField) as? ByteArray ?: return@after
                    writeField(
                        dataSource,
                        bodyField,
                        SpoofVideoStreams.removeVideoPlaybackPostBody(
                            uri as android.net.Uri, method, body
                        ),
                    )
                } catch (ex: Exception) {
                    Logger.printException({ "removeVideoPlaybackPostBody failure" }, ex)
                }
            }
        }
    }

    // Request the plain streams of the other clients as GET requests, which the server accepts.
    run {
        val urlField = ::cronetUrlField.field
        val httpMethodField = ::cronetHttpMethodField.field
        val uploadField = ::cronetUploadField.field

        CronetUrlRequestBuildFingerprint.hookMethod {
            before { param ->
                try {
                    val builder = param.thisObject
                    if (SpoofVideoStreams.isPlainStreamRequest(readField(builder, urlField) as? String)) {
                        writeField(builder, httpMethodField, "GET")
                        writeField(builder, uploadField, null)
                    }
                } catch (ex: Exception) {
                    Logger.printException({ "isPlainStreamRequest failure" }, ex)
                }
            }
        }
    }

    // Show the spoofed client in the stats for nerds panel.
    ::nerdsStatsVideoFormatBuilderMethod.hookMethod {
        after { param ->
            param.result = SpoofVideoStreams.appendSpoofedClient(param.result as? String)
        }
    }

    // Turn off the feature flags that interfere with the spoofed streams.
    run {
        val overrides = featureFlagOverrides()
        ::experimentFlagUtilFingerprint.clazz.declaredMethods
            .filter { method ->
                method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.any { it == Long::class.javaPrimitiveType }
            }
            .forEach { method ->
                method.hookMethod {
                    after { param ->
                        val id = param.args.firstOrNull { it is Long } as? Long ?: return@after
                        val override = overrides[id] ?: return@after
                        param.result = override(param.result as Boolean)
                    }
                }
            }
    }

    // Turn SABR off for the clients whose streams are not SABR streams.
    run {
        val sabrValue = ::sabrDisabledValueField.field
        ::mediaFetchDecisionMethod.hookMethod {
            after { param ->
                try {
                    if (areStreamsSpoofed() && SpoofVideoStreams.disableSABR()) {
                        param.result = sabrValue.get(null)
                    }
                } catch (ex: Exception) {
                    Logger.printException({ "disableSABR failure" }, ex)
                }
            }
        }
    }
}
