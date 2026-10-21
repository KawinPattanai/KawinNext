package io.github.kawinnext.revanced.strava.upselling

import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val getModulesFingerprint = fingerprint {
    opcodes(Opcode.IGET_OBJECT)
    methodMatcher { name = "getModules" }
    classMatcher { className(".GenericLayoutEntry", StringMatchType.EndsWith) }
}
