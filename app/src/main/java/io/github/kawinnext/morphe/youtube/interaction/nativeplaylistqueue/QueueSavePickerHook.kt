/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import android.os.Bundle
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.QueueSaveActions
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

// ปลั๊กอินนี้ส่งรายการที่เลือกไว้ให้ picker ของแอป และแก้ผลของ picker ให้บันทึกทั้งชุด
// ต้นทางแทรก smali ที่จุดที่ Bundle ถูกสร้างและที่จุดที่ response ถูกอ่าน ผลลัพธ์เหมือนกัน

/** คลาสของคำขอของ picker ซึ่งอ่าน endpoint จาก arguments ของมัน */
private val queueSaveRequestType = findClassDirect {
    QueueSaveRequestFingerprint().declaredClass!!
}

/** เมธอดของ picker fragment ที่อ่าน endpoint จาก arguments */
private val queueSavePickerMethod = findMethodDirect {
    queueSavePickerFingerprint(queueSaveRequestType(this).descriptor).match().method
}

/** ฟิลด์ arguments ของ picker ซึ่งรายการที่เลือกไว้ถูกเก็บเพิ่ม */
private val queueSavePickerArguments = findFieldDirect {
    queueSavePickerFingerprint(queueSaveRequestType(this).descriptor).match()
        .instructionMatches.first().instruction.fieldRef!!
}

/** เมธอดที่เปิด picker ด้วย endpoint ที่ถูกแปลงเป็น bytes ลงใน arguments */
private val queueSavePickerFactory = findMethodDirect {
    val picker = queueSavePickerMethod(this).declaredClass!!.descriptor
    queueSavePickerFactoryFingerprint(picker, queueMenuEndpointType()).match().method
}

/** เมธอดที่รับผลของ picker ซึ่งแอปคืนรายการ playlist ให้เลือก */
private val queueSaveResponseMethod = findMethodDirect {
    QueueSaveResponseFingerprint.match().method
}

/** ฟิลด์ picker ใน callback ของผลลัพธ์ */
private val queueSaveResponsePicker = findFieldDirect {
    val picker = queueSavePickerMethod(this).declaredClass!!.descriptor
    queueSaveResponsePickerFingerprint(picker).match().instructionMatches.single().instruction.fieldRef!!
}

/** ติดตั้ง picker ไปแล้วหรือยัง (ทั้ง entry และ hook ของเมนูเรียกฟังก์ชันนี้) */
private var queueSavePickerInstalled = false

/**
 * ส่งรายการที่เลือกไว้ของคิวเราไปกับ arguments ของ picker
 *
 * ต้นทางย้ายรายการก่อนที่ bytes ของ endpoint จะถูกเขียนทับลง Bundle เดียวกัน ที่นี่ hook
 * `Bundle.putByteArray` เฉพาะที่ถูกเรียกจากเมธอดเปิด picker จึงได้ Bundle ตัวเดียวกัน
 *
 * ต้นทางติดตั้งจาก hook ของเมนูเท่านั้น ซึ่งถูกเรียกครั้งเดียว ส่วนที่นี่ entry ติดตั้งไว้ก่อน
 * แล้ว hook ของเมนูจึงเรียกซ้ำได้ จึงกันการติดตั้งซ้ำไว้ เพราะ hook ที่ซ้อนกันจะทำงานสองรอบ
 */
internal fun PatchExecutor.installQueueSavePicker() {
    if (queueSavePickerInstalled) return
    queueSavePickerInstalled = true

    installQueueSaveFeedback()

    val putByteArray = DexMethod("Landroid/os/Bundle;->putByteArray(Ljava/lang/String;[B)V").toMethod()
    ::queueSavePickerFactory.method.hookMethod(scopedHook(putByteArray) {
        before { param ->
            val arguments = param.thisObject as? Bundle ?: return@before
            val endpoint = outerParam.args.firstOrNull() ?: return@before
            queueAction("onQueueSaveArguments") {
                NativePlaylistQueuePatch.onQueueSaveArguments(
                    PlaylistQueueRenderer.NavigationEndpoint.parseFrom(appMessageBytes(endpoint)!!),
                    arguments
                )
            }
        }
    })

    // คำขอของ picker ถูกปล่อยไว้ตามเดิม เพราะชุดรายการทำให้เซิร์ฟเวอร์ตอบ 400
    // ผลลัพธ์ที่แอปได้จึงเป็นของเรา เฉพาะ picker ที่เปิดจากคิวของเรา
    val argumentsField = ::queueSavePickerArguments.field
    val pickerField = ::queueSaveResponsePicker.field
    ::queueSaveResponseMethod.method.hookMethod {
        before { param ->
            val response = param.args.firstOrNull() ?: return@before
            val picker = pickerField.read(param.thisObject) ?: return@before
            val decorated = queueAction("onPickerResponse") {
                QueueSaveActions.onPickerResponse(
                    NativeQueuePickerProxy(argumentsField.read(picker) as? Bundle),
                    PlaylistQueueRenderer.QueueSaveResponse.parseFrom(appMessageBytes(response)!!)
                )
            } ?: return@before
            if (decorated.toByteArray().contentEquals(appMessageBytes(response))) return@before
            param.args[0] = parseAppMessage(response.javaClass, decorated.toByteArray())
        }
    }
}
