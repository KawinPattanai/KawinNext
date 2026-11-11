/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.miniplayer

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

/**
 * Matches the miniplayer constructor.
 * Identified by the play/pause button resource literal and a unique string in the method body.
 */
internal object MiniPlayerConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        resourceLiteral(
            type = ResourceType.ID,
            name = "mini_player_play_pause_replay_button"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "findViewById",
            location = MatchAfterWithin(5)
        ),
        string("sharedToggleMenuItemMutations")
    )
)

/**
 * Matches the TabLayout method that assigns the navigation bar background color.
 */
internal object NavigationBarTabLayoutFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("FEmusic_radio_builder"),
        resourceLiteral(
            type = ResourceType.COLOR,
            name = "ytm_color_grey_12"
        ),
        methodCall(name = "setBackgroundColor")
    )
)

internal object SwitchToggleColorParentFingerprint : Fingerprint(
    classFingerprint = MiniPlayerConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "J")
)

/**
 * Method that switches the color of the miniplayer to the color of the album art.
 * The album art color object and its int field follow the toggle branch.
 */
internal object SwitchToggleColorFingerprint : Fingerprint(
    classFingerprint = MiniPlayerConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "J"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_STATIC, smali = "Lj\$/time/Duration;->ofMillis(J)Lj\$/time/Duration;"),
        opcode(Opcode.IF_NE),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, location = MatchAfterWithin(5)),
        fieldAccess(opcode = Opcode.IGET, type = "I", location = MatchAfterWithin(5)),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "L", location = MatchAfterWithin(5))
    )
)

internal object MusicActivityWidgetFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/activities/MusicActivity;",
    name = "onCreate",
    filters = listOf(
        string("widget_key"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(5)),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf(),
            returnType = "Ljava/lang/Object;",
            location = MatchAfterWithin(3)
        ),
        opcode(Opcode.CHECK_CAST, location = MatchAfterWithin(3)),
        fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterWithin(3)),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterWithin(3)),
        literal(literal = 79500L, location = MatchAfterWithin(3)),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I"),
            location = MatchAfterWithin(3)
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("L"),
            location = MatchAfterWithin(3)
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("L", "L", "L"),
            returnType = "V",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Matches the watch-while dismiss callback (swipe-dismiss or "Dismiss queue"),
 * identified by an IGET_OBJECT of the MusicActivity peer's AtomicBoolean and
 * a following `AtomicBoolean.set(Z)`. Caller must supply the peer class via
 * [MusicActivityWidgetFingerprint].
 */
internal fun watchWhileDismissedFingerprint(musicActivityPeerClass: String) = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = musicActivityPeerClass,
            type = "Ljava/util/concurrent/atomic/AtomicBoolean;"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Ljava/util/concurrent/atomic/AtomicBoolean;->set(Z)V",
            location = MatchAfterWithin(3)
        )
    )
)
