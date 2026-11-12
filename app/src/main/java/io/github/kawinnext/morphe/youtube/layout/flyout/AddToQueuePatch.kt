/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/1837
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.layout.flyout

import app.morphe.extension.youtube.patches.AddToQueuePatch
import app.morphe.extension.youtube.patches.LocalQueuePatch
import app.morphe.extension.youtube.patches.queue.NativePlaylistQueuePatch
import io.github.kawinnext.morphe.setExtensionIsPatchIncluded
import io.github.kawinnext.morphe.shared.misc.settings.preference.ListPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.kawinnext.morphe.youtube.layout.hide.general.ContextualMenuItemBuilderOnClickFingerprint
import io.github.kawinnext.morphe.youtube.layout.hide.general.contextualMenuItemMessageField
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.nativePlaylistQueuePatch
import io.github.kawinnext.morphe.youtube.layout.playlistautoplay.navigationIntentHook
import io.github.kawinnext.morphe.youtube.misc.auth.authHookPatch
import io.github.kawinnext.morphe.youtube.misc.loadvideo.loadVideoHookPatch
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_05_or_greater
import io.github.kawinnext.morphe.youtube.misc.playservice.is_21_15_or_greater
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.information.playerStatusMethod
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val addToQueuePatch = patch(
    name = "Add to queue",
    description = "Adds a video queue to the feed flyout menu, and to the menus without a queue item, " +
            "such as the menus of Shorts."
) {
    dependsOn(flyoutPatch, authHookPatch, loadVideoHookPatch, VideoInformationPatch, nativePlaylistQueuePatch)

    PreferenceScreen.FEED.addPreferences(
        noTitleUnsortedPreferenceCategory(
            // The native queue requires 21.16+.
            if (is_21_15_or_greater) {
                ListPreference("morphe_queue_type")
            } else {
                ListPreference(
                    key = "morphe_queue_type",
                    entriesKey = "morphe_queue_type_legacy_entries",
                    entryValuesKey = "morphe_queue_type_legacy_entry_values"
                )
            },
            SwitchPreference("morphe_native_queue_fetch_on_scroll", summary = true)
        )
    )

    val runnableField = ::feedFlyoutButtonRunnableField.field
    FeedFlyoutButtonsInitializerFingerprint.hookMethod(scopedHook(::feedFlyoutMenuItemOptionalMethod.method) {
        before {
            val menuItem = it.args[0]
            if (runnableField.declaringClass.isInstance(menuItem)) {
                val original = runnableField.get(menuItem) as Runnable?
                // 21.15+ reloads the source model from the factory parameter, because the
                // item register may hold its own Runnable by then.
                val replaced = if (is_21_15_or_greater) {
                    AddToQueuePatch.replaceButtonRunnable(original, outerParam.args[0])
                } else {
                    AddToQueuePatch.replaceButtonRunnable(original)
                }
                runnableField.set(menuItem, replaced)
            }
        }
    })

    val messageField = ::contextualMenuItemMessageField.field
    val getSubMessage = ::getSubMessageReference.method
    val enumIntField = ::enumIntField.field
    val enumMethodCall = ::enumMethodCall.method
    ContextualMenuItemBuilderOnClickFingerprint.hookMethod {
        before {
            val message = messageField.get(it.thisObject)
            if (!getSubMessage.parameterTypes.single().isInstance(message)) return@before
            // 21.15+ the native queue replaces the playlist item itself, before the icon
            // and the name of the button are resolved.
            if (is_21_15_or_greater && AddToQueuePatch.replacePlaylistItemClick(message)) {
                it.result = null
                return@before
            }
            val buttonInfo = getSubMessage(null, message)
            val buttonName = (enumMethodCall(null, enumIntField.get(buttonInfo)) as Enum<*>).name
            if (AddToQueuePatch.replaceOnItemClick(buttonName)) {
                it.result = null
            }
        }
    }

    if (!is_21_05_or_greater) {
        FeedFlyoutButtonsInitializerOnItemClickFingerprint.hookMethod {
            before {
                if (AddToQueuePatch.replaceOnItemClick(it.args[2] as Int)) {
                    it.result = null
                }
            }
        }
    }

    navigationIntentHook(LocalQueuePatch::shouldCancelNavigation)

    ::playerStatusMethod.hookMethod {
        before {
            if (LocalQueuePatch.shouldCancelEndOfVideo(it.args[0] as Enum<*>)) {
                it.result = null
            }
        }
    }

    // The native queue cancels its pending playlist page loads when leaving the app,
    // unless a Next/Previous is waiting on them during background playback.
    if (is_21_15_or_greater) {
        DexMethod("$YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE->onUserLeaveHint()V").hookMethod {
            before { NativePlaylistQueuePatch.onUserLeaveHint() }
        }
    }

    setExtensionIsPatchIncluded(AddToQueuePatch::class.java)
}
