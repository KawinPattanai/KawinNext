package io.github.kawinnext.morphe.reddit.misc.guest

import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall


internal object FrontPageApplicationHasFinishedOnboardingFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/frontpage/FrontpageApplication;",
    filters = listOf(
        methodCall(smali = "Lcom/reddit/session/Session;->isLoggedIn()Z"),
        methodCall(smali = "Lcom/reddit/session/Session;->isIncognito()Z"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "Z",
            parameters = listOf(),
            location = MatchAfterWithin(10)
        )
    )
)

val FrontPageApplicationHasFinishedOnboarding = findMethodDirect {
    FrontPageApplicationHasFinishedOnboardingFingerprint.instructionMatches.last().instruction.methodRef!!
}
