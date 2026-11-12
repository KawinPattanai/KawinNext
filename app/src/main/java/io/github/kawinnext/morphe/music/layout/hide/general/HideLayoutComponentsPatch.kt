/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.music.layout.hide.general

import android.view.View
import app.morphe.extension.music.patches.components.CommentsFilter
import app.morphe.extension.music.patches.components.LayoutComponentsFilter
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.ResourceType
import io.github.kawinnext.hookMethod
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldListDirect
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.music.misc.litho.filter.LithoFilter
import io.github.kawinnext.morphe.music.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.patch
import io.github.kawinnext.scopedHook
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Modifier

private val findViewByIdMethod = DexMethod("Landroid/view/View;->findViewById(I)Landroid/view/View;")

private val inflateMethods = listOf(
    DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;"),
    DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;")
)

// The app has more than one player, and each of them finds its own view, so every match is hooked.
private val audioVideoToggleMethods = findMethodListDirect {
    AudioVideoSwitchPillContainerFingerprint.matchAll().map { it.method }
}

private val overlayLoopMethods = findMethodListDirect {
    OverlayQueueLoopButtonFingerprint.matchAll().map { it.method }
}

private val playbackLoopMethods = findMethodListDirect {
    PlaybackQueueLoopButtonFingerprint.matchAll().map { it.method }
}

private val overlayShuffleMethods = findMethodListDirect {
    OverlayQueueShuffleButtonFingerprint.matchAll().map { it.method }
}

private val playbackShuffleMethods = findMethodListDirect {
    PlaybackQueueShuffleButtonFingerprint.matchAll().map { it.method }
}

private val commentsInfoButtonMethod = findMethodDirect {
    InformationButtonFingerprint.match().method
}

private val chipCloudMethod = findMethodDirect {
    ChipCloudFingerprint.match().method
}

// The search suggestions response passes a section list to a wrapper that renders its sections.
// Upstream drops the shelves that only hold suggested searches from that list right before the
// wrapper is built. A runtime hook cannot inject a call there, so the constructor of the wrapper is
// hooked instead, and the list it receives is filtered the same way.

/** The wrapper that renders the section list of the search suggestions response. */
private val sectionListWrapperConstructor = findMethodDirect {
    SearchSuggestionsRenderFingerprint.instructionMatches[1].instruction.methodRef!!
}

/** The list of the search suggestions, which holds the search box the filter looks for. */
private val searchSuggestionsRecyclerView = findFieldDirect {
    SearchSuggestionsRenderFingerprint.instructionMatches[3].instruction.fieldRef!!
        .declaredClass!!.fields
        .first { it.typeSign == "Landroid/support/v7/widget/RecyclerView;" }
}

/** The section list the wrapper is built from, of which the contents fields hold the shelves. */
private val searchSuggestionsSectionList = findClassDirect {
    sectionListWrapperConstructor().paramTypes.single()
}

/** The item of a section list, which converts to the renderer of the section. */
private val searchSuggestionsSectionItem = findClassDirect {
    sectionListWrapperConstructor().declaredClass!!.methods
        .first { it.returnTypeName == "j\$.util.Optional" && it.paramCount == 1 }
        .paramTypes.single()
}

