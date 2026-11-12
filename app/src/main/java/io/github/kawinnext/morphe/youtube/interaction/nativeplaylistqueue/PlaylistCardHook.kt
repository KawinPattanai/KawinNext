/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4404
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import android.support.v7.widget.RecyclerView
import android.view.View
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.PlaylistCardSource
import com.google.protobuf.MessageLite
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream adds two interfaces to classes of the app and lets the extension read them: TapActions
// on the mounted card node, whose bridge returns the tap action list of its click handler, and
// TapCommand on the action itself, whose bridge decodes the future that action holds. A module
// cannot add an interface to a class of the app, so the same members are read by the proxies below
// and the tap commands they produce are handed to the extension's own tap action reader. The
// extension asks for them through its view reader, whose empty result is what the proxies fill in.

/** The node a mounted card stores in the tag of its host, which upstream gives TapActions. */
private val cardNodeStore = findFieldDirect {
    playlistNodeStoreFingerprint().match().instructionMatches.single().instruction.fieldRef!!
}

/** The click listener of the card, which is built from the tap handler of the node. */
private val cardClickListener = findMethodDirect { PlaylistClickListenerFingerprint.match().method }

/** The handler field of the node, whose type holds the owner of the actions. */
private val cardTapHandler = findFieldDirect {
    playlistClickFieldFingerprint(
        cardClickListener(this).declaredClass!!.descriptor,
        Opcode.IGET_OBJECT
    ).match().instructionMatches.single().instruction.fieldRef!!
}

/** The owner field of the click listener, which holds the actions of a tap. */
private val cardTapOwner = findFieldDirect {
    playlistClickFieldFingerprint(cardTapHandler(this).type.descriptor, Opcode.IGET_OBJECT)
        .match().instructionMatches.single().instruction.fieldRef!!
}

/** The view event field of the click listener, which identifies the tap dispatch of the owner. */
private val cardTapEvent = findFieldDirect {
    playlistClickFieldFingerprint(null, Opcode.IPUT_OBJECT).match()
        .instructionMatches.single().instruction.fieldRef!!
}

/** The field that binds a node to its own click handler while the card is mounted. */
private val cardNodeClick = findFieldDirect {
    playlistClickMountFingerprint(
        cardNodeStore(this).type.descriptor,
        cardClickListener(this).declaredClass!!.descriptor,
        cardTapHandler(this).type.descriptor
    ).match().instructionMatches.first().instruction.fieldRef!!
}

/** The class of the tap owner, which dispatches the actions of the click to its callback. */
private val cardTapOwnerType = findClassDirect {
    PlaylistTapOwnerFingerprint.match().method.declaredClass!!
}

/** The tap actions list of the owner. */
private val cardTapActions = findFieldDirect {
    playlistTapActionsFingerprint(
        cardTapOwnerType(this).descriptor,
        cardTapEvent(this),
        cardTapHandler(this).type.descriptor
    ).match().instructionMatches[1].instruction.fieldRef!!
}

/** The callback that the owner hands its tap actions to, which every action implements. */
private val cardTapDispatch = findMethodDirect {
    playlistTapActionsFingerprint(
        cardTapOwnerType(this).descriptor,
        cardTapEvent(this),
        cardTapHandler(this).type.descriptor
    ).match().instructionMatches[2].instruction.methodRef!!
}

/**
 * One tap action of the list, which implements the interface of the owner's dispatch and holds the
 * future of its command. Upstream adds the TapCommand interface to this class.
 */
private val cardTapCommand = findMethodDirect {
    val dispatch = cardTapDispatch(this)
    playlistTapCommandFingerprint(
        dispatch.declaredClass!!.descriptor,
        dispatch.paramTypes.map { it.descriptor }
    ).match().method
}

/** The method that decodes the command a tap action holds. */
private val cardTapDecode = findMethodDirect {
    val dispatch = cardTapDispatch(this)
    playlistTapCommandFingerprint(
        dispatch.declaredClass!!.descriptor,
        dispatch.paramTypes.map { it.descriptor }
    ).match().instructionMatches.single().instruction.methodRef!!
}

/** The future field of a tap action, whose type the decode method of the action is declared on. */
private val cardTapFuture = findFieldDirect {
    playlistTapFutureFingerprint(
        cardTapCommand(this),
        cardTapDecode(this).declaredClass!!.descriptor,
        cardTapDecode(this)
    ).match().instructionMatches.first().instruction.fieldRef!!
}

/**
 * The tap actions of a mounted card node, which stands in for the TapActions interface upstream
 * adds to the class of the node: it reads the click handler of the node, then the owner of the
 * tap, then the list of actions the owner holds.
 */
