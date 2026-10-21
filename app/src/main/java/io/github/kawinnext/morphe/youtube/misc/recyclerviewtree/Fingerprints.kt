package io.github.kawinnext.morphe.youtube.misc.recyclerviewtree

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.OpcodesFilter
import io.github.kawinnext.morphe.findMethodDirect
import org.luckypray.dexkit.result.ClassData

internal object RecyclerViewTreeObserverFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.CHECK_CAST,
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.NEW_INSTANCE,
    ),
    strings = listOf("LithoRVSLCBinder")
)

private val ClassData.isObject
    get() = this.descriptor.startsWith("L")

val RecyclerView_addOnScrollListener = findMethodDirect {
    RecyclerViewTreeObserverFingerprint().let { method ->
        val recyclerView = method.paramTypes[1]
        method.invokes.single {
            it.declaredClass == recyclerView && it.paramTypes.singleOrNull { clz -> clz.isObject } != null
        }
    }
}
