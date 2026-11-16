/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.returnyoutubedislike

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.AddToPlaylistButtonViewModel
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.ButtonViewModel
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.ComponentType
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.DislikeButtonViewModel
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.Element
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.LikeButtonViewModel
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.LockupModel
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.ToggleButtonViewModelHolder
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.VideoActionBarData
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.VideoActionBarItemContent
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.VideoActionBarModel
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.VideoActionBarOverflow
import app.morphe.extension.youtube.innertube.FeedCardLayoutOuterClass.VideoActionBarViewModel
import app.morphe.extension.youtube.patches.utils.FeedVideoCardsPatch
import io.github.kawinnext.morphe.shared.misc.proto.hookElement
import io.github.kawinnext.morphe.youtube.misc.proto.elementProtoParserHookPatch
import io.github.kawinnext.patch
import java.util.concurrent.ConcurrentHashMap

/**
 * เติมไอคอนให้ปุ่มของ video action bar ที่เซิร์ฟเวอร์ส่งมาโดยไม่มีชื่อไอคอน
 *
 * ปุ่ม like และ dislike ของ action bar บน 21.45 เป็น segmented button ที่เซิร์ฟเวอร์ส่งมาเป็น
 * `VideoActionBarModel` (ของ action bar ที่ยังไม่ถูกยุบ เช่นบนแท็บเล็ต) โดยไม่มี `iconName` ของปุ่ม
 * ให้แอปวาดอนิเมชัน (`likeAnimationUrl`) แทน ซึ่งบัญชีที่ไม่ได้รับอนิเมชันจะเห็นช่องปุ่มเป็นตัวเลขล้วน
 * ไม่มีนิ้วโป้ง (upstream เจอเคสเดียวกันและแก้ด้วย `FALLBACK_ICONS` ใน
 * `RestoreOldVideoActionBarPatch.withButtonIcons` แต่ตัวนั้นถูกเรียกเฉพาะ element ของ action bar
 * ที่ถูกยุบ (`compactify_video_action_bar.eml`) และเฉพาะตอนผู้ใช้เลือก layout เอง — `LAYOUT == DEFAULT`
 * ซึ่งเป็นค่าตั้งต้นบน 21.36 ขึ้นไปและเป็นค่าที่แท็บเล็ตใช้เสมอ จะคืนไบต์เดิมก่อนถึงบรรทัดนั้น)
 *
 * แพตช์นี้จึงทำงานที่ชั้น element เหมือน `HideVideoActionButtonsPatch`: เก็บไอคอนที่เซิร์ฟเวอร์เคยส่ง
 * ของแต่ละปุ่มไว้ก่อน แล้วเติมให้ปุ่มที่มาว่าง ด้วยไอคอนชุดเดียวกับ upstream (ชื่อของแอปเวอร์ชันใหม่ก่อน)
 * ไม่แตะ layout ที่เซิร์ฟเวอร์เลือก จึงไม่กระทบหน้าตาของ action bar นอกจากการได้ไอคอนกลับมา
 *
 * ปุ่ม dislike ของ action bar แบบ segmented เป็นปุ่มที่ Return YouTube Dislike วาดยอดไว้ข้างไอคอน
 * ใน margin ที่มันขอจาก Yoga (`onYogaSetWidth`) ไอคอนที่เติมที่นี่จึงเป็นไอคอนที่ยอดนั้นวางข้าง ๆ
 * และเป็นไอคอนที่เหลืออยู่เมื่อดึงยอดไม่ได้ ซึ่ง extension ไม่ให้ margin เลยในกรณีนั้น (fix ของ
 * upstream) ปุ่มจึงเหลือไอคอนเดียวโดยไม่มีช่องว่างค้าง ตรวจบน 21.45.82 แล้วทั้งสองฝั่งทำงานร่วมกัน:
 * log ขึ้น "Giving the dislike button a margin of 71" และปุ่มขึ้น `👍 19M | 👎 522K` ครบทั้งสองช่อง
 */
val videoActionBarIconsPatch = patch(
    description = "Gives the buttons of the video action bar the icon the server left out, " +
            "so the like and dislike buttons keep their thumbs."
) {
    dependsOn(elementProtoParserHookPatch)

    hookElement(::fillButtonIcons)
}

/**
 * ชื่อ template ของ action bar ทั้งสองแบบ ตัวที่ถูกยุบแล้วมีชื่อนี้ต่อท้าย
 */
private const val ACTION_BAR_TEMPLATE = "video_action_bar.eml"

/**
 * ต่อท้าย target id ของปุ่มที่ถูกกดอยู่ ซึ่งมีไอคอนอีกชุดหนึ่ง (แบบทึบ)
 */
