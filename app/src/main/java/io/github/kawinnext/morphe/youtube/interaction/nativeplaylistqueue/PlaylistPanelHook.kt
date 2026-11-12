/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import android.view.View
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.LoadingView
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.PagingOwner
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.PlaylistController
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.PlaylistModel
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.Match
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream adds eight bridge methods to the playlist controller, one to its model, and injects three
// calls: the extension decision before the controller stores an incoming model, the controller update
// after it accepted one, and a playback transition at every return of the descriptor transition. A
// module cannot add a method to a class of the app, so the bridges are the proxies below, and the
// three injection points are the hooks of the two methods they sit in: the guard becomes an entry
// hook that skips the accept, the update runs after it stored the model, and the transition runs
// after the native method returned.

// The native model factory initializes queueMode to 1 before accepting a watch response.
// Keep that neutral mode: rows use ordinary watch endpoints, without attaching
// a native playlist navigator through navigationSets.
private const val DEFAULT_QUEUE_MODE = 1

/** The model whose toString names every field of a playlist panel. */
private val playlistModelDescription = findMethodDirect {
    PlaylistModelDescriptionFingerprint.match().method
}

/** The playlist model of the panel, which also answers the PlaylistModel interface. */
private val playlistModelType = findClassDirect { playlistModelDescription(this).declaredClass!! }

/** The controller that accepts a playlist model for the video it plays. */
private val playlistControllerType = findClassDirect {
    PlaylistControllerConstructorFingerprint.match().method.declaredClass!!
}

/** The video ID the model carries from its watch response. */
private val playlistResponseVideo = findFieldDirect {
    playlistModelDescription(this).findFieldFromToString("{videoId=")
}

/** The playlist identity of the model. */
private val playlistModelPlaylist = findFieldDirect {
    playlistModelDescription(this).findFieldFromToString(", playlistId=")
}

/** The panel of the model, whose rows the queue replaces. */
private val playlistPanel = findFieldDirect {
    playlistModelDescription(this).findFieldFromToString(", playlistPanel=")
}

/** The navigation sets of the model, which a queue of ours never attaches. */
private val playlistModelNavigation = findFieldDirect {
    playlistModelDescription(this).findFieldFromToString(", navigationSets=")
}

/** The native acceptance of a model, whose second instruction reads the active video of the video. */
private fun DexKitBridge.acceptedModel(): Match = acceptedPlaylistModelFingerprint(
    playlistControllerType(this).descriptor,
    playlistModelType(this).descriptor,
    playlistResponseVideo(this)
).match()

/** The method that accepts a model, which the module hooks on both sides of its store. */
private val playlistAccept = findMethodDirect { acceptedModel().method }

/** The field of the controller that holds the accepted model. */
private val playlistAcceptedModel = findFieldDirect {
    acceptedModel().instructionMatches.last().instruction.fieldRef!!
}

/** The playlist identity the controller considers active. */
private val playlistActivePlaylist = findFieldDirect {
    playlistControllerIdentityFingerprint(playlistAccept(this)).match()
        .instructionMatches.first().instruction.fieldRef!!
}

/** The video the controller plays, which the accepted model must belong to. */
private val playlistActiveVideo = findFieldDirect {
    acceptedModel().instructionMatches[2].instruction.fieldRef!!
}

/** The collapsed panel view, whose attachment says whether the playlist is on screen. */
private val playlistCollapsedView = findFieldDirect {
    PlaylistControlsCreationFingerprint.match().instructionMatches.first().instruction.fieldRef!!
}

/** The native render of an accepted model. */
private val playlistRender = findMethodDirect {
    playlistPanelRenderFingerprint(playlistAcceptedModel(this)).match().method
}

/** The descriptor transition, which can complete without any playlist model. */
private val playlistTransition = findMethodDirect {
    playlistVideoTransitionFingerprint(playlistActiveVideo(this)).match().method
}

/** The native builder of a model, which keeps the defaults of the app. */
private val playlistModelBuild = findMethodDirect {
    playlistModelBuildFingerprint(playlistModelType(this).descriptor).match().method
}

/** The static factory of that builder. */
private val playlistModelFactory = findMethodDirect {
    playlistModelFactoryFingerprint(
        playlistModelType(this).descriptor,
        playlistModelBuild(this).declaredClass!!.descriptor
    ).match().method
}

/** The constructor of a model, whose parameters are its fields. */
private fun DexKitBridge.modelConstructor(): Match =
    playlistModelConstructorFingerprint(playlistModelType(this).descriptor).match()

/** The constructor that builds a model from the arguments of the native constructor. */
private val playlistModelConstructor = findMethodDirect { modelConstructor().method }

/** The model fields in the order of the constructor parameters. */
private val playlistModelFields = findFieldListDirect {
    val stores = modelConstructor().instructionMatches.sortedBy { it.instruction.register(0) }
    check(stores.map { it.instruction.register(0) }.zipWithNext().all { (first, next) -> next == first + 1 }) {
        "Native playlist queue: each model parameter must be stored directly"
    }
    stores.map { it.instruction.fieldRef!! }
}

