package io.github.kawinnext.morphe.youtube.misc.verticalscroll

import io.github.kawinnext.morphe.youtube.insertLiteralOverride
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_18_or_greater
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

val fixVerticalScrollPatch = patch(
    description = "Fixes issues with refreshing the feed when the first component is of type EmptyComponent."
) {
    dependsOn(VersionCheck)

    if (is_21_18_or_greater) {
        // Can cause issues with scrolling.
        insertLiteralOverride(45782902L)
    }

    CanScrollVerticallyFingerprint.hookMethod(scopedHook(::canScrollVerticallyMethod.method) {
        after { it.result = false }
    })
}