private const val TOGGLED_ICON_KEY = "|toggled"

/**
 * ไอคอนสำรองของแต่ละปุ่ม เรียงชื่อของแอปเวอร์ชันใหม่ไว้ก่อน ชื่อที่ไม่มีในแอปจะถูกข้าม
 */
private val fallbackIcons = mapOf(
    "id.video.like.button" to listOf(
        "yt_outline_experimental_thumb_up_black_24",
        "yt_outline_thumb_up_black_24"
    ),
    "id.video.like.button$TOGGLED_ICON_KEY" to listOf(
        "yt_fill_experimental_thumb_up_black_24",
        "yt_fill_thumb_up_black_24"
    ),
    "id.video.dislike.button" to listOf(
        "yt_outline_experimental_thumb_down_black_24",
        "yt_outline_thumb_down_black_24"
    ),
    "id.video.dislike.button$TOGGLED_ICON_KEY" to listOf(
        "yt_fill_experimental_thumb_down_black_24",
        "yt_fill_thumb_down_black_24"
    ),
    "id.video.share.button" to listOf(
        "yt_outline_experimental_share_black_24",
        "yt_outline_share_black_24"
    ),
    "id.video.add_to.button" to listOf(
        "yt_outline_experimental_bookmark_black_24",
        "yt_outline_bookmark_black_24"
    ),
    "id.video.add_to.button$TOGGLED_ICON_KEY" to listOf(
        "yt_fill_experimental_bookmark_black_24",
        "yt_fill_bookmark_black_24"
    ),
    "id.video.remove_ads.button" to listOf(
        "yt_outline_experimental_flag_black_24",
        "yt_outline_flag_black_24"
    )
)

/**
 * ไอคอนที่เซิร์ฟเวอร์ส่งมาให้แต่ละปุ่มในรอบนี้ ปุ่มเดียวกันปรากฏทั้งในแถวและในเมนู และปุ่มที่ถูกกด
 * มีไอคอนอีกชุด จึงเก็บทั้งแบบธรรมดาและแบบ toggled
 */
private val serverIcons = ConcurrentHashMap<String, String>()

/**
 * @return ไบต์ของ element ที่เติมไอคอนแล้ว หรือไบต์เดิมเมื่อไม่ใช่ action bar หรือไม่ต้องแก้อะไร
 *         (hook ของ parser ส่งไบต์ต่อกันเป็นทอด ๆ ค่า null จะทำให้ element หายไป จึงคืนไบต์เดิม)
 */
private fun fillButtonIcons(bytes: ByteArray?): ByteArray? {
    if (bytes == null) {
        return null
    }

    try {
        val text = FeedVideoCardsPatch.getElementText(bytes)
        if (!text.contains(ACTION_BAR_TEMPLATE)) {
            return bytes
        }

        val element = Element.parseFrom(bytes)
        val componentType = element.type.componentType
        val model = componentType.model

        // action bar มีสองแบบในไบต์เดียวกันคนละ element: แบบที่ยังไม่ถูกยุบ (video_action_bar.eml,
        // ของแท็บเล็ต) อยู่ใน videoActionBarModel และแบบที่ถูกยุบแล้ว (compactify_video_action_bar.eml)
        // อยู่ใน lockupModel
        if (model.hasVideoActionBarModel()) {
            val actionBarModel = model.videoActionBarModel
            val data = actionBarModel.videoActionBarData
            rememberServerIcons(data)
            val newData = replaceMissingIcons(data)
            return if (newData === data) {
                bytes
            } else {
                withActionBarData(element, componentType, actionBarModel, newData).toByteArray()
            }
        }

        val lockupModel = model.lockupModel
        val actionBar = lockupModel.viewModel.videoActionBarViewModel
        rememberServerIcons(actionBar)
        val newActionBar = replaceMissingIcons(actionBar)
        return if (newActionBar === actionBar) {
            bytes
        } else {
            withActionBar(element, componentType, lockupModel, newActionBar).toByteArray()
        }
    } catch (ex: Exception) {
        Logger.printException({ "Could not fill the icons of the video action bar" }, ex)
    }

    return bytes
}

/**
 * เก็บไอคอนของทุกปุ่มที่เซิร์ฟเวอร์ส่งมาก่อน ซึ่งทำรอบเดียวทั้ง action bar เพราะปุ่มเดียวกัน
 * ปรากฏได้ทั้งในแถวและในเมนูของ layout minimal
 */
