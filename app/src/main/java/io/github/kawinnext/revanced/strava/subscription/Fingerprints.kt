package io.github.kawinnext.revanced.strava.subscription

import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val getSubscribedFingerprint = fingerprint {
    opcodes(Opcode.IGET_BOOLEAN)
    classMatcher { className(".SubscriptionDetailResponse", StringMatchType.EndsWith) }
    methodMatcher { name = "getSubscribed" }
}