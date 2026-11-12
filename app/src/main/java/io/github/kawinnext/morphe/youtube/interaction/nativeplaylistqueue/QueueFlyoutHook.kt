/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
import app.morphe.extension.youtube.patches.queue.QueueFlyoutActions
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.youtube.layout.flyout.FeedFlyoutButtonsInitializerFingerprint
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import java.lang.reflect.Field
import java.lang.reflect.Method

// ปลั๊กอินนี้ทำสองอย่างกับเมนูของแอป: เติมโมเดลเมนูให้มีรายการคิวของเรา และกันคำสั่งของแอป
// เมื่อรายการที่กดเป็นของเรา ต้นทางแทรก smali กลางเมธอด แต่ที่นี่ hook ที่ต้นเมธอดแล้วอ่าน/เขียน
// ฟิลด์หรือพารามิเตอร์ตัวเดียวกับที่ smali ของต้นทางใช้

/** ชนิดของรายการเมนู ซึ่งตัวสร้างปุ่มของ flyout และชีตใช้ร่วมกัน */
private fun DexKitBridge.queueFlyoutItemType(): String =
    FeedFlyoutButtonsInitializerFingerprint.match().instructionMatches.first()
        .instruction.methodRef!!.paramTypes.single().descriptor

/** คลาสปุ่มของชีต ซึ่งฟิลด์รายการเมนูอยู่บนปุ่ม ไม่ได้อยู่บนชีต */
private fun DexKitBridge.queueFlyoutButtonType(): String =
    queueFlyoutButtonConstructorFingerprint(
        FeedFlyoutButtonsInitializerFingerprint.match().method, queueFlyoutItemType()
    ).match().instructionMatches.single().instruction.methodRef!!.declaredClass!!.descriptor

/** เมธอดของชีตทั้งสองที่อ่านโมเดลเมนูของตัวเองจากฟิลด์ของชีต */
private val queueFlyoutSheets = findMethodListDirect {
    QueueFlyoutRendererFingerprint.matchAll().map { it.method }
}

/** ฟิลด์โมเดลเมนูของชีตแต่ละแบบ ตามลำดับเดียวกับ [queueFlyoutSheets] */
private val queueFlyoutMenuFields = findFieldListDirect {
    QueueFlyoutRendererFingerprint.matchAll().map {
        it.instructionMatches.first().instruction.fieldRef!!
    }
}

/** ฟิลด์รายการของเมนู ซึ่งทั้งชีตและป๊อปอัปวนอ่านรายการจากมัน */
private val queueFlyoutItemField = findFieldDirect {
    QueueFlyoutRendererFingerprint.matchAll().first()
        .instructionMatches[1].instruction.fieldRef!!
}

/** เมธอดของป๊อปอัปที่รับโมเดลเมนูเข้ามาเป็นพารามิเตอร์ตัวแรก */
private val queueFlyoutPopupMethod = findMethodDirect {
    val resolver = QueueFlyoutResolverFingerprint().declaredClass!!.descriptor
    val menu = QueueFlyoutRendererFingerprint.matchAll().first()
        .instructionMatches.first().instruction.fieldRef!!.type.descriptor
    queueFlyoutPopupFingerprint(resolver, menu, queueFlyoutItemField(this)).match().method
}

/** แปลงรายการเมนูเป็นโมเดลไอคอนของแอป ซึ่งตัวสร้างปุ่มของ flyout ใช้ร่วมกัน */
private val queueFlyoutIconGetter = findMethodDirect {
    FeedFlyoutButtonsInitializerFingerprint.match().instructionMatches.first().instruction.methodRef!!
}

/** ฟิลด์ชนิดไอคอนในโมเดลไอคอนของแอป */
private val queueFlyoutIconField = findFieldDirect {
    FeedFlyoutButtonsInitializerFingerprint.match().instructionMatches[6].instruction.fieldRef!!
}

/** แปลงชนิดไอคอนเป็นค่าของ Enum ที่ส่วนขยายใช้ตัดสินใจ */
private val queueFlyoutIconEnum = findMethodDirect {
    FeedFlyoutButtonsInitializerFingerprint.match().instructionMatches[7].instruction.methodRef!!
}

/** ตัวสร้างปุ่มของชีต ซึ่งบอกคลาสของปุ่มที่ถือรายการเมนู */
private val queueFlyoutButtonConstructor = findMethodDirect {
    queueFlyoutButtonConstructorFingerprint(
        FeedFlyoutButtonsInitializerFingerprint.match().method, queueFlyoutItemType()
    ).match().instructionMatches.single().instruction.methodRef!!
}

/**
 * เมธอดคลิกของชีตทั้งสอง ตามด้วยเมธอดปิดชีตของมันเอง
 *
 * ต้นทางกระโดดข้ามคำสั่งของแอปไปยังเมธอดปิดชีต ที่นี่จึงเก็บเมธอดปิดชีตของแต่ละชีตไว้เรียกแทน
 * เก็บเป็นคู่ในลิสต์เดียวเพื่อให้ลำดับตรงกับ [queueFlyoutSheets] เสมอ
 */
private val queueFlyoutClicks = findMethodListDirect {
    val item = queueFlyoutItemType()
    val button = queueFlyoutButtonType()
    QueueFlyoutRendererFingerprint.matchAll().flatMap { sheet ->
        val click = queueFlyoutClickFingerprint(sheet.method.declaredClass!!.descriptor, button, item).match()
        listOf(click.method, click.instructionMatches[1].instruction.methodRef!!)
    }
}

