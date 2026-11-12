/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.utils.FlyoutUtils
import com.google.protobuf.MessageLite
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

// Upstream keeps the ownership of one row and of its panel in a field that it adds to the class of
// the app object, and the extension reads that field through the QueueRow and QueuePanel
// interfaces. A field cannot be added at runtime, so the mark is held here against the app object
// itself, the way the other state that upstream stores in a class of the app is held: weakly, so
// that the mark lives exactly as long as the object it belongs to. The row hook marks the rows and
// the panel that it publishes, and the proxies below answer the two interfaces for them.

/** The rows of the local queue, which upstream marks with its `patch_queueOwned` field. */
private val ownedRows: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

/** The panels of the local queue, which upstream marks with its `patch_queueOwned` field. */
private val ownedPanels: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

internal fun isQueueRowOwned(row: Any?): Boolean =
    row != null && synchronized(ownedRows) { ownedRows.contains(row) }

/** Marks one row that a published panel of the local queue carries. */
internal fun markQueueRowOwned(row: Any?) {
    if (row == null) return
    synchronized(ownedRows) { ownedRows.add(row) }
}

internal fun isQueuePanelOwned(panel: Any?): Boolean =
    panel != null && synchronized(ownedPanels) { ownedPanels.contains(panel) }

/** Marks the panel that the local queue published. */
internal fun markQueuePanelOwned(panel: Any?) {
    if (panel == null) return
    synchronized(ownedPanels) { ownedPanels.add(panel) }
}

/**
 * The video ID field of a row model, which the row hook resolves. Upstream adds the bridge that reads
 * it to the class of that model; here the proxy of a row reads the same field.
 */
internal var queueRowVideoId: Field? = null

/**
 * One row of the native playlist, which stands in for the QueueRow and the video ID interfaces
 * upstream adds to the class of a row: the bridges of those interfaces read the mark of this one row
 * and its video ID.
 *
 * The row itself is kept, because the extension identifies a row of the published panel by the app
 * object while the proxy is what it receives, and the comparison has to be made on that object.
 */
internal class QueueRowProxy(val appRow: Any) : NativePlaylistQueuePatch.QueueRow,
    FlyoutUtils.FlyoutMenuVideoIdInterface {
    override fun patch_isQueueOwned(): Boolean = isQueueRowOwned(appRow)

    override fun patch_getVideoId(): String? =
        queueRowVideoId?.let { bridgeField(it, appRow) as? String }
}

/**
 * One panel of the native playlist, which stands in for the QueuePanel interface.
 *
 * The extension also reads the panel of the playing playlist as a message, which upstream can do
 * because the bridge returns the native protobuf object itself. The message is therefore parsed from
 * the bytes of the panel with the module's own schema.
 */
internal class QueuePanelProxy(val appPanel: Any) : NativePlaylistQueuePatch.QueuePanel,
    MessageLite by (appMessageBytes(appPanel)?.let { PlaylistQueueRenderer.Panel.parseFrom(it) }
        ?: PlaylistQueueRenderer.Panel.getDefaultInstance()) {
    override fun patch_isQueueOwned(): Boolean = isQueuePanelOwned(appPanel)
}

private val rowProxies = ProxyCache { row: Any -> QueueRowProxy(row) }

private val panelProxies = ProxyCache { panel: Any -> QueuePanelProxy(panel) }

/** The proxy of one row, which is the same object at every read of that row. */
internal fun queueRowProxy(row: Any): QueueRowProxy = rowProxies.get(row)

/** The proxy of one panel, which is the same object at every read of that panel. */
internal fun queuePanelProxy(panel: Any): QueuePanelProxy = panelProxies.get(panel)

/**
 * The proxy of the object that a menu command belongs to, or null when the object is not one of
 * ours.
 *
 * The two interfaces stay apart, because the extension reads the header of the queue through
 * QueuePanel and every other command through QueueRow: an owned row must not answer as a header,
 * which would consume the command of that row as if it were the header of the queue.
 */
internal fun queueOwnerProxy(owner: Any?): Any? = when {
    owner == null -> null
    isQueuePanelOwned(owner) -> queuePanelProxy(owner)
    isQueueRowOwned(owner) -> queueRowProxy(owner)
    else -> null
}