private fun rememberServerIcons(actionBar: VideoActionBarViewModel) {
    mapButtons(actionBar) { button, toggled ->
        if (button.targetId.isNotEmpty() && button.iconName.isNotEmpty()) {
            serverIcons[if (toggled) button.targetId + TOGGLED_ICON_KEY else button.targetId] = button.iconName
        }
        button
    }
}

private fun rememberServerIcons(data: VideoActionBarData) {
    mapButtons(data) { button, toggled ->
        if (button.targetId.isNotEmpty() && button.iconName.isNotEmpty()) {
            serverIcons[if (toggled) button.targetId + TOGGLED_ICON_KEY else button.targetId] = button.iconName
        }
        button
    }
}

private fun replaceMissingIcons(data: VideoActionBarData): VideoActionBarData =
    mapButtons(data) { button, toggled -> fillIcon(button, toggled) }

private fun replaceMissingIcons(actionBar: VideoActionBarViewModel): VideoActionBarViewModel =
    mapButtons(actionBar) { button, toggled -> fillIcon(button, toggled) }

private fun fillIcon(button: ButtonViewModel, toggled: Boolean): ButtonViewModel {
    val targetId = button.targetId
    if (button.iconName.isNotEmpty() || targetId.isEmpty()) {
        return button
    }

    // ปุ่มที่ถูกกดและยังไม่มีไอคอนของตัวเองใช้ไอคอนของปุ่มนั้น
    val toggledIcon = if (toggled) buttonIcon(targetId + TOGGLED_ICON_KEY) else null
    val icon = toggledIcon ?: buttonIcon(targetId) ?: return button

    Logger.printDebug {
        "Button " + targetId + (if (toggled) " (toggled)" else "") + " has no icon, using: " + icon
    }
    return button.toBuilder().setIconName(icon).build()
}

private fun buttonIcon(key: String): String? {
    serverIcons[key]?.let { return it }

    for (fallbackIcon in fallbackIcons[key].orEmpty()) {
        // แอปเวอร์ชันเก่ามีชื่ออื่นสำหรับไอคอนเดียวกัน
        if (ResourceUtils.getIdentifier(ResourceType.DRAWABLE, fallbackIcon) != 0) {
            return fallbackIcon
        }
    }

    return null
}

/**
 * @return action bar เดิมถ้า mapper ไม่เปลี่ยนปุ่มใดเลย
 */
private fun mapButtons(
    data: VideoActionBarData,
    mapper: (ButtonViewModel, Boolean) -> ButtonViewModel
): VideoActionBarData {
    var builder: VideoActionBarData.Builder? = null

    for (index in 0 until data.actionsCount) {
        val action = data.getActions(index)
        val content = mapButtons(action.content, mapper)
        if (content !== action.content) {
            val newBuilder = builder ?: data.toBuilder().also { builder = it }
            newBuilder.setActions(index, action.toBuilder().setContent(content).build())
        }
    }

    return builder?.build() ?: data
}

/**
 * @return action bar เดิมถ้า mapper ไม่เปลี่ยนปุ่มใดเลย
 */
private fun mapButtons(
    actionBar: VideoActionBarViewModel,
    mapper: (ButtonViewModel, Boolean) -> ButtonViewModel
): VideoActionBarViewModel {
    var builder: VideoActionBarViewModel.Builder? = null

    for (index in 0 until actionBar.actionsCount) {
        val action = actionBar.getActions(index)
        val content = mapButtons(action.content, mapper)
        if (content !== action.content) {
            val newBuilder = builder ?: actionBar.toBuilder().also { builder = it }
            newBuilder.setActions(index, action.toBuilder().setContent(content).build())
        }
    }

    return builder?.build() ?: actionBar
}

/**
 * @return content เดิมถ้า mapper ไม่เปลี่ยนปุ่มใดเลย
 */