/**
 * The classes, fields and methods the bridge methods of the controller read, and the native work
 * their smali repeats: building a model of a queue, accepting it, and restoring the previous one.
 *
 * The model crosses this boundary as the app object itself, because the extension keeps it as an
 * opaque identity and compares it with the model the controller holds now.
 */
internal class PlaylistControllerMembers(
    private val modelType: Class<*>,
    private val modelField: Field,
    private val activePlaylist: Field,
    private val activeVideo: Field,
    private val collapsedView: Field,
    private val modelVideo: Field,
    private val modelPlaylist: Field,
    private val modelPanel: Field,
    private val modelNavigation: Field,
    private val accept: Method,
    private val render: Method,
    private val factory: Method,
    private val build: Method,
    private val constructor: Constructor<*>,
    private val constructorFields: List<Field>
) {
    /** The video the controller plays, which a watch response can leave empty. */
    fun videoId(controller: Any): String = activeVideo.read(controller) as? String ?: ""

    fun model(controller: Any): Any? = modelField.read(controller)

    /** The playlist the controller considers active, or empty when it has no model. */
    fun playlistId(controller: Any): String =
        model(controller)?.let { modelPlaylist.read(it) as? String } ?: ""

    /** The playlist identity of one model, which is what the PlaylistModel bridge returns. */
    fun modelPlaylistId(model: Any): String? = modelPlaylist.read(model) as? String

    fun panel(controller: Any): Any? = model(controller)?.let { modelPanel.read(it) }

    fun isAttached(controller: Any): Boolean =
        (collapsedView.read(controller) as? View)?.isAttachedToWindow == true

    /**
     * Replaces the panel of the controller with one the extension built, and answers with the model
     * the native acceptance kept, or null when it rejected it.
     *
     * Ordinary videos can have no playlist model at all, so the missing source is the builder's own
     * defaults, including the tracking parameters the native validation requires.
     */
    fun updatePanel(controller: Any, panel: ByteArray): Any? {
        val parsed = parseAppMessage(modelPanel.type, panel)
        val source = modelField.read(controller) ?: run {
            val builder = factory.call(null) ?: return null
            build.call(builder) ?: return null
        }
        val arguments = constructorFields.map { field ->
            when (field) {
                modelVideo -> activeVideo.read(controller)
                modelPlaylist -> ""
                modelPanel -> parsed
                modelNavigation -> null
                else -> if (field.type == Int::class.javaPrimitiveType) DEFAULT_QUEUE_MODE
                else field.read(source)
            }
        }.toTypedArray()

        val updated = constructor.newInstance(*arguments)
        accept.call(controller, updated)
        if (modelField.read(controller) !== updated) {
            // A rejected model must not keep the playlist the controller was bound to.
            activePlaylist.write(controller, "")
            return null
        }
        return updated
    }

    /** Puts the previous model back and renders it, unless the controller already moved on. */
    fun restorePanel(controller: Any, expectedModel: Any?, originalModel: Any?) {
        if (modelField.read(controller) === expectedModel) return
        val model = originalModel?.takeIf(modelType::isInstance)
        activePlaylist.write(controller, model?.let { modelPlaylist.read(it) } ?: "")
        modelField.write(controller, model)
        render.call(controller)
    }
}

/** The members of the controller, which every hook that answers with its proxy needs. */
private var playlistControllerMembers: PlaylistControllerMembers? = null

/** One model of the native panel, which stands in for the PlaylistModel interface of its class. */
private class PlaylistModelProxy(
    private val model: Any,
    private val members: PlaylistControllerMembers
) : PlaylistModel {
    override fun patch_getPlaylistId(): String? = members.modelPlaylistId(model)
}

private val playlistModels = ProxyCache { model: Any ->
    val members = playlistControllerMembers
        ?: error("Native playlist queue: the playlist controller is not installed")
    PlaylistModelProxy(model, members)
}

/** The proxy of one model, which is the same object at every read of that model. */
private fun playlistModelProxy(model: Any): PlaylistModel = playlistModels.get(model)

/**
 * The controller of the playlist panel, which stands in for the bridge methods upstream adds to its
 * class: every member reads the field its smali bridge reads, and the paging members answer with the
 * list the paging hook of the module already knows.
 */
