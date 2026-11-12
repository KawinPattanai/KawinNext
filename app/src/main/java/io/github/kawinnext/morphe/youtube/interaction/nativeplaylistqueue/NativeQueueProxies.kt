/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import android.os.Bundle
import app.morphe.extension.shared.Logger
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import app.morphe.extension.youtube.patches.queue.QueueFlyoutActions
import app.morphe.extension.youtube.patches.queue.QueueSaveActions
import app.morphe.extension.youtube.patches.queue.QueueSaveFeedback
import app.morphe.extension.youtube.patches.queue.QueueUndoActions
import com.google.protobuf.MessageLite
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

// ต้นทางเพิ่มอินเทอร์เฟซของส่วนขยายให้คลาสของแอป และเก็บสถานะของ bridge ไว้ในฟิลด์ที่มันเพิ่มเข้าไป
// ซึ่งทำตอนรันไม่ได้ object ของโมดูลจึงแทนที่แอปและส่งต่อให้ฟิลด์/เมธอดของมัน ส่วนสถานะเก็บไว้ใน
// ไฟล์นี้โดยยึดแอปออบเจกต์แบบ weak

/**
 * แปลง [bytes] เป็นข้อความของแอปด้วย protobuf ของแอปเอง
 *
 * ส่วนขยายส่งข้อความข้ามขอบเขตกันเป็น bytes เท่านั้น เพราะโมดูลกับแอปต่างมีสำเนาของ runtime
 * และของ schema ของตัวเอง
 */
internal fun parseAppMessage(type: Class<*>, bytes: ByteArray): Any {
    // Generated messages declare their own byte[] parser, which keeps the app's registry.
    type.methods.firstOrNull {
        it.name == "parseFrom" && Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
    }?.let { return it.invoke(null, bytes)!! }

    // Otherwise parse through the default instance, as the patch's smali bridge does.
    val defaultInstance = (type.declaredFields + type.fields).firstOrNull {
        Modifier.isStatic(it.modifiers) && it.type == type
    }?.apply { isAccessible = true }?.get(null)
        ?: throw NoSuchFieldException("No default instance of ${type.name}")

    val parser = defaultInstance.javaClass.getMethod("getParserForType")
        .invoke(defaultInstance)!!
    val parseFrom = parser.javaClass.methods.first {
        it.name == "parseFrom" && it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
    }.apply { isAccessible = true }
    return parseFrom.invoke(parser, bytes)!!
}

/** แปลงข้อความของแอปเป็น bytes ซึ่งเป็นทางเดียวที่โมดูลส่งต่อให้ส่วนขยายได้ */
internal fun appMessageBytes(message: Any?): ByteArray? {
    if (message == null) return null
    return message.javaClass.methods.firstOrNull {
        it.name == "toByteArray" && it.parameterTypes.isEmpty()
    }?.apply { isAccessible = true }?.invoke(message) as? ByteArray
}

internal fun Field.read(instance: Any?): Any? = apply { isAccessible = true }.get(instance)

internal fun Field.write(instance: Any?, value: Any?) = apply { isAccessible = true }.set(instance, value)

internal fun Method.call(instance: Any?, vararg args: Any?): Any? =
    apply { isAccessible = true }.invoke(instance, *args)

// ---------------------------------------------------------------------------
// State the patch keeps in fields of the app classes.
// ---------------------------------------------------------------------------

/** State of one navigator wrapper: its local queue flag and the autoplay navigator it replaced. */
internal class NativeQueueWrapperState {
    var localQueue = false
    var lastAutoplay: Any? = null
}

private val wrapperStates = WeakHashMap<Any, NativeQueueWrapperState>()

internal fun wrapperState(wrapper: Any): NativeQueueWrapperState =
    synchronized(wrapperStates) { wrapperStates.getOrPut(wrapper) { NativeQueueWrapperState() } }

/** The item storages that hold rows of the local queue. */
private val localItemOwners: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

internal fun isLocalItems(owner: Any?): Boolean = owner != null &&
        synchronized(localItemOwners) { localItemOwners.contains(owner) }

internal fun setLocalItems(owner: Any?, local: Boolean) {
    if (owner == null) return
    synchronized(localItemOwners) {
        if (local) localItemOwners.add(owner) else localItemOwners.remove(owner)
    }
}

/** The native save rows of an edit callback, kept until its result is dispatched. */
internal class NativeQueueSaveSelection(
    val playlistId: String,
    val title: String,
    val videoIds: List<String>
)

private val saveSelections = WeakHashMap<Any, NativeQueueSaveSelection>()

internal fun saveSelection(callback: Any): NativeQueueSaveSelection? =
    synchronized(saveSelections) { saveSelections[callback] }

