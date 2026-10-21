package io.github.kawinnext.morphe.youtube.misc.litho.observer

import app.morphe.extension.youtube.patches.LayoutReloadObserverPatch
import io.github.kawinnext.morphe.shared.misc.litho.node.hookTreeNodeResult
import io.github.kawinnext.morphe.youtube.misc.litho.node.TreeNodeElementHook
import io.github.kawinnext.patch


val LayoutReloadObserver = patch(
    description = "Hooks a method to detect in the extension when the RecyclerView at the bottom of the player is redrawn.",
) {
    dependsOn(
        TreeNodeElementHook
    )

    hookTreeNodeResult(LayoutReloadObserverPatch::onLazilyConvertedElementLoaded)
}