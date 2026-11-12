/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4404
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.PlaylistPage
import app.morphe.extension.youtube.patches.queue.PlaylistPages
import com.google.protobuf.MessageLite
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.Future

// Upstream adds the Source interface to the playback service of the native watch page, whose bridges
// read the account of a request, fill and dispatch a page request, and read the panel and the
// navigation of its response, and injects the attach of that source at every return of the service
// constructor. A module cannot add an interface to a class of the app, so the interface is the proxy
// below and the attach is the hook of that constructor.

/** The serializer of the native watch request, which names the arguments of a page request. */
private val playlistPageSerializer = findMethodDirect {
    PlaylistPageRequestSerializerFingerprint.match().method
}

/** The request whose playlist fields a page request fills. */
private val playlistPageRequestType = findClassDirect { playlistPageSerializer(this).declaredClass!! }

/** One named argument of the native request serializer. */
private fun DexKitBridge.playlistPageRequestField(label: String): FieldData =
    playlistPageRequestFieldFingerprint(
        playlistPageRequestType(this).descriptor, playlistPageSerializer(this).name, label
    ).match().instructionMatches.last().instruction.fieldRef!!

/** The playlist a page request belongs to. */
private val playlistPagePlaylist = findFieldDirect { playlistPageRequestField("playlistId") }

/** The serialized parameters of a playlist window. */
private val playlistPageParams = findFieldDirect { playlistPageRequestField("params") }

/** The index of the window inside the playlist. */
private val playlistPageIndex = findFieldDirect {
    playlistPageIndexFingerprint(playlistPageRequestType(this).descriptor, playlistPageSerializer(this).name)
        .match().instructionMatches.first().instruction.fieldRef!!
}

/** The native setter of the parameters of a request. */
private val playlistPageParamsSetter = findMethodDirect {
    NativeRequestStringSetterFingerprint(
        playlistPageRequestType(this).descriptor, playlistPageParams(this).name
    ).match().method
}

/** The click tracking field of the request base class, which a read-only request must fill too. */
private val playlistPageTracking = findFieldDirect {
    NativeBaseRequestDescriptionFingerprint.match().instructionMatches[1].instruction.fieldRef!!
}

/** The factory that creates a request for one video ID. */
private val playlistPageFactory = findMethodDirect {
    playlistPageFactoryFingerprint(playlistPageRequestType(this).descriptor).match().method
}

/** The native preparation that fills a page request, whose third store is the entry ID. */
private val playlistPageEntryId = findFieldDirect {
    playlistPagePreparationFingerprint(
        playlistPageRequestType(this).descriptor,
        playlistPageFactory(this),
        playlistPageParamsSetter(this)
    ).match().instructionMatches[3].instruction.fieldRef!!
}

/** The watch response a page request builds, which is the response the queue engine receives. */
private fun DexKitBridge.playlistPageResponse(): String =
    NativeQueueReceiveFingerprint(nativeQueueWrapperClass(this).descriptor).match()
        .method.paramTypes.single().descriptor

/** The panel field of that response. */
private val playlistPageResponsePanel = findFieldDirect {
    NativeQueueReceiveFingerprint(nativeQueueWrapperClass(this).descriptor).match()
        .instructionMatches[1].instruction.fieldRef!!
}

/** The method of the playback service that performs a page request and returns its response. */
private val playlistPageBlocking = findMethodDirect {
    playlistPageServiceFingerprint(playlistPageRequestType(this).descriptor, playlistPageResponse())
        .match().method
}

/** The response envelope around the server response. */
private val playlistPageResponseConstructor = findMethodDirect {
    nativeQueueResponseConstructorFingerprint(playlistPageResponse()).match().method
}

/** The navigation sets the model of the panel carries. */
private fun DexKitBridge.playlistPageNavigationType(): String =
    PlaylistModelDescriptionFingerprint.match().method
        .findFieldFromToString(", navigationSets=").type.descriptor

/** The navigation sets of a page response. */
private val playlistPageResponseNavigation = findFieldDirect {
    nativeQueueResponseNavigationFingerprint(playlistPageResponse(), playlistPageNavigationType())
        .match().instructionMatches.single().instruction.fieldRef!!
}

/** The selector of a navigation set, which the response wrapper calls with all four flags off. */
private val playlistPageNavigationSelector = findMethodDirect {
    playlistPageNavigationSelectorFingerprint(playlistPageNavigationType()).match().method
}

