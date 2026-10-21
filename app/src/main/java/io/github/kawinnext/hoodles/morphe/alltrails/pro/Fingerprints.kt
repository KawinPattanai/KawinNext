package io.github.kawinnext.hoodles.morphe.alltrails.pro

import io.github.kawinnext.morphe.Fingerprint

object IsProFingerprint : Fingerprint(
    name = "isPro",
    returnType = "Z",
)

object GetSubscriptionTierFingerprint : Fingerprint(
    name = "getSubscriptionTier",
    returnType = "Ljava/lang/String;",
)
