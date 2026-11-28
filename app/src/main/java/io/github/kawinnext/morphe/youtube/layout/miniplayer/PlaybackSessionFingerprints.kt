/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5436
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

@file:Suppress("SpellCheckingInspection")

package io.github.kawinnext.morphe.youtube.layout.miniplayer

import io.github.kawinnext.SkipTest
import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.string

// ---------------------------------------------------------------------------
// Continue watching on startup (upstream PR 5436)
//
// fingerprint ชุดนี้เป็นของแพตช์ "Continue watching on startup" อย่างเดียว จึงแยกไฟล์ไว้จาก
// Fingerprints.kt ของ miniplayer ซึ่ง upstream แชร์ให้แพตช์อื่นใช้
// ---------------------------------------------------------------------------

/** Presence bit ของฟิลด์ start paused ใน playback start descriptor proto */
private const val START_PAUSED_PRESENCE_BIT = 0x4000L

/** Presence bit ของฟิลด์ start position ใน playback start descriptor proto */
private const val START_POSITION_PRESENCE_BIT = 0x200L

/**
 * คำอธิบายของ playback start descriptor
 *
 * เมธอด toString เรียกตัวอ่าน video ID เป็นเมธอดแรกหลัง `Locale.getDefault()` ซึ่งเป็นตัวที่
 * proxy ของโมดูลใช้แทนเมธอด `patch_sessionVideoId` ที่ upstream เพิ่มให้คลาสของแอป
 */
internal object PlaybackSessionDescriptorToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        methodCall(smali = "Ljava/util/Locale;->getDefault()Ljava/util/Locale;"),
        // เมธอดแรกที่ถูกเรียกหลัง Locale คือตัวอ่าน video ID
        methodCall(returnType = "Ljava/lang/String;", parameters = listOf()),
        string("PlaybackStartDescriptor:\n  VideoId:%s\n  PlaylistId:%s\n  Index:%d\n  VideoIds:%s")
    )
)

/** เริ่มวิดีโอแบบหยุดไว้ ใช้โดย miniplayer ตอนเปิดแอปของ YouTube Premium */
internal object PlaybackStartDescriptorStartPausedFingerprint : Fingerprint(
    classFingerprint = PlaybackSessionDescriptorToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        literal(START_PAUSED_PRESENCE_BIT),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, type = "Z")
    )
)

/** กำหนดตำแหน่งเริ่มเล่น หน่วยมิลลิวินาที */
internal object PlaybackStartDescriptorStartPositionFingerprint : Fingerprint(
    classFingerprint = PlaybackSessionDescriptorToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        literal(START_POSITION_PRESENCE_BIT),
        fieldAccess(opcode = Opcode.IPUT_WIDE, type = "J")
    )
)

/** สร้าง playback start descriptor จาก command, local proto หรือรายการ video ID */
internal object PlaybackStartDescriptorBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        string("Only one of localProto, command, videoIdList can be set"),
        // รายการ video ID
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT, type = "Ljava/util/List;"),
        methodCall(definingClass = "Ljava/util/List;", name = "get")
    )
)

/**
 * เมธอดที่รับ intent ของวิดีโอพร้อม context แล้วสร้าง playback start descriptor
 *
 * instructionMatches[0] คือตัวอ่าน descriptor ที่ฟังก์ชัน hook เรียกก่อนที่เมธอดจะเริ่มทำงาน
 */
internal object VideoPlaybackIntentFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf(), returnType = "L"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        string("PLAYBACK_START_DESCRIPTOR_MUTATOR", location = MatchAfterImmediately()),
        string("force_fullscreen"),
        string("VideoPresenterConstants.VIDEO_THUMBNAIL_BITMAP_KEY")
    )
)

/** เมธอดเปิด Shorts จาก intent ซึ่ง upstream เรียก prepareShortsDescriptor ที่ต้นเมธอด */
internal object ShortsPlaybackIntentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "L",
        "Ljava/util/Map;",
        "J",
        "Ljava/lang/String;"
    ),
    filters = listOf(
        // ไม่มีสตริงใดในสามตัวนี้ที่ไม่ซ้ำ
        string("PLAYBACK_START_DESCRIPTOR_MUTATOR"),
        string("com.google.android.apps.youtube.app.endpoint.flags"),
        string("com.google.android.libraries.youtube.rendering.elements.sender_view")
    )
)

/** คลาสของ miniplayer แบบใหม่ ซึ่งเป็นเจ้าของ overlay ที่ต้องผูกปุ่มเล่นกับเซสชันที่กู้คืน */
private object PlaybackSessionMiniplayerViewParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("player_overlay_modern_mini_player_controls")
    )
)

/**
 * เมธอดที่ inflate layout ของ overlay miniplayer แบบใหม่
 *
 * upstream อ่าน register ของ `MOVE_RESULT_OBJECT` แล้วส่ง view ให้
 * `MiniplayerPatch.bindRestoredMiniplayerOverlay` โมดูลอ่าน register ไม่ได้ จึง hook
 * `LayoutInflater.inflate` ที่ถูกเรียกจากเมธอดนี้แทน (ได้ view ตัวเดียวกันจากค่าที่คืน)
 */
internal object PlaybackSessionOverlayInflationFingerprint : Fingerprint(
    classFingerprint = PlaybackSessionMiniplayerViewParentFingerprint,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "modern_miniplayer_controls_overlay"),
        methodCall(
            definingClass = "Landroid/view/LayoutInflater;",
            name = "inflate",
            returnType = "Landroid/view/View;",
            location = MatchAfterWithin(6)
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)

/**
 * คลาสที่มีตัวอ่านบัญชีปัจจุบันของแอป
 *
 * มีสอง provider และตัวใดตัวหนึ่งอาจเป็นตัวที่ใช้งาน จึง hook ตัวที่ตรงกับ contract ของบัญชี
 *
 * fingerprint ของคลาส ไม่ใช่ของเมธอดเดียว จึงจับได้หลายเมธอดและถูกใช้ผ่าน [matchAll] เท่านั้น
 */
@SkipTest
internal object PlaybackSessionAccountProviderFingerprint : Fingerprint(
    filters = listOf(
        string("NEXT_INCOGNITO_SESSION_INDEX")
    )
)