/** The navigation message of the selected set. */
private val playlistPageNavigationProto = findFieldDirect {
    val selector = playlistPageNavigationSelector(this)
    val set = selector.returnType!!
    val constructor = set.methods.single { it.name == "<init>" }
    set.fields.single { it.type == constructor.paramTypes.first() }
}

/** The dispatcher a page request is handed to. */
private val playlistPageDispatch = findMethodDirect {
    playlistPageDispatchFingerprint(playlistPageBlocking(this)).match()
        .instructionMatches.single().instruction.methodRef!!
}

/** The context of that dispatcher, which the native transport requires. */
private val playlistPageContext = findMethodDirect {
    playlistPageContextFingerprint(playlistPageBlocking(this)).match()
        .instructionMatches.single().instruction.methodRef!!
}

/** The asynchronous request of the dispatcher, which returns the cancellable future. */
private val playlistPageAsyncRequest = findMethodDirect {
    playlistPageAsyncRequestFingerprint(playlistPageDispatch(this)).match().method
}

/** The executor of the native transport. */
private val playlistPageExecutor = findMethodDirect {
    playlistPageExecutorFingerprint(playlistPageAsyncRequest(this)).match()
        .instructionMatches.first().instruction.methodRef!!
}

/** The field of the service that holds its dispatcher. */
private val playlistPageDispatchOwner = findFieldDirect {
    playlistPageDispatchOwnerFingerprint(playlistPageBlocking(this), playlistPageDispatch(this))
        .match().instructionMatches.first().instruction.fieldRef!!
}

/** The field of the service that holds its request factory, before 21.44. */
private val playlistPageFactoryField = findFieldDirect {
    val service = playlistPageBlocking(this).declaredClass!!
    val factory = playlistPageFactory(this)
    service.fields.single { it.type.descriptor == factory.declaredClass!!.descriptor }
}

/** 21.44 resolves the request factory through a provider of the service instead of a direct field. */
private fun DexKitBridge.playlistPageFactoryProvider(): Pair<FieldData, MethodData>? =
    playlistPageFactoryProviderFingerprint(
        playlistPageBlocking(this).declaredClass!!.descriptor, playlistPageFactory(this)
    ).matchAllOrNull()?.single()?.let { match ->
        match.instructionMatches.first().instruction.fieldRef!! to
                match.instructionMatches[1].instruction.methodRef!!
    }

/** The provider field of the service, which stays empty before 21.44. */
private val playlistPageFactoryProviderField = findFieldListDirect {
    playlistPageFactoryProvider()?.let { listOf(it.first) } ?: emptyList()
}

/** The provider call of that field. */
private val playlistPageFactoryProviderCall = findMethodListDirect {
    playlistPageFactoryProvider()?.let { listOf(it.second) } ?: emptyList()
}

/** The current-account contract of a native request, which its identity getter returns. */
internal data class NativeAccountContract(val type: String, val id: MethodData, val incognito: MethodData)

/** Also used by the native playlist queue. Resolving it does not install the Series tracker. */
internal fun DexKitBridge.resolveNativeAccountContract(): NativeAccountContract {
    val identity = AccountIdentityFingerprint.match().method
    val identityType = identity.declaredClass!!

    fun getters(field: FieldData): List<MethodData> =
        IdentityFieldGetterFingerprint(field, identityType.descriptor).matchAll()
            .map { it.method }
            .filter { method ->
                identityType.interfaces.any { type ->
                    IdentityInterfaceMethodFingerprint(method, type.descriptor).matchAllOrNull() != null
                }
            }

    val idGetter = getters(identity.findFieldFromToString("AccountIdentity{getId=")).single()
    val signedOut = SignedOutIdentityFingerprint.match().method.declaredClass!!
    // Two accessors read the same field on a signed-in identity. On a pseudonymous identity,
    // one means unauthenticated (true) and the actual incognito accessor is false.
    val incognitoGetter = getters(identity.findFieldFromToString(", isIncognito="))
        .single { candidate ->
            SignedOutIncognitoFingerprint(candidate, signedOut.descriptor).matchAllOrNull() != null
        }
    val contract = identityType.interfaces.single { type ->
        IdentityInterfaceMethodFingerprint(idGetter, type.descriptor).matchAllOrNull() != null
    }
    return NativeAccountContract(contract.descriptor, idGetter, incognitoGetter)
}

