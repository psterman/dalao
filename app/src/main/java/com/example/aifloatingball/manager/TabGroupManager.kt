package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.model.TabGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * 标签页组管理器
 * 负责管理所有标签页组，包括创建、删除、编辑、排序等操作
 */
class TabGroupManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TabGroupManager"
        private const val PREFS_NAME = "tab_groups"
        private const val KEY_GROUPS = "groups"
        private const val KEY_CURRENT_GROUP_ID = "current_group_id"
        
        @Volatile
        private var INSTANCE: TabGroupManager? = null
        
        fun getInstance(context: Context): TabGroupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TabGroupManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // 内存中的组列表
    private val groups = mutableListOf<TabGroup>()
    
    // 当前选中的组ID
    private var currentGroupId: String? = null
    
    // 组变化监听器
    private val groupChangeListeners = mutableListOf<() -> Unit>()
    
    // 暗道模式状态（只在内存中，不持久化）
    private var isSecretModeActive: Boolean = false
    private var secretModePasswordHash: String? = null
    
    init {
        loadGroups()
    }
    
    /**
     * 添加组变化监听器
     */
    fun addGroupChangeListener(listener: () -> Unit) {
        groupChangeListeners.add(listener)
    }
    
    /**
     * 移除组变化监听器
     */
    fun removeGroupChangeListener(listener: () -> Unit) {
        groupChangeListeners.remove(listener)
    }
    
    /**
     * 通知组变化
     */
    private fun notifyGroupChanged() {
        groupChangeListeners.forEach { it.invoke() }
    }
    
    /**
     * 获取所有组（默认只返回可见的组，暗道模式下返回所有组包括隐藏的）
     */
    fun getAllGroups(): List<TabGroup> {
        return if (isSecretModeActive) {
            getAllGroupsIncludingHidden()
        } else {
            getVisibleGroups()
        }
    }
    
    /**
     * 获取所有组（内部方法，直接从groups列表获取并排序）
     */
    private fun getAllGroupsInternal(): List<TabGroup> {
        return groups.sortedWith(compareBy<TabGroup> { !it.isPinned } // 置顶的在前
            .thenBy { it.order }) // 然后按排序顺序
    }
    
    /**
     * 根据ID获取组
     */
    fun getGroupById(id: String): TabGroup? {
        return groups.find { it.id == id }
    }
    
    /**
     * 获取当前组
     */
    fun getCurrentGroup(): TabGroup? {
        return currentGroupId?.let { getGroupById(it) }
    }
    
    /**
     * 设置当前组
     */
    fun setCurrentGroup(groupId: String) {
        if (groups.any { it.id == groupId }) {
            currentGroupId = groupId
            saveCurrentGroupId()
            notifyGroupChanged()
            Log.d(TAG, "切换到组: $groupId")
        }
    }
    
    /**
     * 创建新组
     */
    fun createGroup(name: String): TabGroup {
        val group = TabGroup.createNew(name)
        groups.add(group)
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "创建新组: ${group.name} (${group.id})")
        return group
    }
    
    /**
     * 更新组信息
     */
    fun updateGroup(groupId: String, name: String? = null, isPinned: Boolean? = null) {
        val group = getGroupById(groupId) ?: return
        
        name?.let { group.name = it }
        isPinned?.let { group.isPinned = it }
        group.updatedAt = System.currentTimeMillis()
        
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "更新组: ${group.name} (${group.id})")
    }
    
    /**
     * 设置组密码
     */
    fun setGroupPassword(groupId: String, password: String) {
        val group = getGroupById(groupId) ?: return
        group.passwordHash = hashPassword(password)
        group.updatedAt = System.currentTimeMillis()
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "设置组密码: ${group.name}")
    }
    
    /**
     * 验证组密码
     */
    fun verifyGroupPassword(groupId: String, password: String): Boolean {
        val group = getGroupById(groupId) ?: return false
        if (group.passwordHash == null) return true // 没有密码，直接通过
        return group.passwordHash == hashPassword(password)
    }
    
    /**
     * 移除组密码
     */
    fun removeGroupPassword(groupId: String) {
        val group = getGroupById(groupId) ?: return
        group.passwordHash = null
        group.quickAccessCode = null
        group.updatedAt = System.currentTimeMillis()
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "移除组密码: ${group.name}")
    }
    
    /**
     * 设置快捷访问码
     */
    fun setQuickAccessCode(groupId: String, code: String) {
        val group = getGroupById(groupId) ?: return
        group.quickAccessCode = hashPassword(code)
        group.updatedAt = System.currentTimeMillis()
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "设置快捷访问码: ${group.name}")
    }
    
    /**
     * 验证快捷访问码
     */
    fun verifyQuickAccessCode(groupId: String, code: String): Boolean {
        val group = getGroupById(groupId) ?: return false
        if (group.quickAccessCode == null) return false
        return group.quickAccessCode == hashPassword(code)
    }
    
    /**
     * 切换组隐藏状态
     */
    fun toggleGroupHidden(groupId: String) {
        val group = getGroupById(groupId) ?: return
        group.isHidden = !group.isHidden
        group.updatedAt = System.currentTimeMillis()
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "${if (group.isHidden) "隐藏" else "显示"}组: ${group.name}")
    }
    
    /**
     * 获取可见的组（排除隐藏的组）
     */
    fun getVisibleGroups(): List<TabGroup> {
        return getAllGroupsInternal().filter { !it.isHidden }
    }
    
    /**
     * 获取所有组（包括隐藏的）
     */
    fun getAllGroupsIncludingHidden(): List<TabGroup> {
        return getAllGroupsInternal()
    }
    
    /**
     * 密码哈希（简单实现，生产环境应使用更安全的加密方式）
     */
    private fun hashPassword(password: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(password.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "密码哈希失败", e)
            password // 失败时返回原密码（不安全，但至少能工作）
        }
    }
    
    /**
     * 删除组
     */
    fun deleteGroup(groupId: String): Boolean {
        val group = getGroupById(groupId) ?: return false
        
        // 如果删除的是当前组，切换到其他组
        if (currentGroupId == groupId) {
            val remainingGroups = groups.filter { it.id != groupId }
            currentGroupId = remainingGroups.firstOrNull()?.id
            saveCurrentGroupId()
        }
        
        groups.remove(group)
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "删除组: ${group.name} (${group.id})")
        return true
    }
    
    /**
     * 置顶/取消置顶组
     */
    fun togglePinGroup(groupId: String) {
        val group = getGroupById(groupId) ?: return
        group.isPinned = !group.isPinned
        group.updatedAt = System.currentTimeMillis()
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "${if (group.isPinned) "置顶" else "取消置顶"}组: ${group.name}")
    }
    
    /**
     * 更新组排序
     */
    fun updateGroupOrder(groupIds: List<String>) {
        groupIds.forEachIndexed { index, groupId ->
            val group = getGroupById(groupId)
            group?.order = index
            group?.updatedAt = System.currentTimeMillis()
        }
        saveGroups()
        notifyGroupChanged()
        Log.d(TAG, "更新组排序")
    }
    
    /**
     * 从SharedPreferences加载组数据
     */
    private fun loadGroups() {
        try {
            val groupsJson = prefs.getString(KEY_GROUPS, null)
            if (groupsJson.isNullOrEmpty()) {
                // 如果没有保存的组，创建默认组（但不显示在UI中）
                val defaultGroup = TabGroup.createDefault()
                groups.add(defaultGroup)
                currentGroupId = defaultGroup.id
                saveGroups()
                saveCurrentGroupId()
                Log.d(TAG, "创建默认组（不显示在UI中）")
            } else {
                val type = object : TypeToken<List<TabGroup>>() {}.type
                val loadedGroups = gson.fromJson<List<TabGroup>>(groupsJson, type) ?: emptyList()
                groups.clear()
                groups.addAll(loadedGroups)
                
                // 如果没有组，创建默认组
                if (groups.isEmpty()) {
                    val defaultGroup = TabGroup.createDefault()
                    groups.add(defaultGroup)
                    currentGroupId = defaultGroup.id
                    saveGroups()
                    saveCurrentGroupId()
                    Log.d(TAG, "加载后没有组，创建默认组")
                } else {
                    // 加载当前组ID
                    currentGroupId = prefs.getString(KEY_CURRENT_GROUP_ID, null)
                    
                    // 如果没有当前组或当前组不存在，使用第一个组（如果有的话）
                    if (currentGroupId == null || groups.none { it.id == currentGroupId }) {
                        currentGroupId = groups.firstOrNull()?.id
                        saveCurrentGroupId()
                    }
                }
                
                Log.d(TAG, "加载 ${groups.size} 个组，当前组: $currentGroupId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载组数据失败", e)
            // 加载失败，创建默认组
            val defaultGroup = TabGroup.createDefault()
            groups.clear()
            groups.add(defaultGroup)
            currentGroupId = defaultGroup.id
            saveGroups()
            saveCurrentGroupId()
        }
    }
    
    /**
     * 保存组数据到SharedPreferences
     */
    private fun saveGroups() {
        try {
            val groupsJson = gson.toJson(groups)
            prefs.edit().putString(KEY_GROUPS, groupsJson).apply()
            Log.d(TAG, "保存 ${groups.size} 个组")
        } catch (e: Exception) {
            Log.e(TAG, "保存组数据失败", e)
        }
    }
    
    /**
     * 保存当前组ID
     */
    private fun saveCurrentGroupId() {
        prefs.edit().putString(KEY_CURRENT_GROUP_ID, currentGroupId).apply()
    }
    
    /**
     * 设置暗道密码（用于验证进入暗道）
     */
    fun setSecretModePassword(password: String) {
        secretModePasswordHash = hashPassword(password)
        // 保存到SharedPreferences，以便持久化
        prefs.edit().putString("secret_mode_password_hash", secretModePasswordHash).apply()
        Log.d(TAG, "设置暗道密码")
    }
    
    /**
     * 检查是否已设置暗道密码
     */
    fun hasSecretModePassword(): Boolean {
        if (secretModePasswordHash != null) return true
        // 从SharedPreferences加载
        secretModePasswordHash = prefs.getString("secret_mode_password_hash", null)
        return secretModePasswordHash != null
    }
    
    /**
     * 验证暗道密码
     */
    fun verifySecretModePassword(password: String): Boolean {
        // 先从SharedPreferences加载（如果内存中没有）
        if (secretModePasswordHash == null) {
            secretModePasswordHash = prefs.getString("secret_mode_password_hash", null)
        }
        
        if (secretModePasswordHash == null) {
            // 如果没有设置暗道密码，使用第一个隐藏组的密码作为默认密码
            val hiddenGroups = getAllGroupsIncludingHidden().filter { it.isHidden }
            if (hiddenGroups.isNotEmpty()) {
                val firstHiddenGroup = hiddenGroups.first()
                if (firstHiddenGroup.passwordHash != null) {
                    return firstHiddenGroup.passwordHash == hashPassword(password)
                }
            }
            // 如果没有隐藏组或隐藏组没有密码，返回false
            return false
        }
        return secretModePasswordHash == hashPassword(password)
    }
    
    /**
     * 激活暗道模式
     */
    fun activateSecretMode() {
        isSecretModeActive = true
        notifyGroupChanged()
        Log.d(TAG, "激活暗道模式")
    }
    
    /**
     * 退出暗道模式
     */
    fun deactivateSecretMode() {
        isSecretModeActive = false
        notifyGroupChanged()
        Log.d(TAG, "退出暗道模式")
    }
    
    /**
     * 检查暗道模式是否激活
     */
    fun isSecretModeActive(): Boolean {
        return isSecretModeActive
    }
}

