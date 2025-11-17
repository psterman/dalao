package com.example.aifloatingball.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
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
    private var engines: MutableList<AISearchEngine> = mutableListOf()
    private var categoryName: String = ""
    private lateinit var disableAllButton: Button
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())
        arguments?.let {
            val engineList = it.getParcelableArrayList<AISearchEngine>("engines") ?: emptyList()
            engines.clear()
            engines.addAll(engineList)
            categoryName = it.getString("category", "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_ai_engine_list_with_button, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerViewSearchEngines)
        disableAllButton = rootView.findViewById(R.id.btnDisableAll)
        
        setupRecyclerView()
        setupDisableAllButton()
        
        return rootView
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        
        // 加载保存的排序顺序
        val savedOrder = settingsManager.getAIEngineOrder(categoryName, engines.map { it.name })
        val orderedEngines = mutableListOf<AISearchEngine>()
        // 先按保存的顺序排列
        savedOrder.forEach { engineName ->
            engines.find { it.name == engineName }?.let { orderedEngines.add(it) }
        }
        // 添加未在排序中的新引擎
        engines.forEach { engine ->
            if (!orderedEngines.any { it.name == engine.name }) {
                orderedEngines.add(engine)
            }
        }
        engines.clear()
        engines.addAll(orderedEngines)
        
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
            },
            onOrderChanged = { orderedList ->
                // 保存排序顺序
                settingsManager.saveAIEngineOrder(categoryName, orderedList.map { it.name })
            }
        )
        recyclerView.adapter = adapter
        
        // 设置拖拽排序
        setupDragAndDrop()
    }
    
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }
        }
        
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }
    
    private fun setupDisableAllButton() {
        disableAllButton.setOnClickListener {
            // 显示确认对话框
            AlertDialog.Builder(requireContext())
                .setTitle("确认关闭")
                .setMessage("确定要关闭当前分类下的所有AI引擎吗？")
                .setPositiveButton("确定") { _, _ ->
                    // 关闭当前分类下的所有引擎
                    val allEnabledEngines = settingsManager.getEnabledAIEngines().toMutableSet()
                    engines.forEach { engine ->
                        allEnabledEngines.remove(engine.name)
                    }
                    settingsManager.saveEnabledAIEngines(allEnabledEngines)
                    requireContext().sendBroadcast(Intent(DualFloatingWebViewService.ACTION_UPDATE_AI_ENGINES))
                    
                    // 更新适配器
                    adapter.updateEngines(engines)
                    Toast.makeText(requireContext(), "已关闭所有引擎", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    fun getEnabledEngines(): List<AISearchEngine> {
        return adapter.getEnabledEngines()
    }

    companion object {
        fun newInstance(engines: List<AISearchEngine>, category: String = ""): AIEngineListFragment {
            val fragment = AIEngineListFragment()
            val args = Bundle()
            args.putParcelableArrayList("engines", ArrayList(engines))
            args.putString("category", category)
            fragment.arguments = args
            return fragment
        }
    }
} 