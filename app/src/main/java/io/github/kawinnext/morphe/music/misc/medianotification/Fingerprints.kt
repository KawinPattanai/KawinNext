/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4617
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.misc.medianotification

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string

/**
 * Name getter of the shuffle action of the media session.
 * Each media session action is its own class and returns its name from a constant getter.
 */
internal object ShuffleActionNameFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("shuffle_action"),
        opcode(opcode = Opcode.RETURN_OBJECT, location = MatchAfterImmediately())
    )
)

/**
 * Tells if the shuffle action is shown as a media control button.
 */
internal object ShuffleActionShownFingerprint : Fingerprint(
    classFingerprint = ShuffleActionNameFingerprint,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z"
        )
    )
)
