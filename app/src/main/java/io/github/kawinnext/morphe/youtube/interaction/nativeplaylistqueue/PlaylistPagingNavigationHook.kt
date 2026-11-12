/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4404
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.NativePlayback
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.PlaylistNavigator
import io.github.kawinnext.MethodHookParam
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream adds two interfaces to classes of the app: PlaylistNavigator to the playback service of
// the queue, whose bridges read the intent of a request, resume a deferred request and expose the
// queue playback of the service, and NativePlayback to the wrapper of the active queue, whose bridge
// reads the sequence it holds. It then injects the decision of the extension at the head of the
// navigator dispatch and at the head of the three lookups that walk the sequence of a request. A
// module cannot add a member to a class of the app, so the navigator is a proxy over the service, the
// sequence bridge is the playback proxy of the engine, and the four call sites are the hooks below.

/** The native queue of the app, which the wrapper replaces with the owned queue. */
private fun DexKitBridge.playlistQueueType(): String =
    NativeQueueDescriptorFingerprint.match().method.declaredClass!!.descriptor

/** The type of the request that carries an intent to navigate. */
private fun DexKitBridge.playlistRequestType(): String = nativeQueueRequestClass(this).descriptor

/** The native request of one navigation direction, which the service dispatches. */
private fun DexKitBridge.playlistRequest(direction: String): FieldData {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    val intent = playlistNavigationIntentFingerprint(enum, direction).match()
        .instructionMatches.last().instruction.fieldRef!!
    return playlistNavigationSingletonFingerprint(playlistRequestType(), intent)
        .match().instructionMatches.last().instruction.fieldRef!!
}

/** The field of a request that holds its navigation intent, which the extension reads. */
private val playlistRequestIntent = findFieldDirect {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    queueNavigationIntentFieldFingerprint(playlistRequestType(), enum)
        .match().instructionMatches.single().instruction.fieldRef!!
}

/** The requests of the two directions, which the navigator proxy dispatches. */
private val playlistRequests = findFieldListDirect {
    listOf(playlistRequest("NEXT"), playlistRequest("PREVIOUS"))
}

/** The dispatch of the playback service, which every request of a playlist passes. */
private val playlistServiceNavigation = findMethodDirect {
    playlistServiceNavigatorFingerprint(playlistRequestType()).match().method
}

/** The method of the service that answers whether a request may navigate. */
private val playlistServiceCapability = findMethodDirect {
    playlistServiceCanNavigateFingerprint(
        playlistServiceNavigation(this).declaredClass!!.descriptor, playlistRequestType()
    ).match().method
}

/** The player fields of the service, which reach the state that names the playing video. */
private val playlistServicePlayers = findFieldListDirect {
    playlistServiceNavigatorFingerprint(playlistRequestType())
        .match().instructionMatches.take(3).map { it.instruction.fieldRef!! }
}

/** The method of that state which returns the playing video id. */
private val playlistServiceVideoId = findMethodDirect {
    playlistServiceNavigatorFingerprint(playlistRequestType())
        .match().instructionMatches[4].instruction.methodRef!!
}

/** The sequencer holder of the service, the sequencer it holds and the queue navigator inside it. */
private val playlistServiceSequencer = findFieldListDirect {
    val service = playlistServiceNavigatorFingerprint(playlistRequestType())
        .match().method.declaredClass!!.descriptor
    val loader = nativeQueueLoaderFingerprint(service).match()
    val sequencer = loader.instructionMatches[0].instruction.fieldRef!!
    val sequencerType = loader.instructionMatches[2].instruction.classRef!!.descriptor
    listOf(
        nativeQueueSequencerOwnerFingerprint(service, sequencer.declaredClass!!.descriptor)
            .match().instructionMatches.first().instruction.fieldRef!!,
        sequencer,
        nativeQueueSequencerNavigatorFingerprint(sequencerType, playlistQueueType())
            .match().instructionMatches.single().instruction.fieldRef!!,
    )
}

/**
 * The playback service, which stands in for the PlaylistNavigator interface upstream adds to it.
 *
 * The members are the bridges of that interface: the intent of a request is read from the request,
 * a deferred request is dispatched through the same native method, the queue playback is the active
 * sequencer of the service, and the playing video is the one its player names.
 */
private class PlaylistNavigatorProxy(
    private val service: Any,
    private val navigator: PlaylistServiceNavigator
) : PlaylistNavigator {
    override fun patch_getVideoId(): String? = navigator.videoId(service)

    override fun patch_getQueuePlayback(): NativePlayback? = navigator.playback(service)

    override fun patch_navigateQueue(next: Boolean) = navigator.navigate(service, next)

    override fun patch_canNavigateQueue(next: Boolean): Boolean = navigator.canNavigate(service, next)

    override fun patch_getPlaylistIntent(request: Any?): Enum<*>? = navigator.intent(request)

    override fun patch_resumePlaylistNavigation(request: Any?) =
        navigator.resume(service, request)
}

