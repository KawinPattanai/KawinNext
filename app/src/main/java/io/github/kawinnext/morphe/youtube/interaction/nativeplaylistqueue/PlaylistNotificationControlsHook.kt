/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.MediaControls
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.NotificationControls
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.ServiceNavigator
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream gives both native notification adapters the NotificationControls interface, gives the
// media owner they keep the MediaControls interface, injects the creation calls at the returns of
// their constructors and of their playback state handlers, and rewrites the two capability flags at
// the call of the model setter. A module cannot add a member to a class of the app, so the two
// interfaces are the proxy below, the creation calls are the hooks of those methods, and the flags
// are rewritten where the setter receives them.

/** The capabilities model of the media notification, which the adapters publish their flags to. */
private fun DexKitBridge.notificationModelType(): String =
    NotificationSkipActionsFingerprint.match().instructionMatches.first().instruction.fieldRef!!
        .type.descriptor

/** The model setter that takes the Previous and Next capabilities and returns the changed flags. */
private val notificationCapabilitySetter = findMethodDirect {
    notificationCapabilitiesSetterFingerprint(notificationModelType()).match().method
}

/** The two native adapters: the older one returns void, the newer one returns its change flags. */
private fun DexKitBridge.notificationAdapters(): List<MethodData> =
    notificationCapabilitiesFingerprint(notificationCapabilitySetter(this)).matchAll()
        .map { it.method }

/** The two native adapters of the notification, which the hooks below are installed on. */
private val notificationCapabilityAdapters = findMethodListDirect { notificationAdapters() }

/** The request of the Next action of the notification, which the media owner dispatches. */
private fun DexKitBridge.notificationNextRequest(): FieldData {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    val intent = playlistNavigationIntentFingerprint(enum, "NEXT").match()
        .instructionMatches.last().instruction.fieldRef!!
    return playlistNavigationSingletonFingerprint(nativeQueueRequestClass(this).descriptor, intent)
        .match().instructionMatches.last().instruction.fieldRef!!
}

/** The media owner that dispatches the skips of the notification, which each adapter keeps. */
private val notificationOwner = findClassDirect {
    notificationNextDispatchFingerprint(notificationNextRequest()).match().method.declaredClass!!
}

/** The provider the media owner asks for the playback service of the notification. */
private val notificationProvider = findFieldDirect {
    notificationNextDispatchFingerprint(notificationNextRequest()).match()
        .instructionMatches[2].instruction.fieldRef!!
}

/** The method of that provider that returns the playback service. */
private val notificationServiceCall = findMethodDirect {
    notificationNextDispatchFingerprint(notificationNextRequest()).match()
        .instructionMatches[3].instruction.methodRef!!
}

/** The playback service the notification navigates through, which the provider returns. */
private val notificationService = findClassDirect {
    notificationNextDispatchFingerprint(notificationNextRequest()).match()
        .instructionMatches[5].instruction.classRef!!
}

/** The media owner field of each adapter. */
private val notificationAdapterOwners = findFieldListDirect {
    val owner = notificationOwner(this).descriptor
    notificationAdapters().map { adapter ->
        notificationAdapterConstructorFingerprint(adapter.declaredClass!!.descriptor, owner)
            .match().instructionMatches.single().instruction.fieldRef!!
    }
}

/** The constructor of each adapter, which finishes before it can refresh the notification. */
private val notificationAdapterConstructors = findMethodListDirect {
    val owner = notificationOwner(this).descriptor
    notificationAdapters().map { adapter ->
        notificationAdapterConstructorFingerprint(adapter.declaredClass!!.descriptor, owner)
            .match().method
    }
}

/** The playback state handler of each adapter, which clears the flags of a committed selection. */
private val notificationPlaybackStates = findMethodListDirect {
    val adapters = notificationAdapters()
    val trace = notificationPlaybackStateFingerprint(
        adapters.single { it.returnType!!.descriptor == "V" }.declaredClass!!.descriptor
    ).match().method
    adapters.map { adapter ->
        notificationPlaybackStateFingerprint(adapter.declaredClass!!.descriptor, trace).match().method
    }
}

/** The native notifier the newer adapter dispatches its returned change flags to. */
private val notificationDispatches = findMethodListDirect {
    notificationAdapters()
        .filter { it.returnType!!.descriptor == "I" }
        .map { computation ->
            notificationCapabilitiesDispatchFingerprint(computation).matchAll()
                .map { it.instructionMatches.last().instruction.methodRef!! }
                .distinct().single()
        }
}

/** The bits of the capability answer: bit 0 is Previous and bit 1 is Next. */
private const val PREVIOUS_BIT = 0x1
private const val NEXT_BIT = 0x2

/** The answer that keeps the native flags of the notification. */
private const val KEEP_NATIVE_CAPABILITIES = -1

/**
 * The media owner of one adapter, which stands in for the MediaControls interface upstream adds to
 * its class, and the adapter itself, which stands in for the NotificationControls interface.
 *
 * The owner bridge reads the service of the notification through the provider it keeps, and the
 * navigator it returns is the proxy of the playback service of the queue patch. The refresh bridge
 * runs the native computation of the adapter again and hands its changed flags to the native
 * notifier, so releasing the queue restores the native capabilities without a cached copy.
 */
