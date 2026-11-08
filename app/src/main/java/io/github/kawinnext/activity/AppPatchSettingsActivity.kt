@file:Suppress("DEPRECATION")

package io.github.kawinnext.activity

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.preference.Preference
import android.preference.PreferenceFragment
import android.provider.Settings
import android.view.MenuItem
import io.github.libxposed.service.XposedService
import io.github.kawinnext.AppPatchInfo
import io.github.kawinnext.Patch
import io.github.kawinnext.R
import io.github.kawinnext.activity.preference.HaloPatchHeaderPreference
import io.github.kawinnext.activity.preference.HaloPatchPreference
import io.github.kawinnext.activity.preference.HaloSearchPreference
import io.github.kawinnext.appPatchConfigurations

class AppPatchSettingsActivity : Activity() {

    companion object {
        const val ARGUMENT_APP_NAME = "app_name_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_patch_settings)

        actionBar?.setDisplayHomeAsUpEnabled(true)

        val appName = intent.getStringExtra(ARGUMENT_APP_NAME)
        actionBar?.title = appName

        if (savedInstanceState != null) return
        val fragment = AppPatchSettingsFragment().apply {
            arguments = Bundle().apply {
                putString(ARGUMENT_APP_NAME, appName)
            }
        }
        fragmentManager.beginTransaction()
            .replace(R.id.app_patch_settings_container, fragment)
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    class AppPatchSettingsFragment : PreferenceFragment(), SettingApplication.ServiceStateListener {

        private var mService: XposedService? = null
        private var remotePrefs: SharedPreferences? = null
        private var header: HaloPatchHeaderPreference? = null
        private var appPatchInfo: AppPatchInfo? = null

        private var patches: List<Patch> = emptyList()
        private var defaultStates: Map<String, Boolean> = emptyMap()
        private val patchRows = mutableListOf<Preference>()
        private var query = ""

        override fun onStart() {
            super.onStart()
            SettingApplication.addServiceStateListener(this, true)
        }

        override fun onStop() {
            SettingApplication.removeServiceStateListener(this)
            super.onStop()
        }

        override fun onServiceStateChanged(service: XposedService?) {
            mService = service
            if (service == null) {
                activity.actionBar?.subtitle = "Binder is null"
                return
            }

            activity.runOnUiThread {
                if (!isAdded) return@runOnUiThread

                val appName = arguments?.getString(ARGUMENT_APP_NAME)
                val info = appPatchConfigurations.find { it.appName == appName }
                    ?: throw Exception("AppPatchInfo not found, app_name: $appName")

                appPatchInfo = info
                patches = info.patches
                    .filter { it.name.isNotEmpty() && !it.name.startsWith("<") }
                    .sortedBy { it.name }
                defaultStates = patches.associate { it.name to it.use }
                remotePrefs = service.getRemotePreferences(info.packageName)

                buildScreen()
            }
        }

        private fun buildScreen() {
            val ctx = context ?: return
            val prefs = remotePrefs ?: return

            query = ""
            val root = preferenceManager.createPreferenceScreen(ctx)

            header = HaloPatchHeaderPreference(
                ctx,
                showAppInfo = isInstalled(),
                onDefault = ::restoreDefaults,
                onNone = ::disableAll,
                onAppInfo = ::openAppInfo,
            )
            root.addPreference(header)
            root.addPreference(HaloSearchPreference(ctx) { value ->
                query = value
                applyFilter()
            })

            patchRows.clear()
            for (patch in patches) {
                val row = makePatchRow(patch, prefs)
                patchRows += row
                root.addPreference(row)
            }

            preferenceScreen = root
            refreshCounts()
        }

        private fun makePatchRow(patch: Patch, prefs: SharedPreferences) =
            HaloPatchPreference(context).apply {
                key = patch.name
                title = patch.name
                summary = patch.description
                isChecked = prefs.getBoolean(patch.name, patch.use)

                setOnPreferenceChangeListener { _, newValue ->
                    prefs.edit().putBoolean(patch.name, newValue as Boolean).apply()
                    vibrate()
                    refreshCounts()
                    true
                }
            }

        private fun applyFilter() {
            val root = preferenceScreen ?: return
            val prefs = remotePrefs ?: return

            patchRows.forEach { root.removePreference(it) }
            patchRows.clear()

            for (patch in patches) {
                if (!matches(patch)) continue
                val row = makePatchRow(patch, prefs)
                patchRows += row
                root.addPreference(row)
            }
            refreshCounts()
        }

        private fun matches(patch: Patch): Boolean {
            if (query.isEmpty()) return true
            return patch.name.contains(query, ignoreCase = true) ||
                patch.description.contains(query, ignoreCase = true)
        }

        private fun refreshCounts() {
            val prefs = remotePrefs ?: return
            val enabled = patches.count { prefs.getBoolean(it.name, it.use) }
            header?.setCounts(enabled, patches.size)
            context?.let {
                activity?.actionBar?.subtitle =
                    it.getString(R.string.patches_enabled_format, enabled, patches.size)
            }
        }

        private fun restoreDefaults() {
            val prefs = remotePrefs ?: return
            val editor = prefs.edit()
            for (patch in patches) {
                val value = defaultStates[patch.name] ?: patch.use
                editor.putBoolean(patch.name, value)
            }
            editor.apply()
            syncRowsFromPrefs()
            refreshCounts()
        }

        private fun disableAll() {
            val prefs = remotePrefs ?: return
            val editor = prefs.edit()
            for (patch in patches) {
                editor.putBoolean(patch.name, false)
            }
            editor.apply()
            syncRowsFromPrefs()
            refreshCounts()
        }

        private fun syncRowsFromPrefs() {
            val prefs = remotePrefs ?: return
            patchRows.forEach { row ->
                if (row is HaloPatchPreference) {
                    row.isChecked = prefs.getBoolean(row.key, false)
                }
            }
        }

        private fun isInstalled(): Boolean {
            val packageName = appPatchInfo?.packageName ?: return false
            return runCatching {
                context?.packageManager?.getPackageInfo(packageName, 0)
            }.getOrNull() != null
        }

        private fun openAppInfo() {
            val packageName = appPatchInfo?.packageName ?: return
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        private fun vibrate() {
            val vibrator = context?.getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
            if (vibrator.hasVibrator()) vibrator.vibrate(50)
        }
    }
}
