package io.github.kawinnext.morphe.reddit

import io.github.kawinnext.morphe.reddit.ad.HideAds
import io.github.kawinnext.morphe.reddit.misc.guest.startAsGuestPatch
import io.github.kawinnext.morphe.reddit.misc.privacy.SanitizeSharingLinks

val RedditPatches = arrayOf(
    HideAds,
    SanitizeSharingLinks,
    startAsGuestPatch,
)