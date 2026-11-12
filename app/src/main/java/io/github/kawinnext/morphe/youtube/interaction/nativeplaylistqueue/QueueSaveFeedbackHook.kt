/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.QueueSaveActions
import app.morphe.extension.youtube.patches.queue.QueueSaveFeedback
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findMethodDirect
import org.luckypray.dexkit.DexKitBridge

// ต้นทางเพิ่มอินเทอร์เฟซ Callback กับฟิลด์ patch_queueSaveSelection ให้คลาสของแอป และแทรก smali
// เก็บ/คืนผลของ save ที่นี่ใช้ proxy ของโมดูลถือ selection แทน และ hook จุดเดียวกันของแอป

/** คลาสของ callback ที่แจ้งผลการแก้ไข playlist */
private fun DexKitBridge.queueSaveEditCallbackType(): String =
    QueueSaveEditErrorFingerprint().declaredClass!!.descriptor

/** เมธอดที่สร้าง callback ของ native save ซึ่งรับ endpoint ของเมนูเข้ามาเป็นพารามิเตอร์แรก */
private val queueSaveEditCreationMethod = findMethodDirect {
    queueSaveEditCreationFingerprint(
        queueSaveEditCallbackType(), queueMenuEndpointType()
    ).match().method
}

/** ตัวสร้างของ callback นั้น ซึ่งรับ endpoint ที่จะใช้แจ้งผลเข้ามาด้วย */
private val queueSaveEditCallbackConstructor = findMethodDirect {
    queueSaveEditCreationFingerprint(
        queueSaveEditCallbackType(), queueMenuEndpointType()
    ).match().instructionMatches.single().instruction.methodRef!!
}

/** เมธอดที่รับผลของ save ที่สำเร็จ ซึ่งแอปส่ง action ของมันต่อ */
private val queueSaveEditSuccessMethod = findMethodDirect {
    queueSaveEditSuccessFingerprint(queueSaveEditCallbackType()).match().method
}

/**
 * เติมผลของ native save ที่แอปส่งกลับ ให้มี snackbar ของเราและ Undo ของ playlist
 *
 * smali ของต้นทางเรียก `onEditCreated` หลังสร้าง callback และแทน response ด้วยผลของ
 * `onEditResult` ก่อนที่แอปจะส่ง action ที่นี่ hook ตัวสร้างของ callback และเมธอดที่รับผลแทน
 */
internal fun PatchExecutor.installQueueSaveFeedback() {
    val endpointType = ::queueSaveEditCreationMethod.method.parameterTypes.first()
    val responseType = ::queueSaveEditSuccessMethod.method.parameterTypes.single()

    ::queueSaveEditCallbackConstructor.constructor.hookMethod {
        after { param ->
            val endpoint = param.args.firstOrNull { endpointType.isInstance(it) } ?: return@after
            queueAction("onEditCreated") {
                QueueSaveActions.onEditCreated(
                    NativeQueueSaveCallbackProxy(param.thisObject),
                    PlaylistQueueRenderer.NavigationEndpoint.parseFrom(appMessageBytes(endpoint)!!)
                )
            }
        }
    }

    ::queueSaveEditSuccessMethod.method.hookMethod {
        before { param ->
            val response = param.args.firstOrNull() ?: return@before
            val decorated = queueAction("onEditResult") {
                QueueSaveActions.onEditResult(
                    NativeQueueSaveCallbackProxy(param.thisObject),
                    PlaylistQueueRenderer.QueueSaveEditResponse.parseFrom(appMessageBytes(response)!!)
                )
            } ?: return@before
            if (decorated.toByteArray().contentEquals(appMessageBytes(response))) return@before
            param.args[0] = parseAppMessage(responseType, decorated.toByteArray())
        }
    }
}

/** ตัวสร้าง snackbar ของปุ่มในยุคใหม่ ซึ่งรับ toast renderer เข้ามาเป็นพารามิเตอร์แรก */
private val queueSnackbarFactory = findMethodDirect {
    QueueSnackbarModernFactoryFingerprint.match().method
}

/** เมธอดที่กำหนดระยะเวลาของ snackbar บน builder ของแอป */
private val queueSnackbarDurationSetter = findMethodDirect {
    val builder = QueueSnackbarModernFactoryFingerprint.match().method.returnType!!.descriptor
    queueSnackbarDurationFingerprint(builder).match().instructionMatches[4].instruction.methodRef!!
}

/**
 * ใช้ระยะเวลาของ native builder กับ snackbar ที่ส่วนขยายสร้าง
 *
 * ต้นทางแทรกก่อนที่ builder จะถูกส่งคืน ซึ่งคือค่าที่เมธอดคืนมา ที่นี่จึงกำหนดระยะเวลาที่ผลของ hook
 */
internal fun PatchExecutor.installQueueSaveConfirmationDuration() {
    val durationSetter = ::queueSnackbarDurationSetter.method

    ::queueSnackbarFactory.method.hookMethod {
        after { param ->
            val builder = param.result ?: return@after
            val toast = param.args.firstOrNull() ?: return@after
            val duration = queueAction("confirmationDuration") {
                QueueSaveFeedback.confirmationDuration(
                    PlaylistQueueRenderer.QueueToastRenderer.parseFrom(appMessageBytes(toast)!!)
                )
            } ?: return@after
            if (duration > 0) durationSetter.call(builder, duration)
        }
    }
}
