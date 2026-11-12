/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch.NativePlayback
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

// คิวของแอปถูกใช้ผ่าน bridge ที่ต้นทางเพิ่มให้คลาสของมัน (patch_*) ซึ่งทำตอนรันไม่ได้
// ที่นี่จึงแทนด้วย object ของโมดูลที่อ่าน/เรียกเมธอดเดียวกัน และเก็บค่าที่ต้นทางเก็บในฟิลด์
// (patch_localQueue, patch_localItems, patch_lastAutoplay) ไว้ใน WeakHashMap ของโมดูล

/** ชนิดของคำขอเลื่อนตำแหน่งในคิว ซึ่งเป็นพารามิเตอร์ตัวเดียวของ descriptor ของคิว */
internal val nativeQueueRequestClass = findClassDirect {
    nativeQueueRequestConstructor(this).declaredClass!!
}

/** คลาสของ navigator wrapper ซึ่งถือ navigator ที่ใช้งานอยู่ของคิว */
internal val nativeQueueWrapperClass = findClassDirect {
    nativeQueueWrapperFlag(this).declaredClass
}

/** ฟิลด์ธงของ wrapper ที่บอกว่า navigator ปัจจุบันเป็นคิวของแอป */
private val nativeQueueWrapperFlag = findFieldDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    nativeQueueWrapperFingerprint(queue).match().instructionMatches.last().instruction.fieldRef!!
}

/** เมธอด descriptor ของคิว ซึ่งสร้างคำขอเลื่อนตำแหน่งจากคอนเทนเนอร์ */
private val nativeQueueDescriptor = findMethodDirect {
    NativeQueueDescriptorFingerprint.match().method
}

/** ฟิลด์ที่เก็บรายการของคิว และตัวป้องกันการเปลี่ยนที่ใช้ร่วมกันระหว่าง accept กับ dispose */
private val nativeQueueFields = findFieldListDirect {
    val descriptor = NativeQueueDescriptorFingerprint.match()
    val end = descriptor.instructionMatches[5].instruction.methodRef!!
    listOf(
        descriptor.instructionMatches[2].instruction.fieldRef!!,
        nativeQueueChangeEndFingerprint(end).match().instructionMatches[0].instruction.fieldRef!!,
    )
}

/** เมธอดของคิว: เริ่มและจบการเปลี่ยน, ปล่อยตัวป้องกัน, และ refresh สถานะ */
private val nativeQueueChangeMethods = findMethodListDirect {
    val descriptor = NativeQueueDescriptorFingerprint.match()
    val end = descriptor.instructionMatches[5].instruction.methodRef!!
    val changeEnd = nativeQueueChangeEndFingerprint(end).match()
    listOf(
        descriptor.instructionMatches[0].instruction.methodRef!!,
        end,
        changeEnd.instructionMatches[1].instruction.methodRef!!,
        nativeQueueRefreshFingerprint(changeEnd.instructionMatches[2].instruction.methodRef!!).match().method,
    )
}

/** เมธอดที่ทิ้งคิว ซึ่งเป็นจุดที่รายการของคิวถูกปลดเช่นกัน */
private val nativeQueueDispose = findMethodDirect {
    NativeQueueDisposeFingerprint.match().method
}

/** เมธอดที่รับ response ของ playlist ซึ่งอาจเปลี่ยน navigator เป็นคิวของแอป */
private val nativeQueueReceive = findMethodDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    NativeQueueReceiveFingerprint(queue).match().method
}

/** ฟิลด์ของ response: แผง, navigator, คอนเทนเนอร์, คำสั่ง watch, ที่เก็บ response */
private val nativeQueueReceiveFields = findFieldListDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    val receive = NativeQueueReceiveFingerprint(queue).match()
    listOf(1, 3, 4, 7, 8, 16).map { receive.instructionMatches[it].instruction.fieldRef!! }
}

/** เมธอดของ response: ตัวสร้าง builder, สร้าง descriptor, สร้างคิว, สลับ navigator, ส่งต่อ */
private val nativeQueueReceiveMethods = findMethodListDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    val receive = NativeQueueReceiveFingerprint(queue).match()
    listOf(6, 9, 11, 13, 18).map { receive.instructionMatches[it].instruction.methodRef!! }
}

