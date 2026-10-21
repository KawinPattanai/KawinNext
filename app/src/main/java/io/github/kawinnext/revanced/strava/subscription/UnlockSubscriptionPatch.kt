package io.github.kawinnext.revanced.strava.subscription

import io.github.kawinnext.patch

val UnlockSubscription = patch(
    name = "Unlock subscription features",
    description = "Unlocks \"Routes\", \"Matched Runs\" and \"Segment Efforts\".",
) {
    ::getSubscribedFingerprint.hookMethod {
        before { param ->
            param.result = true
        }
    }
}