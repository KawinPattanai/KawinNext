/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5436
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

@file:Suppress("SpellCheckingInspection", "UNCHECKED_CAST")

package io.github.kawinnext.morphe.youtube.layout.miniplayer

import android.app.Activity
import android.media.session.MediaSession
import android.os.Bundle
import android.view.View
import app.morphe.extension.shared.Logger
import app.morphe.extension.youtube.patches.MiniplayerPatch
import app.morphe.extension.youtube.patches.playback.PlaybackSessionPatch
import io.github.kawinnext.HookDsl
import io.github.kawinnext.IScopedHookCallback
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.setExtensionIsPatchIncluded
import io.github.kawinnext.morphe.shared.misc.settings.preference.BasePreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.PlaylistControllerConstructorFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.PlaylistModelDescriptionFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseClickFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseFlingFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseFlingLegacyFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseTouchFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerControllerFlingFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerControllerReleaseFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.acceptedPlaylistModelFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.resolveNativeAccountContract
import io.github.kawinnext.morphe.youtube.misc.loadvideo.dismissPlayerMethod
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeFingerprint
import io.github.kawinnext.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.shared.YouTubeActivityOnCreateFingerprint
import io.github.kawinnext.morphe.youtube.video.information.VideoInformationPatch
import io.github.kawinnext.morphe.youtube.video.information.playerStatusMethod
import io.github.kawinnext.morphe.youtube.video.information.videoTimeHooks
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Method

// ---------------------------------------------------------------------------
// ข้อมูลที่อ่านจาก dex ครั้งเดียวตอนติดตั้งแพตช์
// ---------------------------------------------------------------------------

/** ตัวอ่าน video ID ของ playback start descriptor */
private val sessionDescriptorVideoId = findMethodDirect {
    PlaybackSessionDescriptorToStringFingerprint.instructionMatches[1].instruction.methodRef!!
}

