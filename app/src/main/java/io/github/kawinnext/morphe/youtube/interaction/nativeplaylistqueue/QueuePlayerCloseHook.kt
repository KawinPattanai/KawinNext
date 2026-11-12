/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.HookDsl
import io.github.kawinnext.IScopedHookCallback
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.youtube.misc.loadvideo.dismissPlayerMethod
import io.github.kawinnext.scopedHook
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method

// ต้นทางแทรก `onQueuePlayerClosing()` ที่จุดเรียก dismiss ของทุกเส้นทางที่ผู้ใช้ปิด player
// (คลิกปุ่มปิด, ปัดผ่านเกณฑ์, ปล่อยนิ้ว) แต่ไม่แตะการปิดที่โปรแกรมสั่งเอง
// ที่นี่ hook เมธอด dismiss แล้วใช้ scopedHook ให้ทำงานเฉพาะเมื่อถูกเรียกจากเส้นทางเหล่านั้น
// ซึ่งให้ผลเท่ากันเพราะคำสั่งถูกแทรกอยู่ "ก่อน" การเรียก dismiss ในเส้นทางที่ผ่านเกณฑ์แล้ว

/** เมธอดที่ปิด mini player ซึ่งทุกเส้นทางปิดของผู้ใช้เรียกใช้ */
private val queuePlayerDismiss = findMethodDirect { dismissPlayerMethod(this) }

/** เส้นทางที่ผู้ใช้ปิด player ในรุ่นนี้ ทุกตัวเรียกเมธอด dismiss ข้างต้น */
private val queuePlayerCloseMethods = findMethodListDirect {
    val dismiss = queuePlayerDismiss(this)
    listOf(
        QueuePlayerCloseClickFingerprint(dismiss),
        QueuePlayerCloseFlingFingerprint(dismiss),
        QueuePlayerCloseFlingLegacyFingerprint(dismiss),
        QueuePlayerCloseTouchFingerprint(dismiss),
        QueuePlayerControllerFlingFingerprint(dismiss),
        QueuePlayerControllerReleaseFingerprint(dismiss),
    ).mapNotNull { it.matchOrNull()?.method }
}

/** ปลายทางของ feedback ที่ controller ของ playlist ใช้ ซึ่งเป็นของ activity ไม่ใช่ของ playback */
private val playlistControllerDispatcher = findFieldDirect {
    val controller = PlaylistControllerConstructorFingerprint.match().method.declaredClass!!.descriptor
    val dispatcher = queueFeedbackDispatch(this).declaredClass!!.descriptor
    playlistControllerDispatcherFingerprint(controller, dispatcher).match()
        .instructionMatches.first().instruction.fieldRef!!
}

/** เมธอดที่ส่ง endpoint พร้อม context ไปให้ตัวกระจายคำสั่งของ activity */
private val queueFeedbackDispatch = findMethodDirect {
    NativeButtonDispatchFingerprint.match().instructionMatches.single().instruction.methodRef!!
}

private var queueFeedbackDispatcher: Field? = null
private var queueFeedbackDispatchMethod: Method? = null

/**
 * สังเกตการปิด player ก่อนที่แอปจะรื้อทิ้ง โดยไม่แทนที่ listener ของมัน
 *
 * ต้นทางย้าย `patch_showQueueFeedback` ไปเป็นเมธอดของ controller ด้วย ซึ่งที่นี่เก็บปลายทาง
 * ของคำสั่งไว้ให้ proxy ของ controller เรียกใช้ (ดู [showQueueFeedback])
 */
internal fun PatchExecutor.installQueuePlayerClose() {
    val onClosing: HookDsl<IScopedHookCallback>.() -> Unit = {
        before { NativePlaylistQueuePatch.onQueuePlayerClosing() }
    }
    val closes = with(this) {
        ::queuePlayerCloseMethods.dexMethodList.map { it.toMember() }
    }
    val pairs: Array<Pair<Member, HookDsl<IScopedHookCallback>.() -> Unit>> =
        closes.map { it to onClosing }.toTypedArray()
    if (pairs.isNotEmpty()) ::queuePlayerDismiss.method.hookMethod(scopedHook(*pairs))

    queueFeedbackDispatcher = ::playlistControllerDispatcher.field
    queueFeedbackDispatchMethod = ::queueFeedbackDispatch.method
}

/**
 * ส่งคำสั่ง feedback ผ่านตัวกระจายคำสั่งของ activity ซึ่งยังใช้ได้หลัง watch view ถูกปลด
 *
 * แทนเมธอด `patch_showQueueFeedback` ที่ต้นทางเพิ่มให้ controller: แปลง bytes เป็น endpoint
 * ของแอปแล้วเรียกตัวกระจายคำสั่งด้วย context ว่าง ตามที่ smali ของต้นทางทำ
 */
internal fun showQueueFeedback(controller: Any, command: ByteArray) {
    val dispatcher = queueFeedbackDispatcher ?: return
    val dispatch = queueFeedbackDispatchMethod ?: return
    queueAction("showQueueFeedback") {
        val dispatcherInstance = dispatcher.read(controller) ?: return@queueAction
        dispatch.call(
            dispatcherInstance,
            parseAppMessage(dispatch.parameterTypes.first(), command),
            emptyMap<String, Any>()
        )
    }
}
