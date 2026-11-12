/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.DirectQueueNavigator
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.MediaControls
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.NativePlayback
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.PlaylistNavigator
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.ServiceNavigator
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_42_or_greater
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream gives three classes of the app the ServiceNavigator interface (the playback service, the
// watch navigators that wrap it and the navigator that owns the queue backend directly), gives the
// media owner of the notification the MediaControls interface, and injects the extension decision at
// the head of the two notification skips and of the dispatch that leaves the loaded rows. A module
// cannot add a member to a class of the app, so every interface is a proxy below, the skips are the
// hooks of the two dispatch methods, and the dispatch that leaves the loaded rows is the hook of the
// navigator it belongs to.

/** The request of one skip direction, which the app dispatches from the static field of its class. */
private fun DexKitBridge.notificationRequest(direction: String): FieldData {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    val intent = playlistNavigationIntentFingerprint(enum, direction).match()
        .instructionMatches.last().instruction.fieldRef!!
    return playlistNavigationSingletonFingerprint(nativeQueueRequestClass(this).descriptor, intent)
        .match().instructionMatches.last().instruction.fieldRef!!
}

/** The two requests the navigator dispatches, in the order of the two skip directions. */
private val notificationRequests = findFieldListDirect {
    listOf(notificationRequest("NEXT"), notificationRequest("PREVIOUS"))
}

/** The dispatch of the playback service, which every request of a playlist passes. */
private val notificationServiceDispatch = findMethodDirect {
    playlistServiceNavigatorFingerprint(nativeQueueRequestClass(this).descriptor).match().method
}

/** The player fields of that service, which reach the state that names the playing video. */
private val notificationServicePlayers = findFieldListDirect {
    playlistServiceNavigatorFingerprint(nativeQueueRequestClass(this).descriptor)
        .match().instructionMatches.take(3).map { it.instruction.fieldRef!! }
}

/** The media owner that dispatches the skips of the notification. */
private val notificationOwner = findClassDirect {
    notificationNextDispatchFingerprint(notificationRequest("NEXT")).match().method.declaredClass!!
}

/** The Next dispatch of that owner, after logging and before the optional foreground callback. */
private val notificationNext = findMethodDirect {
    notificationNextDispatchFingerprint(notificationRequest("NEXT")).match().method
}

/**
 * The Previous dispatch of the same owner.
 *
 * It is reached through the media session callbacks: their delegate forwards the skip to the owner
 * method of the same name, and the owner dispatch of that name is the one the notification uses.
 */
private fun DexKitBridge.notificationPreviousDelegate(): MethodData {
    val owner = notificationOwner(this).descriptor
    val next = notificationNextDispatchFingerprint(notificationRequest("NEXT")).match().method
    val nextCallback = notificationSkipCallbackFingerprint("onSkipToNext").match()
        .instructionMatches.last().instruction.methodRef!!
    val delegateType = notificationDelegateFingerprint(nextCallback, owner, next.name)
        .match().method.declaredClass!!.descriptor
    val previousCallback = notificationSkipCallbackFingerprint("onSkipToPrevious").match()
        .instructionMatches.last().instruction.methodRef!!
    return notificationDelegateFingerprint(previousCallback, owner, delegateType = delegateType)
        .match().instructionMatches[1].instruction.methodRef!!
}

/** The Previous dispatch of the media owner. */
private val notificationPrevious = findMethodDirect {
    notificationPreviousDispatchFingerprint(notificationPreviousDelegate()).match().method
}

/** The foreground callbacks of the owner that take a skip over while a watch screen is open. */
private val notificationForeground = findFieldListDirect {
    listOf(
        notificationNextDispatchFingerprint(notificationRequest("NEXT")).match()
            .instructionMatches.first().instruction.fieldRef!!,
        notificationPreviousDispatchFingerprint(notificationPreviousDelegate()).match()
            .instructionMatches.first().instruction.fieldRef!!,
    )
}

/** The watch-page callbacks that ask the extension for the Next skip instead. */
private val notificationNextForeground = findClassDirect {
    notificationForegroundSkipFingerprint(
        notificationForeground(this).first().type.descriptor, notificationRequest("NEXT")
    ).match().method.declaredClass!!
}

/** The watch-page callbacks that ask the extension for the Previous skip instead. */
private val notificationPreviousForeground = findClassDirect {
    notificationForegroundSkipFingerprint(
        notificationForeground(this).last().type.descriptor, notificationRequest("PREVIOUS")
    ).match().method.declaredClass!!
}

/** The provider the media owner asks for the playback service of the notification. */
private val notificationProvider = findFieldDirect {
    notificationNextDispatchFingerprint(notificationRequest("NEXT")).match()
        .instructionMatches[2].instruction.fieldRef!!
}

/** The method of that provider that returns the playback service. */
private val notificationServiceCall = findMethodDirect {
    notificationNextDispatchFingerprint(notificationRequest("NEXT")).match()
        .instructionMatches[3].instruction.methodRef!!
}

/** The playback service the notification navigates through, which the provider returns. */
private val notificationService = findClassDirect {
    notificationNextDispatchFingerprint(notificationRequest("NEXT")).match()
        .instructionMatches[5].instruction.classRef!!
}

/** The wrappers of newer watch navigators, which forward every request to the playback service. */
private val notificationDelegates = findMethodListDirect {
    val service = notificationServiceDispatch(this)
    playlistServiceDelegateFingerprint(service).matchAllOrNull().orEmpty().map { it.method }
}

/** The service field of each of those wrappers. */
private val notificationDelegateServices = findFieldListDirect {
    val service = notificationServiceDispatch(this)
    playlistServiceDelegateFingerprint(service).matchAllOrNull().orEmpty()
        .map { it.instructionMatches.first().instruction.fieldRef!! }
}

/** The capability check each wrapper answers itself, which stays effective for its own dispatch. */
private val notificationDelegateCapabilities = findMethodListDirect {
    val request = nativeQueueRequestClass(this).descriptor
    val service = notificationServiceDispatch(this)
    playlistServiceDelegateFingerprint(service).matchAllOrNull().orEmpty().map { wrapper ->
        playlistServiceCanNavigateFingerprint(wrapper.method.declaredClass!!.descriptor, request)
            .match().method
    }
}

/** The navigator that owns the active queue backend directly, instead of through the service. */
private val directQueueNavigator = findClassDirect {
    DirectQueueNavigatorFingerprint().declaredClass!!
}

/** The backend of that navigator, which is the queue playback once the queue is ours. */
private val directQueueBackend = findFieldDirect {
    directQueueBackendFingerprint(
        DirectQueueNavigatorFingerprint().declaredClass!!.descriptor,
        nativeQueueWrapperClass(this).descriptor,
    ).matchAll().first().instructionMatches.last().instruction.fieldRef!!
}

/** The player owner of the navigator, and the fields below it that name the playing video. */
private val directQueuePlayers = findFieldListDirect {
    val navigator = DirectQueueNavigatorFingerprint().declaredClass!!.descriptor
    val players = playlistServiceNavigatorFingerprint(nativeQueueRequestClass(this).descriptor)
        .match().instructionMatches.take(3).map { it.instruction.fieldRef!! }
    val owner = directQueuePlayerOwnerFingerprint(navigator, players.first().type.descriptor)
        .match().instructionMatches.single().instruction.fieldRef!!
    players + owner
}

/** The method of that player state which returns the playing video id. */
private val directQueueVideoId = findMethodDirect {
    playlistServiceNavigatorFingerprint(nativeQueueRequestClass(this).descriptor)
        .match().instructionMatches[4].instruction.methodRef!!
}

/** The navigation intent of a request, which the paging decision of the navigator reads. */
private val directQueueIntent = findFieldDirect {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    queueNavigationIntentFieldFingerprint(nativeQueueRequestClass(this).descriptor, enum)
        .match().instructionMatches.single().instruction.fieldRef!!
}

/** The capability check of the queue backend, before the watch-history window policy is added. */
private val directQueueAvailability = findMethodDirect {
    directQueueBackendAvailabilityFingerprint(
        directQueueBackend(this), nativeQueueRequestClass(this).descriptor
    ).match().method
}

/** The dispatch of the direct navigator, which every skip of the newer player passes. */
private val directQueueDispatch = findMethodDirect {
    directQueueNavigateFingerprint(
        DirectQueueNavigatorFingerprint().declaredClass!!.descriptor,
        nativeQueueRequestClass(this).descriptor,
    ).match().method
}

/** The capability check the direct navigator answers itself. */
private val directQueueCapability = findMethodDirect {
    playlistServiceCanNavigateFingerprint(
        DirectQueueNavigatorFingerprint().declaredClass!!.descriptor,
        nativeQueueRequestClass(this).descriptor,
    ).match().method
}

/** The direction of the Next request in the native order of the two static requests. */
private const val NEXT_REQUEST = 0

/** One static navigation request, which the proxies of the navigators dispatch. */
private fun List<Field>.request(next: Boolean): Any? =
    this[if (next) NEXT_REQUEST else 1 - NEXT_REQUEST].apply { isAccessible = true }.get(null)

/**
 * The media owner of the notification, which stands in for the MediaControls interface upstream adds
 * to its class.
 *
 * The bridge returns the navigator of the playback service the owner asks its provider for, and it
 * answers nothing while a foreground callback of the watch screen takes the skips over, exactly as
 * the native owner skips its service path for those callbacks.
 */
private class MediaOwnerProxy(
    private val owner: Any,
    private val provider: Field,
    private val serviceCall: Method,
    private val service: Class<*>,
    private val foreground: List<Field>,
    private val foregroundTypes: List<Class<*>>
) : MediaControls {
    override fun patch_getQueueNavigator(): ServiceNavigator? {
        foreground.zip(foregroundTypes).forEach { (field, type) ->
            val callback = bridgeField(field, owner) ?: return@forEach
            if (type.isInstance(callback)) return null
        }
        val providerObject = bridgeField(provider, owner) ?: return null
        if (!serviceCall.declaringClass.isInstance(providerObject)) return null
        val serviceObject = serviceCall.call(providerObject) ?: return null
        if (!service.isInstance(serviceObject)) return null
        return playlistServiceNavigator(serviceObject)
    }
}

/**
 * One watch navigator that wraps the playback service, which stands in for the ServiceNavigator
 * interface upstream adds to its class.
 *
 * The skip is dispatched through the wrapper's own dispatch, so the hooks other patches install on
 * it stay effective, and the capability check is the one the wrapper answers itself.
 */
private class ServiceDelegateProxy(
    private val delegate: Any,
    private val service: Field,
    private val dispatch: Method,
    private val capability: Method,
    private val requests: List<Field>
) : ServiceNavigator {
    private fun inner(): ServiceNavigator? =
        bridgeField(service, delegate)?.let(::playlistServiceNavigator)

    override fun patch_getVideoId(): String? = inner()?.patch_getVideoId()

    override fun patch_getQueuePlayback(): NativePlayback? = inner()?.patch_getQueuePlayback()

    override fun patch_navigateQueue(next: Boolean) {
        val request = requests.request(next) ?: return
        dispatch.call(delegate, request)
    }

    override fun patch_canNavigateQueue(next: Boolean): Boolean {
        val request = requests.request(next) ?: return false
        return capability.call(delegate, request) as Boolean
    }
}

/**
 * The navigator that owns the queue backend directly, which stands in for the ServiceNavigator and
 * PlaylistNavigator interfaces upstream adds to its class.
 *
 * The four skips are the ones the window table of the backend already answers, and the two members
 * the paging decision adds read the intent of a request and resume it through the native dispatch.
 */
private class DirectPlaylistNavigatorProxy(
    private val navigator: Any,
    private val window: NativeQueueWindow,
    private val intent: Field,
    private val dispatch: Method
) : PlaylistNavigator {
    private val navigatorOfQueue: DirectQueueNavigator get() = window.proxy(navigator)

    override fun patch_getVideoId(): String? = navigatorOfQueue.patch_getVideoId()

    override fun patch_getQueuePlayback(): NativePlayback? = navigatorOfQueue.patch_getQueuePlayback()

    override fun patch_navigateQueue(next: Boolean) = navigatorOfQueue.patch_navigateQueue(next)

    override fun patch_canNavigateQueue(next: Boolean): Boolean =
        navigatorOfQueue.patch_canNavigateQueue(next)

    override fun patch_getPlaylistIntent(request: Any?): Enum<*>? =
        request?.let { intent.read(it) as? Enum<*> }

    override fun patch_resumePlaylistNavigation(request: Any?) {
        if (request != null) dispatch.call(navigator, request)
    }
}

/** The proxies of the watch navigators that wrap the playback service, by the class they wrap. */
private var delegateNavigators: List<Pair<Class<*>, ProxyCache<ServiceDelegateProxy>>>? = null

/**
 * The navigator proxy of one watch navigator that wraps the playback service, or null when the
 * object is not one of them.
 *
 * A wrapper is reached from the classes of the app, which hold it as an object, so the caller has to
 * ask for its proxy the way it asks [playlistServiceNavigator] for the proxy of the service.
 */
internal fun playlistDelegateNavigator(owner: Any?): ServiceNavigator? {
    if (owner == null) return null
    delegateNavigators?.forEach { (type, proxies) ->
        if (type.isInstance(owner)) return proxies.get(owner)
    }
    return null
}

/**
 * Routes the notification skips of the owned queue through the native navigator.
 *
 * Upstream adds the three ServiceNavigator bridges, the MediaControls bridge of the media owner and
 * the paging entry of the navigation, then injects the extension decision at the head of the two
 * notification skips. Here the same members are the proxies above, the paging entry is the hook of
 * [installPlaylistPagingNavigation], and the two skips are hooked at entry: a skip the extension
 * navigates does not run the native body, which is what the upstream return does. The upstream point
 * sits after the native logging of the skip, which an entry hook runs before, so the extension logs
 * the skip it navigated instead of the app.
 *
 * @param wrapperType The request class of one navigation.
 * @param enumType The enum of the navigation intents.
 */
internal fun PatchExecutor.installPlaylistNotification(
    wrapperType: Class<*>,
    enumType: Class<*>
) {
    check(::nativeQueueRequestClass.clazz == wrapperType) {
        "Native playlist queue: the notification was given another navigation request"
    }
    check(NavigationIntentEnumFingerprint.declaredClass == enumType) {
        "Native playlist queue: the notification was given another navigation enum"
    }

    val owner = ::notificationOwner.clazz
    val service = ::notificationServiceDispatch.method
    val navigatorType = service.declaringClass
    val players = ::notificationServicePlayers.fieldList
    // The current player only reaches the playing video through the service that holds it.
    check(players.first().declaringClass == navigatorType &&
            players.zipWithNext().all { (parent, child) -> parent.type == child.declaringClass }) {
        "Native playlist queue: current player must belong to the playback service"
    }
    check(::notificationProvider.field.declaringClass == owner) {
        "Native playlist queue: the notification provider belongs to another media owner"
    }

    // The playback engine of the native queue, which upstream installs from this hook before the
    // bridges of the navigator that carries it.
    installNativeQueueEngine()

    // The playback service, whose navigator the owner of the notification answers with.
    installPlaylistPagingNavigation(navigatorType, wrapperType, enumType, service)

    // Newer watch navigators wrap that service instead of being one. They are reached through their
    // exact native request delegation; older versions expose the service without a wrapper.
    val delegateDispatches = ::notificationDelegates.dexMethodList.map { it.toMethod() }
    val delegateServices = ::notificationDelegateServices.fieldList
    val delegateCapabilities = ::notificationDelegateCapabilities.dexMethodList.map { it.toMethod() }
    check(delegateDispatches.size == delegateServices.size &&
            delegateDispatches.size == delegateCapabilities.size) {
        "Native playlist queue: the playback service delegates disagree about their members"
    }
    check(delegateDispatches.size <= 1 && (!is_21_42_or_greater || delegateDispatches.size == 1)) {
        "Native playlist queue: missing or ambiguous playback service delegate"
    }
    delegateNavigators = delegateDispatches.mapIndexed { index, dispatch ->
        val wrapped = delegateServices[index]
        val capability = delegateCapabilities[index]
        check(wrapped.declaringClass == dispatch.declaringClass &&
                capability.declaringClass == dispatch.declaringClass) {
            "Native playlist queue: the members of a playback service delegate belong to another class"
        }
        dispatch.declaringClass to ProxyCache { delegate: Any ->
            ServiceDelegateProxy(
                delegate, wrapped, dispatch, capability, ::notificationRequests.fieldList
            )
        }
    }

    // The navigator that owns the backend directly, which the newer player navigates through.
    val direct = ::directQueueNavigator.clazz
    val directDispatch = ::directQueueDispatch.method
    val directIntent = ::directQueueIntent.field
    check(::directQueueBackend.field.declaringClass == direct) {
        "Native playlist queue: the queue backend belongs to another navigator"
    }
    check(directDispatch.declaringClass == direct) {
        "Native playlist queue: the direct dispatch belongs to another navigator"
    }

    // The paging entry of that navigator, which resumes a deferred skip through its own dispatch.
    val directWindow = NativeQueueWindow(
        backend = ::directQueueBackend.field,
        capability = ::directQueueAvailability.method,
        videoPlayers = ::directQueuePlayers.fieldList,
        videoId = ::directQueueVideoId.method,
        intent = directIntent,
        requests = ::notificationRequests.fieldList,
        navigate = directDispatch,
        canNavigate = ::directQueueCapability.method,
    )
    val directProxies = ProxyCache { navigator: Any ->
        DirectPlaylistNavigatorProxy(navigator, directWindow, directIntent, directDispatch)
    }
    directDispatch.hookMethod {
        before { param ->
            val navigator = param.thisObject ?: return@before
            val request = param.args.getOrNull(0)
            val deferred = queueAction("deferPlaylistNavigation") {
                NativePlaylistQueuePatch.deferPlaylistNavigation(directProxies.get(navigator), request)
            } ?: false
            if (deferred) param.result = null
        }
    }

    // The window of the newer player, which the paging navigation above leaves to the queue.
    if (is_21_42_or_greater) installNativeQueueWindow()

    // The media owner of the notification, and the two skips it dispatches.
    val owners = ProxyCache { mediaOwner: Any ->
        MediaOwnerProxy(
            mediaOwner,
            ::notificationProvider.field,
            ::notificationServiceCall.method,
            ::notificationService.clazz,
            ::notificationForeground.fieldList,
            listOf(::notificationNextForeground.clazz, ::notificationPreviousForeground.clazz),
        )
    }
    ::notificationNext.method.hookMethod {
        before { param ->
            val mediaOwner = param.thisObject ?: return@before
            val handled = queueAction("onNotificationNext") {
                NativePlaylistQueuePatch.onNotificationNext(owners.get(mediaOwner))
            } ?: false
            if (handled) param.result = null
        }
    }
    ::notificationPrevious.method.hookMethod {
        before { param ->
            val mediaOwner = param.thisObject ?: return@before
            val handled = queueAction("onNotificationPrevious") {
                NativePlaylistQueuePatch.onNotificationPrevious(owners.get(mediaOwner))
            } ?: false
            if (handled) param.result = null
        }
    }

    installPlaylistNotificationControls(owner)
}
