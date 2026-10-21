package io.github.kawinnext.morphe.youtube.layout.hide.shelves

import app.morphe.extension.youtube.patches.components.HorizontalShelvesFilter
import io.github.kawinnext.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.kawinnext.morphe.youtube.layout.buttons.navigation.NavigationBar
import io.github.kawinnext.morphe.youtube.misc.engagement.EngagementPanelHook
import io.github.kawinnext.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.kawinnext.morphe.youtube.misc.litho.observer.LayoutReloadObserver
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.patch

val HideHorizontalShelves = patch {
    dependsOn(
        LithoFilter,
        PlayerTypeHook,
        NavigationBar,
        EngagementPanelHook,
        LayoutReloadObserver
    )

    addLithoFilter(HorizontalShelvesFilter())
}