/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4148
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package io.github.kawinnext.morphe.youtube.interaction.playlistpanel

import android.app.Activity
import app.morphe.extension.youtube.patches.CommandRouterPatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.createProxy
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.youtube.misc.commandrouter.commandRouterExecuteCommandMethod
import java.lang.reflect.Modifier

// ต้นทางเพิ่มเมธอด bridge ให้คลาส command router ของแอป (ให้คลาสนั้นทำอินเทอร์เฟซของส่วนขยาย)
// แล้วเรียกเมธอดของตัวมันเองด้วยคำสั่งที่แปลงจาก bytes ของส่วนขยาย โมดูลเพิ่มเมธอดหรืออินเทอร์เฟซ
// ให้คลาสของแอปตอนรันไม่ได้ object ของโมดูลจึงทำอินเทอร์เฟซแทน แล้วเรียกเมธอดเดิมของ router
// ผ่าน reflection ด้วยคำสั่งที่แปลงเป็นข้อความของแอปด้วย protobuf ของแอปเอง
//
// ส่วนขยายกับแอปมีคนละสำเนาของ runtime และ schema คำสั่งจึงข้ามฝั่งกันเป็น bytes เท่านั้น
// (วิธีเดียวกับที่ bridge ของ native playlist queue ทำ)

/**
 * ติดตั้ง command router ของทุก activity ให้ส่วนขยายวิ่งคำสั่งได้เหมือนคำสั่งที่มาจากเซิร์ฟเวอร์
 *
 * เรียกจากแพตช์ที่ต้องใช้คำสั่งของแอป (ปุ่มเปิดแผงเพลย์ลิสต์) แพตช์ละครั้ง เพราะการเรียกซ้ำจะ
 * hook constructor ของ router ซ้อนกันสองตัว แล้วลงทะเบียน router ของ activity เดิมซ้ำโดยไม่จำเป็น
 */
internal fun PatchExecutor.installCommandRouter() {
    val executeCommand = ::commandRouterExecuteCommandMethod.method
    check(executeCommand.parameterCount == 2 && executeCommand.parameterTypes[1] == Map::class.java) {
        "The command router runs another command than a command and its context: $executeCommand"
    }
    val commandType = executeCommand.parameterTypes[0]

    CommandRouterConstructorFingerprint.hookMethod {
        after {
            val activity = it.args[0] as? Activity ?: return@after
            val router = it.thisObject
            val proxy = router.createProxy<CommandRouterPatch.CommandRouter, Any> { routerRef ->
                object : CommandRouterPatch.CommandRouter {
                    override fun patch_executeCommand(
                        command: ByteArray,
                        context: MutableMap<String, Any>
                    ) {
                        val instance = routerRef.get() ?: return
                        executeCommand.invoke(instance, parseAppCommand(commandType, command), context)
                    }
                }
            }
            CommandRouterPatch.setCommandRouter(proxy, activity)
        }
    }
}

/**
 * แปลง [bytes] เป็นข้อความของแอปด้วย protobuf ของแอปเอง
 *
 * คลาสข้อความที่สร้างจาก protobuf มีเมธอดกลางสำหรับอ่าน bytes อยู่แล้ว ถ้าแอปไม่ได้เก็บชื่อไว้
 * ก็อ่านผ่าน default instance ของคลาสนั้น (ทางเดียวกับที่ smali bridge ของ upstream ใช้)
 */
private fun parseAppCommand(type: Class<*>, bytes: ByteArray): Any {
    type.methods.firstOrNull {
        it.name == "parseFrom" && Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
    }?.let { return it.invoke(null, bytes)!! }

    val defaultInstance = (type.declaredFields + type.fields).firstOrNull {
        Modifier.isStatic(it.modifiers) && it.type == type
    }?.apply { isAccessible = true }?.get(null)
        ?: throw NoSuchFieldException("No default instance of ${type.name}")

    val parser = defaultInstance.javaClass.getMethod("getParserForType").invoke(defaultInstance)!!
    val parseFrom = parser.javaClass.methods.first {
        it.name == "parseFrom" && it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
    }.apply { isAccessible = true }
    return parseFrom.invoke(parser, bytes)!!
}