/** เมธอดที่รับ response เข้าคิว ซึ่งเก็บ response ไว้ในฟิลด์ของคิว */
private val nativeQueueAccept = findMethodDirect {
    val descriptor = NativeQueueDescriptorFingerprint.match()
    val queue = descriptor.method.declaredClass!!.descriptor
    val receive = NativeQueueReceiveFingerprint(queue).match()
    nativeQueueAcceptFingerprint(
        queue,
        receive.method.paramTypes.single().descriptor,
        descriptor.instructionMatches[0].instruction.methodRef!!,
    ).match().method
}

/** ฟิลด์ที่เก็บ response ล่าสุดของคิว ซึ่งใช้เป็นต้นแบบของแผงของเรา */
private val nativeQueueStoredResponse = findFieldDirect {
    val descriptor = NativeQueueDescriptorFingerprint.match()
    val queue = descriptor.method.declaredClass!!.descriptor
    val receive = NativeQueueReceiveFingerprint(queue).match()
    nativeQueueAcceptFingerprint(
        queue,
        receive.method.paramTypes.single().descriptor,
        descriptor.instructionMatches[0].instruction.methodRef!!,
    ).match().instructionMatches.last().instruction.fieldRef!!
}

/** เมธอดตั้งและอ่านโหมดเล่นซ้ำ ซึ่ง wrapper ส่งต่อไปยัง navigator */
private val nativeQueueRepeatMethods = findMethodListDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    listOf(
        NativeQueueRepeatSetterFingerprint(queue).match().instructionMatches.last().instruction.methodRef!!,
        NativeQueueRepeatGetterFingerprint(queue).match().instructionMatches.last().instruction.methodRef!!,
    )
}

/** ตัวสร้าง response เปล่า ซึ่งรับคำสั่ง watch ของ response เดิม และฟิลด์ของมัน */
private val nativeQueueResponseConstructor = findMethodDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    val response = NativeQueueReceiveFingerprint(queue).match().method.paramTypes.single().descriptor
    nativeQueueResponseConstructorFingerprint(response).match().method
}

/** ฟิลด์ของ response: คำสั่ง watch ต้นฉบับ และชุดการนำทางของ playlist */
private val nativeQueueResponseFields = findFieldListDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    val response = NativeQueueReceiveFingerprint(queue).match().method.paramTypes.single().descriptor
    val constructor = nativeQueueResponseConstructorFingerprint(response).match().method
    val navigationType = PlaylistModelDescriptionFingerprint.match().method
        .findFieldFromToString(", navigationSets=").type.descriptor
    listOf(
        nativeQueueResponseSourceFingerprint(response, constructor.paramTypes.single().descriptor)
            .match().instructionMatches.single().instruction.fieldRef!!,
        nativeQueueResponseNavigationFingerprint(response, navigationType)
            .match().instructionMatches.single().instruction.fieldRef!!,
    )
}

/** เมธอดของตัวคิวที่สร้างลำดับสุ่ม: ตั้งค่า shuffler และอ่านตำแหน่งที่เลือก */
private val nativeQueueShuffleMethods = findMethodListDirect {
    val shuffle = NativeQueueShuffleFingerprint.match()
    listOf(shuffle.method, shuffle.instructionMatches[2].instruction.methodRef!!)
}

/** ฟิลด์ลิงก์ก่อน/หลังของลำดับสุ่ม ซึ่งใช้ไม่ได้เมื่อรายการถูกแทนด้วยของเรา */
private val nativeQueueShuffleFields = findFieldListDirect {
    val shuffle = NativeQueueShuffleFingerprint.match()
    listOf(
        shuffle.instructionMatches[5].instruction.fieldRef!!,
        shuffle.instructionMatches[7].instruction.fieldRef!!,
    )
}

/** เมธอดที่รับ descriptor เข้าคิว ซึ่งต้องคืนคำขอแบบ JUMP เมื่อคิวเป็นของเรา */
private val nativeQueueLocalLoad = findMethodDirect {
    val descriptor = nativeQueueDescriptor(this)
    nativeQueueLocalLoadFingerprint(
        NativeQueueShuffleFingerprint.match().method.declaredClass!!.descriptor,
        descriptor.returnType!!.descriptor,
        descriptor.paramTypes.single().descriptor,
    ).match().method
}

