package io.github.kawinnext.revanced.photomath.misc.unlock.plus

import de.robv.android.xposed.XC_MethodReplacement
import io.github.kawinnext.patch
import io.github.kawinnext.revanced.photomath.misc.unlock.bookpoint.EnableBookpoint

val UnlockPlus = patch(
    name = "Unlock plus",
) {
    dependsOn(EnableBookpoint)
    ::isPlusUnlockedFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}