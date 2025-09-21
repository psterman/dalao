package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * AI服务选择管理器
 * 负责管理用户选择的AI服务状态，支持多选、全选、清空等操作
 */
class AIServiceSelectionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AIServiceSelectionManager"
        private const val PREFS_NAME = "ai_service_selection"
        private const val KEY_SELECTED_SERVICES = "selected_services"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val selectedServices = mutableSetOf<String>()
    
    // 可用的AI服务列表
    val availableServices = listOf(
        "DeepSeek",
        "智谱AI", 
        "Kimi",
        "ChatGPT",
        "Claude",
        "Gemini",
        "文心一言",
        "通义千问",
        "讯飞星火"
    )
    
    init {
        loadSelectedServices()
    }
    
    /**
     * 获取当前选中的AI服务列表
     */
    fun getSelectedServices(): List<String> {
        return selectedServices.toList()
    }
    
    /**
     * 切换AI服务选择状态
     * @param serviceName AI服务名称
     * @return 是否被选中
     */
    fun toggleService(serviceName: String): Boolean {
        val wasSelected = selectedServices.contains(serviceName)
        if (wasSelected) {
            selectedServices.remove(serviceName)
        } else {
            selectedServices.add(serviceName)
        }
        saveSelectedServices()
        Log.d(TAG, "切换AI服务选择: $serviceName, 选中: ${!wasSelected}")
        return !wasSelected
    }
    
    /**
     * 检查AI服务是否被选中
     * @param serviceName AI服务名称
     * @return 是否被选中
     */
    fun isSelected(serviceName: String): Boolean {
        return selectedServices.contains(serviceName)
    }
    
    /**
     * 全选所有AI服务
     */
    fun selectAll() {
        selectedServices.clear()
        selectedServices.addAll(availableServices)
        saveSelectedServices()
        Log.d(TAG, "全选所有AI服务")
    }
    
    /**
     * 清空所有选择
     */
    fun clearAll() {
        selectedServices.clear()
        saveSelectedServices()
        Log.d(TAG, "清空所有AI服务选择")
    }
    
    /**
     * 设置AI服务选择状态
     * @param serviceName AI服务名称
     * @param selected 是否选中
     */
    fun setServiceSelected(serviceName: String, selected: Boolean) {
        if (selected) {
            selectedServices.add(serviceName)
        } else {
            selectedServices.remove(serviceName)
        }
        saveSelectedServices()
    }
    
    /**
     * 获取选中服务数量
     */
    fun getSelectedCount(): Int {
        return selectedServices.size
    }
    
    /**
     * 检查是否有选中的服务
     */
    fun hasSelectedServices(): Boolean {
        return selectedServices.isNotEmpty()
    }
    
    /**
     * 获取选中服务的显示文本
     */
    fun getSelectedServicesText(): String {
        return when {
            selectedServices.isEmpty() -> "未选择AI服务"
            selectedServices.size == availableServices.size -> "已选择所有AI服务"
            selectedServices.size == 1 -> "已选择: ${selectedServices.first()}"
            else -> "已选择 ${selectedServices.size} 个AI服务"
        }
    }
    
    /**
     * 从SharedPreferences加载选中的服务
     */
    private fun loadSelectedServices() {
        try {
            val selectedServicesJson = prefs.getString(KEY_SELECTED_SERVICES, "[]")
            val servicesArray = org.json.JSONArray(selectedServicesJson)
            
            selectedServices.clear()
            for (i in 0 until servicesArray.length()) {
                val serviceName = servicesArray.getString(i)
                if (availableServices.contains(serviceName)) {
                    selectedServices.add(serviceName)
                }
            }
            
            // 如果没有保存的选择，默认选择第一个服务
            if (selectedServices.isEmpty() && availableServices.isNotEmpty()) {
                selectedServices.add(availableServices.first())
                saveSelectedServices()
            }
            
            Log.d(TAG, "加载选中的AI服务: $selectedServices")
        } catch (e: Exception) {
            Log.e(TAG, "加载选中的AI服务失败", e)
            // 默认选择第一个服务
            if (availableServices.isNotEmpty()) {
                selectedServices.add(availableServices.first())
                saveSelectedServices()
            }
        }
    }
    
    /**
     * 保存选中的服务到SharedPreferences
     */
    private fun saveSelectedServices() {
        try {
            val servicesArray = org.json.JSONArray()
            selectedServices.forEach { serviceName ->
                servicesArray.put(serviceName)
            }
            
            prefs.edit()
                .putString(KEY_SELECTED_SERVICES, servicesArray.toString())
                .apply()
                
            Log.d(TAG, "保存选中的AI服务: $selectedServices")
        } catch (e: Exception) {
            Log.e(TAG, "保存选中的AI服务失败", e)
        }
    }
    
    /**
     * 重置为默认选择
     */
    fun resetToDefault() {
        selectedServices.clear()
        if (availableServices.isNotEmpty()) {
            selectedServices.add(availableServices.first())
        }
        saveSelectedServices()
        Log.d(TAG, "重置为默认选择")
    }
}