/** เมธอดที่อ่าน playlist ของ descriptor ซึ่งใช้ตัดสินว่าคำขอเป็นของ playlist จริงหรือไม่ */
private val nativeQueueLocalPlaylistId = findMethodDirect {
    val descriptor = nativeQueueDescriptor(this)
    val load = nativeQueueLocalLoadFingerprint(
        NativeQueueShuffleFingerprint.match().method.declaredClass!!.descriptor,
        descriptor.returnType!!.descriptor,
        descriptor.paramTypes.single().descriptor,
    ).match()
    load.instructionMatches.first().instruction.methodRef!!
}

/** เมธอดที่รับ navigator ของ autoplay ซึ่งแทน navigator ของคิวของเรา */
private val nativeQueueAutoplay = findMethodDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    val receive = NativeQueueReceiveFingerprint(queue).match()
    nativeQueueAutoplayFingerprint(
        receive.instructionMatches[3].instruction.fieldRef!!,
        receive.instructionMatches[13].instruction.methodRef!!,
    ).match().method
}

/** ตัวสร้าง navigator ของ autoplay ซึ่งใช้เมื่อคืนคิวให้ autoplay ของเซิร์ฟเวอร์ */
private val nativeQueueAutoplayConstructor = findMethodDirect {
    val type = nativeQueueAutoplay(this).paramTypes.single().descriptor
    nativeQueueAutoplayConstructorFingerprint(type).match().method
}

/** เมธอดที่ปลด wrapper ซึ่งต้องล้างธงคิวของเราและ navigator ที่จำไว้ */
private val nativeQueueWrapperDispose = findMethodDirect {
    val queue = nativeQueueDescriptor(this).declaredClass!!.descriptor
    val receive = NativeQueueReceiveFingerprint(queue).match()
    nativeQueueWrapperDisposeFingerprint(
        receive.instructionMatches[3].instruction.fieldRef!!,
        NativeQueueDisposeFingerprint.match().method.name,
    ).match().method
}

/** ตัวสร้างคำขอเลื่อนตำแหน่ง ซึ่งใช้สร้างคำขอแบบ JUMP ให้รายการของเรา */
private val nativeQueueRequestConstructor = findMethodDirect {
    val enumType = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    playlistNavigationRequestFingerprint(enumType).match().method
}

/** ค่าคงที่ของ intent ที่คิวของแอปใช้โหลดรายการที่มีอยู่แล้ว */
private val nativeQueueJumpIntent = findFieldDirect {
    val enumType = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    playlistNavigationIntentFingerprint(enumType, "JUMP").match()
        .instructionMatches.last().instruction.fieldRef!!
}

/**
 * ตารางของ bridge ที่ต้นทางเพิ่มให้คลาสของคิว: สมาชิกจริงของแอปที่ proxy ของโมดูลเรียกใช้
 *
 * ทุกเมธอดที่นี่คือ body ของ `patch_*` ตัวหนึ่งใน NativeQueueEngineHook ของต้นทาง
 */
