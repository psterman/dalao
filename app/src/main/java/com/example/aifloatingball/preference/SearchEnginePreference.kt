package com.example.aifloatingball.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine

class SearchEnginePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private val settingsManager = SettingsManager.getInstance(context)

    init {
        isPersistent = true
    }

    override fun onClick() {
        SearchEnginePickerDialog(
            context = context,
            currentValue = value ?: "baidu",
            onEngineSelected = { newValue ->
                value = newValue
                notifyChanged()
            }
        ).show()
    }

    var value: String?
        get() = getPersistedString(null)
        set(value) {
            if (callChangeListener(value)) {
                persistString(value)
                updateSummary()
            }
        }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedString(defaultValue as? String)
        updateSummary()
    }

    private fun updateSummary() {
        val engine = settingsManager.getSearchEngineById(value ?: "baidu")
        summary = when (engine) {
            is AISearchEngine -> engine.name
            is SearchEngine -> engine.displayName
            else -> value
        }
    }
} 