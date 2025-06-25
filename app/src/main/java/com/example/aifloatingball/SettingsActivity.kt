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

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, SearchView.OnQueryTextListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var searchManager: SettingsSearchManager
    private lateinit var resultsAdapter: SettingsSearchResultAdapter
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var settingsContainer: View
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager = SettingsManager.getInstance(this)
        settingsManager.registerOnSharedPreferenceChangeListener(this)

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
        if (settingsManager.isLeftHandModeEnabled()) {
            resultsRecyclerView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else {
            resultsRecyclerView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        setupRecyclerView()
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == "left_handed_mode") {
            recreate()
        }
    }

    abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val settingsManager = SettingsManager.getInstance(requireContext())
            if (settingsManager.isLeftHandModeEnabled()) {
                listView.layoutDirection = View.LAYOUT_DIRECTION_RTL
            } else {
                listView.layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
        }
    }

    class SettingsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
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
}