internal class NativeQueueEngine(
    private val queueType: Class<*>,
    private val mutableType: Class<*>,
    private val panelType: Class<*>,
    private val nativeNavigator: Field,
    private val response: Field,
    private val wrapperFlag: Field,
    private val queueOwner: Field,
    private val changeGuard: Field,
    private val storedResponse: Field,
    private val shuffleLinks: List<Field>,
    private val position: Method,
    private val repeatSetter: Method,
    private val repeatGetter: Method,
    private val beginQueueChange: Method,
    private val releaseChange: Method,
    private val refresh: Method,
    private val accept: Method,
    private val queueDispose: Method,
    private val builderConstructor: Constructor<*>,
    private val watchCommand: Field,
    private val commandField: Field,
    private val watchCommandType: Class<*>,
    private val nativeReceive: Method,
    private val buildDescriptor: Method,
    private val playlistId: Method,
    private val container: Field,
    private val containerFactory: Method,
    private val switchNavigator: Method,
    private val responseConstructor: Constructor<*>,
    private val sourceResponse: Field,
    private val responsePanel: Field,
    private val responseNavigation: Field,
    private val autoplay: Method,
    private val autoplayConstructor: Constructor<*>,
    private val requestConstructor: Constructor<*>,
    private val jumpIntent: Any,
    private val shuffleSetter: Method
) {
    /** navigator ที่ใช้งานอยู่ของ wrapper ซึ่งเป็นลำดับของคิว */
    fun queueSequence(wrapper: Any): Any? = nativeNavigator.read(wrapper)

    /** ตำแหน่งที่เลือกในรายการของคิว ซึ่งอ่านจากที่เก็บรายการ */
    fun selectedIndex(wrapper: Any): Int {
        val navigator = nativeNavigator.read(wrapper) ?: return -1
        val items = queueOwner.read(navigator) ?: return -1
        return position.call(items) as Int
    }

    /** โหมดเล่นซ้ำของ navigator ปัจจุบัน */
    fun repeatMode(wrapper: Any): Int {
        val navigator = nativeNavigator.read(wrapper) ?: return 0
        return repeatGetter.call(navigator) as Int
    }

    /** คืนโหมดเล่นซ้ำให้ navigator ของเซิร์ฟเวอร์ แต่ไม่แตะเมื่อคิวเป็นของเรา */
    fun restoreQueueRepeat(wrapper: Any, mode: Int) {
        if (wrapperState(wrapper).localQueue) return
        nativeNavigator.read(wrapper)?.let { repeatSetter.call(it, mode) }
    }

    /**
     * แทน response ของเซิร์ฟเวอร์ด้วยแผงของเรา และสลับ navigator ไปที่คิวของแอป
     *
     * ตรงกับ `patch_attachQueue` ของต้นทาง: ใช้คิวเดิมถ้ามี navigator เป็นคิวอยู่แล้ว
     * ไม่งั้นสร้าง descriptor จากคำสั่ง watch ของวิดีโอที่เล่นอยู่ แล้วให้คอนเทนเนอร์สร้างคิว
     */
    fun attachQueue(wrapper: Any, panel: ByteArray, currentWatchCommand: ByteArray): Boolean {
        val local = localPanel(wrapper, panel) ?: return false
        val navigator = nativeNavigator.read(wrapper) ?: return false
        val repeat = repeatGetter.call(navigator) as Int
        val queue = if (queueType.isInstance(navigator)) navigator else {
            val builder = builderConstructor.newInstance()
            commandField.write(builder, parseAppMessage(watchCommandType, currentWatchCommand))
            val descriptor = buildDescriptor.call(builder) ?: return false
            val descriptorPlaylist = playlistId.call(descriptor) as? String ?: return false
            if (descriptorPlaylist.isEmpty()) return false
            val containerInstance = container.read(wrapper) ?: return false
            containerFactory.call(containerInstance, descriptor) ?: return false
        }

        val items = queueOwner.read(queue) ?: return false
        if (!mutableType.isInstance(items)) return false
        clearShuffleOrder(items)
        accept.call(queue, local)
        setLocalItems(items, true)
        switchNavigator.call(wrapper, navigator, queue)
        wrapperFlag.write(wrapper, true)
        wrapperState(wrapper).localQueue = true
        repeatSetter.call(queue, repeat)
        refresh.call(queue, true)
        return true
    }

    /** อัปเดตแผงที่คิวถืออยู่ แต่ไม่แตะเมื่อ navigator เป็นคิวของเรา */
    fun updateSnapshot(wrapper: Any, panel: ByteArray) {
        if (wrapperState(wrapper).localQueue) return
        val snapshot = localPanel(wrapper, panel) ?: return
        val queue = nativeNavigator.read(wrapper) ?: return
        if (!queueType.isInstance(queue)) return
        storedResponse.write(queue, snapshot)
    }

    /**
     * คืน navigator ให้ autoplay ของเซิร์ฟเวอร์ แล้วทิ้งคิวของเราโดยไม่แตะการเล่น
     *
     * ตรงกับ `patch_clearQueue`: เพลย์ลิสต์ที่ถูกแทนจะไม่กลับไปใช้ชุดการนำทางเดิมของมัน
     */
    fun clearQueue(wrapper: Any): Boolean {
        if (wrapperState(wrapper).localQueue) return false
        val stored = response.read(wrapper) ?: return false
        val queue = nativeNavigator.read(wrapper) ?: return false
        if (queueType.isInstance(queue)) return false

        val builder = builderConstructor.newInstance()
        commandField.write(builder, watchCommand.read(stored))
        val descriptor = buildDescriptor.call(builder) ?: return false
        val descriptorPlaylist = playlistId.call(descriptor) as? String ?: return false

        var autoplayResponse = stored
        var savedAutoplay: Any? = null
        var created = false
        if (descriptorPlaylist.isNotEmpty()) {
            savedAutoplay = wrapperState(wrapper).lastAutoplay
            created = savedAutoplay == null
        } else {
            autoplayResponse = responseConstructor.newInstance(sourceResponse.read(stored))
            responseNavigation.write(autoplayResponse, null)
            created = true
        }

        val navigator = if (created) newAutoplayNavigator() else savedAutoplay
        wrapperState(wrapper).localQueue = false
        autoplay.call(wrapper, navigator)
        wrapperState(wrapper).lastAutoplay = savedAutoplay
        nativeNavigator.read(wrapper)?.let { nativeReceive.call(it, autoplayResponse) }
        if (savedAutoplay != null) autoplay.call(wrapper, savedAutoplay)
        disposeWithoutPlayback(queue)
        return true
    }

    /** คำขอแบบ JUMP สำหรับรายการของเรา ซึ่งอยู่ในคิวของแอปแล้ว */
    fun localJump(descriptor: Any?, target: Any?): Any =
        requestConstructor.newInstance(jumpIntent, descriptor, target)

    /** ถามส่วนขยายว่า response นี้เป็นคำสั่ง watch ที่เราแทนที่เองหรือไม่ */
    fun ignorePlaylistResponse(wrapper: Any, envelope: Any?): Boolean {
        val command = envelope?.let { watchCommand.read(it) } ?: return false
        return NativePlaylistQueuePatch.shouldIgnorePlaylistResponse(
            nativeQueuePlayback(wrapper),
            PlaylistQueueRenderer.NavigationEndpoint.parseFrom(appMessageBytes(command)!!)
        )
    }

    /** เก็บ response ของ playlist ไว้ใช้สร้างแผงของเรา และเก็บไว้ให้ autoplay ภายหลัง */
    fun storeResponse(wrapper: Any, envelope: Any?) {
        response.write(wrapper, envelope)
    }

    /** response ใหม่ที่ใช้คำสั่ง watch เดิมของคิว แต่มีแผงของเราแทน */
    fun localPanel(wrapper: Any, panel: ByteArray): Any? {
        val stored = response.read(wrapper) ?: return null
        val copy = responseConstructor.newInstance(sourceResponse.read(stored))
        responsePanel.write(copy, parseAppMessage(panelType, panel))
        return copy
    }

    /** ทิ้งคิวในธุรกรรมเดียวกับที่ accept ใช้ เพื่อไม่ให้ listener เห็นวิดีโอว่าง */
    fun disposeWithoutPlayback(queue: Any) {
        val token = beginQueueChange.call(queue)
        val guard = changeGuard.read(queue) ?: return
        try {
            queueDispose.call(queue)
        } finally {
            releaseChange.call(guard, token)
        }
    }

    /** คิวที่ถูกทิ้งไม่ได้ถือรายการของเราแล้ว */
    fun releaseLocalItems(queue: Any) {
        setLocalItems(queueOwner.read(queue), false)
    }

    /** navigator ที่ส่งเข้ามาเป็น wrapper ของคิวของแอปหรือไม่ */
    fun isWrapper(candidate: Any): Boolean = wrapperFlag.declaringClass.isInstance(candidate)

    /** ล้างลำดับสุ่มของตัวคิว เพราะรายการเดิมถูกแทนด้วยของเราแล้ว */
    fun clearShuffleOrder(items: Any) {
        shuffleSetter.call(items, false)
        shuffleLinks.forEach { it.write(items, null) }
    }

    private fun newAutoplayNavigator(): Any {
        val empty = autoplayConstructor.parameterTypes.first()
            .methods.first { it.name == "empty" && Modifier.isStatic(it.modifiers) }
            .invoke(null)
        return autoplayConstructor.newInstance(empty, empty, empty)
    }

}

