package io.github.kawinnext.morphe.music.shared

import io.github.kawinnext.morphe.Fingerprint

internal const val YOUTUBE_MUSIC_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/google/android/apps/youtube/music/activities/MusicActivity;"

internal object MusicActivityOnCreateFingerprint : Fingerprint(
    definingClass = YOUTUBE_MUSIC_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)