/** The app members that the proxy of the service above calls, in the order they are reached. */
internal class PlaylistServiceNavigator(
    private val sequencerOwner: Field,
    private val sequencer: Field,
    private val sequencerField: Field,
    private val players: List<Field>,
    private val videoId: Method,
    private val navigate: Method,
    private val canNavigate: Method,
    private val intent: Field,
    private val requests: List<Field>
) {
    private val proxies = ProxyCache { service: Any -> PlaylistNavigatorProxy(service, this) }

    /** The proxy of one playback service, or null when the receiver is not one. */
    fun proxy(service: Any): PlaylistNavigator? =
        if (navigate.declaringClass.isInstance(service)) proxies.get(service) else null

    /** The active sequencer of the service, which holds the navigator of the queue it plays. */
    fun playback(service: Any): NativePlayback? {
        val engine = nativeQueueEngine ?: return null
        val holder = sequencerOwner.read(service) ?: return null
        val active = sequencer.read(holder) ?: return null
        if (!sequencerField.declaringClass.isInstance(active)) return null
        val wrapper = sequencerField.read(active) ?: return null
        if (!engine.isWrapper(wrapper)) return null
        return nativeQueuePlayback(wrapper)
    }

    fun videoId(service: Any): String? {
        val owner = players[0].read(service) ?: return null
        val player = players[1].read(owner) ?: return null
        val state = players[2].read(player) ?: return null
        if (!videoId.declaringClass.isInstance(state)) return null
        return videoId.call(state) as? String
    }

    fun navigate(service: Any, next: Boolean) {
        val request = requests[if (next) 0 else 1].apply { isAccessible = true }.get(null) ?: return
        navigate.call(service, request)
    }

    fun canNavigate(service: Any, next: Boolean): Boolean {
        val request = requests[if (next) 0 else 1].apply { isAccessible = true }.get(null) ?: return false
        return canNavigate.call(service, request) as Boolean
    }

    fun intent(request: Any?): Enum<*>? = request?.let { intent.read(it) as? Enum<*> }

    fun resume(service: Any, request: Any?) {
        if (request == null) return
        navigate.call(service, request)
    }
}

/** The navigator table of the installed hook, which the notification owner also answers with. */
private var playlistServiceNavigators: PlaylistServiceNavigator? = null

/**
 * The navigator proxy of one playback service, which the media owner of the notification answers
 * with, as the bridge method that upstream adds to that owner returns it.
 *
 * It is null before this hook is installed, which is the case the notification hook reaches only
 * when the patch of the queue itself did not run.
 */
internal fun playlistServiceNavigator(service: Any): PlaylistNavigator? =
    playlistServiceNavigators?.proxy(service)

/**
 * Defers the navigation of a request that leaves the rows of the owned queue.
 *
 * Upstream adds the navigator interface to the service and injects the extension decision at the
 * head of the dispatch, so the native navigation returns before it commits a target. Here the same
 * dispatch is hooked at its entry, and a deferred request keeps the native body from running.
 *
 * @param navigatorType The playback service, which the proxy of the navigator stands for.
 * @param wrapperType The request class, whose field declares the intent of a navigation.
 * @param enumType The enum of those intents.
 * @param serviceMethod The dispatch of the service, which is also the method that resumes a request.
 */
internal fun PatchExecutor.installPlaylistPagingNavigation(
    navigatorType: Class<*>,
    wrapperType: Class<*>,
    enumType: Class<*>,
    serviceMethod: Method
) {
    val navigation = ::playlistServiceNavigation.method
    check(navigation == serviceMethod) {
        "Native playlist queue: the paging navigator was given another dispatch of the service"
    }
    check(navigation.declaringClass == navigatorType) {
        "Native playlist queue: the dispatch of another class was given as the playlist navigator"
    }
    check(::nativeQueueRequestClass.clazz == wrapperType) {
        "Native playlist queue: the paging navigator was given another navigation request"
    }
    check(NavigationIntentEnumFingerprint.declaredClass == enumType) {
        "Native playlist queue: the paging navigator was given another navigation enum"
    }
    check(::playlistRequestIntent.field.type == enumType) {
        "Native playlist queue: the request of a navigation carries another intent"
    }

    val navigator = PlaylistServiceNavigator(
        sequencerOwner = ::playlistServiceSequencer.fieldList[0],
        sequencer = ::playlistServiceSequencer.fieldList[1],
        sequencerField = ::playlistServiceSequencer.fieldList[2],
        players = ::playlistServicePlayers.fieldList,
        videoId = ::playlistServiceVideoId.method,
        navigate = navigation,
        canNavigate = ::playlistServiceCapability.method,
        intent = ::playlistRequestIntent.field,
        requests = ::playlistRequests.fieldList,
    )
    playlistServiceNavigators = navigator

    navigation.hookMethod {
        before { param ->
            val service = param.thisObject ?: return@before
            val request = param.args.getOrNull(0)
            val deferred = queueAction("deferPlaylistNavigation") {
                NativePlaylistQueuePatch.deferPlaylistNavigation(navigator.proxy(service), request)
            } ?: false
            if (deferred) param.result = null
        }
    }
}

