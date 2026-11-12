/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.Match
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.youtube.layout.originaltitles.PlaylistPanelVideoBindFingerprint
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import java.lang.reflect.Field
import java.util.WeakHashMap

// Upstream observes the native row commands without replacing their listener or routing: it adds the
// video ID and the ownership of a queue row to the video model of a row, the ownership to its panel,
// the bound endpoint to its click listener, and injects one call in the overflow handler, one in the
// click, and one after the bind and the unbind of an endpoint. A module cannot add a member to a class
// of the app, so the interfaces are the proxies of this folder and the three injections are the hooks
// below, at the native points the smali calls sit.

/** The tag of the app's endpoint context, which carries the row of a menu command. */
private const val ENDPOINT_TAG = "com.google.android.libraries.youtube.innertube.endpoint.tag"

/**
 * The video ID field of a row, which every native binder of the panel shares.
 *
 * Original titles also modifies these binders, so the field is read from fresh matches, including all
 * native layouts, and the binders must agree on it.
 */
private fun DexKitBridge.rowVideoId(): FieldData {
    val fields = PlaylistPanelVideoBindFingerprint.matchAll()
        .map { it.instructionMatches[2].instruction.fieldRef!! }
        .distinct()
    check(fields.size == 1) {
        "Native playlist queue: row binders disagree on the native video ID field: $fields"
    }
    return fields.single()
}

/** The video model of one row. */
private val rowVideoType = findClassDirect { rowVideoId().declaredClass!! }

/** The video ID of one row, which the flyout of its menu reads. */
private val rowVideoIdField = findFieldDirect { rowVideoId() }

/** The overflow handler of a row, which creates the endpoint context before showing its menu. */
private fun DexKitBridge.rowMenu(): Match =
    playlistRowMenuOpenFingerprint(rowVideoType(this).descriptor).match()

/** The method that opens the menu of a row. */
private val rowMenuMethod = findMethodDirect { rowMenu().method }

/** The native creation of the context of that menu, which the extension observes. */
private val rowMenuContextMethod = findMethodDirect {
    rowMenu().instructionMatches[2].instruction.methodRef!!
}

/** The click listener of a row, whose veto predicate runs before the endpoint is decorated. */
private fun DexKitBridge.rowClick(): Match = PlaylistRowClickFingerprint.match()

/** The class of the click listener of a row. */
private val rowClickListenerType = findClassDirect { rowClick().method.declaredClass!! }

/** The click of a row. */
private val rowClickMethod = findMethodDirect { rowClick().method }

/** The native veto predicate of that click. */
private val rowClickVeto = findMethodDirect { rowClick().instructionMatches.first().instruction.methodRef!! }

/** The endpoint the click listener of a row is bound to. */
private val rowEndpointField = findFieldDirect { rowClick().instructionMatches[4].instruction.fieldRef!! }

/** The binder that reads the listener of a row and then the navigation endpoint of its video. */
private fun DexKitBridge.rowEndpoint(): Match = playlistRowEndpointFingerprint(
    rowClickListenerType(this).descriptor,
    rowVideoType(this).descriptor,
    rowEndpointField(this).type.descriptor
).match()

/** The listener field of a row, which its swipe background is refreshed through. */
private val rowListenerField = findFieldDirect {
    rowEndpoint().instructionMatches.first().instruction.fieldRef!!
}

/** The navigation endpoint of a row's video, which identifies that row. */
private val rowVideoEndpoint = findFieldDirect {
    rowEndpoint().instructionMatches.last().instruction.fieldRef!!
}

/** The video of each item in the panel, which is the row the extension reads. */
private val rowItemVideo = findFieldDirect {
    playlistItemFactoryFingerprint(rowVideoType(this).descriptor).match()
        .instructionMatches.single().instruction.fieldRef!!
}

/** The contents of the panel, which holds the items of the playlist. */
private val rowContents = findFieldDirect {
    val factory = playlistItemFactoryFingerprint(rowVideoType(this).descriptor).match().method
    val panel = PlaylistModelDescriptionFingerprint.match().method
        .findFieldFromToString(", playlistPanel=")
    playlistContentsFingerprint(panel.type.descriptor, factory).match()
        .instructionMatches.first().instruction.fieldRef!!
}

/** The bind of a row's endpoint to its listener. */
private val rowEndpointBind = findMethodDirect {
    playlistEndpointBindFingerprint(rowEndpointField(this)).match().method
}

/** The unbind of that endpoint, which clears the identity of a recycled row. */
private val rowEndpointUnbind = findMethodDirect {
    playlistEndpointUnbindFingerprint(rowEndpointField(this)).match().method
}

/** The endpoint each row listener was bound to, which logging replaces on the listener itself. */
private val rowEndpoints: MutableMap<Any, Any?> = WeakHashMap()

/** The click listener of a row, whose bound endpoint the extension reads when the row is clicked. */
private class BoundRowClick(private val listener: Any) : NativePlaylistQueuePatch.RowClick {
    override fun patch_getQueueEndpoint(): Any? = synchronized(rowEndpoints) { rowEndpoints[listener] }
}

private val rowClicks = ProxyCache { listener: Any -> BoundRowClick(listener) }

/**
 * The rows of the panel of one controller, which stand in for the two bridge methods upstream adds to
 * the controller: the position of a row that a menu command or a click belongs to, and the ownership
 * of every row of a panel the extension published.
 */
