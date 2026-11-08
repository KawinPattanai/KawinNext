package io.github.kawinnext.activity.preference

import android.content.Context
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import io.github.kawinnext.AppPatchInfo
import io.github.kawinnext.BuildConfig
import io.github.kawinnext.R

/**
 * Rows of the Halo settings UI. Every row keeps the preference framework's
 * title/summary contract (`@android:id/title`, `@android:id/summary`) so the
 * framework still fills them; only the surround is drawn by these layouts.
 */
private fun Context.haloColor(resId: Int) = getColor(resId)

/** A small uppercase mono label that opens a section, e.g. "PATCH SELECTION". */
class HaloLabelPreference(context: Context, private val label: String) : Preference(context) {
    init {
        layoutResource = R.layout.preference_label
        widgetLayoutResource = 0
        isSelectable = false
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        view.findViewById<TextView>(R.id.label_text).text = label
    }
}

/** Logo, module state chip and version line at the top of the settings screen. */
class HaloHeroPreference(context: Context) : Preference(context) {
    init {
        layoutResource = R.layout.preference_hero
        widgetLayoutResource = 0
        isSelectable = false
    }

    private var active = false

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        notifyChanged()
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        view.findViewById<TextView>(R.id.hero_status).apply {
            text = context.getString(if (active) R.string.hero_status_active else R.string.hero_status_inactive)
            setTextColor(context.haloColor(if (active) R.color.halo_mint else R.color.halo_text_faint))
        }
        view.findViewById<View>(R.id.hero_dot).apply {
            visibility = if (active) View.VISIBLE else View.INVISIBLE
        }
        view.findViewById<TextView>(R.id.hero_version).text = context.getString(
            R.string.hero_version_format, BuildConfig.VERSION_NAME, BuildConfig.PATCH_VERSION
        )
    }
}

/** One app on the settings screen: monogram tile, name, package id, patch count. */
class HaloAppRowPreference(context: Context, val app: AppPatchInfo) : Preference(context) {
    init {
        layoutResource = R.layout.preference_app_row
        widgetLayoutResource = 0
        key = app.appName
        title = app.appName
        summary = app.packageName
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        view.findViewById<TextView>(R.id.row_tile).text = monogram(app.appName)
        view.findViewById<TextView>(R.id.row_badge).text = countPatches(app).toString()
    }

    private fun countPatches(app: AppPatchInfo) = app.patches.count {
        it.name.isNotEmpty() && !it.name.startsWith("<")
    }

    private fun monogram(name: String): String {
        val words = name.split(' ').filter { it.isNotEmpty() }
        return if (words.size > 1) {
            words.take(2).joinToString("") { it.first().uppercase() }
        } else {
            name.take(2).uppercase()
        }
    }
}

/** A tappable row with a chevron: FAQ, licenses, update check. */
class HaloRowPreference(context: Context) : Preference(context) {
    init {
        layoutResource = R.layout.preference_row
        widgetLayoutResource = 0
    }
}

/** Plain prose under a section: the activation warning and the disclaimer. */
class HaloNotePreference(context: Context, var text: CharSequence = "") : Preference(context) {
    init {
        layoutResource = R.layout.preference_note
        widgetLayoutResource = 0
        isSelectable = false
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        view.findViewById<TextView>(R.id.note_text).text = text
    }
}

/** Header of the patch screen: enabled counter, progress line, bulk actions. */
class HaloPatchHeaderPreference(
    context: Context,
    private val showAppInfo: Boolean,
    private val onDefault: () -> Unit,
    private val onNone: () -> Unit,
    private val onAppInfo: () -> Unit,
) : Preference(context) {
    init {
        layoutResource = R.layout.preference_patch_header
        widgetLayoutResource = 0
        isSelectable = false
    }

    private var enabled = 0
    private var total = 0

    fun setCounts(enabled: Int, total: Int) {
        if (this.enabled == enabled && this.total == total) return
        this.enabled = enabled
        this.total = total
        notifyChanged()
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        view.findViewById<TextView>(R.id.header_count).text = context.getString(
            R.string.patches_enabled_format, enabled, total
        )
        view.findViewById<ProgressBar>(R.id.header_progress).progress =
            if (total == 0) 0 else enabled * 100 / total
        view.findViewById<TextView>(R.id.header_default).setOnClickListener { onDefault() }
        view.findViewById<TextView>(R.id.header_none).setOnClickListener { onNone() }
        view.findViewById<TextView>(R.id.header_app_info).apply {
            visibility = if (showAppInfo) View.VISIBLE else View.GONE
            setOnClickListener { onAppInfo() }
        }
    }
}

/** Filter field above the patch list. */
class HaloSearchPreference(
    context: Context,
    private val onQuery: (String) -> Unit,
) : Preference(context) {
    init {
        layoutResource = R.layout.preference_search
        widgetLayoutResource = 0
        isSelectable = false
    }

    var query: String = ""
        private set

    override fun onBindView(view: View) {
        super.onBindView(view)
        val field = view.findViewById<EditText>(R.id.search_field)
        if (field.text.toString() != query) field.setText(query)
        if (field.tag == null) {
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString().orEmpty()
                    if (value == query) return
                    query = value
                    onQuery(value)
                }
            }
            field.tag = watcher
            field.addTextChangedListener(watcher)
        }
    }
}

/** A patch toggle: name and description on the left, Halo switch on the right. */
class HaloPatchPreference(context: Context) : CheckBoxPreference(context) {
    init {
        layoutResource = R.layout.preference_patch_row
        widgetLayoutResource = 0
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        view.findViewById<CheckBox>(R.id.patch_switch).isChecked = isChecked
    }
}