private class PlaylistControllerProxy(
    private val controller: Any,
    private val members: PlaylistControllerMembers
) : PlaylistController, PagingOwner {
    override fun patch_getVideoId(): String = members.videoId(controller)

    override fun patch_getPlaylistId(): String = members.playlistId(controller)

    override fun patch_getModel(): Any? = members.model(controller)

    override fun patch_getPanel(): Any? = members.panel(controller)?.let(::queuePanelProxy)

    override fun patch_findQueueRow(target: Any?): Int =
        playlistRowLookup?.findQueueRow(controller, target) ?: -1

    override fun patch_markQueueRows() {
        playlistRowLookup?.markQueueRows(controller)
    }

    override fun patch_isAttached(): Boolean = members.isAttached(controller)

    override fun patch_getNavigator(): Any? = playlistNavigatorOwner(controller)?.patch_getNavigator()

    override fun patch_updatePanel(panel: ByteArray): Any? = members.updatePanel(controller, panel)

    override fun patch_restorePanel(expectedModel: Any?, originalModel: Any?) {
        members.restorePanel(controller, expectedModel, originalModel)
    }

    override fun patch_showQueueFeedback(command: ByteArray) {
        showQueueFeedback(controller, command)
    }

    // The extension reads the list of the panel, and the frame that shows its progress, through the
    // paging bridges of the same native controller.
    override fun patch_getPlaylistList(): Any? =
        playlistPaging?.owner(controller)?.patch_getPlaylistList()

    override fun patch_getPlaylistLoadingView(): LoadingView? =
        playlistPaging?.owner(controller)?.patch_getPlaylistLoadingView()
}

private val playlistControllers = ProxyCache { controller: Any ->
    val members = playlistControllerMembers
        ?: error("Native playlist queue: the playlist controller is not installed")
    PlaylistControllerProxy(controller, members)
}

/** The proxy of one playlist controller, which the extension reads the playlist through. */
internal fun playlistControllerProxy(controller: Any): PlaylistController =
    playlistControllers.get(controller)

/**
 * Bridges the accepted watch model of the playlist panel, which the extension then replaces with the
 * panel of its own queue.
 *
 * Upstream only adds the bridge methods here and injects the three callbacks; the playback engine is
 * attached separately at runtime. Here the row hook is installed first, because its bridges read the
 * rows of the panel this model holds, and the two callbacks are the hooks of the native acceptance
 * and of the descriptor transition.
 */
internal fun PatchExecutor.installPlaylistPanel() {
    val controllerType = ::playlistControllerType.clazz
    val modelField = ::playlistAcceptedModel.field
    val panelField = ::playlistPanel.field
    val constructor = ::playlistModelConstructor.constructor
    val fields = ::playlistModelFields.fieldList
    val activeVideo = ::playlistActiveVideo.field

    check(modelField.declaringClass == controllerType) {
        "Native playlist queue: the accepted model belongs to another controller"
    }
    check(activeVideo.declaringClass == controllerType) {
        "Native playlist queue: active video must belong to the playlist controller"
    }
    check(panelField.declaringClass == ::playlistModelType.clazz) {
        "Native playlist queue: the panel of the model belongs to another class"
    }
    check(::playlistCollapsedView.field.declaringClass == controllerType) {
        "Native playlist queue: the collapsed panel belongs to another controller"
    }
    check(::playlistRender.method.declaringClass == controllerType) {
        "Native playlist queue: the render of a model belongs to another controller"
    }
    check(constructor.parameterCount == fields.size) {
        "Native playlist queue: each model parameter must be stored directly"
    }

    playlistControllerMembers = PlaylistControllerMembers(
        modelType = ::playlistModelType.clazz,
        modelField = modelField,
        activePlaylist = ::playlistActivePlaylist.field,
        activeVideo = activeVideo,
        collapsedView = ::playlistCollapsedView.field,
        modelVideo = ::playlistResponseVideo.field,
        modelPlaylist = ::playlistModelPlaylist.field,
        modelPanel = ::playlistPanel.field,
        modelNavigation = ::playlistModelNavigation.field,
        accept = ::playlistAccept.method,
        render = ::playlistRender.method,
        factory = ::playlistModelFactory.method,
        build = ::playlistModelBuild.method,
        // A generated constructor is not public, and the native acceptance calls it directly.
        constructor = constructor.apply { isAccessible = true },
        constructorFields = fields,
    )

    installPlaylistRowPlayback(modelField, panelField)

    ::playlistAccept.method.hookMethod {
        before { param ->
            val controller = param.thisObject ?: return@before
            val model = param.args.getOrNull(0) ?: return@before
            // The native stale-video validation runs before the store upstream guards, so a model it
            // rejects never reaches this decision. A model of ours that the extension already built
            // must not let the app claim the controller again.
            val ignored = queueAction("shouldIgnorePlaylistModel") {
                NativePlaylistQueuePatch.shouldIgnorePlaylistModel(
                    playlistControllerProxy(controller),
                    playlistModelProxy(model)
                )
            } ?: return@before
            if (!ignored) return@before
            param.result = null
        }

        after { param ->
            val controller = param.thisObject ?: return@after
            // Upstream observes the controller after the store of the accepted model only.
            if (modelField.read(controller) !== param.args.getOrNull(0)) return@after
            queueAction("onControllerUpdated") {
                NativePlaylistQueuePatch.onControllerUpdated(playlistControllerProxy(controller))
            }
        }
    }

    ::playlistTransition.method.hookMethod {
        after { param ->
            val controller = param.thisObject ?: return@after
            queueAction("onPlaybackTransition") {
                NativePlaylistQueuePatch.onPlaybackTransition(playlistControllerProxy(controller))
            }
        }
    }
}
