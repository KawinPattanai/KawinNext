package io.github.kawinnext.morphe.youtube.layout.hide.shorts

import android.view.View
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceUtils.getIdIdentifier
import app.morphe.extension.youtube.patches.components.ShortsFilter
import com.google.protobuf.MessageLite
import de.robv.android.xposed.XC_MethodReplacement
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.layout.buttons.navigation.NavigationBar
import io.github.kawinnext.morphe.youtube.misc.engagement.EngagementPanelHook
import io.github.kawinnext.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.kawinnext.morphe.youtube.misc.litho.observer.LayoutReloadObserver
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.patch
import org.luckypray.dexkit.wrap.DexMethod

/** ฟิลด์ของ settings ของ navigation bar ใน config ของหน้า Shorts */
private val shortsReelWatchNavigationBarSettingsField = findFieldDirect {
    ShortsNavigationBarModeFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/** ฟิลด์ของโหมด navigation bar ใน config ของหน้า Shorts */
private val shortsNavigationBarModeField = findFieldDirect {
    ShortsNavigationBarModeFingerprint.instructionMatches[3].instruction.fieldRef!!
}

/** ฟิลด์ static ที่ถือ config ซึ่งเมธอดของแอปอ่านโหมด navigation bar จากมัน */
private val shortsNavigationBarModeHolderFields = findFieldListDirect {
    shortsNavigationBarModeReadFingerprint(
        shortsReelWatchNavigationBarSettingsField(),
        shortsNavigationBarModeField()
    ).matchAll()
        .map { it.instructionMatches[2].instruction.fieldRef!! }
        .distinctBy { it.descriptor }
}

/** ทุกเมธอดที่อ่านโหมด navigation bar ของหน้า Shorts */
private val shortsNavigationBarModeReadMethods = findMethodListDirect {
    shortsNavigationBarModeReadFingerprint(
        shortsReelWatchNavigationBarSettingsField(),
        shortsNavigationBarModeField()
    ).matchAll().map { it.method }
}

val HideShortsComponents = patch(
    name = "Hide Shorts components",
    description = "Adds options to hide components related to Shorts.",
) {
    dependsOn(
        EngagementPanelHook,
        LayoutReloadObserver,
        LithoFilter,
        NavigationBar
    )

    PreferenceScreen.addShortsPlayerPreferences(
        // Shorts player components.
        // Ideally each group should be ordered similar to how they appear in the UI

        // Vertical row of buttons on right side of the screen.
        // Like fountain may no longer be used by YT anymore.
        //SwitchPreference("morphe_hide_shorts_like_fountain"),
        SwitchPreference("morphe_disable_shorts_double_tap_to_like"),
        SwitchPreference("morphe_hide_shorts_like_button"),
        SwitchPreference("morphe_hide_shorts_comments_button"),
        SwitchPreference("morphe_hide_shorts_save_button"),
        SwitchPreference("morphe_hide_shorts_share_button"),
        SwitchPreference("morphe_hide_shorts_remix_button"),
//                SwitchPreference("morphe_hide_shorts_sound_button"),

        // Upper and middle area of the player.
        SwitchPreference("morphe_hide_shorts_join_button"),
        SwitchPreference("morphe_hide_shorts_subscribe_button"),
        SwitchPreference("morphe_hide_shorts_gesture_hints"),
        SwitchPreference("morphe_hide_shorts_paused_overlay_buttons"),

        // Suggested actions.
        SwitchPreference("morphe_hide_shorts_preview_comment"),
        SwitchPreference("morphe_hide_shorts_save_sound_button"),
        SwitchPreference("morphe_hide_shorts_use_sound_button"),
        SwitchPreference("morphe_hide_shorts_use_template_button"),
        SwitchPreference("morphe_hide_shorts_upcoming_button"),
        SwitchPreference("morphe_hide_shorts_effect_button"),
        SwitchPreference("morphe_hide_shorts_green_screen_button"),
        SwitchPreference("morphe_hide_shorts_hashtag_button"),
        SwitchPreference("morphe_hide_shorts_live_preview"),
        SwitchPreference("morphe_hide_shorts_new_posts_button"),
        SwitchPreference("morphe_hide_shorts_shop_button"),
        SwitchPreference("morphe_hide_shorts_tagged_products"),
        SwitchPreference("morphe_hide_shorts_search_suggestions"),
        SwitchPreference("morphe_hide_shorts_super_thanks_button"),

        // Bottom of the screen.
        SwitchPreference("morphe_hide_shorts_ai_button"),
        SwitchPreference("morphe_hide_shorts_auto_dubbed_label"),
        SwitchPreference("morphe_hide_shorts_location_label"),
        SwitchPreference("morphe_hide_shorts_channel_bar"),
        SwitchPreference("morphe_hide_shorts_info_panel"),
        SwitchPreference("morphe_hide_shorts_full_video_link_label"),
        SwitchPreference("morphe_hide_shorts_video_title"),
        SwitchPreference("morphe_hide_shorts_sound_metadata_label"),
//                SwitchPreference("morphe_hide_shorts_navigation_bar"),
    )

    PreferenceScreen.SHORTS.addPreferences(
        PreferenceCategory(
            titleKey = "morphe_hide_shorts_category_title",
            preferences = setOf(
                SwitchPreference("morphe_hide_shorts_channel"),
                SwitchPreference("morphe_hide_shorts_home"),
                SwitchPreference("morphe_hide_shorts_search"),
                SwitchPreference("morphe_hide_shorts_subscriptions"),
                SwitchPreference("morphe_hide_shorts_video_description"),
                SwitchPreference("morphe_hide_shorts_history")
            )
        )
    )

    addLithoFilter(ShortsFilter())

    // TODO Hide sound button.
    // TODO Hide the navigation bar.

    // region Disable experimental Shorts flags.

    // Flags might be present in earlier targets, but they are not found in 19.47.53.
    // If these flags are forced on, the experimental layout is still not used, and
    // it appears the features requires additional server side data to fully use.

    // Experimental Shorts player uses Android native buttons and not Litho,
    // and the layout is provided by the server.
    //
    // Since the buttons are native components and not Litho, it should be possible to
    // fix the RYD Shorts loading delay by asynchronously loading RYD and updating
    // the button text after RYD has loaded.
    ShortsExperimentalPlayerFeatureFlagFingerprint.hookMethod(
        XC_MethodReplacement.returnConstant(
            false
        )
    )

    // Experimental UI renderer must also be disabled since it requires the
    // experimental Shorts player. If this is enabled but Shorts player
    // is disabled then the app crashes when the Shorts player is opened.
    RenderNextUIFeatureFlagFingerprint.hookMethod(XC_MethodReplacement.returnConstant(false))
    // endregion

    // region Disable double-tap to like.

    DoubleTapToLikeLogicFingerprint.hookMethod {
        val doubleTapField = ::isDoubleTapField.field
        before {
            val originalValue = doubleTapField.get(it.thisObject) as Boolean
            val newValue = ShortsFilter.allowDoubleTapToLike(originalValue)
            doubleTapField.set(it.thisObject, newValue)
        }
    }

    // endregion


    // region Keep the navigation bar of the Shorts that show the comment bar instead of it.

    // The Shorts opened from a channel show the comment bar below them instead of the
    // navigation bar. When "Hide preview comment" hides the comment bar, nothing is shown
    // in place of the navigation bar. The navigation bar is kept for these Shorts.
    //
    // upstream เติมโค้ดไว้ในเมธอดที่อ่านโหมดของ navigation bar สองจุด:
    //   setReelWatchConfig(config) ที่ label ต้นทางของคำสั่งที่อ่าน field ของ settings
    //   getNavigationBarMode(mode) หลังคำสั่งที่อ่านโหมด เพื่อแทนค่าที่เพิ่มอ่านได้
    // โมดูลนี้ไม่แพตช์ bytecode จึง hook ทุกเมธอดที่อ่านโหมด (matchAll) แล้วทำใน before-hook:
    //   1. ส่ง config ของหน้า Shorts ให้ extension รู้ว่าหน้านั้นมีแถบ comment ใต้คลิปหรือไม่
    //   2. ค่าโหมดเป็น register ภายในเมธอดซึ่ง hook แทนค่าไม่ได้ จึงสลับค่าที่ฟิลด์ของ config
    //      ก่อนเมธอดอ่าน แล้วคืนค่าเดิมใน after-hook
    runCatching {
        val navigationBarSettingsField = ::shortsReelWatchNavigationBarSettingsField.field
            .apply { isAccessible = true }
        val modeField = ::shortsNavigationBarModeField.field.apply { isAccessible = true }
        val modeHolderFields = ::shortsNavigationBarModeHolderFields.fieldList
            .onEach { it.isAccessible = true }

        // ค่าโหมดเดิมของแต่ละครั้งที่สลับ เก็บเป็น stack เพราะเมธอดที่อ่านโหมดอาจเรียกซ้อนกัน
        val swappedModes =
            ThreadLocal.withInitial<ArrayDeque<List<Pair<Any, Int>>>> { ArrayDeque() }

        fun swapNavigationBarMode() {
            val swapped = mutableListOf<Pair<Any, Int>>()
            try {
                modeHolderFields.forEach { holderField ->
                    val holder = holderField.get(null) ?: return@forEach
                    val original = modeField.getInt(holder)
                    val mode = ShortsFilter.getNavigationBarMode(original)
                    if (mode == original) return@forEach

                    modeField.setInt(holder, mode)
                    swapped += holder to original
                }
            } finally {
                // เก็บค่าไว้คืนใน after-hook แม้สลับได้ไม่ครบ เพื่อให้จำนวนครั้งที่สลับกับคืนตรงกัน
                swappedModes.get()!!.addLast(swapped)
            }
        }

        fun restoreNavigationBarMode() {
            swappedModes.get()!!.removeLastOrNull()?.forEach { (holder, original) ->
                // คืนค่าเดิมเฉพาะเมื่อยังเป็นค่าที่ hook ใส่ไว้ กันทับค่าที่แอปเขียนระหว่างนั้น
                if (modeField.getInt(holder) == ShortsFilter.getNavigationBarMode(original)) {
                    modeField.setInt(holder, original)
                }
            }
        }

        // upstream ใช้ register ที่ถือ config ของหน้านั้น ตอน hook จึงหา instance ของคลาส
        // ที่ประกาศ field ของ settings จาก this และอาร์กิวเมนต์ของเมธอดที่ถูก hook แทน
        val reelWatchConfigClass = navigationBarSettingsField.declaringClass

        ::shortsNavigationBarModeReadMethods.dexMethodList.forEach { method ->
            method.hookMethod {
                before { param ->
                    runCatching {
                        val config = sequenceOf(param.thisObject)
                            .plus(param.args.asSequence())
                            .firstOrNull { reelWatchConfigClass.isInstance(it) } as? MessageLite

                        ShortsFilter.setReelWatchConfig(config)
                        swapNavigationBarMode()
                    }.onFailure {
                        Logger.printException({ "Shorts navigation bar mode failure" }, it)
                    }
                }

                after {
                    runCatching { restoreNavigationBarMode() }.onFailure {
                        Logger.printException({ "Shorts navigation bar mode restore failure" }, it)
                    }
                }
            }
        }
    }.onFailure {
        Logger.printException({ "Shorts navigation bar mode hook failure" }, it)
    }

    // endregion

    // region Hide Shorts gesture hints.

    DexMethod("Landroid/view/ViewStub;->setInflatedId(I)V").hookMethod {
        val speedmaster_indicator_chip = getIdIdentifier("speedmaster_indicator_chip")
        val reel_speedmaster_edu_container = getIdIdentifier("reel_speedmaster_edu_container")
        after {
            val id = it.args[0]
            if (id == speedmaster_indicator_chip || id == reel_speedmaster_edu_container) {
                ShortsFilter.hideGestureHints(it.thisObject as View?)
            }
        }
    }

    // endregion
}