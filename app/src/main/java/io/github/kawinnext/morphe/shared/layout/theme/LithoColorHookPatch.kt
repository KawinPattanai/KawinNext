package io.github.kawinnext.morphe.shared.layout.theme

import android.graphics.Paint
import android.graphics.drawable.Drawable
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook

private val colorOverrides = mutableListOf<(Int) -> Int>()
private val drawableColorOverrides = mutableListOf<(Drawable, Int) -> Int>()

fun lithoColorOverrideHook(override: (Int) -> Int) {
    colorOverrides.add(override)
}

/**
 * Hooks the original color of a Litho background drawable, before [lithoColorOverrideHook].
 * The patch that adds the hook must depend on [lithoColorHookPatch].
 * The hook runs where the drawable sets its color, so its bounds are already the new ones.
 *
 * @param override Called with the drawable and its original color, returns the color to use.
 */
fun lithoDrawableColorHook(override: (Drawable, Int) -> Int) {
    drawableColorOverrides.add(override)
}

internal fun lithoColorHookPatch(useModernHook: () -> Boolean) = patch {
    val modern = useModernHook()
    val fingerprint = if (modern) LithoOnBoundsChangeFingerprint else LithoOnBoundsChangeLegacyFingerprint
    val paintField = if (modern) ::lithoPaintField.field else ::lithoPaintLegacyField.field
    fingerprint.hookMethod(scopedHook(Paint::class.java.getDeclaredMethod("setColor", Int::class.javaPrimitiveType)) {
        before {
            val drawable = outerParam.thisObject as? Drawable ?: return@before
            if (it.thisObject !== paintField.get(drawable)) return@before
            var color = it.args[0] as Int
            drawableColorOverrides.forEach { override -> color = override(drawable, color) }
            colorOverrides.forEach { override -> color = override(color) }
            it.args[0] = color
        }
    })
}
