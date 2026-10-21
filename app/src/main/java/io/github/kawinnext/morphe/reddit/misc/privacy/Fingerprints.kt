package io.github.kawinnext.morphe.reddit.misc.privacy

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.methodCall

internal object ShareLinkFormatterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;", "Ljava/util/Map;"),
    filters = listOf(
        methodCall(smali = "Landroid/net/Uri${'$'}Builder;->clearQuery()Landroid/net/Uri${'$'}Builder;")
    )
)
