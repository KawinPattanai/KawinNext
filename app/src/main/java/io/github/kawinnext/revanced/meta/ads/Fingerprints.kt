package io.github.kawinnext.revanced.meta.ads

import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.strings

val adInjectorFingerprint = findMethodDirect {
    findMethod {
        matcher {
            strings("Is ad pod")
        }
    }.single()
}