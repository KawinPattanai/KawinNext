/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4404
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.scopedHook
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream adds two interfaces to classes of the app: PagingOwner to the playlist controller, whose
// bridges return its native list and the loading frame of that list, and LoadingView to the loading
// frame, whose bridge writes the state that shows or hides the progress. It then injects one call
// inside the near-end branch of the native scroll listener. A module cannot add an interface to a
// class of the app, so the same members are read by the proxies below, and the branch is observed
// through the predicate that guards it instead of at the point the smali call would sit.

/** LoadingFrameLayout's native states: 1 is progress and 2 is bound content. */
private const val PROGRESS_STATE = 1
private const val CONTENT_STATE = 2

/** The list of the playlist panel, whose constructor owns the reorder education resource. */
private val playlistListType = findClassDirect {
    PlaylistListConstructorFingerprint.match().method.declaredClass!!
}

/** The controller of the playlist panel, which stores that list. */
private val playlistControllerType = findClassDirect {
    PlaylistControllerConstructorFingerprint.match().method.declaredClass!!
}

/** The only field of the controller whose type is the list. */
private val playlistControllerList = findFieldDirect {
    val list = playlistListType().descriptor
    playlistControllerType().fields.single { it.type.descriptor == list }
}

/** The only field of the list whose type is the loading frame. */
private val playlistListFrame = findFieldDirect {
    playlistListType().fields.single { it.type.descriptor == PLAYLIST_LOADING_FRAME }
}

/** The existing setter of the loading frame state, which private builds hide from the outside. */
private val playlistLoadingState = findMethodDirect {
    PlaylistLoadingStateFingerprint.match().method
}

/** The scroll method of the list, which requests the next page at the near end of the list. */
private val playlistScroll = findMethodDirect {
    playlistNativeScrollFingerprint(playlistListType().descriptor).match().method
}

/** The near-end predicate of that branch, which the native listener evaluates first. */
private val playlistNearEnd = findMethodDirect {
    playlistNativeScrollFingerprint(playlistListType().descriptor).match()
        .instructionMatches[1].instruction.methodRef!!
}

/** The list field that the accepted branch reads. */
private val playlistScrollList = findFieldDirect {
    playlistNativeScrollFingerprint(playlistListType().descriptor).match()
        .instructionMatches.last().instruction.fieldRef!!
}

/**
 * The two paging interfaces over one playlist controller: the list it stores, and the loading frame
 * of that list whose state the native setter writes.
 *
 * The list is only read from a controller of the class that declares its field, which is the check
 * of the bridge method the upstream patch would add there.
 */
internal class PlaylistPaging(
    private val list: Field,
    private val frame: Field,
    private val state: Method
) {
    private val owners = ProxyCache { controller: Any -> Owner(controller) }

    fun owner(controller: Any): NativePlaylistQueuePatch.PagingOwner = owners.get(controller)

    private inner class Owner(private val controller: Any) : NativePlaylistQueuePatch.PagingOwner {
        override fun patch_getPlaylistList(): Any? = bridgeField(list, controller)

        override fun patch_getPlaylistLoadingView(): NativePlaylistQueuePatch.LoadingView? {
            val list = patch_getPlaylistList() ?: return null
            return bridgeField(frame, list)?.let(::Loading)
        }
    }

    private inner class Loading(private val view: Any) : NativePlaylistQueuePatch.LoadingView {
        override fun patch_setPlaylistLoading(loading: Boolean) {
            state.call(view, if (loading) PROGRESS_STATE else CONTENT_STATE)
        }
    }
}

/** The paging interfaces over the playlist controller, which its proxy answers with. */
internal var playlistPaging: PlaylistPaging? = null

/**
 * Observes the accepted branch of the native scroll listener, which asks for the next page.
 *
 * Upstream adds the paging interfaces to the controller and injects one call in that branch. Here
 * the branch is reached through the two things it is made of: the scroll method is hooked for its
 * scope and the near-end predicate for its result, so a scroll that keeps the native request also
 * reports the list it read, and a scroll that rejects the branch reports nothing. The extension
 * keeps its own guards, including the setting that decides whether pages are fetched on scroll.
 */
internal fun PatchExecutor.installPlaylistPaging() {
    playlistPaging = PlaylistPaging(
        list = ::playlistControllerList.field,
        frame = ::playlistListFrame.field,
        state = ::playlistLoadingState.method,
    )

    val list = ::playlistScrollList.field
    ::playlistScroll.method.hookMethod(scopedHook(::playlistNearEnd.method) {
        after { param ->
            if (param.result != true) return@after
            val listener = outerParam.thisObject ?: return@after
            val playlist = bridgeField(list, listener) ?: return@after
            queueAction("onNativePlaylistScroll") {
                NativePlaylistQueuePatch.onNativePlaylistScroll(playlist)
            }
        }
    })
}
