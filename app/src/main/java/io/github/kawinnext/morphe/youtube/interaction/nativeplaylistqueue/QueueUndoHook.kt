/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findMethodDirect

// ต้นทางเพิ่มอินเทอร์เฟซ Command ให้ endpoint เพื่อให้ส่วนขยายอ่านคำสั่งของ snackbar ได้
// และกันคำสั่งของ Undo ไม่ให้ไปถึง service ของแอป ที่นี่ proxy ของโมดูลอ่านคำสั่งแทน
// และ hook ตัวกระจายคำสั่งเดียวกัน โดยกินเฉพาะ endpoint ที่มี token ของเรา

/** ตัวกระจายคำสั่งของ endpoint ซึ่งทั้งเมนูและ snackbar เรียกใช้ */
private val queueMenuDispatchMethod = findMethodDirect {
    NativeButtonDispatchFingerprint.match().instructionMatches.single().instruction.methodRef!!
}

/**
 * เติม snackbar ของ Undo และปล่อยให้คำสั่งของเราไม่ไปถึง service ของแอป
 *
 * smali ของต้นทางตรวจคำสั่งที่จุดเรียกทั้งสามของ listener ของ snackbar ซึ่งมีเงื่อนไขเดียวกัน
 * คือ endpoint ที่มี token ของเรา การ hook ตัวกระจายคำสั่งจึงกินคำสั่งชุดเดียวกัน และคำสั่งอื่น
 * ยังทำงานตามปกติ
 */
internal fun PatchExecutor.installQueueUndo() {
    installQueueSaveConfirmationDuration()
    installQueuePlayerClose()

    ::queueMenuDispatchMethod.method.hookMethod {
        before { param ->
            val endpoint = param.args.firstOrNull() ?: return@before
            val consumed = queueAction("onQueueUndoCommand") {
                NativePlaylistQueuePatch.onQueueUndoCommand(
                    PlaylistQueueRenderer.NavigationEndpoint.parseFrom(appMessageBytes(endpoint)!!)
                )
            } ?: return@before
            if (consumed) param.result = null
        }
    }
}