internal class PlaylistTapActionsProxy(
    private val node: Any,
    private val handlerField: Field,
    private val ownerField: Field,
    private val actionsField: Field,
    private val ownerType: Class<*>
) : PlaylistCardSource.TapActions {
    override fun patch_getPlaylistTapActions(): List<*>? {
        val click = bridgeField(handlerField, node) ?: return null
        val owner = bridgeField(ownerField, click)?.takeIf(ownerType::isInstance) ?: return null
        return bridgeField(actionsField, owner) as? List<*>
    }
}

/**
 * One tap action of a mounted card, which stands in for the TapCommand interface upstream adds to
 * the class of the action: the command it holds is decoded by the same native method the bridge
 * calls, and the command crosses to the extension as the module's own copy of it.
 *
 * The interface returns a message, so an action whose command cannot be decoded reads as an empty
 * command, which the extension treats as an action without a playlist target.
 */
internal class PlaylistTapCommandProxy(
    private val action: Any,
    private val futureField: Field,
    private val decode: Method
) : PlaylistCardSource.TapCommand {
    override fun patch_getPlaylistTapCommand(): MessageLite =
        bridgeMessage(bridgeCall(decode, bridgeField(futureField, action))) {
            PlaylistQueueRenderer.ElementsCommand.parseFrom(it)
        } ?: PlaylistQueueRenderer.ElementsCommand.getDefaultInstance()
}

/** The extension's view reader, which finds no app object that implements its two interfaces. */
private val playlistCardSourceView = PlaylistCardSource::class.java
    .getDeclaredMethod("fromView", View::class.java)
    .apply { isAccessible = true }

/** The extension's tap action reader, which decides which collection a tapped card starts. */
private val playlistCardSourceActions = PlaylistCardSource::class.java
    .getDeclaredMethod("fromTapActions", List::class.java)
    .apply { isAccessible = true }

/**
 * Reads the playlist identity of a card that is mounted right now, the way upstream exposes it
 * through the two interfaces it adds to the app.
 *
 * Only the ancestors of the tapped view are walked, and the walk stops at the list that holds the
 * cards, because a shelf buffer can carry cards that the tap did not select.
 */
internal class PlaylistCardSourceReader(
    private val nodeType: Class<*>,
    private val commandType: Class<*>,
    private val ownerType: Class<*>,
    private val handlerField: Field,
    private val ownerField: Field,
    private val actionsField: Field,
    private val futureField: Field,
    private val decode: Method
) {
    fun selection(sender: View): PlaylistCardSource.Selection? {
        val nodeInfoId = ResourceUtils.getIdIdentifier(COMPONENT_NODE_INFO)
        if (nodeInfoId == 0) return null

        var view: View? = sender
        while (view != null && view !is RecyclerView) {
            val node = view.getTag(nodeInfoId)
            if (node != null && nodeType.isInstance(node)) {
                val selection = tapSelection(node)
                if (selection != null) return selection
            }
            view = view.parent as? View
        }
        return null
    }

    /** The selection of one node, decided by the extension from the commands of its actions. */
    private fun tapSelection(node: Any): PlaylistCardSource.Selection? =
        queueAction("fromTapActions") {
            val actions = PlaylistTapActionsProxy(
                node, handlerField, ownerField, actionsField, ownerType
            ).patch_getPlaylistTapActions() ?: return@queueAction null

            val commands = actions.mapNotNull { action ->
                if (action != null && commandType.isInstance(action)) {
                    PlaylistTapCommandProxy(action, futureField, decode)
                } else {
                    null
                }
            }
            playlistCardSourceActions.call(null, commands) as PlaylistCardSource.Selection?
        }

    private companion object {
        /** The view tag under which Litho keeps the NodeInfo of a mounted host. */
        const val COMPONENT_NODE_INFO = "component_node_info"
    }
}

/**
 * Reads the mounted card's native tap command without invoking or replacing its listener.
 *
 * Upstream only adds the two interfaces, because the extension reads them when a card is tapped.
 * The reader is registered here and the extension's view reader is filled in when it finds no app
 * object of its own, which is the same point of the tap that upstream's interfaces answer.
 */
internal fun PatchExecutor.installPlaylistCardSource() {
    val nodeType = ::cardNodeStore.type
    val commandType = classLoader.loadClass(::cardTapCommand.dexMethod.declaredClassName)
    val ownerType = ::cardTapOwnerType.clazz
    val handlerField = ::cardTapHandler.field
    val ownerField = ::cardTapOwner.field
    val actionsField = ::cardTapActions.field
    val futureField = ::cardTapFuture.field
    val decode = ::cardTapDecode.method

    check(ownerField.declaringClass == ::cardNodeClick.type) {
        "Native playlist queue: the click owner must belong to its mounted handler"
    }

    val reader = PlaylistCardSourceReader(
        nodeType, commandType, ownerType, handlerField, ownerField, actionsField, futureField, decode
    )
    playlistCardSourceView.hookMethod {
        after { param ->
            if (param.result != null) return@after
            val sender = param.args.firstOrNull() as? View ?: return@after
            param.result = reader.selection(sender)
        }
    }
}
