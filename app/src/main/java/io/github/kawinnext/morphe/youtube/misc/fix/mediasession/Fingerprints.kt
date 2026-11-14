package io.github.kawinnext.morphe.youtube.misc.fix.mediasession

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.string
import org.luckypray.dexkit.result.MethodData

internal const val MEDIA_SESSION_OWNER_FEATURE_FLAG = 45857115L

/**
 * 21.42+
 *
 * A/B flag: a player stops the media session only if the session was started by that player.
 *
 * The patch turns the flag on through [io.github.kawinnext.morphe.youtube.insertLiteralOverride],
 * which is keyed by the flag id; this fingerprint is what keeps the id of a build under test.
 */
internal object MediaSessionOwnerFeatureFlagFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(MEDIA_SESSION_OWNER_FEATURE_FLAG)
    )
)

/**
 * Starts the media session shared by all players.
 */
internal object MediaSessionStartFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("MediaSession setActive(true)")
    )
)

/**
 * Stops the media session shared by all players (the parameter is true if the playback is destroyed).
 */
internal object MediaSessionStopFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        string("MediaSession setActive(false)")
    )
)

/**
 * Method of the player that starts the media session.
 */
internal fun playerMediaSessionStartFingerprint(
    sessionClass: String,
    startMethodName: String
) = Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            definingClass = sessionClass,
            name = startMethodName,
            parameters = listOf(),
            returnType = "V"
        )
    )
)

/**
 * Every method that calls a method of the media session.
 * Use with [Fingerprint.matchAll].
 */
internal fun mediaSessionCallFingerprint(
    sessionClass: String,
    methodName: String,
    parameters: List<String>
) = Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = sessionClass,
            name = methodName,
            parameters = parameters,
            returnType = "V"
        )
    )
)

/** The session method that starts it. */
internal val mediaSessionStartMethod = findMethodDirect {
    MediaSessionStartFingerprint.match().method
}

/** The session method that stops it. */
internal val mediaSessionStopMethod = findMethodDirect {
    MediaSessionStopFingerprint.match().method
}

/** The player method that starts the session, which tells the class of the players. */
internal val playerMediaSessionStartMethod = findMethodDirect {
    val session = MediaSessionStartFingerprint.match().method
    playerMediaSessionStartFingerprint(session.declaredClass!!.descriptor, session.name).run()
}

/**
 * The player methods that start the session and read it from the field of the player,
 * which is the object the extension is told about.
 */
internal val playerMediaSessionStartMethods = findMethodListDirect {
    val session = MediaSessionStartFingerprint.match().method
    val sessionType = session.declaredClass!!.descriptor
    val playerType = playerMediaSessionStartMethod().declaredClass!!.descriptor

    mediaSessionCallFingerprint(sessionType, session.name, emptyList()).matchAll()
        .map { it.method }
        .filter { it.readsSessionField(playerType, sessionType) }
}

/** The player methods that stop the session and read it from the field of the player. */
internal val playerMediaSessionStopMethods = findMethodListDirect {
    val session = MediaSessionStopFingerprint.match().method
    val sessionType = session.declaredClass!!.descriptor
    val playerType = playerMediaSessionStartMethod().declaredClass!!.descriptor

    mediaSessionCallFingerprint(sessionType, session.name, listOf("Z")).matchAll()
        .map { it.method }
        .filter { it.readsSessionField(playerType, sessionType) }
}

/** The player reads the session from its field before it calls the session method. */
private fun MethodData.readsSessionField(playerType: String, sessionType: String): Boolean {
    val sessionField = fieldAccess(
        opcode = Opcode.IGET_OBJECT,
        definingClass = playerType,
        type = sessionType
    )
    return instructions.any { sessionField.matches(this, it) }
}