/** ฟิลด์รายการ video ID ของ builder */
private val sessionDescriptorVideoIds = findFieldDirect {
    PlaybackStartDescriptorBuilderFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/** ตัวอ่าน playback start descriptor ที่เมธอด intent เรียกเป็นคำสั่งแรกที่จับได้ */
private val sessionIntentDescriptorGetter = findMethodDirect {
    VideoPlaybackIntentFingerprint.instructionMatches[0].instruction.methodRef!!
}

/** ฟิลด์ที่ intent เก็บ descriptor ไว้ ใช้เขียน descriptor ใหม่ที่ส่วนขยายสร้างกลับคืน */
private val sessionIntentDescriptorField = findFieldListDirect {
    val descriptorType = PlaybackSessionDescriptorToStringFingerprint.match().method.declaredClass!!.descriptor
    sessionIntentDescriptorGetter(this).usingFields
        .filter { using ->
            using.usingType == FieldUsingType.Read && using.field.type.descriptor == descriptorType
        }
        .map { it.field }
}

/** ตัวอ่าน ID และ incognito ของ identity บัญชี ซึ่ง contract มาจาก native request */
private val sessionAccountId = findMethodDirect { resolveNativeAccountContract().id }
private val sessionAccountIncognito = findMethodDirect { resolveNativeAccountContract().incognito }

/**
 * ตัวอ่านบัญชีปัจจุบันของทุก provider
 *
 * upstream เพิ่มเมธอด `patch_sessionAccount` ให้ provider ของแอป โมดูลทำ proxy แทนและ hook
 * ตัวอ่านที่มีอยู่แล้ว จึงต้องรู้ทั้งสองฝั่ง: method ที่คืน contract และ accessor ของ identity
 */
private val sessionAccountGetters = findMethodListDirect {
    val contract = resolveNativeAccountContract().type
    PlaybackSessionAccountProviderFingerprint.matchAll()
        .mapNotNull { it.method.declaredClass?.descriptor }
        .distinct()
        .flatMap { provider ->
            Fingerprint(
                definingClass = provider,
                parameters = emptyList(),
                returnType = contract,
            ).matchAllOrNull()?.map { it.method } ?: emptyList()
        }
        .distinctBy { it.descriptor }
}

/** เมธอดที่ playlist controller รับ model ที่ตรงกับวิดีโอที่เล่นอยู่ */
private val sessionPlaylistAccept = findMethodDirect {
    val model = PlaylistModelDescriptionFingerprint.match().method
    acceptedPlaylistModelFingerprint(
        PlaylistControllerConstructorFingerprint.match().method.declaredClass!!.descriptor,
        model.declaredClass!!.descriptor,
        model.findFieldFromToString("{videoId=")
    ).match().method
}

/** ฟิลด์ video ID ที่ controller ถือว่าเป็นของที่เล่นอยู่ */
private val sessionPlaylistActiveVideo = findFieldDirect {
    val model = PlaylistModelDescriptionFingerprint.match().method
    acceptedPlaylistModelFingerprint(
        PlaylistControllerConstructorFingerprint.match().method.declaredClass!!.descriptor,
        model.declaredClass!!.descriptor,
        model.findFieldFromToString("{videoId=")
    ).match().instructionMatches[2].instruction.fieldRef!!
}

/** ฟิลด์ model ที่ controller ยอมรับไว้ */
private val sessionPlaylistModel = findFieldDirect {
    val model = PlaylistModelDescriptionFingerprint.match().method
    acceptedPlaylistModelFingerprint(
        PlaylistControllerConstructorFingerprint.match().method.declaredClass!!.descriptor,
        model.declaredClass!!.descriptor,
        model.findFieldFromToString("{videoId=")
    ).match().instructionMatches.last().instruction.fieldRef!!
}

/** ฟิลด์ panel ของ playlist model */
private val sessionPlaylistPanel = findFieldDirect {
    PlaylistModelDescriptionFingerprint.match().method.findFieldFromToString(", playlistPanel=")
}

/** เมธอดที่ทุกเส้นทางปิด player ของผู้ใช้เรียกใช้ */
private val sessionPlayerDismiss = findMethodDirect { dismissPlayerMethod(this) }

/** เส้นทางปิด player ของผู้ใช้ในรุ่นนี้ เส้นทางที่ไม่มีใน build นี้จะถูกข้าม */
private val sessionPlayerCloseMethods = findMethodListDirect {
    val dismiss = sessionPlayerDismiss(this)
    listOf(
        QueuePlayerCloseClickFingerprint(dismiss),
        QueuePlayerCloseFlingFingerprint(dismiss),
        QueuePlayerCloseFlingLegacyFingerprint(dismiss),
        QueuePlayerCloseTouchFingerprint(dismiss),
        QueuePlayerControllerFlingFingerprint(dismiss),
        QueuePlayerControllerReleaseFingerprint(dismiss),
    ).mapNotNull { it.matchOrNull()?.method }
}

// ---------------------------------------------------------------------------
// proxy ที่ทำอินเทอร์เฟซของส่วนขยายแทนคลาสของแอป
//
// upstream เพิ่มเมธอดของอินเทอร์เฟซเหล่านี้ให้คลาสของแอปตอนแพตช์ APK โมดูลทำแบบนั้นตอนรันไม่ได้
// object ของโมดูลจึงทำอินเทอร์เฟซแทน แล้วเรียกเมธอดเดิมของแอปผ่าน reflection
// ---------------------------------------------------------------------------

/** สถานะที่ส่วนขยายสั่งไว้ระหว่างเตรียม descriptor หนึ่งครั้ง */
private class SessionDescriptorState {
    var paused = false
    var position: Long? = null
}

/** ตัวสร้าง descriptor จากรายการ video ID ซึ่ง upstream เรียก builder ของแอปใน smali */
private class SessionDescriptorFactory(
    private val videoIdMethod: Method,
    private val startPausedMethod: Method,
    private val setPositionMethod: Method,
    private val builderClass: Class<*>,
    private val builderMethod: Method,
    private val builderVideoIds: Field,
) {
    fun videoId(descriptor: Any): String =
        runCatching { videoIdMethod.invoke(descriptor) as? String }.getOrNull().orEmpty()

    fun startPaused(descriptor: Any) {
        startPausedMethod.invoke(descriptor)
    }

    fun setPosition(descriptor: Any, positionMilliseconds: Long) {
        setPositionMethod.invoke(descriptor, positionMilliseconds)
    }

    fun wrap(descriptor: Any, state: SessionDescriptorState) =
        SessionDescriptor(descriptor, this, state)

    fun build(videoIds: List<String>, state: SessionDescriptorState): PlaybackSessionPatch.Descriptor {
        val builder = builderClass.getDeclaredConstructor().newInstance()
        builderVideoIds.set(builder, videoIds)
        return SessionDescriptor(builderMethod.invoke(builder)!!, this, state)
    }
}

/** descriptor ของแอปที่ทำอินเทอร์เฟซ [PlaybackSessionPatch.Descriptor] แทน */
private class SessionDescriptor(
    val appDescriptor: Any,
    private val factory: SessionDescriptorFactory,
    private val state: SessionDescriptorState,
) : PlaybackSessionPatch.Descriptor {
    override fun patch_sessionVideoId(): String = factory.videoId(appDescriptor)

    override fun patch_startSessionPaused() {
        state.paused = true
        factory.startPaused(appDescriptor)
    }

    override fun patch_setSessionPosition(positionMilliseconds: Long) {
        state.position = positionMilliseconds
        factory.setPosition(appDescriptor, positionMilliseconds)
    }

    override fun patch_newSessionQueue(videoIds: MutableList<String>): PlaybackSessionPatch.Descriptor =
        factory.build(videoIds, state)
}

/** ตัวอ่านบัญชีปัจจุบันของแอปในนามของ [PlaybackSessionPatch.AccountProvider] */
private class SessionAccountProvider(
    private val provider: Any,
    private val currentAccount: Method,
    private val accountId: Method,
    private val accountIncognito: Method,
) : PlaybackSessionPatch.AccountProvider {
    override fun patch_sessionAccount(): PlaybackSessionPatch.Account? = runCatching {
        val identity = currentAccount.invoke(provider) ?: return@runCatching null
        val id = accountId.invoke(identity) as? String
        val incognito = accountIncognito.invoke(identity) as Boolean
        PlaybackSessionPatch.Account(id, incognito)
    }.getOrNull()
}

/** ตัวอ่าน playlist ที่กำลังเล่นในนามของ [PlaybackSessionPatch.PlaylistReader] */
private class SessionPlaylistReader(
    private val controller: Any,
    private val activeVideo: Field,
    private val model: Field,
    private val panel: Field,
) : PlaybackSessionPatch.PlaylistReader {
    override fun patch_sessionPlaylistVideoId(): String =
        runCatching { activeVideo.get(controller) as? String }.getOrNull().orEmpty()

    override fun patch_sessionPlaylistPanel(): Any? = runCatching {
        model.get(controller)?.let { panel.get(it) }
    }.getOrNull()
}

/**
 * Continue watching on startup
 *
 * เก็บเซสชันการเล่น (วิดีโอ ตำแหน่ง และคิวที่เหลือ) ไว้ในเครื่อง แล้วเปิดกลับมาแบบหยุดรอใน
 * miniplayer เมื่อแอปเริ่มทำงาน — พอร์ตจาก upstream PR 5436
 */
val playbackSessionPatch = patch(
    name = "Continue watching on startup",
    description = "Saves the playback session on this device and restores it paused in the miniplayer when YouTube starts.",
) {
    dependsOn(
        miniplayerPatch,
        VideoInformationPatch,
        PlayerTypeHook,
    )

    // region Preference

    // upstream วางสวิตช์ไว้ในหน้าจอ Miniplayer ซึ่งแพตช์ miniplayer สร้างไว้แล้ว
    // โมดูลไม่แก้ไฟล์ของแพตช์นั้น จึงหยิบหน้าจอเดิมมาเพิ่มลูกเข้าไป ไม่สร้างหน้าจอซ้ำ
    val restoreSession = SwitchPreference("morphe_miniplayer_restore_session", summary = true)
    val miniplayerScreen = PreferenceScreen.PLAYER.preferences
        .filterIsInstance<PreferenceScreenPreference>()
        .firstOrNull { it.key == "morphe_miniplayer_screen" }
    val miniplayerPreferences = miniplayerScreen?.preferences as? MutableSet<BasePreference>
    if (miniplayerPreferences != null) {
        miniplayerPreferences.add(restoreSession)
    } else {
        PreferenceScreen.PLAYER.addPreferences(restoreSession)
    }

    // endregion

    // region Playback start descriptor

    val descriptorFactory = SessionDescriptorFactory(
        ::sessionDescriptorVideoId.method,
        PlaybackStartDescriptorStartPausedFingerprint.method,
        PlaybackStartDescriptorStartPositionFingerprint.method,
        classLoader.loadClass(PlaybackStartDescriptorBuilderFingerprint.dexMethod.className),
        PlaybackStartDescriptorBuilderFingerprint.method,
        ::sessionDescriptorVideoIds.field.apply { isAccessible = true },
    )

    // ฟิลด์ที่ intent เก็บ descriptor ไว้ ไม่มีในบางรุ่น จึงไม่บังคับ
    val intentDescriptorGetter = ::sessionIntentDescriptorGetter.method
    val intentDescriptorField = runCatching {
        ::sessionIntentDescriptorField.fieldList.firstOrNull()?.apply { isAccessible = true }
    }.getOrNull()

    VideoPlaybackIntentFingerprint.hookMethod {
        before { param ->
            // คำสั่งเปิดวิดีโอทุกครั้งผ่านที่นี่ คำสั่งที่ไม่ใช่การกู้คืนจะได้ descriptor เดิมกลับมา
            runCatching {
                val context = param.args[1] as? MutableMap<*, *> ?: return@runCatching
                val receiver = sequenceOf(param.thisObject, param.args[0])
                    .filterNotNull()
                    .firstOrNull { intentDescriptorGetter.declaringClass.isInstance(it) }
                    ?: return@runCatching
                val descriptor = intentDescriptorGetter.invoke(receiver) ?: return@runCatching

                val state = SessionDescriptorState()
                val prepared = PlaybackSessionPatch.prepareWatchDescriptor(
                    descriptorFactory.wrap(descriptor, state), context
                )
                if (prepared == null) {
                    // upstream คืนจากเมธอดก่อนเปิดวิดีโอ ซึ่งเท่ากับการยกเลิกคำสั่งนี้
                    Logger.printDebug { "Playback session canceled the watch command" }
                    param.result = null
                    return@runCatching
                }

                val preparedDescriptor = (prepared as? SessionDescriptor)?.appDescriptor
                if (preparedDescriptor == null || preparedDescriptor === descriptor) return@runCatching

                // คิวใหม่ที่ส่วนขยายสร้างให้ ถูกเขียนกลับที่เดิมของ intent ถ้า intent เก็บไว้ในฟิลด์
                val field = intentDescriptorField
                val stored = field != null &&
                    runCatching { field.set(receiver, preparedDescriptor); true }.getOrDefault(false)
                if (!stored) {
                    // เขียนกลับไม่ได้: คงสถานะหยุดและตำแหน่งไว้บน descriptor ตัวจริงแทน
                    // คิวที่เหลือจะหายไป เหลือวิดีโอเดียวที่หยุดรอ ณ ตำแหน่งเดิม
                    Logger.printDebug { "Playback session cannot replace the intent descriptor" }
                    if (state.paused) descriptorFactory.startPaused(descriptor)
                    state.position?.let { descriptorFactory.setPosition(descriptor, it) }
                }
            }.onFailure {
                Logger.printException { "Could not prepare the restored watch descriptor" }
            }
        }
    }

    ShortsPlaybackIntentFingerprint.hookMethod {
        before { param ->
            runCatching {
                PlaybackSessionPatch.prepareShortsDescriptor(
                    param.args[0],
                    param.args[1] as? MutableMap<*, *>,
                )
            }.onFailure {
                Logger.printException { "Could not prepare the restored Shorts descriptor" }
            }
        }
    }

    // endregion

    // region Media session

    // upstream อ่าน MediaSession จาก register ของเมธอดที่แอปตั้ง metadata โมดูลอ่าน register ไม่ได้
    // จึง hook เมธอดของแพลตฟอร์มซึ่งมี session เป็น `this` และได้ object ตัวเดียวกัน
    DexMethod("Landroid/media/session/MediaSession;->setMetadata(Landroid/media/MediaMetadata;)V")
        .toMethod()
        .hookMethod {
            before { param ->
                (param.thisObject as? MediaSession)?.let(PlaybackSessionPatch::attachMediaSession)
            }
        }

    // endregion

    // region Activity, player type และ overlay ของ miniplayer

    YouTubeActivityOnCreateFingerprint.hookMethod {
        before { param ->
            PlaybackSessionPatch.initialize(
                param.thisObject as Activity,
                param.args[0] as? Bundle,
            )
        }
    }

    // view ของ watch player เป็น `this` ของเมธอดที่แจ้ง player type
    PlayerTypeFingerprint.hookMethod {
        before { param ->
            (param.thisObject as? View)?.let(PlaybackSessionPatch::observeWatchPlayer)
        }
    }

    // ปุ่มเล่นของ overlay ถูกขอครั้งแรกตอนแตะ จึงผูกตอน layout ถูก inflate ไม่ใช่ตอนสร้าง view
    PlaybackSessionOverlayInflationFingerprint.hookMethod(
        scopedHook(
            DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;")
                .toMethod()
        ) {
            after { param ->
                (param.result as? View)?.let(MiniplayerPatch::bindRestoredMiniplayerOverlay)
            }
        }
    )

    // endregion

    // region บัญชีปัจจุบัน

    // ตัวอ่านบัญชีถูกเรียกเมื่อแอปใช้บัญชีจริง จึงผูกตอนนั้น ไม่ใช่ตอนสร้าง provider
    val accountId = ::sessionAccountId.method
    val accountIncognito = ::sessionAccountIncognito.method
    ::sessionAccountGetters.dexMethodList.forEach { getter ->
        val getterMethod = getter.toMethod()
        getter.hookMethod {
            before { param ->
                PlaybackSessionPatch.attachAccount(
                    SessionAccountProvider(param.thisObject, getterMethod, accountId, accountIncognito)
                )
            }
        }
    }

    // endregion

    // region Playlist ที่แอปโหลดไว้

    // ใช้เมื่อแอปยังไม่มีคิวของ native queue ให้บันทึก เช่นตอนผู้ใช้เปิดเพลย์ลิสต์เอง
    val playlistActiveVideo = ::sessionPlaylistActiveVideo.field.apply { isAccessible = true }
    val playlistModel = ::sessionPlaylistModel.field.apply { isAccessible = true }
    val playlistPanel = ::sessionPlaylistPanel.field.apply { isAccessible = true }

    ::sessionPlaylistAccept.hookMethod {
        after { param ->
            PlaybackSessionPatch.attachPlaylist(
                SessionPlaylistReader(param.thisObject, playlistActiveVideo, playlistModel, playlistPanel)
            )
        }
    }

    // endregion

    // region ปิด miniplayer ด้วยมือ

    // ปิดเอง = ล้างเซสชันที่เก็บไว้ ไม่ให้เปิดกลับตอนเปิดแอปครั้งถัดไป
    // ใช้เส้นทางปิดของผู้ใช้เท่านั้น การปิดที่โปรแกรมสั่งไม่แตะเซสชัน
    val onDismissed: HookDsl<IScopedHookCallback>.() -> Unit = {
        before { PlaybackSessionPatch.onMiniplayerDismissed() }
    }
    val closeMembers = ::sessionPlayerCloseMethods.dexMethodList.map { it.toMember() }
    if (closeMembers.isNotEmpty()) {
        ::sessionPlayerDismiss.method.hookMethod(
            scopedHook(*closeMembers.map { member -> member to onDismissed }.toTypedArray())
        )
    }

    // endregion

    // region เวลาเล่นและสถานะผู้เล่น

    // upstream แทรก videoTimeHook ไว้ที่เดียวกับที่โมดูลเก็บ videoTimeHooks
    videoTimeHooks.add { PlaybackSessionPatch.videoTimeChanged(it) }

    ::playerStatusMethod.hookMethod {
        before { param ->
            PlaybackSessionPatch.onPlayerStatus(param.args[0] as? Enum<*>)
        }
    }

    // endregion

    setExtensionIsPatchIncluded(PlaybackSessionPatch::class.java)
}
