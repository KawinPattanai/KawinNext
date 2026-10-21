package io.github.kawinnext.morphe.youtube.misc.litho.filter

import io.github.kawinnext.morphe.shared.misc.litho.context.conversionContextPatch
import io.github.kawinnext.morphe.shared.misc.litho.filter.sharedLithoFilterPatch
import io.github.kawinnext.morphe.youtube.misc.litho.rendernext.disableRenderNextPatch
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_20_22_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_15_or_greater
import io.github.kawinnext.morphe.youtube.misc.verticalscroll.fixVerticalScrollPatch

val LithoFilter = sharedLithoFilterPatch(
    // YouTube 20.22+ always uses the native Upb encode path.
    hookNonNativeBuffer = { !is_20_22_or_greater },
    // Flag was removed in 21.15+.
    overrideUpbFeatureFlag = { !is_21_15_or_greater },
    useLegacyLithoFiltering = { !is_20_22_or_greater }
) {
    dependsOn(
        fixVerticalScrollPatch,
        VersionCheck,
        conversionContextPatch,
        disableRenderNextPatch,
    )
}
