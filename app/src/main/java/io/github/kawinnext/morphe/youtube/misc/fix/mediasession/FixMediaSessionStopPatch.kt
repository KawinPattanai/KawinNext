package io.github.kawinnext.morphe.youtube.misc.fix.mediasession

import app.morphe.extension.youtube.patches.FixMediaSessionStopPatch
import io.github.kawinnext.morphe.youtube.misc.playservice.VersionCheck
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Member

/**
 * Fixes the media notification going away and the audio stopping in the background while the video
 * keeps playing: all players share one media session, and a player that did not start it can stop
 * it, for example the player of the comment replies panel when the panel is closed.
 *
 * 21.42+ has an A/B flag that ignores a media session stop from a player that did not start the
 * session. Older versions get the same check: the player that starts the session is its owner, and
 * a session stop from another player is ignored.
 */
internal val fixMediaSessionStopPatch = patch(
    name = "Fix media session stop",
    description = "Keeps the media notification and the audio when the panel of the comment replies " +
        "is closed.",
) {
    dependsOn(VersionCheck)

    if (is_21_42_or_greater) {
        // The flag is the value a method of its own returns, where upstream replaces the read of it.
        MediaSessionOwnerFeatureFlagFingerprint.hookMethod {
            after { it.result = true }
        }
        return@patch
    }

    val sessionStart = ::mediaSessionStartMethod.member
    val sessionStop = ::mediaSessionStopMethod.member
    val playerType = ::playerMediaSessionStartMethod.method.declaringClass

    /**
     * Calls the extension with the player that calls the session method, where upstream injects the
     * call right before the player reads the session from its field. Calls of the session method
     * that are not made by a player are not changed.
     */
    fun hookPlayerSessionCalls(callers: List<DexMethod>, sessionMethod: Member, onCall: (Any) -> Unit) {
        callers.forEach { caller ->
            caller.hookMethod(scopedHook(sessionMethod) {
                before {
                    val player = outerParam.thisObject
                    if (playerType.isInstance(player)) onCall(player)
                }
            })
        }
    }

    hookPlayerSessionCalls(
        ::playerMediaSessionStartMethods.dexMethodList, sessionStart,
        FixMediaSessionStopPatch::setMediaSessionOwner
    )
    hookPlayerSessionCalls(
        ::playerMediaSessionStopMethods.dexMethodList, sessionStop,
        FixMediaSessionStopPatch::setMediaSessionStoppingPlayer
    )

    // The session is stopped only when the player that asks for it started it.
    MediaSessionStopFingerprint.hookMethod {
        before {
            if (!FixMediaSessionStopPatch.isMediaSessionStopAllowed(it.args[0] as Boolean)) {
                it.result = null
            }
        }
    }
}
