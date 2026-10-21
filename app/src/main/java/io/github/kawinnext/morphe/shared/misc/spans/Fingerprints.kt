package io.github.kawinnext.morphe.shared.misc.spans

import io.github.kawinnext.SkipTest
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.shared.SpannableStringBuilderFingerprint

@SkipTest
internal object CustomCharacterStyleFingerprint : Fingerprint(
    returnType = "Landroid/graphics/Path;",
    parameters = listOf("Landroid/text/Layout;")
)

val customCharacterStyleClass = findClassDirect {
    val builder = SpannableStringBuilderFingerprint()
    // Both Elements implementations have this signature; use the span created by this text builder.
    CustomCharacterStyleFingerprint.matchAll().map { it.method.declaredClass!! }.distinct().single { candidate ->
        builder.instructions.any { it.opcode == Opcode.NEW_INSTANCE.ordinal && it.classRef == candidate }
    }
}

val setTextStyleMethod = findMethodDirect {
    SpannableStringBuilderFingerprint.instructionMatches[2].instruction.methodRef!!
}
