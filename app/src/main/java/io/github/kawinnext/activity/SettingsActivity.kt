@file:Suppress("DEPRECATION")

package io.github.kawinnext.activity

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceFragment
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.window.OnBackInvokedDispatcher
import app.morphe.extension.shared.Utils
import app.morphe.extension.shared.settings.preference.about.MorpheAboutPreference
import io.github.libxposed.service.XposedService
import io.github.kawinnext.BuildConfig
import io.github.kawinnext.R
import io.github.kawinnext.activity.preference.HaloAppRowPreference
import io.github.kawinnext.activity.preference.HaloHeroPreference
import io.github.kawinnext.activity.preference.HaloLabelPreference
import io.github.kawinnext.activity.preference.HaloNotePreference
import io.github.kawinnext.activity.preference.HaloRowPreference
import io.github.kawinnext.appPatchConfigurations
import io.github.kawinnext.common.UpdateChecker
import kotlin.system.exitProcess

class SettingsActivity : Activity(), SettingApplication.ServiceStateListener {

    private var mService: XposedService? = null
    private lateinit var aboutPreference: MorpheAboutPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                onBackPressed()
            }
        }
        setContentView(R.layout.activity_settings)
        actionBar?.setDisplayShowHomeEnabled(true)

        Utils.setContext(this)
        aboutPreference = MorpheAboutPreference(this).apply {
            setTitle(R.string.about_title)
        }

        if (savedInstanceState != null) return

        fragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.xp_settings_menu, menu)
        menu.findItem(R.id.menu_disable_auto_check).isVisible = false
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val aliasName = ComponentName(this, SettingsActivity::class.java.name + "Alias")
        menu.findItem(R.id.menu_hide_icon).isChecked =
            packageManager.getComponentEnabledSetting(aliasName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val menuDisableAutoCheck = menu.findItem(R.id.menu_disable_auto_check)
        try {
            val prefs = mService!!.getRemotePreferences("prefs")
            menuDisableAutoCheck.isChecked =
                prefs.getBoolean("disable_auto_check_update", false)
            menuDisableAutoCheck.isVisible = true
        } catch (_: Throwable) {
            menuDisableAutoCheck.isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                aboutPreference.onPreferenceClickListener?.onPreferenceClick(aboutPreference)
                true
            }

            R.id.menu_hide_icon -> {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                val aliasName = ComponentName(this, SettingsActivity::class.java.name + "Alias")
                val status = if (newChecked) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                packageManager.setComponentEnabledSetting(
                    aliasName,
                    status,
                    PackageManager.DONT_KILL_APP
                )
                true
            }

            R.id.menu_disable_auto_check -> {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                mService!!.getRemotePreferences("prefs")
                    .edit().putBoolean("disable_auto_check_update", newChecked).apply()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAndRemoveTask()
        exitProcess(0)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    class SettingsFragment : PreferenceFragment(), SettingApplication.ServiceStateListener {
        private var mService: XposedService? = null
        private var activated = false

        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            Utils.setContext(context)
            buildScreen()

            UpdateChecker().apply {
                setActivity(activity)
                autoCheckUpdate()
            }
        }

        private fun buildScreen() {
            val ctx = context ?: return
            val root = preferenceManager.createPreferenceScreen(ctx)

            root.addPreference(HaloHeroPreference(ctx).apply { setActive(activated) })
            root.addPreference(HaloLabelPreference(ctx, ctx.getString(R.string.patch_selection)))
            if (activated) {
                root.addPreference(
                    HaloNotePreference(ctx, ctx.getString(R.string.force_stop_to_apply_summary))
                )
                for (appPatchInfo in appPatchConfigurations) {
                    root.addPreference(HaloAppRowPreference(ctx, appPatchInfo).apply {
                        intent = Intent(ctx, AppPatchSettingsActivity::class.java).apply {
                            putExtra(AppPatchSettingsActivity.ARGUMENT_APP_NAME, appPatchInfo.appName)
                        }
                    })
                }
            } else {
                root.addPreference(
                    HaloNotePreference(ctx, ctx.getString(R.string.module_not_activated_summary))
                )
            }

            root.addPreference(HaloLabelPreference(ctx, ctx.getString(R.string.app_section)))

            root.addPreference(HaloRowPreference(ctx).apply {
                setTitle(R.string.check_for_update_title)
                summary = buildVersionSummary()
                setOnPreferenceClickListener {
                    UpdateChecker().apply {
                        setActivity(activity)
                        checkUpdate(silent = false)
                    }
                    true
                }
            })

            preferenceScreen = root

            // Adds the "Open-source licenses" row into the screen set above.
            addPreferencesFromResource(R.xml.license_prefs)

            root.addPreference(HaloRowPreference(ctx).apply {
                setTitle(R.string.morphe_credit_title)
                summary = "https://morphe.software"
                intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://morphe.software"))
            })

            root.addPreference(
                HaloNotePreference(ctx, ctx.getString(R.string.slogan_summary))
            )
        }

        private fun buildVersionSummary(): CharSequence {
            val buildDate = DateUtils.getRelativeTimeSpanString(BuildConfig.COMMIT_DATE * 1000)
            return """Current version: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_HASH}) ${BuildConfig.BUILD_TYPE}
                       |Build Date: $buildDate""".trimMargin()
        }

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

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread

                val isActivated: Boolean = if (service == null) {
                    false
                } else {
                    try {
                        service.getRemotePreferences("prefs")
                        service.apiVersion
                        true
                    } catch (_: Throwable) {
                        false
                    }
                }

                if (isActivated == activated) return@runOnUiThread
                activated = isActivated
                buildScreen()
            }
        }
    }
}
