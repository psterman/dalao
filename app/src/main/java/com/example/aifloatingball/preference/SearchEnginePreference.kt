package com.example.aifloatingball.preference

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
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
        summaryProvider = SimpleSummaryProvider.getInstance()
    }

    override fun onClick() {
        val currentVal = value ?: settingsManager.getDefaultSearchEngine()
        
        val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager 
            ?: (context as? AppCompatActivity)?.supportFragmentManager

        if (fragmentManager != null) {
            val dialog = SearchEnginePickerDialogFragment.newInstance(currentVal) { newValue ->
                value = newValue
            }
            dialog.show(fragmentManager, "SearchEnginePickerDialogFragment")
        } else {
            // Fallback or error handling if FragmentManager is not available
            // This case should ideally not happen if preferences are hosted in a FragmentActivity/AppCompatActivity
        }
    }

    var value: String?
        get() = getPersistedString(settingsManager.getDefaultSearchEngine())
        set(value) {
            if (callChangeListener(value)) {
                persistString(value)
            }
        }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
    }

    class SimpleSummaryProvider private constructor() : SummaryProvider<SearchEnginePreference> {
        override fun provideSummary(preference: SearchEnginePreference): CharSequence {
            val engineId = preference.value ?: preference.settingsManager.getDefaultSearchEngine()
            val engine = preference.settingsManager.getSearchEngineById(engineId)
            return when (engine) {
                is AISearchEngine -> engine.name
                is SearchEngine -> engine.displayName
                else -> engineId
            }
        }

        companion object {
            private var sSimpleSummaryProvider: SimpleSummaryProvider? = null
            fun getInstance(): SimpleSummaryProvider {
                if (sSimpleSummaryProvider == null) {
                    sSimpleSummaryProvider = SimpleSummaryProvider()
                }
                return sSimpleSummaryProvider!!
            }
        }
    }
} 