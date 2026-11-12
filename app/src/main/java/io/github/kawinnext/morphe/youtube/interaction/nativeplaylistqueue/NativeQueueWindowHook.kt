/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4076
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.DirectQueueNavigator
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.NativePlayback
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method

// ต้นทางกำหนดให้ navigator ที่ถือ backend ของคิวโดยตรงทำ interface DirectQueueNavigator และแทรก
// smali ในเมธอดตรวจความพร้อมของมัน เพื่อให้คิวของเราไม่ขึ้นกับหน้าต่างประวัติการดูของ player ใหม่
// ที่นี่ proxy ของโมดูลทำหน้าที่เป็น navigator ตัวนั้น และ hook ทำหน้าที่ของ smali ที่แทรกไว้

/** ชนิดของ navigator ที่ถือ backend ของคิวโดยตรง */
private fun DexKitBridge.directQueueType(): String =
    DirectQueueNavigatorFingerprint().declaredClass!!.descriptor

/** ชนิดของคำขอเลื่อนตำแหน่งในคิว */
private fun DexKitBridge.queueRequestType(): String = nativeQueueRequestClass(this).descriptor

/** backend ของคิว ซึ่งเป็นฟิลด์เดียวของชนิด wrapper ใน navigator นั้น */
private val nativeQueueBackend = findFieldDirect {
    directQueueBackendFingerprint(directQueueType(), nativeQueueWrapperClass(this).descriptor)
        .matchAll().first().instructionMatches.last().instruction.fieldRef!!
}

/** เมธอดภายในของ backend ที่ตอบว่าคำขอนี้เลื่อนได้หรือไม่ ก่อนนโยบายหน้าต่างประวัติการดู */
private val nativeQueueBackendAvailability = findMethodDirect {
    directQueueBackendAvailabilityFingerprint(nativeQueueBackend(this), queueRequestType())
        .match().method
}

/** เมธอดที่ส่งคำขอเลื่อนตำแหน่งของ navigator ไปยังคิว */
private val nativeQueueNavigate = findMethodDirect {
    directQueueNavigateFingerprint(directQueueType(), queueRequestType()).match().method
}

/** เมธอดที่ตอบว่าคำขอเลื่อนตำแหน่งทำได้หรือไม่ ซึ่งต้นทางแทรกการตัดสินใจของคิวก่อนนโยบายเดิม */
private val nativeQueueCanNavigate = findMethodDirect {
    playlistServiceCanNavigateFingerprint(directQueueType(), queueRequestType()).match().method
}

/** ฟิลด์ intent ของคำขอ ซึ่งบอกทิศทางการเลื่อนตำแหน่ง */
private val nativeQueueIntent = findFieldDirect {
    val enumType = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    queueNavigationIntentFieldFingerprint(queueRequestType(), enumType).match()
        .instructionMatches.single().instruction.fieldRef!!
}

/** คำขอแบบ NEXT และ PREVIOUS ที่ navigator ส่งเข้าคิว */
private val nativeQueueNavigationRequests = findFieldListDirect {
    val enumType = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    listOf("NEXT", "PREVIOUS").map { direction ->
        val intent = playlistNavigationIntentFingerprint(enumType, direction).match()
            .instructionMatches.last().instruction.fieldRef!!
        playlistNavigationSingletonFingerprint(queueRequestType(), intent).match()
            .instructionMatches.last().instruction.fieldRef!!
    }
}

/** ฟิลด์ของ player ที่ navigator ใช้หาวิดีโอที่เล่นอยู่ และฟิลด์ที่ถือมันไว้ */
private val nativeQueuePlayerFields = findFieldListDirect {
    val request = queueRequestType()
    val players = playlistServiceNavigatorFingerprint(request).match().instructionMatches
        .take(3).map { it.instruction.fieldRef!! }
    val owner = directQueuePlayerOwnerFingerprint(directQueueType(), players[0].type.descriptor)
        .match().instructionMatches.single().instruction.fieldRef!!
    players + owner
}

/** เมธอดที่อ่านวิดีโอของ player ซึ่ง navigator ใช้ตรวจคำขอที่ซ้ำกับวิดีโอที่เล่นอยู่ */
private val nativeQueuePlayerVideoId = findMethodDirect {
    playlistServiceNavigatorFingerprint(queueRequestType()).match()
        .instructionMatches[4].instruction.methodRef!!
}

/** ตัว navigator ของคิว ซึ่งส่วนขยายใช้ตัดสินใจเรื่องการเลื่อนตำแหน่ง */
private class DirectQueueNavigatorProxy(
    private val navigator: Any,
    private val window: NativeQueueWindow
) : DirectQueueNavigator {
    override fun patch_getVideoId(): String? = window.videoId(navigator)

    override fun patch_getQueuePlayback(): NativePlayback? = window.playback(navigator)

    override fun patch_navigateQueue(next: Boolean) = window.navigate(navigator, next)

    override fun patch_canNavigateQueue(next: Boolean): Boolean = window.canNavigate(navigator, next)

    override fun patch_getQueueNavigationIntent(request: Any?): Enum<*>? = window.intent(request)

    override fun patch_canNavigateNativeQueue(request: Any?): Boolean =
        window.canNavigateNative(navigator, request)
}