/** The identity getter of the base request class and the two accessors of the account contract. */
private val playlistPageAccount = findMethodListDirect {
    val account = resolveNativeAccountContract()
    listOf(
        NativeRequestIdentityFingerprint(account.type).match().method,
        account.id,
        account.incognito,
    )
}

/**
 * The playback service of the native watch page, which stands in for the Source interface upstream
 * installs: every member does the work of the bridge method of that interface, with the messages of
 * the app crossing as bytes because the module keeps its own schema.
 */
internal class PlaylistPageSource(
    private val service: Any,
    private val members: PlaylistPageMembers
) : PlaylistPages.Source {
    override fun patch_getPlaylistAccount(): PlaylistPage.Account? = members.account(service)

    override fun patch_requestPlaylistPage(
        playlistId: String,
        videoId: String?,
        index: Int,
        params: String,
        entryId: String,
        tracking: ByteArray,
        account: PlaylistPage.Account?
    ): Future<*>? = members.request(
        service, playlistId, videoId, index, params, entryId, tracking, account
    )

    override fun patch_getPlaylistPage(response: Any?): MessageLite? = members.panel(response)

    override fun patch_getPlaylistNavigation(response: Any?): MessageLite? = members.navigation(response)
}

/**
 * The members of the page service that the bridges of the Source interface read, and the native work
 * their smali repeats: creating a request, reading the account it is sent for, filling it and handing
 * it to the native transport, then reading the panel and the navigation of its response.
 */
internal class PlaylistPageMembers(
    private val factory: Method,
    private val factoryField: Field?,
    private val factoryProvider: Pair<Field, Method>?,
    private val playlist: Field,
    private val index: Field,
    private val entryId: Field,
    private val tracking: Field,
    private val paramsSetter: Method,
    private val dispatchOwner: Field,
    private val asyncRequest: Method,
    private val executor: Method,
    private val context: Method,
    private val responseConstructor: Constructor<*>,
    private val responsePanel: Field,
    private val responseNavigation: Field,
    private val navigationSelector: Method,
    private val navigationProto: Field,
    private val identity: Method,
    private val identityId: Method,
    private val identityIncognito: Method
) {
    /** The factory the service reaches directly, or through the provider of newer builds. */
    private fun factoryOf(service: Any): Any? {
        factoryProvider?.let { (field, call) -> return bridgeCall(call, bridgeField(field, service)) }
        return bridgeField(factoryField ?: return null, service)
    }

    /** A new request of the service, which the native factory fills from the defaults of the app. */
    private fun newRequest(service: Any, videoId: String?): Any? {
        val owner = factoryOf(service) ?: return null
        return factory.call(owner, videoId)
    }

    /** The account a request is sent for, which a signed-out request has only for one provider. */
    private fun accountOf(request: Any): PlaylistPage.Account? {
        val account = bridgeCall(identity, request) ?: return null
        val id = bridgeCall(identityId, account) as? String ?: return null
        val incognito = bridgeCall(identityIncognito, account) as? Boolean ?: return null
        return PlaylistPage.Account(id, incognito)
    }

    /** The account the service requests its pages for. */
    fun account(service: Any): PlaylistPage.Account? = newRequest(service, null)?.let(::accountOf)

    /**
     * One authenticated page of a playlist, which never runs a player response handler.
     *
     * The request is only filled when the account it would be sent for is the account the extension
     * expects, and the future of the native transport is returned so a timeout cancels it.
     */
    fun request(
        service: Any,
        playlistId: String,
        videoId: String?,
        index: Int,
        params: String,
        entryId: String,
        tracking: ByteArray,
        account: PlaylistPage.Account?
    ): Future<*>? {
        val request = newRequest(service, videoId) ?: return null
        val current = accountOf(request) ?: return null
        if (account == null || account != current) return null

        playlist.write(request, playlistId)
        this.index.write(request, index)
        this.entryId.write(request, entryId)
        paramsSetter.call(request, params)
        // Native validation requires non-null tracking even for read-only requests.
        this.tracking.write(request, tracking)

        val executor = this.executor.call(null) ?: return null
        val context = bridgeCall(this.context, service) ?: return null
        val dispatcher = bridgeField(dispatchOwner, service) ?: return null
        return asyncRequest.call(dispatcher, request, executor, context) as? Future<*>
    }

    /** The panel of a page response, as the message of the module with the same schema. */
    fun panel(response: Any?): MessageLite? {
        val envelope = envelope(response) ?: return null
        val panel = bridgeField(responsePanel, envelope) ?: return null
        val bytes = appMessageBytes(panel) ?: return null
        return PlaylistQueueRenderer.Panel.parseFrom(bytes)
    }

    /** The navigation of a page response, which decides whether the playlist ended. */
    fun navigation(response: Any?): MessageLite? {
        val envelope = envelope(response) ?: return null
        val navigationSets = bridgeField(responseNavigation, envelope) ?: return null
        val selected = navigationSelector.call(navigationSets, false, false, false, false) ?: return null
        val navigation = bridgeField(navigationProto, selected) ?: return null
        val bytes = appMessageBytes(navigation) ?: return null
        return PlaylistQueueRenderer.PlaylistNavigation.parseFrom(bytes)
    }

    /** The response envelope around the raw watch response the native transport returns. */
    private fun envelope(response: Any?): Any? {
        if (response == null) return null
        return runCatching { responseConstructor.newInstance(response) }.getOrNull()
    }
}

