/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.NextResponseOuterClass
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import com.google.protobuf.InvalidProtocolBufferException
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.youtube.shared.WatchNextResponseParserFingerprint

// Upstream injects one call right after the cast of the watch next response inside the method that
// parses it, and passes the cast object to the extension. The method is the shared response parser
// that 'Hide related videos' and 'Hide video action buttons' also use, so upstream clears the
// fingerprint match first. This hook reads the same response: the parser receives the response as
// its only argument, and the object the cast produces is the single column results of that same
// response, which the module's own schema names
// NextResponse.primaryContents.singleColumnWatchNextResults. Reading the response from the argument
// keeps every view filter untouched, and a response that does not carry those fields, such as the
// other response types the same method accepts, is skipped.

/**
 * Observes the parsed watch next response before the view filters read it, including the responses
 * that are prefetched for a video that is not watched yet.
 *
 * The parser may return before it reaches the cast, when its own mode says the response was read
 * already. The extension drops a response it has seen before, so reporting such a response as well
 * only repeats what it already holds.
 */
private fun captureWatchNextResponse(response: Any?) {
    queueAction("onWatchNextResponse") {
        val next = nextResponse(response) ?: return@queueAction
        // The parser accepts several response types; only this one has the single column results.
        if (!next.hasPrimaryContents()) return@queueAction
        val contents = next.primaryContents
        if (!contents.hasSingleColumnWatchNextResults()) return@queueAction

        NativePlaylistQueuePatch.onWatchNextResponse(contents.singleColumnWatchNextResults)
    }
}

/** The response in the module's schema, or null when the bytes are of another response type. */
private fun nextResponse(response: Any?): NextResponseOuterClass.NextResponse? =
    try {
        bridgeMessage(response) { NextResponseOuterClass.NextResponse.parseFrom(it) }
    } catch (_: InvalidProtocolBufferException) {
        // Another response type of the shared parser, which does not carry these fields at all.
        null
    }

/**
 * Gives the extension the rating actions that the watch next response carries.
 *
 * Upstream observes the response inside the parser; here the parser method itself is hooked, so the
 * response is seen at the same step of the native parse and before any panel of the app reads it.
 */
internal fun PatchExecutor.installRatingCapture() {
    WatchNextResponseParserFingerprint.hookMethod {
        before { param -> captureWatchNextResponse(param.args.firstOrNull()) }
    }
}