/** ตารางที่ติดตั้งแล้ว ซึ่ง proxy ของ navigator และของ playback ใช้ร่วมกัน */
internal var nativeQueueEngine: NativeQueueEngine? = null

private val nativePlaybacks = ProxyCache { wrapper: Any ->
    NativePlaybackProxy(wrapper, nativeQueueEngine!!)
}

/** proxy ของ navigator wrapper สำหรับส่วนขยาย ซึ่งเป็นตัวเดียวกันทุกครั้งสำหรับ wrapper เดียว */
internal fun nativeQueuePlayback(wrapper: Any): NativePlayback = nativePlaybacks.get(wrapper)

/** แทน `patch_isLocalQueue` และ `patch_*` ของ NativePlayback บน wrapper ของแอป */
private class NativePlaybackProxy(
    private val wrapper: Any,
    private val engine: NativeQueueEngine
) : NativePlayback {
    override fun patch_isLocalQueue(): Boolean = wrapperState(wrapper).localQueue

    override fun patch_getQueueSequence(): Any? =
        queueAction("patch_getQueueSequence") { engine.queueSequence(wrapper) }

    override fun patch_attachQueue(panel: ByteArray, currentWatchCommand: ByteArray): Boolean =
        queueAction("patch_attachQueue") {
            engine.attachQueue(wrapper, panel, currentWatchCommand)
        } ?: false

    override fun patch_updateSnapshot(panel: ByteArray) {
        queueAction("patch_updateSnapshot") { engine.updateSnapshot(wrapper, panel) }
    }

    override fun patch_getSelectedIndex(): Int =
        queueAction("patch_getSelectedIndex") { engine.selectedIndex(wrapper) } ?: -1

    override fun patch_getRepeatMode(): Int =
        queueAction("patch_getRepeatMode") { engine.repeatMode(wrapper) } ?: 0

    override fun patch_restoreQueueRepeat(repeatMode: Int) {
        queueAction("patch_restoreQueueRepeat") { engine.restoreQueueRepeat(wrapper, repeatMode) }
    }

    override fun patch_clearQueue(): Boolean =
        queueAction("patch_clearQueue") { engine.clearQueue(wrapper) } ?: false
}