internal class PlaylistRowLookup(
    private val modelField: Field,
    private val panelField: Field,
    private val contents: Field,
    private val video: Field,
    private val endpoint: Field
) {
    /** The position of [target] among the rows of the panel, or -1 when it is none of them. */
    fun findQueueRow(controller: Any?, target: Any?): Int {
        if (controller == null || target == null) return -1
        val panel = modelField.read(controller)?.let { panelField.read(it) } ?: return -1
        val rows = contents.read(panel) as? List<*> ?: return -1
        rows.forEachIndexed { index, item ->
            val rowVideo = item?.let { video.read(it) } ?: return@forEachIndexed
            if (rowVideo === target || endpoint.read(rowVideo) === target) return index
        }
        return -1
    }

    /** Marks the panel and every row it holds as a row of the local queue. */
    fun markQueueRows(controller: Any?) {
        val model = controller?.let { modelField.read(it) } ?: return
        val panel = panelField.read(model) ?: return
        markQueuePanelOwned(panel)
        (contents.read(panel) as? List<*>).orEmpty().forEach { item ->
            markQueueRowOwned(item?.let { video.read(it) })
        }
    }
}

/** The rows of the panel of the installed hook, which the panel hook answers with. */
internal var playlistRowLookup: PlaylistRowLookup? = null

/** The context of one row menu, whose tag answers as the proxy of the row it belongs to. */
private fun rowMenuContext(context: Map<*, *>, row: Any): Map<Any?, Any?> {
    val tagged = HashMap<Any?, Any?>(context.size + 1)
    context.forEach { (key, value) -> tagged[key] = value }
    tagged[ENDPOINT_TAG] = queueRowProxy(row)
    return tagged
}

/**
 * Connects the rows of the native playlist to the queue of the extension.
 *
 * Upstream adds the video ID and the ownership bit to the class of a row and the bound endpoint to the
 * class of its listener, then injects one call in the overflow handler of a row, one in its click, and
 * one after the bind and the unbind of its endpoint. Here the row model is read through the proxies of
 * this folder, the endpoint is remembered here because logging decorates the one on the listener, and
 * the three calls are the hooks of the native methods they sit in.
 *
 * @param modelField The field of the playlist controller that holds the accepted model.
 * @param panelField The field of that model that holds the panel of the playlist.
 */
internal fun PatchExecutor.installPlaylistRowPlayback(modelField: Field, panelField: Field) {
    val menu = ::rowMenuMethod.method
    val videoType = ::rowVideoType.clazz

    check(menu.parameterCount == 3) {
        "Native playlist queue: the row menu is not the overflow handler of a row"
    }
    check(::rowEndpointField.declaredClass.isAssignableFrom(::rowClickListenerType.clazz)) {
        "Native playlist queue: the endpoint of a row belongs to another listener"
    }

    installPlaylistMenuDispatch(menu.parameterTypes[1].descriptor())
    installPlaylistReordering(videoType)

    // The video model of a row answers the flyout of its menu with its own video ID.
    queueRowVideoId = ::rowVideoIdField.field

    // The controller reaches the rows of the model it holds through these fields.
    playlistRowLookup = PlaylistRowLookup(
        modelField = modelField,
        panelField = panelField,
        contents = ::rowContents.field,
        video = ::rowItemVideo.field,
        endpoint = ::rowVideoEndpoint.field,
    )

    // The navigator of the playlist, which the notification, the repeat control and the player
    // controls of the queue navigate through.
    installPlaylistNavigation(modelField.declaringClass)

    // The same context the native overflow handler builds is what the extension reads its row from.
    ::rowMenuMethod.method.hookMethod(scopedHook(::rowMenuContextMethod.method) {
        after { param ->
            val context = param.result as? Map<*, *> ?: return@after
            val row = context[ENDPOINT_TAG] ?: return@after
            queueAction("onRowMenuOpened") {
                NativePlaylistQueuePatch.onRowMenuOpened(rowMenuContext(context, row))
            }
        }
    })

    // The endpoint the listener keeps is what the native binder stored, and it is cleared again when
    // the view of the row is recycled.
    ::rowEndpointBind.method.hookMethod {
        after { param ->
            val listener = param.thisObject ?: return@after
            val endpoint = param.args.getOrNull(1)
            synchronized(rowEndpoints) { rowEndpoints[listener] = endpoint }
        }
    }

    ::rowEndpointUnbind.method.hookMethod {
        before { param ->
            val listener = param.thisObject ?: return@before
            synchronized(rowEndpoints) { rowEndpoints.remove(listener) }
        }
    }

    // The native click veto runs before the logging decorates the endpoint, which is the moment the
    // extension observes the click: a veto the app rejects never reaches it.
    ::rowClickMethod.method.hookMethod(scopedHook(::rowClickVeto.method) {
        after { param ->
            if (param.result != true) return@after
            val listener = outerParam.thisObject ?: return@after
            queueAction("onRowClicked") {
                NativePlaylistQueuePatch.onRowClicked(rowClicks.get(listener))
            }
        }
    })

    installPlaylistSwipeBackground(::rowListenerField.field)
}

/** The descriptor of one class of the app, which the fingerprints of this folder are declared with. */
private fun Class<*>.descriptor(): String = "L" + name.replace('.', '/') + ";"
