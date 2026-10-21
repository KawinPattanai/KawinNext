package io.github.kawinnext.revanced.photomath.misc.unlock.bookpoint

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.fingerprint

val isBookpointEnabledFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Z")
    parameters()
    strings(
        "NoGeoData",
        "NoCountryInGeo",
        "RemoteConfig",
        "GeoRCMismatch"
    )
}