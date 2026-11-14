/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4659
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.autoplay

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode

/**
 * เมธอดที่ตั้งวิดีโอที่โหลดอยู่ของ background play ซึ่งถูกเรียกทุกครั้งที่วิดีโอปกติเริ่มเล่น
 * แม้ตอนที่ไม่มีภาพวิดีโออยู่ (ฟังเพลย์ลิสต์/มิกซ์อยู่เบื้องหลัง)
 *
 * ตรงกับ `VideoIdBackgroundPlayFingerprint` ของ upstream ที่ `hookBackgroundPlayVideoId` ใช้
 * เอกสารของ upstream ระบุว่า hook วิดีโอตัวหลัก (`hookVideoId`) ไม่ถูกเรียกตอนเล่นอยู่เบื้องหลัง
 * จึงใช้ hook ของ background play ตามที่ Autoplay control ของ upstream เลือก
 *
 * ตัวเขียนฟิลด์ที่ซิงโครไนซ์ไว้: อ่านวิดีโอจากเมธอดของอินเทอร์เฟซ แล้วเขียนลงฟิลด์สตริงของตัวเอง
 * ระหว่าง monitor-enter/exit
 */
@StrictQuery
internal object BackgroundPlayVideoIdFingerprint : Fingerprint(
    accessFlags = listOf(
        AccessFlags.DECLARED_SYNCHRONIZED, AccessFlags.FINAL, AccessFlags.PUBLIC
    ),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_INTERFACE, returnType = "Ljava/lang/String;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.MONITOR_EXIT),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately())
    )
)

/**
 * Constructor ของสถานะการนำทางของตัวเล่น ซึ่งรับว่ามีวิดีโอก่อนหน้า/ถัดไปหรือไม่, โหมดเล่นซ้ำ
 * (พารามิเตอร์ที่ 3) และการสลับลำดับ
 *
 * ตรงกับ `NativeNavigationStateFingerprint` ของ upstream ที่แพตช์ "Disable playlist autoplay"
 * ใช้เก็บโหมดเล่นซ้ำให้การตัดสินของ autoplay ซึ่งกฎของ Autoplay control อ่านค่าด้วย
 * (วิดีโอที่เล่นซ้ำไม่ใช่การไปวิดีโอถัดไป)
 */
@StrictQuery
internal object NativeNavigationStateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Z", "Z", "I", "Z"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, definingClass = "this"),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IPUT,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately())
    )
)