/** ฟิลด์รายการเมนูที่ปุ่มของชีตถืออยู่ ตามลำดับเดียวกับ [queueFlyoutClicks] */
private val queueFlyoutClickItems = findFieldListDirect {
    val item = queueFlyoutItemType()
    val button = queueFlyoutButtonType()
    QueueFlyoutRendererFingerprint.matchAll().map { sheet ->
        queueFlyoutClickFingerprint(sheet.method.declaredClass!!.descriptor, button, item).match()
            .instructionMatches.first().instruction.fieldRef!!
    }
}

/**
 * เติมโมเดลเมนูของชีตและของป๊อปอัปให้มีรายการคิวของเรา
 *
 * smali ของต้นทางแทนค่าที่อ่านได้ด้วยผลของ `completeQueueMenu` ก่อนที่แอปจะวนรายการของมัน
 * ที่นี่เขียนทับฟิลด์หรือพารามิเตอร์ตัวเดียวกันก่อนที่เมธอดของแอปจะอ่านค่า
 */
internal fun PatchExecutor.installQueueFlyout() {
    val menuFields = ::queueFlyoutMenuFields.fieldList
    val menuType = menuFields.first().type
    // ต้นทางยืนยันว่าเมนูทั้งสองแบบใช้โมเดลชนิดเดียวกัน และมีรายการคลิกครบคู่
    check(menuFields.map { it.type }.distinct().size == 1) {
        "Native playlist queue: menus must share their renderer type"
    }
    val sheets = ::queueFlyoutSheets.dexMethodList.map { it.toMethod() }

    sheets.zip(menuFields).forEach { (sheet, menuField) ->
        sheet.hookMethod {
            before { param ->
                val original = menuField.read(param.thisObject) ?: return@before
                val completed = completeQueueMenu(original, menuType) ?: return@before
                if (completed !== original) menuField.write(param.thisObject, completed)
            }
        }
    }

    ::queueFlyoutPopupMethod.method.hookMethod {
        before { param ->
            val original = param.args.firstOrNull() ?: return@before
            val completed = completeQueueMenu(original, menuType) ?: return@before
            if (completed !== original) param.args[0] = completed
        }
    }

    // คลิกของชีตไม่ผ่าน Runnable ของโมเดล จึงต้องตัดสินใจที่นี่ก่อนที่แอปจะส่งคำสั่งของมัน
    val buttonType = classLoader.loadClass(::queueFlyoutButtonConstructor.dexMethod.declaredClassName)
    val iconGetter = ::queueFlyoutIconGetter.method
    val iconField = ::queueFlyoutIconField.field
    val iconEnum = ::queueFlyoutIconEnum.method
    val clicks = ::queueFlyoutClicks.dexMethodList.map { it.toMethod() }.chunked(2)
    val clickItems = ::queueFlyoutClickItems.fieldList
    check(clicks.size == clickItems.size) {
        "Native playlist queue: every sheet click must have its item field"
    }

    clicks.zip(clickItems).forEach { (pair, itemField: Field) ->
        val click: Method = pair.first()
        val dismissal: Method = pair.last()
        click.hookMethod {
            before { param ->
                val button = param.args.firstOrNull { buttonType.isInstance(it) } ?: return@before
                val item = itemField.read(button) ?: return@before
                if (!consumeQueueItemClick(item, iconGetter, iconField, iconEnum)) return@before
                param.result = null
                dismissal.call(param.thisObject)
            }
        }
    }
}

/**
 * ตัดสินใจว่าคลิกนี้เป็นของคิวของเราหรือไม่
 *
 * ลำดับตรงกับ smali ของต้นทาง: รายการคิวของเราจบที่นี่ รายการที่ไม่มีไอคอนไปต่อตามปกติ
 * และไอคอนที่ส่วนขยายไม่ได้รับไปก็ไม่ปล่อยให้คำสั่งของแอปทำงาน
 */
private fun consumeQueueItemClick(
    item: Any,
    iconGetter: Method,
    iconField: Field,
    iconEnum: Method
): Boolean {
    return queueAction("onPlaylistQueueItemClick") {
        if (QueueFlyoutActions.onPlaylistQueueItemClick(moduleMenuItem(item))) return@queueAction true

        val icon = iconGetter.call(null, item) ?: return@queueAction false
        val iconType = iconEnum.call(null, iconField.read(icon)) as? Enum<*>
            ?: return@queueAction false
        !QueueFlyoutActions.onStockQueueItemClick(iconType)
    } ?: false
}

private fun moduleMenuItem(item: Any) =
    PlaylistQueueRenderer.MenuItem.parseFrom(appMessageBytes(item)!!)

/** ผลของ [QueueFlyoutActions.completeQueueMenu] แปลงกลับเป็นโมเดลของแอป */
private fun completeQueueMenu(original: Any, menuType: Class<*>): Any? = queueAction("completeQueueMenu") {
    when (val completed = QueueFlyoutActions.completeQueueMenu(menuProxy(original))) {
        null -> null
        is NativeQueueMenuProxy -> completed.appMenu
        else -> parseAppMessage(menuType, completed.toByteArray())
    }
}