private fun mapButtons(
    content: VideoActionBarItemContent,
    mapper: (ButtonViewModel, Boolean) -> ButtonViewModel
): VideoActionBarItemContent {
    if (content.hasLikeDislikeButtons()) {
        val likeDislike = content.likeDislikeButtons
        val likeButton = mapButtons(likeDislike.likeButton, mapper)
        val dislikeButton = mapButtons(likeDislike.dislikeButton, mapper)
        return if (likeButton === likeDislike.likeButton && dislikeButton === likeDislike.dislikeButton) {
            content
        } else {
            content.toBuilder()
                .setLikeDislikeButtons(likeDislike.toBuilder()
                    .setLikeButton(likeButton)
                    .setDislikeButton(dislikeButton))
                .build()
        }
    }

    if (content.hasOverflow()) {
        // ปุ่มของ layout minimal อยู่ในเมนูของมัน ปุ่มที่ซ่อนอยู่จึงอยู่ในรายการที่แสดง
        val overflow = content.overflow
        var builder: VideoActionBarOverflow.Builder? = null

        for (index in 0 until overflow.shownItemsCount) {
            val item = mapButtons(overflow.getShownItems(index), mapper)
            if (item !== overflow.getShownItems(index)) {
                val newBuilder = builder ?: overflow.toBuilder().also { builder = it }
                newBuilder.setShownItems(index, item)
            }
        }
        for (index in 0 until overflow.menuItemsCount) {
            val item = mapButtons(overflow.getMenuItems(index), mapper)
            if (item !== overflow.getMenuItems(index)) {
                val newBuilder = builder ?: overflow.toBuilder().also { builder = it }
                newBuilder.setMenuItems(index, item)
            }
        }

        return builder?.let { content.toBuilder().setOverflow(it).build() } ?: content
    }

    if (content.hasButton()) {
        val button = mapper(content.button, false)
        return if (button === content.button) content else content.toBuilder().setButton(button).build()
    }

    if (content.hasLikeButton()) {
        val likeButton: LikeButtonViewModel = content.likeButton
        val toggleButton = mapToggleButton(likeButton.toggleButtonViewModel, mapper)
        return if (toggleButton === likeButton.toggleButtonViewModel) {
            content
        } else {
            content.toBuilder().setLikeButton(likeButton.toBuilder()
                .setToggleButtonViewModel(toggleButton)).build()
        }
    }

    if (content.hasDislikeButton()) {
        val dislikeButton: DislikeButtonViewModel = content.dislikeButton
        val toggleButton = mapToggleButton(dislikeButton.toggleButtonViewModel, mapper)
        return if (toggleButton === dislikeButton.toggleButtonViewModel) {
            content
        } else {
            content.toBuilder().setDislikeButton(dislikeButton.toBuilder()
                .setToggleButtonViewModel(toggleButton)).build()
        }
    }

    if (content.hasSaveButton()) {
        val saveButton: AddToPlaylistButtonViewModel = content.saveButton
        val toggleButton = mapToggleButton(saveButton.toggleButtonViewModel, mapper)
        return if (toggleButton === saveButton.toggleButtonViewModel) {
            content
        } else {
            content.toBuilder().setSaveButton(saveButton.toBuilder()
                .setToggleButtonViewModel(toggleButton)).build()
        }
    }

    return content
}

/**
 * @return holder เดิมถ้า mapper ไม่เปลี่ยนปุ่มใดเลย
 */
private fun mapToggleButton(
    holder: ToggleButtonViewModelHolder,
    mapper: (ButtonViewModel, Boolean) -> ButtonViewModel
): ToggleButtonViewModelHolder {
    val toggleButton = holder.toggleButtonViewModel
    if (!toggleButton.hasDefaultButtonViewModel() || !toggleButton.hasToggledButtonViewModel()) {
        return holder
    }

    val defaultButton = toggleButton.defaultButtonViewModel.buttonViewModel
    val toggledButton = toggleButton.toggledButtonViewModel.buttonViewModel
    val newDefaultButton = mapper(defaultButton, false)
    val newToggledButton = mapper(toggledButton, true)
    if (newDefaultButton === defaultButton && newToggledButton === toggledButton) {
        return holder
    }

    return holder.toBuilder()
        .setToggleButtonViewModel(toggleButton.toBuilder()
            .setDefaultButtonViewModel(toggleButton.defaultButtonViewModel.toBuilder()
                .setButtonViewModel(newDefaultButton))
            .setToggledButtonViewModel(toggleButton.toggledButtonViewModel.toBuilder()
                .setButtonViewModel(newToggledButton)))
        .build()
}

private fun withActionBarData(
    element: Element,
    componentType: ComponentType,
    actionBarModel: VideoActionBarModel,
    data: VideoActionBarData
): Element =
    element.toBuilder()
        .setType(element.type.toBuilder()
            .setComponentType(componentType.toBuilder()
                .setModel(componentType.model.toBuilder()
                    .setVideoActionBarModel(actionBarModel.toBuilder()
                        .setVideoActionBarData(data)))))
        .build()

private fun withActionBar(
    element: Element,
    componentType: ComponentType,
    lockupModel: LockupModel,
    actionBar: VideoActionBarViewModel
): Element =
    element.toBuilder()
        .setType(element.type.toBuilder()
            .setComponentType(componentType.toBuilder()
                .setModel(componentType.model.toBuilder()
                    .setLockupModel(lockupModel.toBuilder()
                        .setViewModel(lockupModel.viewModel.toBuilder()
                            .setVideoActionBarViewModel(actionBar))))))
        .build()