private class NotificationControlsProxy(
    private val adapter: Any,
    private val computation: Method,
    private val notifier: Method?,
    private val owner: Field,
    private val provider: Field,
    private val serviceCall: Method,
    private val service: Class<*>
) : NotificationControls, MediaControls {
    override fun patch_getQueueMediaControls(): MediaControls = this

    override fun patch_getQueueNavigator(): ServiceNavigator? {
        val mediaOwner = bridgeField(owner, adapter) ?: return null
        val providerObject = bridgeField(provider, mediaOwner) ?: return null
        if (!serviceCall.declaringClass.isInstance(providerObject)) return null
        val serviceObject = serviceCall.call(providerObject) ?: return null
        if (!service.isInstance(serviceObject)) return null
        return playlistServiceNavigator(serviceObject)
    }

    override fun patch_refreshQueueNotification() {
        // The older adapter returns void and publishes through its own state handler.
        val changed = computation.call(adapter) as? Int ?: return
        notifier?.call(adapter, changed)
    }
}

/** The native members of one adapter, which its proxy and its hooks answer with. */
private class NotificationAdapter(
    val computation: Method,
    val constructor: Constructor<*>,
    val notifier: Method?,
    val state: Method,
    val owner: Field
)

/** The adapter whose capability computation is running, which the model setter belongs to. */
private val computingAdapter: ThreadLocal<Any?> = ThreadLocal()

/**
 * Publishes the capabilities of the owned queue before the native notification uses them.
 *
 * Upstream adds the two interfaces, injects the creation calls at the returns of the constructors
 * and of the playback state handlers, and replaces the two flags the adapter passes to the setter
 * of the model. Here the same methods are hooked: the setter is hooked for the flags themselves, and
 * the adapter it belongs to is the one whose computation is running on the same thread, which is the
 * call site upstream rewrites.
 *
 * @param ownerType The media owner class that the notification adapters keep.
 */
internal fun PatchExecutor.installPlaylistNotificationControls(ownerType: Class<*>) {
    check(::notificationOwner.clazz == ownerType) {
        "Native playlist queue: the notification adapters of another media owner were found"
    }

    val computations = ::notificationCapabilityAdapters.dexMethodList.map { it.toMethod() }
    val constructors = ::notificationAdapterConstructors.dexMethodList.map { it.toConstructor() }
    val owners = ::notificationAdapterOwners.fieldList
    val states = ::notificationPlaybackStates.dexMethodList.map { it.toMethod() }
    val dispatches = ::notificationDispatches.dexMethodList.map { it.toMethod() }
    val setter = ::notificationCapabilitySetter.method

    check(computations.size == owners.size && computations.size == constructors.size &&
            computations.size == states.size) {
        "Native playlist queue: the notification adapters disagree about their members"
    }
    check(computations.count { it.returnType != Void.TYPE } == dispatches.size) {
        "Native playlist queue: an adapter that publishes its flags has no native notifier"
    }

    val provider = ::notificationProvider.field
    val serviceCall = ::notificationServiceCall.method
    val service = ::notificationService.clazz

    var notifier = 0
    val adapters = computations.mapIndexed { index, computation ->
        computation.declaringClass to NotificationAdapter(
            computation = computation,
            constructor = constructors[index],
            notifier = if (computation.returnType == Void.TYPE) null else dispatches[notifier++],
            state = states[index],
            owner = owners[index],
        )
    }.toMap()

    val controls = ProxyCache { adapter: Any ->
        val members = adapters.getValue(adapter.javaClass)
        NotificationControlsProxy(
            adapter, members.computation, members.notifier, members.owner,
            provider, serviceCall, service
        )
    }

    adapters.values.forEach { members ->
        members.constructor.hookMethod {
            after { param ->
                val adapter = param.thisObject ?: return@after
                queueAction("onNotificationControlsCreated") {
                    NativePlaylistQueuePatch.onNotificationControlsCreated(controls.get(adapter))
                }
            }
        }

        members.state.hookMethod {
            after { param ->
                val adapter = param.thisObject ?: return@after
                queueAction("onNotificationPlaybackChanged") {
                    NativePlaylistQueuePatch.onNotificationPlaybackChanged(controls.get(adapter))
                }
            }
        }

        members.computation.hookMethod {
            before { computingAdapter.set(it.thisObject) }
            after { computingAdapter.remove() }
        }
    }

    setter.hookMethod {
        before { param ->
            val adapter = computingAdapter.get() ?: return@before
            val capabilities = queueAction("notificationNavigationAvailability") {
                NativePlaylistQueuePatch.notificationNavigationAvailability(controls.get(adapter))
            } ?: KEEP_NATIVE_CAPABILITIES
            if (capabilities < 0) return@before
            param.args[0] = capabilities and PREVIOUS_BIT != 0
            param.args[1] = capabilities and NEXT_BIT != 0
        }
    }
}
