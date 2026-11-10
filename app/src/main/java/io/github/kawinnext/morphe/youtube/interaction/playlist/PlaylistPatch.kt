/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4324
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.playlist

import android.view.MotionEvent
import android.view.View
import app.morphe.extension.youtube.patches.PlaylistPatch
import com.facebook.litho.ComponentHost
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.interaction.channelsearch.SearchResultsFragmentOnCreateViewFingerprint
import io.github.kawinnext.morphe.youtube.layout.startpage.BrowseIdFingerprint
import io.github.kawinnext.morphe.youtube.layout.startpage.browserIdProtoBuilder
import io.github.kawinnext.morphe.youtube.misc.feedcards.FeedCardComponentHostLayoutFingerprint
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

val playlistPatch = patch(
    name = "Playlist improvements",
    description = "Adds options to widen playlist drag handles, reorder videos by long press, " +
            "and hide removal confirmations."
) {
    dependsOn(PlayerTypeHook)

    PreferenceScreen.FEED.addPreferences(
        SwitchPreference("morphe_playlist_wide_drag_handles", summary = true),
        SwitchPreference("morphe_playlist_hide_removal_snackbar", summary = true),
        SwitchPreference("morphe_playlist_long_press_drag", summary = true)
    )

    // A playlist page browses by its playlist id, which the extension keeps to know
    // that a playlist is shown.
    BrowseIdFingerprint.hookMethod(
        scopedHook(::browserIdProtoBuilder.member) {
            before { PlaylistPatch.setBrowseId(it.args[0] as? String) }
        }
    )

    // The search feed has no browse page, so the playlist id of the page before it is dropped.
    SearchResultsFragmentOnCreateViewFingerprint.hookMethod {
        before { PlaylistPatch.clearBrowseId() }
    }

    // The drag handle of a list row is the view its touch listener is built for.
    DragHandleTouchListenerFingerprint.hookMethod {
        before { PlaylistPatch.onDragHandleCreated(it.args[0] as? View ?: return@before) }
    }

    // Litho sets the size of the drag handle of its rows, so the row host forwards
    // the touches of a wider area to the handle.
    ComponentHostInterceptTouchFingerprint.hookMethod {
        before {
            val host = it.thisObject as ComponentHost
            val event = it.args[0] as MotionEvent
            if (PlaylistPatch.onComponentHostInterceptTouch(host, event)) it.result = true
        }
    }

    ComponentHostTouchFingerprint.hookMethod {
        before {
            val host = it.thisObject as ComponentHost
            val event = it.args[0] as MotionEvent
            if (PlaylistPatch.onComponentHostTouch(host, event)) it.result = true
        }
    }

    // The rows of a playlist are found when their host is laid out.
    FeedCardComponentHostLayoutFingerprint.hookMethod {
        after { PlaylistPatch.onComponentHostLayout(it.thisObject as ComponentHost) }
    }
}
