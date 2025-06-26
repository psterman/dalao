package com.example.aifloatingball

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SettingsSearchResultAdapter
import com.example.aifloatingball.model.SearchableSetting
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.service.FloatingWindowService

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, SearchView.OnQueryTextListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var searchManager: SettingsSearchManager
    private lateinit var resultsAdapter: SettingsSearchResultAdapter
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var settingsContainer: View
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsManager = SettingsManager.getInstance(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager.registerOnSharedPreferenceChangeListener(this)
        applyLayoutDirection()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize search components
        searchManager = SettingsSearchManager(this)
        settingsContainer = findViewById(R.id.settings_container)
        resultsRecyclerView = findViewById(R.id.search_results_recycler_view)
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        updateDisplayMode()
    }

    private fun updateDisplayMode() {
        // This function is crucial for starting/stopping services based on settings.
        val intentBall = Intent(this, FloatingWindowService::class.java)
        val intentIsland = Intent(this, DynamicIslandService::class.java)
        stopService(intentBall)
        stopService(intentIsland)

        val displayMode = settingsManager.getDisplayMode()
        if (displayMode == "floating_ball" && settingsManager.isFloatingBallEnabled()) {
            startService(intentBall)
        } else if (displayMode == "dynamic_island" && settingsManager.isDynamicIslandEnabled()) {
            startService(intentIsland)
        } else if (displayMode == "both") {
            if (settingsManager.isFloatingBallEnabled()) {
                startService(intentBall)
            }
            if (settingsManager.isDynamicIslandEnabled()) {
                startService(intentIsland)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsManager.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun setupRecyclerView() {
        resultsAdapter = SettingsSearchResultAdapter(emptyList()) { setting ->
            // Hide search view
            val menu = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.menu
            menu?.findItem(R.id.action_search)?.collapseActionView()

            // Find the preference in the root fragment and perform a click
            val settingsFragment = supportFragmentManager.findFragmentById(R.id.settings_container) as? SettingsFragment
            settingsFragment?.findPreference<Preference>(setting.key)?.performClick()
            }
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = resultsAdapter
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = pref.title
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_history_menu, menu) // Re-using the same menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)
        searchView.queryHint = "搜索设置项..."

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                // Show search results, hide settings
                resultsRecyclerView.visibility = View.VISIBLE
                settingsContainer.visibility = View.GONE
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                // Hide search results, show settings
                resultsRecyclerView.visibility = View.GONE
                settingsContainer.visibility = View.VISIBLE
                return true
            }
        })
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false // Let the list filter handle it
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        val results = searchManager.search(newText.orEmpty())
        resultsAdapter.updateData(results)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            supportActionBar?.title = "设置" // Restore title on back press
            return true
        }
        onBackPressed()
        return true
    }

    private fun applyLayoutDirection() {
        if (settingsManager.isLeftHandedModeEnabled()) {
            window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else {
            window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "left_handed_mode" -> {
                applyLayoutDirection()
            }
            "display_mode", "floating_ball_enabled", "dynamic_island_enabled" -> {
                updateDisplayMode()
            }
        }
    }

    abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
        // No longer needed, as the Activity handles the layout direction globally.
    }

    class SettingsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            findPreference<Preference>("view_search_history")?.setOnPreferenceClickListener {
                val intent = Intent(activity, SearchHistoryActivity::class.java)
                startActivity(intent)
                true
            }
        }
    }

    // Define other fragments if they are inner classes
    class GeneralSettingsFragment : BaseSettingsFragment() {
         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.general_preferences, rootKey)
        }
    }
     class BallSettingsFragment : BaseSettingsFragment() {
         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.ball_preferences, rootKey)
        }
    }
     class FloatingWindowSettingsFragment : BaseSettingsFragment() {
         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.floating_window_preferences, rootKey)
        }
    }

    class ApiSettingsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.ai_api_preferences, rootKey)
        }
    }

    class MasterPromptSettingsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.master_prompt_preferences, rootKey)
        }
    }

    class WebSearchEngineManagerFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.search_engine_preferences, rootKey)
        }
    }
} 