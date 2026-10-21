package io.github.kawinnext.revanced.photomath.misc.unlock.plus

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val isPlusUnlockedFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Z")
    strings("genius")
    classMatcher { className(".User", StringMatchType.EndsWith) }
}