package com.example.aifloatingball

import android.content.Context
import com.example.aifloatingball.model.SearchableSetting

class SettingsSearchManager(private val context: Context) {

    private val allSettings: List<SearchableSetting> by lazy {
        buildSettingsList()
    }

    fun search(query: String): List<SearchableSetting> {
        if (query.isBlank()) {
            return emptyList()
        }
        val lowerCaseQuery = query.lowercase()
        return allSettings.filter {
            it.title.toString().lowercase().contains(lowerCaseQuery) ||
            (it.summary?.toString()?.lowercase()?.contains(lowerCaseQuery) == true) ||
            it.keywords.any { keyword -> keyword.lowercase().contains(lowerCaseQuery) }
        }
    }

    private fun buildSettingsList(): List<SearchableSetting> {
        // These keys MUST match the keys in root_preferences.xml
        return listOf(
            SearchableSetting("theme", "主题模式", "选择亮色、暗色或跟随系统", listOf("外观", "颜色", "dark mode")),
            SearchableSetting("left_handed_mode", "左手模式", "为左手用户优化界面布局", listOf("习惯", "惯用手")),
            SearchableSetting("ball_settings", "悬浮球设置", "调整悬浮球的透明度、大小和行为", listOf("alpha", "自动贴边", "吸附")),
            SearchableSetting("window_settings", "浮窗设置", "配置主搜索浮窗的显示内容和行为", listOf("自动粘贴", "模块")),
            SearchableSetting("search_engine_management", "搜索引擎管理", "添加、编辑或删除搜索引擎", listOf("默认", "自定义")),
            SearchableSetting("search_engine_shortcuts", "搜索引擎组合管理", "创建和管理搜索引擎快捷组合", listOf("快捷方式", "combo", "group")),
            SearchableSetting("app_search_settings", "应用内搜索管理", "设置可在哪些应用中快速启动搜索", listOf("bilibili", "知乎", "淘宝")),
            SearchableSetting("ai_settings", "AI相关设置", "配置AI服务商、大师指令等高级功能", listOf("API Key", "模型", "prompt")),
            SearchableSetting("search_history", "查看搜索历史", "查看和管理您的所有搜索记录", emptyList())
        )
    }
} 