/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.video.information

import io.github.kawinnext.morphe.youtube.video.information.PlayerControllerSetTimeReferenceFingerprint
import io.github.kawinnext.patch
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Listeners for the track that plays and for its playback position. */
val musicVideoIdHooks = mutableListOf<(String) -> Unit>()
val musicVideoTimeHooks = mutableListOf<(Long) -> Unit>()

// The patch set adds a seekTo method to the player class so the extension can seek. A runtime
// hook cannot add a method, so the seek method of the app is kept and called instead.
private var musicPlayer: Any? = null
private var musicSeekMethod: Method? = null
private var musicSeekSource: Any? = null

/**
 * Seeks the music player, or returns false if the player or a seek source is not known yet.
 */
fun musicSeekTo(positionMillis: Long): Boolean {
    val player = musicPlayer ?: return false
    val method = musicSeekMethod ?: return false
    val source = musicSeekSource ?: return false
    return runCatching { method.invoke(player, positionMillis, source) as Boolean }.getOrDefault(false)
}

/**
 * Index of the second long parameter of the controller method, which carries the playback
 * position. The first long is the sentinel of the method.
 */
private fun positionArgumentIndex(types: Array<Class<*>>): Int {
    var longs = 0
    types.forEachIndexed { index, type ->
        if (type == Long::class.javaPrimitiveType) {
            longs++
            if (longs == 2) return index
        }
    }
    return -1
}

/** A seek source of the app to seek with, such as the one the app uses when it has none. */
private fun seekSourceOf(enumType: Class<*>): Any? {
    val constants = enumType.enumConstants ?: return null
    return constants.firstOrNull { (it as Enum<*>).name.contains("UNKNOWN", ignoreCase = true) }
        ?: constants.firstOrNull()
}

val musicVideoInformationPatch = patch(
    description = "Hooks the video id and the playback position of YouTube Music."
) {
    // The player response and the video id of the track that is playing.
    VideoIdFingerprint.hookMethod {
        after {
            val videoId = it.args[1] as? String ?: return@after
            musicVideoIdHooks.forEach { hook -> hook(videoId) }
        }
    }

    // The playback position is the second long parameter of the controller update.
    PlayerControllerSetTimeReferenceFingerprint.hookMethod {
        before {
            val method = it.method as? Method ?: return@before
            val positionIndex = positionArgumentIndex(method.parameterTypes)
            if (positionIndex < 0) return@before
            val position = it.args[positionIndex] as? Long ?: return@before
            musicVideoTimeHooks.forEach { hook -> hook(position) }
        }
    }

    // The seek method is called with the position and the source of the seek.
    VideoEndFingerprint.hookMethod {
        before {
            val method = it.method as? Method ?: return@before
            val sourceType = method.parameterTypes.getOrNull(1) ?: return@before
            if (!sourceType.isEnum) return@before

            musicPlayer = it.thisObject
            musicSeekMethod = method
            if (musicSeekSource == null) {
                musicSeekSource = seekSourceOf(sourceType)
            }
        }
    }
}

/** True when the seek method of the player is known, so the A-B loop can jump back to A. */
fun isMusicSeekAvailable(): Boolean = musicPlayer != null && musicSeekMethod != null &&
        musicSeekSource != null && !Modifier.isAbstract(musicSeekMethod!!.modifiers)
