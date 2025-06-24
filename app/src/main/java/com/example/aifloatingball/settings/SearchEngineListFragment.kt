package com.example.aifloatingball.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineCategory
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import java.util.*

class SearchEngineListFragment : Fragment(R.layout.fragment_search_engine_list) {

    private lateinit var settingsManager: SettingsManager
    private lateinit var category: SearchEngineCategory
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchEngineAdapter
    private val gson = Gson()

    private var allEngines = mutableListOf<SearchEngine>()
    private var enginesForCategory = mutableListOf<SearchEngine>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())
        category = arguments?.getSerializable(ARG_CATEGORY) as? SearchEngineCategory ?: SearchEngineCategory.GENERAL

        setFragmentResultListener(EditEngineDialogFragment.REQUEST_KEY) { _, bundle ->
            val engineJson = bundle.getString(EditEngineDialogFragment.RESULT_KEY_ENGINE_JSON)
            engineJson?.let {
                val newOrUpdatedEngine = gson.fromJson(it, SearchEngine::class.java)
                Log.d(TAG, "Received engine: ${newOrUpdatedEngine.displayName} for category ${newOrUpdatedEngine.category}")

                val existingIndex = allEngines.indexOfFirst { e -> e.name == newOrUpdatedEngine.name }

                if (existingIndex != -1) {
                    allEngines[existingIndex] = newOrUpdatedEngine
                } else {
                    allEngines.add(newOrUpdatedEngine)
                }
                saveAndReload()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.search_engine_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadSearchEngines()

        adapter = SearchEngineAdapter(enginesForCategory, settingsManager) { engine ->
            showEditDialog(engine)
        }
        recyclerView.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(0, 0) { // Default to no-op
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION || position >= enginesForCategory.size) {
                    return 0
                }
                val engine = enginesForCategory[position]
                // Only allow drag/swipe for custom engines
                return if (engine.isCustom) {
                    val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    val swipeFlags = ItemTouchHelper.LEFT
                    makeMovementFlags(dragFlags, swipeFlags)
                } else {
                    0 // No movement
                }
            }
            
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                Collections.swap(enginesForCategory, fromPosition, toPosition)
                adapter.notifyItemMoved(fromPosition, toPosition)
                updateAndSaveFullList()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val removedEngine = enginesForCategory.removeAt(position)
                adapter.notifyItemRemoved(position)
                updateAndSaveFullList()

                Snackbar.make(recyclerView, "${removedEngine.displayName} 已删除", Snackbar.LENGTH_LONG)
                    .setAction("撤销") {
                        enginesForCategory.add(position, removedEngine)
                        adapter.notifyItemInserted(position)
                        updateAndSaveFullList()
                    }.show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun loadSearchEngines() {
        allEngines = settingsManager.getAllSearchEngines()
        enginesForCategory.clear()
        // Filter engines for the current category, show both default and custom
        enginesForCategory.addAll(allEngines.filter { it.category == category })
    }
    
    private fun updateAndSaveFullList() {
        // This logic correctly rebuilds the master list from the modified sub-list
        val otherCategoryEngines = allEngines.filter { it.category != category }
        val newList = mutableListOf<SearchEngine>()
        newList.addAll(otherCategoryEngines)
        newList.addAll(enginesForCategory)
        
        // Persist the full, updated list
        settingsManager.saveCustomSearchEngines(newList)
        // Update our local copy
        allEngines = newList
    }

    private fun saveAndReload() {
        // First, save the current state of the master list
        settingsManager.saveCustomSearchEngines(allEngines)
        // Then, reload and filter for the adapter
        loadSearchEngines()
        adapter.updateData(enginesForCategory)
    }

    fun showEditDialog(engine: SearchEngine?) {
        // When adding a new engine (engine is null), pass the current category
        val dialog = EditEngineDialogFragment.newInstance(engine, category)
        dialog.show(childFragmentManager, EditEngineDialogFragment.TAG)
    }

    companion object {
        private const val TAG = "SearchEngineListFragment"
        private const val ARG_CATEGORY = "category"

        fun newInstance(category: SearchEngineCategory): SearchEngineListFragment {
            val fragment = SearchEngineListFragment()
            val args = Bundle()
            args.putSerializable(ARG_CATEGORY, category)
            fragment.arguments = args
            return fragment
        }
    }
}
// We need to create the SearchEngineAdapter class as well.
// It will be a standard RecyclerView.Adapter. 