/** The list of the renderers a shelf holds. */
private val musicShelfItemRenderers = findFieldDirect {
    MusicShelfItemsFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** The renderer of a search history entry of a shelf item. */
private val musicShelfItemHistory = findFieldDirect {
    MusicShelfItemsFingerprint.instructionMatches[2].instruction.fieldRef!!
}

/** The renderer of a suggested search of a shelf item. */
private val musicShelfItemSuggested = findFieldDirect {
    MusicShelfItemsFingerprint.instructionMatches[4].instruction.fieldRef!!
}

/** The field of a section item that holds the shelf of the section. */
private val searchSuggestionsSectionShelf = findFieldDirect {
    val shelfType = musicShelfItemRenderers().declaredClass!!.descriptor
    searchSuggestionsSectionItem().fields.first { it.typeSign == shelfType }
}

/** The lists of shelves a section list holds. */
private val searchSuggestionsSectionContents = findFieldListDirect {
    val renderersType = musicShelfItemRenderers().typeSign
    searchSuggestionsSectionList().fields.filter {
        !Modifier.isStatic(it.modifiers) && it.typeSign == renderersType
    }
}

/** The method that copies a protobuf list into a mutable one of the given capacity. */
private val musicShelfItemsCopy = findMethodDirect {
    val listType = musicShelfItemRenderers().typeSign
    Fingerprint(
        definingClass = listType,
        returnType = listType,
        parameters = listOf("I")
    )()
}

/**
 * The settings of this patch. Each of the views below is hidden by the method that finds it,
 * where the upstream patch inserts the call into the method itself. A runtime hook cannot insert
 * into a method, so the view is taken from the call the method makes instead.
 */
val HideLayoutComponents = patch(
    name = "Hide layout components",
    description = "Adds options to hide the shelves and the components of the feed and of the player."
) {
    dependsOn(LithoFilter)

    PreferenceScreen.FEED.addPreferences(
        SwitchPreference("morphe_music_hide_podcast_episode_download_button", summary = true),
        SwitchPreference("morphe_music_hide_explore_shelf"),
        SwitchPreference("morphe_music_hide_grid_shelves"),
        SwitchPreference("morphe_music_hide_filter_bar"),
        SwitchPreference("morphe_music_hide_horizontal_shelves"),
        SwitchPreference("morphe_music_hide_list_shelves"),
        SwitchPreference("morphe_music_hide_new_from_shelf"),
        SwitchPreference("morphe_music_hide_playlist_shelves"),
        SwitchPreference("morphe_music_hide_speed_dial_shelf"),
        SwitchPreference("morphe_music_hide_you_may_also_like_section"),
        SwitchPreference("morphe_music_hide_your_downloads_shelf")
    )

    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_music_hide_comments_info_button")
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_hide_audio_video_toggle"),
        SwitchPreference("morphe_music_hide_repeat_button"),
        SwitchPreference("morphe_music_hide_shuffle_button")
    )

    addLithoFilter(LayoutComponentsFilter())

    val audioVideoToggleId = ResourceUtils.getIdentifier(ResourceType.ID, "audio_video_switch_pill_container")
    if (audioVideoToggleId != 0) {
        ::audioVideoToggleMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == audioVideoToggleId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideAudioVideoToggle)
                        }
                    }
                }
            )
        }
    }

    val commentsInfoButtonId = ResourceUtils.getIdentifier(ResourceType.ID, "information_button")
    if (commentsInfoButtonId != 0) {
        findViewByIdMethod.hookMethod(
            scopedHook(::commentsInfoButtonMethod.member) {
                after { param ->
                    if (param.args[0] == commentsInfoButtonId) {
                        (param.result as? View)?.let(CommentsFilter::hideCommentsInfoButton)
                    }
                }
            }
        )
    }

    val overlayLoopId = ResourceUtils.getIdentifier(ResourceType.ID, "overlay_queue_loop_button_view")
    if (overlayLoopId != 0) {
        ::overlayLoopMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == overlayLoopId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideRepeatButton)
                        }
                    }
                }
            )
        }
    }

    val playbackLoopId = ResourceUtils.getIdentifier(ResourceType.ID, "playback_queue_loop_button_view")
    if (playbackLoopId != 0) {
        ::playbackLoopMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == playbackLoopId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideRepeatButton)
                        }
                    }
                }
            )
        }
    }

    val overlayShuffleId = ResourceUtils.getIdentifier(ResourceType.ID, "overlay_queue_shuffle_button_view")
    if (overlayShuffleId != 0) {
        ::overlayShuffleMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == overlayShuffleId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideShuffleButton)
                        }
                    }
                }
            )
        }
    }

    val playbackShuffleId = ResourceUtils.getIdentifier(ResourceType.ID, "playback_queue_shuffle_button_view")
    if (playbackShuffleId != 0) {
        ::playbackShuffleMethods.dexMethodList.forEach { method ->
            val member = method.toMember()
            member.hookMethod(
                scopedHook(member) {
                    after { param ->
                        if (param.args[0] == playbackShuffleId) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideShuffleButton)
                        }
                    }
                }
            )
        }
    }

    // The filter bar is inflated as a chip cloud, and the view that is inflated is the bar.
    val chipCloudLayout = ResourceUtils.getIdentifier(ResourceType.LAYOUT, "chip_cloud")
    if (chipCloudLayout != 0) {
        inflateMethods.forEach { inflateMethod ->
            inflateMethod.hookMethod(
                scopedHook(::chipCloudMethod.member) {
                    after { param ->
                        if (param.args[0] == chipCloudLayout) {
                            (param.result as? View)?.let(LayoutComponentsFilter::hideFilterBar)
                        }
                    }
                }
            )
        }
    }

    // The 'You may also like' shelf is dropped from the section list of the suggestions before the
    // wrapper that renders the sections is built from it, which is where upstream injects its call.
    // The hook only runs while the render method of the suggestions is on the stack.
    runCatching {
        val recyclerViewField = ::searchSuggestionsRecyclerView.field.apply { isAccessible = true }
        val sectionShelfField = ::searchSuggestionsSectionShelf.field.apply { isAccessible = true }
        val sectionContentsFields = ::searchSuggestionsSectionContents.fieldList
            .onEach { it.isAccessible = true }
        val shelfRenderersField = ::musicShelfItemRenderers.field.apply { isAccessible = true }
        val shelfHistoryField = ::musicShelfItemHistory.field.apply { isAccessible = true }
        val shelfSuggestedField = ::musicShelfItemSuggested.field.apply { isAccessible = true }
        val shelfItemType = shelfHistoryField.declaringClass
        val sectionItemType = ::searchSuggestionsSectionItem.clazz
        val listCopyMethod = ::musicShelfItemsCopy.method.apply { isAccessible = true }

        // A section is only the one upstream removes when it holds at least one item and every item
        // of it is a suggested search, which is a null history and a non-null suggestion.
        fun onlyHoldsSuggestedSearches(section: Any?): Boolean {
            if (!sectionItemType.isInstance(section)) return false
            val shelf = sectionShelfField.get(section) ?: return false
            val renderers = shelfRenderersField.get(shelf) as? List<*> ?: return false
            if (renderers.isEmpty()) return false

            return renderers.all { item ->
                item != null && shelfItemType.isInstance(item) &&
                    shelfHistoryField.get(item) == null && shelfSuggestedField.get(item) != null
            }
        }

        SearchSuggestionsRenderFingerprint.hookMethod(
            scopedHook(::sectionListWrapperConstructor.member) {
                after { param ->
                    try {
                        // The extension hides the shelf only while the search box is empty.
                        val suggestionsView = recyclerViewField.get(outerParam.thisObject) as? View
                        if (!LayoutComponentsFilter.hideYouMayAlsoLikeSection(suggestionsView)) {
                            return@after
                        }

                        val sectionList = param.args.getOrNull(0) ?: return@after
                        sectionContentsFields.forEach { field ->
                            @Suppress("UNCHECKED_CAST")
                            val sections = field.get(sectionList) as? MutableList<Any?>
                                ?: return@forEach
                            val kept = sections.filterNot(::onlyHoldsSuggestedSearches)
                            if (kept.size == sections.size) return@forEach

                            // The shelves are a protobuf list, so the list that replaces it is built by
                            // the app itself, with the capacity of the list it replaces.
                            @Suppress("UNCHECKED_CAST")
                            val copy = listCopyMethod.invoke(sections, sections.size)
                                as? MutableList<Any?> ?: return@forEach
                            copy.clear()
                            copy.addAll(kept)
                            field.set(sectionList, copy)
                        }
                    } catch (e: Throwable) {
                        Logger.printException({ "hideYouMayAlsoLikeSection failure" }, e)
                    }
                }
            }
        )
    }.onFailure {
        Logger.printException({ "hide 'You may also like' section failure" }, it)
    }
}