/** The queue lookup that turns a request into the navigation descriptor of its target. */
private val pagingSequenceDescriptor = findMethodDirect {
    NativeQueueDescriptorFingerprint.match().method
}

/** The queue lookup that commits the intent of a request before it navigates. */
private val pagingSequenceCommit = findMethodDirect {
    val queue = NativeQueueDescriptorFingerprint.match().method
    playlistQueueCommitFingerprint(
        queue.declaredClass!!.descriptor,
        queue.paramTypes.single().descriptor,
        queue.returnType!!.descriptor,
    ).match().method
}

/** The queue lookup that answers how a request of the queue may be played. */
private val pagingSequenceAvailability = findMethodDirect {
    val queue = NativeQueueDescriptorFingerprint.match().method
    playlistQueueAvailabilityFingerprint(
        queue.declaredClass!!.descriptor, queue.paramTypes.single().descriptor
    ).match().method
}

/** The intent field of a request, which decides whether the request leaves the loaded rows. */
private val pagingBoundaryIntent = findFieldDirect {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    queueNavigationIntentFieldFingerprint(playlistRequestType(), enum)
        .match().instructionMatches.single().instruction.fieldRef!!
}

/**
 * The answer of the native queue for a source that is not loaded yet, which upstream returns from
 * the availability lookup of such a boundary.
 */
private const val PAGING_BOUNDARY_AVAILABILITY = 2

/**
 * Keeps the gaps of the source out of the descriptor and prefetch lookups of the queue.
 *
 * Upstream adds the NativePlayback bridge that reads the sequence of the wrapper, and injects the
 * boundary test at the head of the descriptor, commit and availability lookups: a request at the end
 * of the loaded rows returns no target instead of a stale one, and reports the boundary answer. The
 * sequence bridge is the playback proxy of the engine, and the three lookups are hooked at entry.
 *
 * @param wrapperType The wrapper of the active queue, which holds the sequence.
 * @param nativeNavigator The sequence field of that wrapper.
 * @param queueType The native queue, whose lookups walk the sequence.
 * @param requestType The request class, whose intent names the rows the request asks for.
 * @param descriptorType The navigation descriptor the queue builds for a request.
 * @param enumType The enum of the request intents.
 */
internal fun PatchExecutor.installPlaylistPagingSequence(
    wrapperType: Class<*>,
    nativeNavigator: Field,
    queueType: Class<*>,
    requestType: Class<*>,
    descriptorType: Class<*>,
    enumType: Class<*>
) {
    val descriptor = ::pagingSequenceDescriptor.method
    check(nativeNavigator.declaringClass == wrapperType) {
        "Native playlist queue: the sequence field belongs to another queue wrapper"
    }
    check(nativeNavigator.type == queueType) {
        "Native playlist queue: the wrapper holds a sequence of another class"
    }
    check(descriptor.declaringClass == queueType) {
        "Native playlist queue: the descriptor lookup belongs to another native queue"
    }
    check(descriptor.parameterTypes.single() == requestType) {
        "Native playlist queue: the descriptor lookup takes another request"
    }
    check(descriptor.returnType == descriptorType) {
        "Native playlist queue: the descriptor lookup returns another descriptor"
    }
    check(::pagingBoundaryIntent.field.type == enumType) {
        "Native playlist queue: the request of a lookup carries another intent"
    }

    val intent = ::pagingBoundaryIntent.field

    /** Whether the request asks for a row that the loaded source does not have yet. */
    fun isBoundary(param: MethodHookParam): Boolean {
        val sequence = param.thisObject ?: return false
        val request = param.args.getOrNull(0) ?: return false
        val direction = bridgeField(intent, request) as? Enum<*> ?: return false
        return queueAction("isUnloadedPlaylistBoundary") {
            NativePlaylistQueuePatch.isUnloadedPlaylistBoundary(sequence, direction)
        } ?: false
    }

    ::pagingSequenceDescriptor.method.hookMethod {
        before { param -> if (isBoundary(param)) param.result = null }
    }

    ::pagingSequenceCommit.method.hookMethod {
        before { param -> if (isBoundary(param)) param.result = null }
    }

    ::pagingSequenceAvailability.method.hookMethod {
        before { param ->
            if (isBoundary(param)) param.result = PAGING_BOUNDARY_AVAILABILITY
        }
    }
}
