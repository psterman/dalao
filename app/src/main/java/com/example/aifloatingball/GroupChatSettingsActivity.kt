package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.GroupMemberAdapter
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.model.GroupChat
import com.example.aifloatingball.model.GroupMember
import com.example.aifloatingball.model.MemberType
import com.example.aifloatingball.manager.GroupChatManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GroupChatSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GroupChatSettingsActivity"
        const val EXTRA_GROUP_CONTACT = "extra_group_contact"
        private const val CONTACTS_PREFS_NAME = "chat_contacts"
        private const val KEY_SAVED_CONTACTS = "saved_contacts"
    }

    private lateinit var backButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var groupNameInput: TextInputEditText
    private lateinit var groupDescriptionInput: TextInputEditText
    private lateinit var addMemberButton: MaterialButton
    private lateinit var deleteGroupButton: MaterialButton
    private lateinit var leaveGroupButton: MaterialButton
    private lateinit var groupMembersList: RecyclerView

    private lateinit var groupMemberAdapter: GroupMemberAdapter
    private var currentGroupContact: ChatContact? = null
    private var currentGroupChat: GroupChat? = null
    private var groupMembers = mutableListOf<ChatContact>()
    private var allAIContacts = mutableListOf<ChatContact>()
    private lateinit var groupChatManager: GroupChatManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_chat_settings)

        initializeViews()
        initializeGroupChatManager()
        loadGroupData()
        setupListeners()
        setupMembersList()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        saveButton = findViewById(R.id.save_button)
        titleText = findViewById(R.id.title_text)
        groupNameInput = findViewById(R.id.group_name_input)
        groupDescriptionInput = findViewById(R.id.group_description_input)
        addMemberButton = findViewById(R.id.add_member_button)
        deleteGroupButton = findViewById(R.id.delete_group_button)
        leaveGroupButton = findViewById(R.id.leave_group_button)
        groupMembersList = findViewById(R.id.group_members_list)
    }

    private fun initializeGroupChatManager() {
        groupChatManager = GroupChatManager.getInstance(this)
    }

    private fun loadGroupData() {
        currentGroupContact = intent.getParcelableExtra(EXTRA_GROUP_CONTACT)
        if (currentGroupContact == null) {
            Toast.makeText(this, "群聊数据加载失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 从GroupChatManager获取群聊数据
        val groupId = currentGroupContact!!.groupId
        if (groupId != null) {
            currentGroupChat = groupChatManager.getGroupChat(groupId)
            if (currentGroupChat != null) {
                // 设置群聊信息
                groupNameInput.setText(currentGroupChat!!.name)
                groupDescriptionInput.setText(currentGroupChat!!.description ?: "")
                
                Log.d(TAG, "从GroupChatManager加载群聊: ${currentGroupChat!!.name}, 成员数: ${currentGroupChat!!.members.size}")
            } else {
                Log.w(TAG, "GroupChatManager中未找到群聊: $groupId")
                // 回退到旧方式
                groupNameInput.setText(currentGroupContact!!.name)
                groupDescriptionInput.setText(currentGroupContact!!.description ?: "")
            }
        } else {
            Log.w(TAG, "群聊联系人缺少groupId")
            // 回退到旧方式
            groupNameInput.setText(currentGroupContact!!.name)
            groupDescriptionInput.setText(currentGroupContact!!.description ?: "")
        }

        // 加载群成员
        loadGroupMembers()
        loadAllAIContacts()
    }

    private fun loadGroupMembers() {
        try {
            groupMembers.clear()

            // 优先从GroupChatManager加载成员
            if (currentGroupChat != null) {
                Log.d(TAG, "从GroupChatManager加载群成员")
                for (member in currentGroupChat!!.members) {
                    if (member.type == MemberType.AI) {
                        // 将GroupMember转换为ChatContact
                        val contact = ChatContact(
                            id = member.id,
                            name = member.name,
                            type = ContactType.AI,
                            description = member.aiServiceType?.name ?: "AI助手",
                            avatar = member.avatar
                        )
                        groupMembers.add(contact)
                    }
                }
                Log.d(TAG, "从GroupChatManager加载群成员: ${groupMembers.size} 个")
            } else {
                // 回退到旧方式
                Log.d(TAG, "回退到旧方式加载群成员")
                val memberIds = currentGroupContact?.customData?.get("group_members")?.split(",") ?: emptyList()
                
                // 从保存的联系人中查找群成员
                val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
                val json = prefs.getString(KEY_SAVED_CONTACTS, null)
                if (json != null) {
                    val gson = Gson()
                    val type = object : TypeToken<List<com.example.aifloatingball.model.ContactCategory>>() {}.type
                    val categories: List<com.example.aifloatingball.model.ContactCategory> = gson.fromJson(json, type)
                    
                    for (category in categories) {
                        for (contact in category.contacts) {
                            if (contact.type == ContactType.AI && memberIds.contains(contact.id)) {
                                groupMembers.add(contact)
                            }
                        }
                    }
                }
                Log.d(TAG, "从旧方式加载群成员: ${groupMembers.size} 个")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载群成员失败", e)
            Toast.makeText(this, "加载群成员失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAllAIContacts() {
        try {
            allAIContacts.clear()
            val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
            val json = prefs.getString(KEY_SAVED_CONTACTS, null)
            if (json != null) {
                val gson = Gson()
                val type = object : TypeToken<List<com.example.aifloatingball.model.ContactCategory>>() {}.type
                val categories: List<com.example.aifloatingball.model.ContactCategory> = gson.fromJson(json, type)
                
                for (category in categories) {
                    for (contact in category.contacts) {
                        if (contact.type == ContactType.AI) {
                            allAIContacts.add(contact)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载AI联系人失败", e)
        }
    }

    private fun setupMembersList() {
        groupMemberAdapter = GroupMemberAdapter(groupMembers) { member ->
            showRemoveMemberDialog(member)
        }
        
        groupMembersList.apply {
            layoutManager = LinearLayoutManager(this@GroupChatSettingsActivity)
            adapter = groupMemberAdapter
        }
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            saveGroupSettings()
        }

        addMemberButton.setOnClickListener {
            showAddMemberDialog()
        }

        deleteGroupButton.setOnClickListener {
            deleteGroup()
        }

        leaveGroupButton.setOnClickListener {
            showLeaveGroupDialog()
        }
    }

    private fun showAddMemberDialog() {
        // 获取不在群聊中的AI
        val availableAIs = allAIContacts.filter { ai ->
            !groupMembers.any { member -> member.id == ai.id }
        }

        if (availableAIs.isEmpty()) {
            Toast.makeText(this, "没有可添加的AI成员", Toast.LENGTH_SHORT).show()
            return
        }

        val aiNames = availableAIs.map { it.name }.toTypedArray()
        val selectedAIs = BooleanArray(aiNames.size)

        AlertDialog.Builder(this)
            .setTitle("添加群成员")
            .setMultiChoiceItems(aiNames, selectedAIs) { _, which, isChecked ->
                selectedAIs[which] = isChecked
            }
            .setPositiveButton("添加") { _, _ ->
                val newMembers = mutableListOf<ChatContact>()
                for (i in selectedAIs.indices) {
                    if (selectedAIs[i]) {
                        newMembers.add(availableAIs[i])
                    }
                }
                
                if (newMembers.isNotEmpty()) {
                    addMembersToGroup(newMembers)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addMembersToGroup(newMembers: List<ChatContact>) {
        for (member in newMembers) {
            groupMembers.add(member)
            groupMemberAdapter.addMember(member)
        }
        Toast.makeText(this, "已添加 ${newMembers.size} 个成员", Toast.LENGTH_SHORT).show()
    }

    private fun showRemoveMemberDialog(member: ChatContact) {
        AlertDialog.Builder(this)
            .setTitle("移除成员")
            .setMessage("确定要将 ${member.name} 移出群聊吗？")
            .setPositiveButton("移除") { _, _ ->
                removeMemberFromGroup(member)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun removeMemberFromGroup(member: ChatContact) {
        if (groupMembers.size <= 2) {
            Toast.makeText(this, "群聊至少需要2个成员", Toast.LENGTH_SHORT).show()
            return
        }
        
        groupMembers.remove(member)
        groupMemberAdapter.removeMember(member)
        Toast.makeText(this, "已移除 ${member.name}", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除群聊")
            .setMessage("确定要删除这个群聊吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteGroup()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteGroup() {
        try {
            val groupId = currentGroupContact?.groupId
            if (groupId != null) {
                // 从GroupChatManager删除群聊
                val deleted = groupChatManager.deleteGroupChat(groupId)
                if (deleted) {
                    Log.d(TAG, "从GroupChatManager删除群聊成功: $groupId")
                } else {
                    Log.w(TAG, "从GroupChatManager删除群聊失败: $groupId")
                }
            }
            
            // 从本地存储中删除群聊联系人
            val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
            val json = prefs.getString(KEY_SAVED_CONTACTS, null)
            if (json != null) {
                val gson = Gson()
                val type = object : TypeToken<List<com.example.aifloatingball.model.ContactCategory>>() {}.type
                val categories: MutableList<com.example.aifloatingball.model.ContactCategory> = gson.fromJson(json, type)
                
                for (i in categories.indices) {
                    val category = categories[i]
                    val filteredContacts = category.contacts.filter { it.id != currentGroupContact?.id }.toMutableList()
                    categories[i] = category.copy(contacts = filteredContacts)
                }
                
                val updatedJson = gson.toJson(categories)
                prefs.edit().putString(KEY_SAVED_CONTACTS, updatedJson).apply()
                Log.d(TAG, "从本地存储删除群聊联系人: ${currentGroupContact?.id}")
            }
            
            Toast.makeText(this, "群聊已删除", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "删除群聊失败", e)
            Toast.makeText(this, "删除群聊失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLeaveGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出群聊")
            .setMessage("确定要退出这个群聊吗？")
            .setPositiveButton("退出") { _, _ ->
                leaveGroup()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun leaveGroup() {
        // 对于AI群聊，退出等同于删除
        deleteGroup()
    }

    private fun saveGroupSettings() {
        try {
            val newName = groupNameInput.text.toString().trim()
            val newDescription = groupDescriptionInput.text.toString().trim()
            
            if (newName.isEmpty()) {
                Toast.makeText(this, "群聊名称不能为空", Toast.LENGTH_SHORT).show()
                return
            }

            // 更新群聊信息
            val updatedContact = currentGroupContact?.copy(
                name = newName,
                description = newDescription,
                customData = currentGroupContact!!.customData.toMutableMap().apply {
                    put("group_members", groupMembers.map { it.id }.joinToString(","))
                    put("last_updated", System.currentTimeMillis().toString())
                }
            )

            // 保存到本地存储
            if (updatedContact != null) {
                saveGroupToStorage(updatedContact)
                
                // 返回结果
                val resultIntent = Intent()
                resultIntent.putExtra(EXTRA_GROUP_CONTACT, updatedContact)
                setResult(RESULT_OK, resultIntent)
                
                Toast.makeText(this, "群聊设置已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊设置失败", e)
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveGroupToStorage(groupContact: ChatContact) {
        try {
            val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
            val json = prefs.getString(KEY_SAVED_CONTACTS, null)
            if (json != null) {
                val gson = Gson()
                val type = object : TypeToken<List<com.example.aifloatingball.model.ContactCategory>>() {}.type
                val categories: MutableList<com.example.aifloatingball.model.ContactCategory> = gson.fromJson(json, type)
                
                // 查找并更新群聊
                var updated = false
                for (i in categories.indices) {
                    val category = categories[i]
                    val updatedContacts = category.contacts.map { contact ->
                        if (contact.id == groupContact.id) {
                            updated = true
                            groupContact
                        } else {
                            contact
                        }
                    }.toMutableList()
                    if (updated) {
                        categories[i] = category.copy(contacts = updatedContacts)
                        break
                    }
                }
                
                val updatedJson = gson.toJson(categories)
                prefs.edit().putString(KEY_SAVED_CONTACTS, updatedJson).apply()
                
                Log.d(TAG, "群聊设置已保存: ${groupContact.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊到存储失败", e)
            throw e
        }
    }
}