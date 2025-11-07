package com.example.aifloatingball.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.GroupItemTouchHelperCallback
import com.example.aifloatingball.adapter.TabGroupAdapter
import com.example.aifloatingball.manager.TabGroupManager
import com.example.aifloatingball.model.TabGroup

/**
 * 标签页组管理Fragment
 * 显示所有组，支持编辑、删除、置顶、拖动排序
 */
class TabGroupManagerFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TabGroupAdapter
    private lateinit var groupManager: TabGroupManager
    
    private var onGroupSelectedListener: ((TabGroup) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tab_group_manager, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        groupManager = TabGroupManager.getInstance(requireContext())
        
        recyclerView = view.findViewById(R.id.recycler_view_groups)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = TabGroupAdapter(
            groups = groupManager.getAllGroups().toMutableList(),
            onGroupClick = { group ->
                // 点击组，切换到该组
                groupManager.setCurrentGroup(group.id)
                onGroupSelectedListener?.invoke(group)
            },
            onGroupEdit = { group ->
                // 编辑组名
                showEditGroupDialog(group)
            },
            onGroupDelete = { group ->
                // 删除组
                showDeleteGroupDialog(group)
            },
            onGroupPin = { group ->
                // 置顶/取消置顶
                groupManager.togglePinGroup(group.id)
                refreshGroups()
            },
            onGroupDrag = { fromPosition, toPosition ->
                // 拖动排序
                val groups = groupManager.getAllGroups()
                val groupIds = groups.map { it.id }.toMutableList()
                val fromId = groupIds.removeAt(fromPosition)
                groupIds.add(toPosition, fromId)
                groupManager.updateGroupOrder(groupIds)
                refreshGroups()
            }
        )
        
        recyclerView.adapter = adapter
        
        // 设置拖动排序
        val itemTouchHelper = ItemTouchHelper(GroupItemTouchHelperCallback(adapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
        
        // 监听组变化
        groupManager.addGroupChangeListener {
            refreshGroups()
        }
        
        // 添加新建组按钮
        view.findViewById<View>(R.id.btn_add_group)?.setOnClickListener {
            showCreateGroupDialog()
        }
    }
    
    /**
     * 设置组选择监听器
     */
    fun setOnGroupSelectedListener(listener: (TabGroup) -> Unit) {
        onGroupSelectedListener = listener
    }
    
    /**
     * 刷新组列表
     */
    private fun refreshGroups() {
        val groups = groupManager.getAllGroups()
        adapter.updateGroups(groups)
    }
    
    /**
     * 显示创建组对话框
     */
    private fun showCreateGroupDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "请输入组名"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("创建新组")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val groupName = input.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    val newGroup = groupManager.createGroup(groupName)
                    groupManager.setCurrentGroup(newGroup.id)
                    refreshGroups()
                    Toast.makeText(requireContext(), "组已创建", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "组名不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示编辑组对话框
     */
    private fun showEditGroupDialog(group: TabGroup) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(group.name)
            hint = "请输入组名"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("编辑组名")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val groupName = input.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    groupManager.updateGroup(group.id, name = groupName)
                    refreshGroups()
                    Toast.makeText(requireContext(), "组名已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "组名不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示删除组对话框
     */
    private fun showDeleteGroupDialog(group: TabGroup) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除组")
            .setMessage("确定要删除组「${group.name}」吗？组内的所有标签页将被删除。")
            .setPositiveButton("删除") { _, _ ->
                if (groupManager.deleteGroup(group.id)) {
                    refreshGroups()
                    Toast.makeText(requireContext(), "组已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

