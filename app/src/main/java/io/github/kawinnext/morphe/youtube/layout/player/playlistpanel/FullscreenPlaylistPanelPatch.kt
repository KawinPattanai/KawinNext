package io.github.kawinnext.morphe.youtube.layout.player.playlistpanel

import app.morphe.extension.youtube.patches.FullscreenPlaylistPanelPatch
import io.github.kawinnext.PatchExecutor
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.misc.engagement.EngagementPanelHook
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field

/** A config value turned on for one run of a panel rule, and the value it had before. */
private class ConfigValueRestore(val field: Field, val holder: Any, val value: Boolean)

/** Whether a panel rule runs on this thread, so the calls it makes carry its config. */
private val panelRuleDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

/** The config values turned on by the rule that runs now, restored when it returns. */
private val pendingRestores: ThreadLocal<MutableList<ConfigValueRestore>> =
    ThreadLocal.withInitial { mutableListOf() }

/** A thread local with an initial value always has one, which the bridge keeps per thread. */
private fun panelDepth(): Int = panelRuleDepth.get()!!

private fun panelRestores(): MutableList<ConfigValueRestore> = pendingRestores.get()!!

/** The server config of the panel overlay over the fullscreen player, as the setting has it. */
private fun overlayEnabled() = FullscreenPlaylistPanelPatch.useFullscreenOverlay(false)

/** The field of the app that a dex field descriptor names. */
private fun PatchExecutor.declaredField(descriptor: String): Field {
    val className = descriptor.substringBefore("->").removePrefix("L").removeSuffix(";")
    val fieldName = descriptor.substringAfter("->").substringBefore(":")
    var type: Class<*>? = classLoader.loadClass(className)
    while (type != null) {
        runCatching { type!!.getDeclaredField(fieldName) }.getOrNull()?.let {
            return it.apply { isAccessible = true }
        }
        type = type.superclass
    }
    throw NoSuchFieldException("$descriptor not found in $className")
}

/** Turns the config value on for the rule that reads it now, keeping the value to restore. */
private fun turnOn(field: Field, holder: Any) {
    // A register can hold another object on the way to the config, which the type of the field tells.
    if (!field.declaringClass.isInstance(holder)) return
    if (runCatching { field.getBoolean(holder) }.getOrDefault(true)) return
    panelRestores() += ConfigValueRestore(field, holder, false)
    runCatching { field.setBoolean(holder, true) }
}

internal val fullscreenPlaylistPanelPatch = patch(
    name = "Fullscreen playlist panel",
    description = "Adds an option to show the playlist panel over the fullscreen player on tablets, " +
        "instead of exiting fullscreen.",
) {
    dependsOn(EngagementPanelHook)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_fullscreen_playlist_panel")
    )

    // Keep fullscreen when the playlist panel opens.
    PanelExitsFullscreenFingerprint.hookMethod {
        before {
            if (FullscreenPlaylistPanelPatch.keepFullscreen()) it.result = false
        }
    }

    // YouTube reads the server config that puts a panel over the fullscreen player at the place of
    // the rule it decides with, which is only enabled by that config on phones. A hook cannot
    // rewrite a value inside a method, so the value is turned on where the rule takes it from: the
    // result of the call it makes, or the boolean field of the config object it reads. Both are
    // changed only while that rule runs, and put back when it returns.
    //
    // Resolving the rules fills the plan of each one, which the hooks below use.
    ::panelConfigRules.dexMethodList
    panelConfigPlans.forEach { plan ->
        val rule = DexMethod(plan.rule.descriptor)
        val field = plan.field?.let { declaredField(it.descriptor) }
        val staticInstances = plan.reads.filterIsInstance<PanelConfigRead.StaticInstance>()
            .map { declaredField(it.instance.descriptor) }

        // The values this rule turned on, which are the ones it put back when it returns. A rule
        // that runs inside another one keeps the values of the outer rule as they are.
        val turnedOn: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

        rule.hookMethod {
            before {
                panelRuleDepth.set(panelDepth() + 1)
                turnedOn.set(panelRestores().size)
                if (!overlayEnabled()) return@before
                val configField = field ?: return@before
                staticInstances.forEach { instanceField ->
                    val instance = instanceField.get(null) ?: return@forEach
                    turnOn(configField, instance)
                }
            }

            after {
                val restores = panelRestores()
                while (restores.size > turnedOn.get()!!) {
                    val restore = restores.removeAt(restores.lastIndex)
                    restore.field.setBoolean(restore.holder, restore.value)
                }
                panelRuleDepth.set(panelDepth() - 1)
            }
        }

        plan.reads.forEach { read ->
            when (read) {
                // The rule reads the boolean a call returns.
                is PanelConfigRead.Call -> DexMethod(read.method.descriptor).hookMethod {
                    after {
                        if (panelDepth() <= 0) return@after
                        val original = it.result as? Boolean ?: return@after
                        it.result = FullscreenPlaylistPanelPatch.useFullscreenOverlay(original)
                    }
                }

                // The rule reads a boolean field of an object it gets from a call.
                is PanelConfigRead.FieldOfCallResult -> {
                    val containerField = read.container?.let { declaredField(it.descriptor) }
                    DexMethod(read.method.descriptor).hookMethod {
                        after {
                            if (panelDepth() <= 0 || !overlayEnabled()) return@after
                            val configField = field ?: return@after
                            val returned = it.result ?: return@after
                            val holder = if (containerField != null) {
                                containerField.get(returned)
                            } else {
                                returned
                            }
                            holder?.let { turnOn(configField, it) }
                        }
                    }
                }

                // The static default instance is turned on around the rule above.
                is PanelConfigRead.StaticInstance -> Unit
            }
        }
    }
}
