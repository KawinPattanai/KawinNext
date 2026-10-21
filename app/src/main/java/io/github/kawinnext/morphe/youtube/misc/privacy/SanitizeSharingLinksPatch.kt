package io.github.kawinnext.morphe.youtube.misc.privacy

import io.github.kawinnext.morphe.shared.misc.privacy.SanitizeSharingLinks
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch

val SanitizeSharingLinks = patch(
    name = "Sanitize sharing links",
    description = "Removes the tracking query parameters from shared links."
) {
    SanitizeSharingLinks(
        preferenceScreen = PreferenceScreen.MISC,
        replaceLinksWithShortener = true
    )
}