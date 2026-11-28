package io.github.kawinnext.morphe.youtube.interaction.channelsearch

import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.patches.ChannelSearchPatch
import io.github.kawinnext.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.kawinnext.morphe.youtube.misc.settings.PreferenceScreen
import io.github.kawinnext.morphe.youtube.misc.toolbar.hookToolBar
import io.github.kawinnext.morphe.youtube.misc.toolbar.toolBarHookPatch
import io.github.kawinnext.patch

val channelSearchPatch = patch(
    name = "Channel search",
    description = "Adds an option to search inside the channel that is currently open " +
            "instead of searching all of YouTube.",
) {
    dependsOn(toolBarHookPatch)

    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_channel_search", summary = true)
    )

    hookToolBar(ChannelSearchPatch::setSearchButtonView)

    // A channel page browses by its channel id, which is what the search is scoped to.
    ::BrowseFragmentTarget.hookMethod {
        val browserIdMethod = ::browserIdMethod.method
        after {
            ChannelSearchPatch.setBrowseId(browserIdMethod(null, it.args[0]) as? String)
        }
    }

    // Leaving a channel for the search feed shows no browse page, so nothing would
    // otherwise replace the browse id of the channel.
    SearchResultsFragmentOnCreateViewFingerprint.hookMethod {
        before {
            ChannelSearchPatch.clearBrowseId()
        }
    }

    // The hint of the search box and the submit of the app's search are no longer replaced here.
    // The extension moved the feature to a search button of its own and a results sheet the
    // patcher injects, and it keeps both entries private now; a module cannot reach them.
}

