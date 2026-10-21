package io.github.kawinnext.revanced.strava.upselling

import io.github.kawinnext.getObjectFieldOrNullAs
import io.github.kawinnext.patch
import java.util.Collections

val DisableSubscriptionSuggestions = patch(
    name = "Disable subscription suggestions",
) {
    ::getModulesFingerprint.hookMethod {
        before { param ->
            val pageValue = param.thisObject.getObjectFieldOrNullAs<String>("page") ?: return@before
            if (pageValue.contains("_upsell") || pageValue.contains("promo")) {
                param.result = Collections.EMPTY_LIST
            }
        }
    }
}