/**
 * ติดตั้งคิวของแอปให้รับรายการของเราได้
 *
 * ส่วนขยายเรียกคิวนี้ผ่าน NativePlayback ที่ proxy ของโมดูลทำหน้าที่แทนเมธอด `patch_*`
 * ที่ต้นทางเพิ่มให้คลาสของแอป และ hook ที่นี่แทน smali ที่ต้นทางแทรกในเมธอดของ wrapper
 */
internal fun PatchExecutor.installNativeQueueEngine(): Class<*> {
    val changes = with(this) { ::nativeQueueChangeMethods.dexMethodList.map { it.toMethod() } }
    val receiveFields = ::nativeQueueReceiveFields.fieldList
    val receiveMethods = with(this) { ::nativeQueueReceiveMethods.dexMethodList.map { it.toMember() } }
    val repeat = with(this) { ::nativeQueueRepeatMethods.dexMethodList.map { it.toMethod() } }
    val responseFields = ::nativeQueueResponseFields.fieldList
    val shuffleMethods = with(this) { ::nativeQueueShuffleMethods.dexMethodList.map { it.toMethod() } }
    val shuffleFields = ::nativeQueueShuffleFields.fieldList

    val engine = NativeQueueEngine(
        queueType = ::nativeQueueDescriptor.method.declaringClass,
        mutableType = shuffleMethods.first().declaringClass,
        panelType = receiveFields[0].type,
        nativeNavigator = receiveFields[1],
        response = receiveFields[5],
        wrapperFlag = ::nativeQueueWrapperFlag.field,
        queueOwner = ::nativeQueueFields.fieldList[0],
        changeGuard = ::nativeQueueFields.fieldList[1],
        storedResponse = ::nativeQueueStoredResponse.field,
        shuffleLinks = shuffleFields,
        position = shuffleMethods[1],
        repeatSetter = repeat[0],
        repeatGetter = repeat[1],
        beginQueueChange = changes[0],
        releaseChange = changes[2],
        refresh = changes[3],
        accept = ::nativeQueueAccept.method,
        queueDispose = ::nativeQueueDispose.method,
        builderConstructor = receiveMethods[0] as Constructor<*>,
        watchCommand = receiveFields[3],
        commandField = receiveFields[4],
        watchCommandType = receiveFields[3].type,
        buildDescriptor = receiveMethods[1] as Method,
        playlistId = ::nativeQueueLocalPlaylistId.method,
        container = receiveFields[2],
        containerFactory = receiveMethods[2] as Method,
        switchNavigator = receiveMethods[3] as Method,
        responseConstructor = ::nativeQueueResponseConstructor.constructor,
        sourceResponse = responseFields[0],
        responsePanel = receiveFields[0],
        responseNavigation = responseFields[1],
        autoplay = ::nativeQueueAutoplay.method,
        autoplayConstructor = ::nativeQueueAutoplayConstructor.constructor,
        requestConstructor = ::nativeQueueRequestConstructor.constructor,
        jumpIntent = run {
            val jump = ::nativeQueueJumpIntent.field.apply { isAccessible = true }
            jump.get(null) ?: error("Native playlist queue: JUMP intent is missing")
        },
        shuffleSetter = shuffleMethods.first(),
        nativeReceive = receiveMethods[4] as Method,
    )
    nativeQueueEngine = engine

    installNativeQueueResponseHooks(engine, changes[3])
    installNativeQueueEdits(engine)

    // ต้นทางต่อท่อของแถวที่ยังไม่ถูกโหลดจากท้ายเมธอดนี้ โดยส่งชนิดของคิว คำขอ และ descriptor
    // ที่ค้นไว้แล้วให้ hook ของการแบ่งหน้า ซึ่งไม่ค้นซ้ำและตรวจว่าตรงกับตารางของ engine
    installPlaylistPagingSequence(
        wrapperType = ::nativeQueueWrapperFlag.field.declaringClass,
        nativeNavigator = receiveFields[1],
        queueType = ::nativeQueueDescriptor.method.declaringClass,
        requestType = ::nativeQueueRequestClass.clazz,
        descriptorType = ::nativeQueueDescriptor.method.returnType,
        enumType = NavigationIntentEnumFingerprint.declaredClass,
    )
    return ::nativeQueueWrapperFlag.field.declaringClass
}