/** สมาชิกของแอปที่ navigator ใช้ ซึ่ง proxy ข้างบนเรียกแทนเมธอด patch_* ของต้นทาง */
internal class NativeQueueWindow(
    private val backend: Field,
    private val capability: Method,
    private val videoPlayers: List<Field>,
    private val videoId: Method,
    private val intent: Field,
    private val requests: List<Field>,
    private val navigate: Method,
    private val canNavigate: Method
) {
    private val proxies = ProxyCache { navigator: Any -> DirectQueueNavigatorProxy(navigator, this) }

    fun proxy(navigator: Any): DirectQueueNavigator = proxies.get(navigator)

    /** backend ของคิว ซึ่งก็คือ NativePlayback ของคิวของแอป */
    fun playback(navigator: Any): NativePlayback? {
        val queue = backend.read(navigator) ?: return null
        val engine = nativeQueueEngine ?: return null
        if (!engine.isWrapper(queue)) return null
        return nativeQueuePlayback(queue)
    }

    fun videoId(navigator: Any): String? {
        val owner = videoPlayers[3].read(navigator) ?: return null
        val player = videoPlayers[1].read(owner) ?: return null
        val playerState = videoPlayers[2].read(player) ?: return null
        return videoId.call(playerState) as? String
    }

    fun navigate(navigator: Any, next: Boolean) {
        val request = requests[if (next) 0 else 1].apply { isAccessible = true }.get(null) ?: return
        navigate.call(navigator, request)
    }

    fun canNavigate(navigator: Any, next: Boolean): Boolean {
        val request = requests[if (next) 0 else 1].apply { isAccessible = true }.get(null) ?: return false
        return capability.call(navigator, request) as Boolean
    }

    fun intent(request: Any?): Enum<*>? = request?.let { intent.read(it) as? Enum<*> }

    fun canNavigateNative(navigator: Any, request: Any?): Boolean =
        request != null && capability.call(navigator, request) as Boolean
}

private var nativeQueueWindow: NativeQueueWindow? = null

/** ดัชนีของการเรียกตรวจความพร้อมภายในคำขอเลื่อนตำแหน่งที่กำลังทำอยู่ */
private val nativeQueueAvailabilityCalls: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

/**
 * ทำให้การเลื่อนตำแหน่งของคิวของเราไม่ขึ้นกับหน้าต่างประวัติการดูของ player ใหม่
 *
 * ต้นทางแทรกการตัดสินใจของส่วนขยายไว้ที่ต้นเมธอดตรวจความพร้อม และแทรกการคงคำขอที่ commit แล้ว
 * ไว้ที่จุดเรียกครั้งที่สองในตัวส่งคำขอ ที่นี่ hook สองเมธอดนั้นและนับการเรียกภายในคำขอเดียวกัน
 * ซึ่งเป็นสองจุดที่ต้นทางยืนยันด้วย check(gates.size == 2)
 */
internal fun PatchExecutor.installNativeQueueWindow() {
    val window = NativeQueueWindow(
        backend = ::nativeQueueBackend.field,
        capability = ::nativeQueueBackendAvailability.method,
        videoPlayers = ::nativeQueuePlayerFields.fieldList,
        videoId = ::nativeQueuePlayerVideoId.method,
        intent = ::nativeQueueIntent.field,
        requests = ::nativeQueueNavigationRequests.fieldList,
        navigate = ::nativeQueueNavigate.method,
        canNavigate = ::nativeQueueCanNavigate.method,
    )
    nativeQueueWindow = window

    // ตัวส่งคำขอเป็นผู้กำหนดว่าการเรียกตรวจความพร้อมครั้งถัดไปเป็นการเรียกครั้งที่สอง
    ::nativeQueueNavigate.method.hookMethod {
        before { nativeQueueAvailabilityCalls.set(0) }
        after { nativeQueueAvailabilityCalls.remove() }
    }

    ::nativeQueueCanNavigate.method.hookMethod {
        before { param ->
            val navigator = param.thisObject
            val request = param.args.firstOrNull()
            val call = nativeQueueAvailabilityCalls.get() ?: 0
            nativeQueueAvailabilityCalls.set(call + 1)

            val availability = queueAction("nativeQueueNavigationAvailability") {
                NativePlaylistQueuePatch.nativeQueueNavigationAvailability(window.proxy(navigator), request)
            } ?: -1
            // -1 ปล่อยให้นโยบายหน้าต่างประวัติการดูของแอปตัดสินตามเดิม
            if (availability < 0) return@before

            param.result = if (call == 0) availability == 1 else {
                queueAction("retainCommittedQueueNavigation") {
                    NativePlaylistQueuePatch.retainCommittedQueueNavigation(window.proxy(navigator), request)
                } ?: false
            }
        }
    }
}
