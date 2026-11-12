package io.github.kawinnext.morphe.music.misc.litho.filter

import io.github.kawinnext.morphe.shared.misc.litho.context.conversionContextPatch
import io.github.kawinnext.morphe.shared.misc.litho.filter.sharedLithoFilterPatch

val LithoFilter = sharedLithoFilterPatch(
    // YT Music does not ship the Upb feature flag.
    overrideUpbFeatureFlag = { false }
) {
    dependsOn(conversionContextPatch)
}