/**
 * hook ของ response ของ playlist ซึ่งแทน smali ที่ต้นทางแทรกใน wrapper
 *
 * - response ที่เป็นคำสั่งของเราเอง หรือที่มาถึงระหว่างคิวของเรา ไม่ถูกเก็บลงสถานะของแอป
 * - หลังเมธอดรับ response ทำงาน ส่วนขยายได้ทราบว่าคิวของแอปพร้อมใช้งาน
 * - autoplay ของเซิร์ฟเวอร์ถูกจำไว้ให้ Clear และไม่ทับคิวของเรา
 * - refresh ของคิวแจ้งการเลือกใหม่ให้ส่วนขยาย
 */
private fun PatchExecutor.installNativeQueueResponseHooks(engine: NativeQueueEngine, refresh: Method) {
    // สถานะของการเรียกที่กำลังทำอยู่ ใช้ตัดสินว่า after hook ควรแจ้งส่วนขยายหรือไม่
    val suppressed: ThreadLocal<ArrayDeque<Boolean>> = ThreadLocal.withInitial { ArrayDeque() }

    ::nativeQueueReceive.method.hookMethod {
        before { param ->
            val envelope = param.args.firstOrNull()
            val ignored = queueAction("shouldIgnorePlaylistResponse") {
                engine.ignorePlaylistResponse(param.thisObject, envelope)
            } ?: false
            if (ignored || wrapperState(param.thisObject).localQueue) {
                if (!ignored) engine.storeResponse(param.thisObject, envelope)
                suppressed.get()!!.addLast(true)
                param.result = null
                return@before
            }
            suppressed.get()!!.addLast(false)
        }
        after { param ->
            if (suppressed.get()!!.removeLastOrNull() == true) return@after
            NativePlaylistQueuePatch.onNativeQueueResponse(nativeQueuePlayback(param.thisObject))
        }
    }

    ::nativeQueueAutoplay.method.hookMethod {
        before { param ->
            val state = wrapperState(param.thisObject)
            state.lastAutoplay = param.args.firstOrNull()
            if (state.localQueue) param.result = null
        }
    }

    ::nativeQueueWrapperDispose.method.hookMethod {
        before { param ->
            val state = wrapperState(param.thisObject)
            state.localQueue = false
            state.lastAutoplay = null
        }
    }

    refresh.hookMethod {
        after { NativePlaylistQueuePatch.onNativeQueueSelection() }
    }
}

/** hook ของการแก้ไขรายการในคิว ซึ่งแทน smali ของ localLoad และของ dispose */
private fun PatchExecutor.installNativeQueueEdits(engine: NativeQueueEngine) {
    ::nativeQueueLocalLoad.method.hookMethod {
        before { param ->
            if (!isLocalItems(param.thisObject)) return@before
            param.result = queueAction("patch_localJump") {
                engine.localJump(param.args[0], param.args[1])
            }
        }
    }

    ::nativeQueueDispose.method.hookMethod {
        before { engine.releaseLocalItems(it.thisObject) }
    }
}
