package io.github.kawinnext

import io.github.kawinnext.hoodles.morphe.alltrails.AllTrailsPatches
import io.github.kawinnext.morphe.music.YTMusicPatches
import io.github.kawinnext.morphe.reddit.RedditPatches
import io.github.kawinnext.morphe.youtube.YouTubePatches
import io.github.kawinnext.revanced.googlephotos.GooglePhotosPatches
import io.github.kawinnext.revanced.meta.MetaPatches
import io.github.kawinnext.revanced.photomath.PhotomathPatches
import io.github.kawinnext.revanced.strava.StravaPatches

class AppPatchInfo(val appName: String, val packageName: String, val patches: Array<Patch>)

val appPatchConfigurations = listOf(
    AppPatchInfo("YouTube", "com.google.android.youtube", YouTubePatches),
    AppPatchInfo("YT Music", "com.google.android.apps.youtube.music", YTMusicPatches),
    AppPatchInfo("Reddit", "com.reddit.frontpage", RedditPatches),
    AppPatchInfo("Google Photos", "com.google.android.apps.photos", GooglePhotosPatches),
    AppPatchInfo("Photomath", "com.microblink.photomath", PhotomathPatches),
    AppPatchInfo("Instagram", "com.instagram.android", MetaPatches),
    AppPatchInfo("Threads", "com.instagram.barcelona", MetaPatches),
    AppPatchInfo("Strava", "com.strava", StravaPatches),
    AppPatchInfo("AllTrails", "com.alltrails.alltrails", AllTrailsPatches),
)

val patchesByPackage = appPatchConfigurations.associate { it.packageName to it.patches }
