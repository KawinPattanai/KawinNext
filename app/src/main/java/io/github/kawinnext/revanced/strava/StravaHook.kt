package io.github.kawinnext.revanced.strava

import io.github.kawinnext.revanced.strava.subscription.UnlockSubscription
import io.github.kawinnext.revanced.strava.upselling.DisableSubscriptionSuggestions

val StravaPatches = arrayOf(
    UnlockSubscription,
    DisableSubscriptionSuggestions
)