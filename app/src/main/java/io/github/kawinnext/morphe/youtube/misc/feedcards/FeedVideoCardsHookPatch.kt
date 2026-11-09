/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3758
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.misc.feedcards

import android.widget.TextView
import app.morphe.extension.youtube.patches.utils.FeedVideoCardsPatch
import com.facebook.litho.ComponentHost
import io.github.kawinnext.morphe.shared.misc.proto.hookElement
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.misc.proto.elementProtoParserHookPatch
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.videoid.VideoId
import io.github.kawinnext.morphe.youtube.video.videoid.videoIdHooks
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

/**
 * Finds the full width video cards of the feeds and the video of each card,
 * for the patches that draw on the thumbnails of feed videos.
 */
val feedVideoCardsHookPatch = patch(
    description = "Hooks the video cards of the feeds and finds their video ids."
) {
    dependsOn(
        elementProtoParserHookPatch,
        // What is drawn on a thumbnail is hidden while its video plays in the feed.
        PlayerTypeHook,
        VideoInformationPatch,
        VideoId,
    )

    // The elements of the videos include the thumbnail url with the video id,
    // and the texts of the video, which are matched to the texts of the laid out cards.
    hookElement { bytes -> bytes?.let(FeedVideoCardsPatch::onElementLoaded) }

    // The next video that plays in the feed redraws the cards.
    videoIdHooks.add(FeedVideoCardsPatch::onVideoLoaded)

    // Feed videos are Litho components and have no views of their own,
    // so the root host of each video is found after it is laid out.
    FeedCardComponentHostLayoutFingerprint.hookMethod {
        after { FeedVideoCardsPatch.onComponentHostLayout(it.thisObject as ComponentHost) }
    }

    // Compact videos are not drawn by Litho, such as the videos of the watch history.
    // The title of a compact video is set on the view of the title, and the video id is kept
    // by the renderer the card is bound with.
    val rendererClass = ::compactVideoRendererClass.clazz
    val videoIdField = ::compactVideoIdField.field.apply { isAccessible = true }
    val titleViewField = ::compactVideoTitleViewField.field.apply { isAccessible = true }
    CompactVideoBindFingerprint.hookMethod(
        scopedHook(::compactVideoTitleSetterMethod.member) {
            before { param ->
                val titleView = titleViewField.get(param.thisObject) as? TextView ?: return@before
                val videoId = outerParam.args.firstOrNull { rendererClass.isInstance(it) }
                    ?.let { videoIdField.get(it) as? String }
                FeedVideoCardsPatch.onCompactVideoBound(titleView, videoId)
            }
        }
    )
}
