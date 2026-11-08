/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3905
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package io.github.kawinnext.morphe.youtube.misc.commandrouter

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.string
import org.luckypray.dexkit.result.MethodData

/**
 * Looks up the account scoped command router, which only the command router of an activity does.
 */
internal object CommandRouterFragmentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    filters = listOf(
        string("com.google.android.apps.youtube.app.endpoint.routers.AccountScopeCommandRouterFragment")
    )
)

private fun MethodData.isCommandSignature() =
    paramTypes.size == 2 &&
        paramTypes[1].descriptor == "Ljava/util/Map;" &&
        paramTypes[0].descriptor != "Ljava/util/List;"

private fun MethodData.hasSameSignature(other: MethodData) =
    name == other.name &&
        paramTypes.map { it.descriptor } == other.paramTypes.map { it.descriptor }

/**
 * Runs one command with its context, by the command router itself if no other router handles the command.
 *
 * The router handles single commands and lists of commands, so the method is the one of the router
 * interface that hands its single command to the internal method of the same signature.
 */
val commandRouterExecuteCommandMethod = findMethodDirect {
    val routerClass = CommandRouterFragmentFingerprint().declaredClass!!
    val routerInterfaceMethods = routerClass.interfaces.flatMap { it.methods }

    routerClass.methods.single { method ->
        method.isCommandSignature() &&
            routerInterfaceMethods.any { it.hasSameSignature(method) } &&
            method.invokes.any { it.isCommandSignature() }
    }
}
