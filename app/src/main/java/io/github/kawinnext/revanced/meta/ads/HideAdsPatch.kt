package io.github.kawinnext.revanced.meta.ads

import de.robv.android.xposed.XC_MethodReplacement
import io.github.kawinnext.patch

val HideAds = patch(
    name = "Hide ads",
) {
    ::adInjectorFingerprint.hookMethod(XC_MethodReplacement.returnConstant(false))
}