package io.github.kawinnext.morphe.youtube.misc.recyclerviewtree

import android.support.v7.widget.RecyclerView
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

val addRecyclerViewTreeHook = mutableListOf<(RecyclerView) -> Unit>()

val recyclerViewTreeHook = patch {
    RecyclerViewTreeObserverFingerprint.hookMethod(scopedHook(::RecyclerView_addOnScrollListener.member) {
        before {
            val recyclerView = it.thisObject as RecyclerView
            addRecyclerViewTreeHook.forEach { hook ->
                hook(recyclerView)
            }
        }
    })
}