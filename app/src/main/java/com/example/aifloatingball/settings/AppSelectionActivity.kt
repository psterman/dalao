package com.example.aifloatingball.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
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
    private val filteredAppList = mutableListOf<AppItem>()
    private val groupedItems = mutableListOf<ListItem>()
    private lateinit var sharedPreferences: SharedPreferences
    private val selectedAppsKey = "selected_notification_apps"
    private var isAllSelected = false

    private val VIEW_TYPE_HEADER = 0
    private val VIEW_TYPE_APP = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.titleText.text = "选择应用"

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        setupControls()
        setupRecyclerView()
        loadApps()
    }

    private fun setupControls() {
        binding.selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            isAllSelected = isChecked
            appList.forEach { it.isSelected = isChecked }
            regroupItems()
            appAdapter.notifyDataSetChanged()
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter(groupedItems) { appItem ->
            appItem.isSelected = !appItem.isSelected
            updateSelectAllCheckbox()
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
            
            appList.sortBy { it.name.lowercase() }
            
            regroupItems()
            
            binding.progressBar.visibility = View.GONE
            binding.appsRecyclerView.visibility = View.VISIBLE
            
            updateSelectAllCheckbox()
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
    }

    private fun filterApps(query: String) {
        if (query.isBlank()) {
            filteredAppList.clear()
            filteredAppList.addAll(appList)
        } else {
            filteredAppList.clear()
            filteredAppList.addAll(appList.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.packageName.contains(query, ignoreCase = true) 
            })
        }
        
        regroupItems()
        
        updateSelectAllCheckbox()
    }

    private fun regroupItems() {
        groupedItems.clear()
        
        if (filteredAppList.isEmpty()) {
            filteredAppList.clear()
            filteredAppList.addAll(appList)
        }
        
        var currentLetter: String? = null
        
        filteredAppList.forEach { app ->
            val firstLetter = app.name.firstOrNull()?.uppercase() ?: "#"
            
            if (currentLetter != firstLetter) {
                currentLetter = firstLetter
                groupedItems.add(HeaderItem(firstLetter))
            }
            
            groupedItems.add(app)
        }
        
        appAdapter.notifyDataSetChanged()
    }

    private fun updateSelectAllCheckbox() {
        if (appList.isEmpty()) {
            binding.selectAllCheckbox.isChecked = false
            return
        }
        
        val allSelected = appList.all { it.isSelected }
        if (binding.selectAllCheckbox.isChecked != allSelected) {
            binding.selectAllCheckbox.isChecked = allSelected
        }
        isAllSelected = allSelected
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

    sealed interface ListItem

    data class HeaderItem(val letter: String) : ListItem

    data class AppItem(
        val name: String,
        val packageName: String,
        val icon: Drawable,
        var isSelected: Boolean = false
    ) : ListItem

    private inner class AppAdapter(
        private val items: List<ListItem>,
        private val onAppClick: (AppItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_header, parent, false)
                    HeaderViewHolder(view)
                }
                VIEW_TYPE_APP -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
                    AppViewHolder(view)
                }
                else -> throw IllegalArgumentException("Invalid view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is HeaderItem -> (holder as HeaderViewHolder).bind(item)
                is AppItem -> (holder as AppViewHolder).bind(item)
            }
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is HeaderItem -> VIEW_TYPE_HEADER
                is AppItem -> VIEW_TYPE_APP
            }
        }

        inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val letterTextView: TextView = itemView.findViewById(R.id.header_letter)

            fun bind(item: HeaderItem) {
                letterTextView.text = item.letter
            }
        }

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val appNameTextView: TextView = itemView.findViewById(R.id.app_name)
            private val packageNameTextView: TextView = itemView.findViewById(R.id.package_name)
            private val appIconImageView: ImageView = itemView.findViewById(R.id.app_icon)
            private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox)

            init {
                itemView.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = items[position]
                        if (item is AppItem) {
                            onAppClick(item)
                        }
                    }
                }
                
                checkBox.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = items[position]
                        if (item is AppItem) {
                            onAppClick(item)
                        }
                    }
                }
            }

            fun bind(item: AppItem) {
                appNameTextView.text = item.name
                packageNameTextView.text = item.packageName
                appIconImageView.setImageDrawable(item.icon)
                checkBox.isChecked = item.isSelected
            }
        }
    }
} 