package com.example.aifloatingball.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.databinding.ActivityAppSelectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSelectionBinding
    private lateinit var appAdapter: AppAdapter
    private val appList = mutableListOf<AppItem>()
    private lateinit var sharedPreferences: SharedPreferences
    private val selectedAppsKey = "selected_notification_apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        setupRecyclerView()
        loadApps()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter(appList) { appItem ->
            appItem.isSelected = !appItem.isSelected
            appAdapter.notifyDataSetChanged()
        }
        binding.appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AppSelectionActivity)
            adapter = appAdapter
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val loadedApps = loadInstalledApps(this@AppSelectionActivity)
            appList.clear()
            appList.addAll(loadedApps)
            appAdapter.notifyDataSetChanged()
            binding.progressBar.visibility = View.GONE
            binding.appsRecyclerView.visibility = View.VISIBLE
        }
    }

    private suspend fun loadInstalledApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(0)
        val savedSelectedApps = sharedPreferences.getStringSet(selectedAppsKey, emptySet()) ?: emptySet()

        installedApps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { appInfo ->
                AppItem(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    isSelected = savedSelectedApps.contains(appInfo.packageName)
                )
            }
            .sortedBy { it.name }
    }

    private fun saveSelectedApps() {
        val selectedPackageNames = appList
            .filter { it.isSelected }
            .map { it.packageName }
            .toSet()
        sharedPreferences.edit().putStringSet(selectedAppsKey, selectedPackageNames).apply()
    }

    override fun onPause() {
        super.onPause()
        saveSelectedApps()
    }
} 