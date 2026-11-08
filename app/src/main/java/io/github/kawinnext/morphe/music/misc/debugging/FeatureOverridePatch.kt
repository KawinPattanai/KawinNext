package io.github.kawinnext.morphe.music.misc.debugging

import io.github.libxposed.api.XposedInterface
import io.github.kawinnext.atLast
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.shared.misc.debugging.experimentalBooleanFeatureFlagFingerprint
import io.github.kawinnext.patch

private val overrides = mutableMapOf<Long, (Boolean) -> Boolean>()

fun insertLiteralOverride(id: Long, override: (Boolean) -> Boolean) {
    overrides[id] = override
}

val FeatureOverride = patch {
    ::experimentalBooleanFeatureFlagFingerprint.hookMethod {
        priority = XposedInterface.PRIORITY_DEFAULT - 1
        after {
            val id = it.args.atLast(2) as Long
            val orig = it.result as Boolean
            val match = overrides[id] ?: return@after
            it.result = match(orig)
        }
    }
}