/**
 * Fetches authenticated source windows without invoking the native player response handlers.
 *
 * Upstream adds the four bridges of the Source interface to the playback service of the watch page
 * and attaches the source at every return of its constructor. Here the bridges are the proxy above and
 * the attach is the hook of that constructor, which runs before any request of the extension can ask
 * for the current source.
 */
internal fun PatchExecutor.installPlaylistPages() {
    val service = ::playlistPageBlocking.method.declaringClass
    val providerFields = ::playlistPageFactoryProviderField.fieldList
    val providerCalls = ::playlistPageFactoryProviderCall.dexMethodList.map { it.toMethod() }
    val account = ::playlistPageAccount.dexMethodList.map { it.toMethod() }
    val selector = ::playlistPageNavigationSelector.method
    val constructors = service.declaredConstructors

    check(providerFields.size == providerCalls.size) {
        "Native playlist queue: the request factory provider of the page service is incomplete"
    }
    check(::playlistPageEntryId.dexField != ::playlistPagePlaylist.dexField) {
        "Native playlist entry ID must be distinct from playlist ID"
    }
    check(::playlistPageDispatchOwner.field.declaringClass == service) {
        "Native playlist dispatcher must receive the loaded owner"
    }
    check(selector.parameterCount == 4 && selector.parameterTypes.all { it == Boolean::class.javaPrimitiveType }) {
        "Native playlist queue: the navigation selector takes other flags than the four of the response wrapper"
    }
    check(account.size == 3) {
        "Native playlist queue: the account contract of a native request is incomplete"
    }
    check(constructors.isNotEmpty()) {
        "Native playlist queue: the page service has no constructor to attach its source to"
    }

    val members = PlaylistPageMembers(
        factory = ::playlistPageFactory.method,
        factoryField = if (providerFields.isEmpty()) ::playlistPageFactoryField.field else null,
        factoryProvider = providerFields.singleOrNull()?.let { it to providerCalls.single() },
        playlist = ::playlistPagePlaylist.field,
        index = ::playlistPageIndex.field,
        entryId = ::playlistPageEntryId.field,
        tracking = ::playlistPageTracking.field,
        paramsSetter = ::playlistPageParamsSetter.method,
        dispatchOwner = ::playlistPageDispatchOwner.field,
        asyncRequest = ::playlistPageAsyncRequest.method,
        executor = ::playlistPageExecutor.method,
        context = ::playlistPageContext.method,
        responseConstructor = ::playlistPageResponseConstructor.constructor.apply { isAccessible = true },
        responsePanel = ::playlistPageResponsePanel.field,
        responseNavigation = ::playlistPageResponseNavigation.field,
        navigationSelector = selector,
        navigationProto = ::playlistPageNavigationProto.field,
        identity = account[0],
        identityId = account[1],
        identityIncognito = account[2],
    )

    constructors.forEach { constructor ->
        constructor.hookMethod {
            after { param ->
                val owner = param.thisObject ?: return@after
                queueAction("attachPlaylistPages") {
                    PlaylistPages.attach(PlaylistPageSource(owner, members))
                }
            }
        }
    }
}
