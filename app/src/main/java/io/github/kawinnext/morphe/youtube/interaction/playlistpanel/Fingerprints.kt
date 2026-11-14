/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4662
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.playlistpanel

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.youtube.misc.commandrouter.CommandRouterFragmentFingerprint

/**
 * Constructor ของ command router ของ activity ซึ่งสร้างมาพร้อม activity
 *
 * ตรงกับ `CommandRouterConstructorFingerprint` ของ upstream แพตช์ "Command router"
 * พารามิเตอร์แรกคือ activity ที่ router วิ่งคำสั่งให้ ส่วนพารามิเตอร์ที่สามคือ executor
 */
internal object CommandRouterConstructorFingerprint : Fingerprint(
    classFingerprint = CommandRouterFragmentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    name = "<init>",
    parameters = listOf("Landroid/app/Activity;", "L", "Ljava/util/concurrent/Executor;"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Object;-><init>()V")
    )
)
