package com.example.aifloatingball.preference

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class SearchEnginePickerDialogFragment : DialogFragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: SearchEngineAdapter
    
    private var currentEngineId: String? = null
    private var selectedEngineIdInDialog: String = ""
    private var isAIMode: Boolean = false
    private var listener: ((String) -> Unit)? = null

    companion object {
        private const val ARG_CURRENT_ENGINE_ID = "current_engine_id"

        fun newInstance(currentEngineId: String, onSelected: (String) -> Unit): SearchEnginePickerDialogFragment {
            return SearchEnginePickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_ENGINE_ID, currentEngineId)
                }
                listener = onSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        currentEngineId = arguments?.getString(ARG_CURRENT_ENGINE_ID) ?: "baidu"
        selectedEngineIdInDialog = currentEngineId!!
        isAIMode = selectedEngineIdInDialog.startsWith("ai_")

        val view = layoutInflater.inflate(R.layout.dialog_search_engine_picker, null)
        
        tabLayout = view.findViewById(R.id.tabLayout) ?: run { 
            Log.e("SearchEnginePickerDialog", "TabLayout with ID R.id.tabLayout not found in dialog_search_engine_picker.xml")
            dismiss()
            return MaterialAlertDialogBuilder(requireContext()).create() 
        }
        recyclerView = view.findViewById(R.id.recyclerView) ?: run {
            Log.e("SearchEnginePickerDialog", "RecyclerView with ID R.id.recyclerView not found in dialog_search_engine_picker.xml")
            dismiss()
            return MaterialAlertDialogBuilder(requireContext()).create()
        }

        setupTabLayout()
        setupRecyclerView(requireContext())
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_search_engine)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                listener?.invoke(selectedEngineIdInDialog)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.normal_search))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.ai_search))
        tabLayout.selectTab(tabLayout.getTabAt(if (isAIMode) 1 else 0))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                isAIMode = tab.position == 1
                listAdapter.updateEngines(getEngineList(isAIMode))
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun setupRecyclerView(context: Context) {
        listAdapter = SearchEngineAdapter(
            context,
            getEngineList(isAIMode),
            selectedEngineIdInDialog
        ) { engineId ->
            selectedEngineIdInDialog = engineId
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
    }

    private fun getEngineList(isAI: Boolean): List<EngineItem> {
        return if (isAI) {
            AISearchEngine.DEFAULT_AI_ENGINES.map { 
                EngineItem("ai_${it.name}", it.displayName, it.iconResId, true)
            }
        } else {
            SearchEngine.DEFAULT_ENGINES.map { 
                EngineItem(it.name, it.displayName, it.iconResId, false)
            }
        }
    }

    data class EngineItem(
        val id: String,
        val displayName: String,
        val iconResId: Int,
        val isAi: Boolean
    )

    private inner class SearchEngineAdapter(
        private val context: Context,
        private var engines: List<EngineItem>,
        private var currentSelectedEngineId: String,
        private val onItemSelected: (String) -> Unit
    ) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

        fun updateEngines(newEngines: List<EngineItem>) {
            engines = newEngines
            if (engines.none { it.id == currentSelectedEngineId }) {
                currentSelectedEngineId = newEngines.firstOrNull()?.id ?: ""
                onItemSelected(currentSelectedEngineId)
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.engineIcon)
            val name: TextView = view.findViewById(R.id.engineName)
            val radioButton: RadioButton = view.findViewById(R.id.radioButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_search_engine_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val engine = engines[position]
            if (engine.iconResId != 0) {
                holder.icon.setImageResource(engine.iconResId)
            } else {
                // Provide a generic default icon if specific one is missing
                holder.icon.setImageResource(R.drawable.ic_default_search_engine) 
            }
            holder.name.text = engine.displayName
            holder.radioButton.isChecked = engine.id == currentSelectedEngineId

            holder.itemView.setOnClickListener {
                val oldSelectedId = currentSelectedEngineId
                if (oldSelectedId != engine.id) {
                    currentSelectedEngineId = engine.id
                    onItemSelected(engine.id)
                    notifyItemChanged(engines.indexOfFirst { it.id == oldSelectedId })
                    notifyItemChanged(position)
                }
            }
        }

        override fun getItemCount() = engines.size
    }
} 