internal fun setSaveSelection(callback: Any, selection: NativeQueueSaveSelection?) {
    synchronized(saveSelections) {
        if (selection == null) saveSelections.remove(callback) else saveSelections[callback] = selection
    }
}

// ---------------------------------------------------------------------------
// Proxies of the interfaces the extension declares for app objects.
//
// Every message these return belongs to the module's own protobuf runtime, so the boundary is
// message bytes, exactly like the smali bridges: [parseAppMessage] turns those bytes back into
// the app's object, and [moduleMessage] wraps an app message for the extension.
// ---------------------------------------------------------------------------

/**
 * The native menu model, which the extension completes with its own queue items.
 *
 * The message methods are delegated to the module's own copy of the very same menu, so the
 * extension can serialize what the app gave us without linking against the app's runtime.
 */
internal class NativeQueueMenuProxy(private val menu: Any) : QueueFlyoutActions.Menu,
    MessageLite by PlaylistQueueRenderer.MenuRenderer.parseFrom(appMessageBytes(menu)!!) {
    /** The app object this proxy stands for. */
    val appMenu: Any get() = menu

    override fun patch_parseQueueFlyout(bytes: ByteArray): MessageLite =
        PlaylistQueueRenderer.MenuRenderer.parseFrom(bytes)
}

internal fun menuProxy(menu: Any?): NativeQueueMenuProxy? = menu?.let(::NativeQueueMenuProxy)

/** The native picker fragment, which reads its arguments of an owned save. */
internal class NativeQueuePickerProxy(
    private val arguments: Bundle?
) : QueueSaveActions.Picker {
    override fun patch_getQueueSaveVideoIds(): List<String>? =
        NativePlaylistQueuePatch.getQueueSaveVideoIds(arguments)

    override fun patch_parseQueueSaveResponse(bytes: ByteArray): MessageLite =
        PlaylistQueueRenderer.QueueSaveResponse.parseFrom(bytes)
}

/** The native edit callback of an owned save, which carries the rows that were saved. */
internal class NativeQueueSaveCallbackProxy(
    private val callback: Any
) : QueueSaveFeedback.Callback {
    override fun patch_getQueueSaveSelection(): QueueSaveFeedback.Selection? =
        saveSelection(callback)?.let {
            QueueSaveFeedback.Selection(it.playlistId, it.title, it.videoIds)
        }

    override fun patch_setQueueSaveSelection(selection: QueueSaveFeedback.Selection?) {
        setSaveSelection(callback, selection?.let {
            NativeQueueSaveSelection(it.playlistId(), it.title(), it.videoIds())
        })
    }

    override fun patch_parseQueueSaveResult(bytes: ByteArray): MessageLite =
        PlaylistQueueRenderer.QueueSaveEditResponse.parseFrom(bytes)
}

/** The native snackbar command, which the extension parses with its own schema. */
internal class NativeQueueCommandProxy : QueueUndoActions.Command {
    override fun patch_parseQueueCommand(bytes: ByteArray): MessageLite =
        PlaylistQueueRenderer.NavigationEndpoint.parseFrom(bytes)
}

/**
 * เก็บ proxy หนึ่งตัวต่อแอปออบเจกต์หนึ่ง เพราะส่วนขยายเทียบออบเจกต์ที่ถูกส่งให้ด้วยตัวตน
 * เช่น playback ที่ response มาถึง กับตัวที่มันเก็บไว้ ต้องเป็นตัวเดียวกัน
 */
internal class ProxyCache<TProxy : Any>(private val create: (Any) -> TProxy) {
    private val proxies = WeakHashMap<Any, TProxy>()

    fun get(instance: Any): TProxy = synchronized(proxies) {
        proxies.getOrPut(instance) { create(instance) }
    }
}

/**
 * ชนิดของ endpoint ของเมนู ซึ่งคำสั่งของปุ่ม native ส่งเข้ามาเป็นพารามิเตอร์ตัวแรก
 *
 * ต้นทางได้ค่านี้จากไฟล์ที่ติดตั้ง hook ของเมนู ที่นี่อ่านจากคำสั่งของปุ่มเดียวกันโดยตรง
 */
internal fun DexKitBridge.queueMenuEndpointType(): String =
    NativeButtonDispatchFingerprint.match().instructionMatches.single().instruction.methodRef!!
        .paramTypes.first().descriptor

/** เรียกส่วนขยายแบบกันพลาด เพื่อไม่ให้ข้อผิดพลาดของมันลามไปถึงแอป */
internal inline fun <T> queueAction(name: String, block: () -> T?): T? = try {
    block()
} catch (exception: Exception) {
    Logger.printException({ "Native playlist queue: $name failure" }, exception)
    null
}
