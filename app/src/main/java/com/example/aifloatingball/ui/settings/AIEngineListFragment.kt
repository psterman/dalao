package com.example.aifloatingball.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.adapter.GenericSearchEngineAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.service.DualFloatingWebViewService

class AIEngineListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GenericSearchEngineAdapter<AISearchEngine>
    private lateinit var settingsManager: SettingsManager
    private var engines: List<AISearchEngine> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())
        arguments?.let {
            engines = it.getParcelableArrayList("engines") ?: emptyList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Dynamically create the RecyclerView to bypass any XML inflation or caching issues.
        recyclerView = RecyclerView(requireContext()).apply {
            id = R.id.recyclerViewSearchEngines // Keep the ID for consistency, though not strictly needed here
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setupRecyclerView()
        return recyclerView
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        
        val enabledEngines = settingsManager.getEnabledAIEngines().toMutableSet()

        adapter = GenericSearchEngineAdapter(
            context = requireContext(),
            engines = engines,
            enabledEngines = enabledEngines,
            onEngineToggled = { engineName, isEnabled ->
                val allEnabledEngines = settingsManager.getEnabledAIEngines().toMutableSet()
                if (isEnabled) {
                    allEnabledEngines.add(engineName)
                } else {
                    allEnabledEngines.remove(engineName)
                }
                settingsManager.saveEnabledAIEngines(allEnabledEngines)
                requireContext().sendBroadcast(Intent(DualFloatingWebViewService.ACTION_UPDATE_AI_ENGINES))
            }
        )
        recyclerView.adapter = adapter
    }

    fun getEnabledEngines(): List<AISearchEngine> {
        return adapter.getEnabledEngines()
    }

    companion object {
        fun newInstance(engines: List<AISearchEngine>): AIEngineListFragment {
            val fragment = AIEngineListFragment()
            val args = Bundle()
            args.putParcelableArrayList("engines", ArrayList(engines))
            fragment.arguments = args
            return fragment
        }
    }
} 