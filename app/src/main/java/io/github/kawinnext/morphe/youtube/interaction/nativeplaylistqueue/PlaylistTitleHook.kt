/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import android.widget.TextView
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findMethodDirect
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field

// Upstream adds the PlaylistTitle interface to the engagement header and injects one call at the
// point the title TextView is stored, and one at every return of the method that binds a panel.
// Here the interface is a module proxy that reads the same two fields, and the two call sites are
// the two hooked methods: the field is written before the inflate method returns, and a binding
// method returns after the panel it binds.

/** The panel of the playlist header, whose fields the toString of its model names. */
private fun DexKitBridge.playlistPanelType(): String =
    PlaylistModelDescriptionFingerprint.match().method.findFieldFromToString(", playlistPanel=")
        .type.descriptor

/** The method that inflates the header and stores its title view. */
private val playlistTitleInflate = findMethodDirect { PlaylistTitleFingerprint.match().method }

/** The title view field of the header. */
private val playlistTitleView = findFieldDirect {
    PlaylistTitleFingerprint.match().instructionMatches.last().instruction.fieldRef!!
}

/** The method that binds a panel to the header. */
private val playlistTitleBinding = findMethodDirect {
    val header = PlaylistTitleFingerprint.match().method.declaredClass!!.descriptor
    playlistTitleBindingFingerprint(header, playlistPanelType()).match().method
}

/** The panel field of the header. */
private val playlistTitlePanel = findFieldDirect {
    val header = PlaylistTitleFingerprint.match().method.declaredClass!!.descriptor
    playlistTitleBindingFingerprint(header, playlistPanelType()).match()
        .instructionMatches.single().instruction.fieldRef!!
}

/**
 * The engagement header, which stands in for the PlaylistTitle interface upstream adds to its
 * class: the two bridge methods of that interface read the panel and the title field.
 *
 * The panel answers with the proxy of the panel, which the controller of the playlist answers with too
 * and which the extension compares by identity against it.
 */
private class PlaylistTitleProxy(
    private val header: Any,
    private val panel: Field,
    private val title: Field
) : NativePlaylistQueuePatch.PlaylistTitle {
    override fun patch_getPanel(): Any? = bridgeField(panel, header)?.let(::queuePanelProxy)

    override fun patch_getTitle(): TextView? = bridgeField(title, header) as? TextView
}

/**
 * Observes the native playlist title after inflation and after model rebinding.
 *
 * Upstream injects after the store of the title view, so a call that returns without storing it
 * must stay silent, which the null test below keeps. The binding method itself can run before any
 * title view exists, and the extension rechecks the panel afterwards on the main thread.
 */
internal fun PatchExecutor.installPlaylistTitle() {
    val panelField = ::playlistTitlePanel.field
    val titleField = ::playlistTitleView.field
    val proxies = ProxyCache { header: Any -> PlaylistTitleProxy(header, panelField, titleField) }

    ::playlistTitleInflate.method.hookMethod {
        after { param ->
            // The title view is stored before the method returns, which is where upstream injects.
            if (bridgeField(titleField, param.thisObject) == null) return@after
            notifyTitleBound(param.thisObject, proxies)
        }
    }

    ::playlistTitleBinding.method.hookMethod {
        after { param -> notifyTitleBound(param.thisObject, proxies) }
    }
}

/** Hands one bound header to the extension, which resizes the title of an owned queue only. */
private fun notifyTitleBound(header: Any?, proxies: ProxyCache<PlaylistTitleProxy>) {
    if (header == null) return
    queueAction("onPlaylistTitleBound") {
        NativePlaylistQueuePatch.onPlaylistTitleBound(proxies.get(header))
    }
}
