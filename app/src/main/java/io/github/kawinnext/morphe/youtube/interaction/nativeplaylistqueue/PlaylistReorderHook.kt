/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.MethodHookParam
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.youtube.layout.originaltitles.PlaylistPanelVideoBindFingerprint
import java.lang.reflect.Method

// Upstream adds the ReorderItems interface to the adapter of the playlist and injects the two
// notifications of a drag after the check that rejects a callback of another holder. A module
// cannot add an interface to a class of the app, so the adapter is read through the proxy below,
// and the two callbacks are hooked with that check repeated, which leaves the gesture, its
// animation and its accessibility untouched.

/** The video model of one row, read through the interface the adapter's edit events use. */
private val playlistRowVideo = findMethodDirect {
    val video = PlaylistPanelVideoBindFingerprint.matchAll()
        .map { it.instructionMatches[2].instruction.fieldRef!!.declaredClass!!.descriptor }
        .distinct().single()
    playlistEditEventsFingerprint(video).match()
        .instructionMatches.single().instruction.methodRef!!
}

/** The start of a native drag, which notifies only the holder the drag belongs to. */
private val playlistDragStart = findMethodDirect { PlaylistDragStartFingerprint.match().method }

/** The end of that drag, with the two positions it moved between. */
private val playlistDragEnd = findMethodDirect { PlaylistDragEndFingerprint.match().method }

/** The unbind of a holder, which ends the drag it may still hold. */
private val playlistDragUnbind = findMethodDirect { PlaylistDragUnbindFingerprint.match().method }

/**
 * The list a native drag moves, which stands in for the ReorderItems interface upstream adds to the
 * class of that list: one bridge reads its size, the other reads the video model of one row.
 *
 * A row of another adapter reports nothing, which is the check-cast of the bridge method.
 */
private class ReorderItemsProxy(
    private val items: List<*>,
    private val rowVideo: Method
) : NativePlaylistQueuePatch.ReorderItems {
    override fun patch_getSize(): Int = items.size

    override fun patch_getQueueRow(index: Int): Any? {
        val row = items.getOrNull(index) ?: return null
        if (!rowVideo.declaringClass.isInstance(row)) return null
        val video = rowVideo.call(row) ?: return null
        return queueRowProxy(video)
    }
}

/**
 * Lets the extension follow the native drag of a row of the local queue.
 *
 * Upstream injects the two notifications in the guarded branch of the callbacks, before the native
 * work they guard continues. Here the same callbacks are hooked, and a notification of another
 * holder is dropped by comparing the receiver of the callback with the holder it received, which is
 * the comparison the native branch makes. The notifications are read after the callback, because
 * the extension identifies the destination row of a drop in the list the drag has already moved.
 *
 * @param videoType The video model the row hook bound, whose rows are reordered.
 */
internal fun PatchExecutor.installPlaylistReordering(videoType: Class<*>) {
    val start = ::playlistDragStart.method
    val end = ::playlistDragEnd.method
    check(start.parameterTypes.take(2) == end.parameterTypes.take(2)) {
        "Native playlist queue: drag callbacks disagree about their holder/adapter types"
    }
    check(::playlistRowVideo.method.returnType == videoType) {
        "Native playlist queue: the row video of the adapter is not the bound video model"
    }

    val rowVideo = ::playlistRowVideo.method
    val adapters = ProxyCache { list: Any -> ReorderItemsProxy(list as List<*>, rowVideo) }

    /** The moved list of one callback, or null when it notifies the drag of another holder. */
    fun dragItems(param: MethodHookParam): NativePlaylistQueuePatch.ReorderItems? {
        val holder = param.thisObject ?: return null
        if (holder !== param.args.getOrNull(0)) return null
        return (param.args.getOrNull(1) as? List<*>)?.let(adapters::get)
    }

    start.hookMethod {
        after { param ->
            val items = dragItems(param) ?: return@after
            val index = param.args.getOrNull(2) as? Int ?: return@after
            queueAction("onReorderStarted") {
                NativePlaylistQueuePatch.onReorderStarted(param.args.firstOrNull(), items, index)
            }
        }
    }

    end.hookMethod {
        after { param ->
            val items = dragItems(param) ?: return@after
            val from = param.args.getOrNull(2) as? Int ?: return@after
            val to = param.args.getOrNull(3) as? Int ?: return@after
            queueAction("onReorderEnded") {
                NativePlaylistQueuePatch.onReorderEnded(param.args.firstOrNull(), items, from, to)
            }
        }
    }

    ::playlistDragUnbind.method.hookMethod {
        before { param ->
            queueAction("onReorderHolderUnbound") {
                NativePlaylistQueuePatch.onReorderHolderUnbound(param.thisObject)
            }
        }
    }
}
