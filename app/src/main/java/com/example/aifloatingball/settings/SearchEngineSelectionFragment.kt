package com.example.aifloatingball.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.CategoryAdapter
import com.example.aifloatingball.model.SearchEngineCategory

class SearchEngineSelectionFragment : Fragment() {

    private lateinit var categoryRecyclerView: RecyclerView
    private lateinit var categoryAdapter: CategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search_engine_selection_vertical, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryRecyclerView = view.findViewById(R.id.category_recycler_view)
        setupCategoryRecyclerView()

        // Load the first category by default
        if (savedInstanceState == null) {
            val initialCategory = SearchEngineCategory.values().first()
            selectCategory(initialCategory)
        }
    }

    private fun setupCategoryRecyclerView() {
        categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val categories = SearchEngineCategory.values().toList()
        categoryAdapter = CategoryAdapter(categories) { category ->
            selectCategory(category)
        }
        categoryRecyclerView.adapter = categoryAdapter
    }

    private fun selectCategory(category: SearchEngineCategory) {
        val fragment = SearchEngineListFragment.newInstance(category)
        childFragmentManager.beginTransaction()
            .replace(R.id.search_engine_list_container, fragment)
            .commit()
    }
} 