package io.github.kawinnext.morphe.music.misc.privacy

import io.github.kawinnext.patch
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.misc.privacy.SanitizeSharingLinks

val SanitizeSharingLinks = patch(
    name = "Sanitize sharing links",
    description = "Removes the tracking query parameters from shared links."
) {
    SanitizeSharingLinks(
        preferenceScreen = PreferenceScreen.MISC,
        replaceMusicLinksWithYouTube = true
    )
}