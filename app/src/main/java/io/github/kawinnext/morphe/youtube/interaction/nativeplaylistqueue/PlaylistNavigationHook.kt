/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.NavigatorOwner
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import java.lang.reflect.Field

// Upstream gives the playlist controls and the controller that creates them the NavigatorOwner
// interface, whose bridge returns the navigator of the playlist, and then installs the notification,
// the repeat control and the player controls of that navigator. A module cannot add a member to a
// class of the app, so the interface is the proxy below, and the three installations take the same
// navigator the upstream function passes them.

/** The request of one navigation, which the controls of the playlist dispatch. */
private val playlistNavigationRequestClass = findClassDirect {
    val enum = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    playlistNavigationRequestFingerprint(enum).match().method.declaredClass!!
}

/** The creation of the playlist controls, which keeps the navigator and stores the controls. */
private val playlistControlsCreation = findMethodDirect {
    PlaylistControlsCreationFingerprint.match().method
}

/** The navigator the playlist controls are created with. */
private val playlistControlsNavigator = findClassDirect {
    PlaylistControlsCreationFingerprint.match().instructionMatches[4].instruction.classRef!!
}

/** The field of the controller that holds the controls of the playlist. */
private val playlistControlsField = findFieldDirect {
    PlaylistControlsCreationFingerprint.match().instructionMatches.last().instruction.fieldRef!!
}

/** The navigator field of each implementation of the controls. */
private val playlistControlsNavigators = findFieldListDirect {
    val creation = PlaylistControlsCreationFingerprint.match()
    val navigator = creation.instructionMatches[4].instruction.classRef!!.descriptor
    val controls = creation.instructionMatches.last().instruction.fieldRef!!.type.descriptor
    // 21.28+ declares the controls as an interface with two implementations, and only an
    // implementation declares the constructor that stores the navigator. Older versions declare the
    // single class the interface replaces.
    val implementations = playlistControlsImplementationFingerprint(navigator, controls)
        .matchAllOrNull()
        ?: listOf(playlistControlsConstructorFingerprint(navigator, controls).match())
    implementations.map { it.instructionMatches.single().instruction.fieldRef!! }
}

/**
 * The controls of the playlist, or the controller that holds them, which stands in for the
 * NavigatorOwner interface upstream adds to both.
 *
 * The bridge reads the navigator the controls were created with, which the controller reaches
 * through the controls it stores. The proxies are kept per owner, because the extension compares the
 * navigator of a playlist by identity with the one it was given.
 */
private class NavigatorOwnerProxy(
    private val owner: Any,
    private val read: (Any) -> Any?
) : NavigatorOwner {
    override fun patch_getNavigator(): Any? = read(owner)
}

/** The controls of the playlist and the navigators their implementations keep. */
internal class PlaylistControlsNavigators(
    private val controls: Field,
    private val navigators: Map<Class<*>, Field>
) {
    private val proxies = ProxyCache { owner: Any -> NavigatorOwnerProxy(owner, ::navigator) }

    fun proxy(owner: Any): NavigatorOwner = proxies.get(owner)

    /** The navigator of the controls a controller holds, or of the controls themselves. */
    fun navigator(owner: Any): Any? {
        val instance = if (controls.declaringClass.isInstance(owner)) {
            owner
        } else {
            bridgeField(controls, owner) ?: return null
        }
        val field = navigators.entries.firstOrNull { it.key.isInstance(instance) }?.value ?: return null
        return bridgeField(field, instance)
    }
}

/** The controls table of the installed hook, which the row and panel hooks answer with. */
private var playlistControlsOwners: PlaylistControlsNavigators? = null

/**
 * The navigator owner proxy of a playlist controller or of its controls, or null before this hook is
 * installed.
 */
internal fun playlistNavigatorOwner(owner: Any?): NavigatorOwner? =
    owner?.let { playlistControlsOwners?.proxy(it) }

/**
 * Resolves the shared native navigation owner of the controls and of the playback engine.
 *
 * Upstream gives the controls and the controller the NavigatorOwner bridge, then installs the
 * notification of the playlist with the request class and the intent enum, and the repeat control
 * and player controls of the navigator the controls were created with. Here the bridges are the
 * proxies above, which the row and panel hooks of the module ask for, and the three installations
 * take the same members.
 *
 * @param controllerType The playlist controller, which creates the controls of the playlist.
 */
internal fun PatchExecutor.installPlaylistNavigation(controllerType: Class<*>) {
    val enumType = NavigationIntentEnumFingerprint.declaredClass
    val wrapperType = ::playlistNavigationRequestClass.clazz
    val creation = ::playlistControlsCreation.method
    val controls = ::playlistControlsField.field
    val navigatorType = ::playlistControlsNavigator.clazz
    val navigators = ::playlistControlsNavigators.fieldList

    check(creation.declaringClass == controllerType) {
        "Native playlist queue: the playlist controls are created by another controller"
    }
    check(controls.declaringClass == controllerType) {
        "Native playlist queue: the playlist controls belong to another controller"
    }
    check(navigators.isNotEmpty()) {
        "Native playlist queue: playlist controls are missing"
    }
    check(navigators.all { it.type == navigatorType }) {
        "Native playlist queue: the playlist controls keep navigators of another class"
    }

    playlistControlsOwners = PlaylistControlsNavigators(
        controls = controls,
        navigators = navigators.associateBy(Field::getDeclaringClass),
    )

    installPlaylistNotification(wrapperType, enumType)
    installPlaylistRepeat(navigatorType)
    installPlaylistPlayerControls(navigatorType, enumType, wrapperType)
}
