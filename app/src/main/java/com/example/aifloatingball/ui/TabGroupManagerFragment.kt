package com.example.aifloatingball.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
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
                // 点击组，检查密码后切换到该组
                if (group.passwordHash != null) {
                    // 🔧 修复：加密组必须先验证密码，验证通过后才切换组
                    showPasswordVerificationDialog(group) { verified ->
                        if (verified) {
                            // 密码验证通过后，才切换组并显示标签页
                            groupManager.setCurrentGroup(group.id)
                            onGroupSelectedListener?.invoke(group)
                        }
                        // 如果验证失败，不执行任何操作，保持当前组不变
                    }
                } else {
                    // 无密码的组，直接切换
                    groupManager.setCurrentGroup(group.id)
                    onGroupSelectedListener?.invoke(group)
                }
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
            },
            onGroupSecurity = { group ->
                // 密码设置
                showGroupSecurityDialog(group)
            },
            onGroupVisibility = { group ->
                // 隐藏/显示组
                groupManager.toggleGroupHidden(group.id)
                refreshGroups()
                Toast.makeText(requireContext(), if (group.isHidden) "组已显示" else "组已隐藏", Toast.LENGTH_SHORT).show()
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
        
        // 添加激活隐藏组按钮（在标题栏添加一个菜单按钮）
        // 可以通过长按标题栏或添加一个菜单按钮来激活隐藏组
        view.findViewById<TextView>(R.id.text_group_manager_title)?.setOnLongClickListener {
            showActivateHiddenGroupDialog()
            true
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
        // 🔧 新功能：如果组有密码，删除前需要验证密码
        if (group.passwordHash != null) {
            val passwordInput = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "请输入密码以删除此组"
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle("删除加密组")
                .setMessage("组「${group.name}」已加密，删除前需要验证密码。组内的所有标签页将被删除。")
                .setView(passwordInput)
                .setPositiveButton("删除") { _, _ ->
                    val password = passwordInput.text.toString()
                    if (password.isEmpty()) {
                        Toast.makeText(requireContext(), "密码不能为空", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    // 验证密码
                    if (groupManager.verifyGroupPassword(group.id, password)) {
                        if (groupManager.deleteGroup(group.id)) {
                            refreshGroups()
                            Toast.makeText(requireContext(), "组已删除", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "密码错误，无法删除", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            // 没有密码的组，直接删除
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
    
    /**
     * 显示密码验证对话框
     */
    private fun showPasswordVerificationDialog(group: TabGroup, onVerified: (Boolean) -> Unit) {
        val passwordInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入密码"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("输入密码")
            .setMessage("组「${group.name}」已加密，请输入密码")
            .setView(passwordInput)
            .setPositiveButton("确定") { _, _ ->
                val password = passwordInput.text.toString()
                val verified = groupManager.verifyGroupPassword(group.id, password) ||
                               groupManager.verifyQuickAccessCode(group.id, password)
                if (verified) {
                    onVerified(true)
                    Toast.makeText(requireContext(), "密码正确", Toast.LENGTH_SHORT).show()
                } else {
                    onVerified(false)
                    Toast.makeText(requireContext(), "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消") { _, _ ->
                onVerified(false)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示组安全设置对话框
     */
    private fun showGroupSecurityDialog(group: TabGroup) {
        val items = mutableListOf<String>()
        if (group.passwordHash != null) {
            items.add("修改密码")
            items.add("移除密码")
        } else {
            items.add("设置密码")
        }
        items.add(if (group.isHidden) "显示组" else "隐藏组")
        if (group.passwordHash != null) {
            items.add("设置快捷访问码")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("组安全设置 - ${group.name}")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "设置密码" -> showSetPasswordDialog(group)
                    "修改密码" -> showChangePasswordDialog(group)
                    "移除密码" -> showRemovePasswordDialog(group)
                    "隐藏组" -> {
                        groupManager.toggleGroupHidden(group.id)
                        refreshGroups()
                        Toast.makeText(requireContext(), "组已隐藏", Toast.LENGTH_SHORT).show()
                    }
                    "显示组" -> {
                        groupManager.toggleGroupHidden(group.id)
                        refreshGroups()
                        Toast.makeText(requireContext(), "组已显示", Toast.LENGTH_SHORT).show()
                    }
                    "设置快捷访问码" -> showSetQuickAccessCodeDialog(group)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示设置密码对话框
     */
    private fun showSetPasswordDialog(group: TabGroup) {
        val passwordInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入密码"
        }
        val confirmInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请确认密码"
        }
        
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(passwordInput)
            addView(confirmInput)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("设置密码")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val password = passwordInput.text.toString()
                val confirm = confirmInput.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(requireContext(), "密码不能为空", Toast.LENGTH_SHORT).show()
                } else if (password != confirm) {
                    Toast.makeText(requireContext(), "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                } else {
                    groupManager.setGroupPassword(group.id, password)
                    refreshGroups()
                    Toast.makeText(requireContext(), "密码已设置", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示修改密码对话框
     */
    private fun showChangePasswordDialog(group: TabGroup) {
        val oldPasswordInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入原密码"
        }
        val newPasswordInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入新密码"
        }
        val confirmInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请确认新密码"
        }
        
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(oldPasswordInput)
            addView(newPasswordInput)
            addView(confirmInput)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("修改密码")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val oldPassword = oldPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirm = confirmInput.text.toString()
                
                if (!groupManager.verifyGroupPassword(group.id, oldPassword)) {
                    Toast.makeText(requireContext(), "原密码错误", Toast.LENGTH_SHORT).show()
                } else if (newPassword.isEmpty()) {
                    Toast.makeText(requireContext(), "新密码不能为空", Toast.LENGTH_SHORT).show()
                } else if (newPassword != confirm) {
                    Toast.makeText(requireContext(), "两次输入的新密码不一致", Toast.LENGTH_SHORT).show()
                } else {
                    groupManager.setGroupPassword(group.id, newPassword)
                    refreshGroups()
                    Toast.makeText(requireContext(), "密码已修改", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示移除密码对话框
     */
    private fun showRemovePasswordDialog(group: TabGroup) {
        val passwordInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入密码确认"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("移除密码")
            .setMessage("确定要移除组「${group.name}」的密码保护吗？")
            .setView(passwordInput)
            .setPositiveButton("确定") { _, _ ->
                val password = passwordInput.text.toString()
                if (groupManager.verifyGroupPassword(group.id, password)) {
                    groupManager.removeGroupPassword(group.id)
                    refreshGroups()
                    Toast.makeText(requireContext(), "密码已移除", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示设置快捷访问码对话框
     */
    private fun showSetQuickAccessCodeDialog(group: TabGroup) {
        val codeInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入快捷访问码（特殊密码）"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("设置快捷访问码")
            .setMessage("快捷访问码是一个特殊的密码，可以用来快速访问加密的组")
            .setView(codeInput)
            .setPositiveButton("确定") { _, _ ->
                val code = codeInput.text.toString()
                if (code.isEmpty()) {
                    Toast.makeText(requireContext(), "访问码不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    groupManager.setQuickAccessCode(group.id, code)
                    Toast.makeText(requireContext(), "快捷访问码已设置", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示激活隐藏组对话框
     */
    private fun showActivateHiddenGroupDialog() {
        val passwordInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入密码或快捷访问码"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("激活隐藏组")
            .setMessage("请输入隐藏组的密码或快捷访问码来激活并显示该组")
            .setView(passwordInput)
            .setPositiveButton("激活") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入密码或快捷访问码", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 查找所有隐藏的组
                val allGroups = groupManager.getAllGroupsIncludingHidden()
                val hiddenGroups = allGroups.filter { it.isHidden }
                
                // 尝试匹配密码或快捷访问码
                var activated = false
                for (group in hiddenGroups) {
                    if (groupManager.verifyGroupPassword(group.id, password) || 
                        groupManager.verifyQuickAccessCode(group.id, password)) {
                        // 激活组：显示并切换到该组
                        groupManager.toggleGroupHidden(group.id)
                        groupManager.setCurrentGroup(group.id)
                        refreshGroups()
                        onGroupSelectedListener?.invoke(group)
                        Toast.makeText(requireContext(), "已激活组：${group.name}", Toast.LENGTH_SHORT).show()
                        activated = true
                        break
                    }
                }
                
                if (!activated) {
                    Toast.makeText(requireContext(), "密码或快捷访问码错误，或没有匹配的隐藏组", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

