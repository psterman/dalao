package com.example.aifloatingball.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 圆弧操作栏设置管理器
 */
class ArcOperationBarSettings(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "arc_operation_bar_settings"
        private const val KEY_IS_LEFT_HANDED = "is_left_handed"
        private const val KEY_ARC_RADIUS = "arc_radius"
        private const val KEY_BUTTON_BACK_VISIBLE = "button_back_visible"
        private const val KEY_BUTTON_REFRESH_VISIBLE = "button_refresh_visible"
        private const val KEY_BUTTON_HOME_VISIBLE = "button_home_visible"
        private const val KEY_BUTTON_NEW_VISIBLE = "button_new_visible"
        private const val KEY_BUTTON_BACK_POSITION = "button_back_position"
        private const val KEY_BUTTON_REFRESH_POSITION = "button_refresh_position"
        private const val KEY_BUTTON_HOME_POSITION = "button_home_position"
        private const val KEY_BUTTON_NEW_POSITION = "button_new_position"
        
        // 默认值
        private const val DEFAULT_ARC_RADIUS = 120f
        private const val DEFAULT_IS_LEFT_HANDED = false
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 是否左手模式
     */
    var isLeftHanded: Boolean
        get() = prefs.getBoolean(KEY_IS_LEFT_HANDED, DEFAULT_IS_LEFT_HANDED)
        set(value) = prefs.edit().putBoolean(KEY_IS_LEFT_HANDED, value).apply()
    
    /**
     * 圆弧半径
     */
    var arcRadius: Float
        get() = prefs.getFloat(KEY_ARC_RADIUS, DEFAULT_ARC_RADIUS)
        set(value) = prefs.edit().putFloat(KEY_ARC_RADIUS, value).apply()
    
    /**
     * 按钮可见性设置
     */
    fun isButtonVisible(buttonId: String): Boolean {
        return when (buttonId) {
            "back" -> prefs.getBoolean(KEY_BUTTON_BACK_VISIBLE, true)
            "refresh" -> prefs.getBoolean(KEY_BUTTON_REFRESH_VISIBLE, true)
            "home" -> prefs.getBoolean(KEY_BUTTON_HOME_VISIBLE, true)
            "new" -> prefs.getBoolean(KEY_BUTTON_NEW_VISIBLE, true)
            else -> true
        }
    }
    
    /**
     * 设置按钮可见性
     */
    fun setButtonVisible(buttonId: String, visible: Boolean) {
        val key = when (buttonId) {
            "back" -> KEY_BUTTON_BACK_VISIBLE
            "refresh" -> KEY_BUTTON_REFRESH_VISIBLE
            "home" -> KEY_BUTTON_HOME_VISIBLE
            "new" -> KEY_BUTTON_NEW_VISIBLE
            else -> return
        }
        prefs.edit().putBoolean(key, visible).apply()
    }
    
    /**
     * 获取按钮位置
     */
    fun getButtonPosition(buttonId: String): Int {
        return when (buttonId) {
            "back" -> prefs.getInt(KEY_BUTTON_BACK_POSITION, 0)
            "refresh" -> prefs.getInt(KEY_BUTTON_REFRESH_POSITION, 1)
            "home" -> prefs.getInt(KEY_BUTTON_HOME_POSITION, 2)
            "new" -> prefs.getInt(KEY_BUTTON_NEW_POSITION, 3)
            else -> 0
        }
    }
    
    /**
     * 设置按钮位置
     */
    fun setButtonPosition(buttonId: String, position: Int) {
        val key = when (buttonId) {
            "back" -> KEY_BUTTON_BACK_POSITION
            "refresh" -> KEY_BUTTON_REFRESH_POSITION
            "home" -> KEY_BUTTON_HOME_POSITION
            "new" -> KEY_BUTTON_NEW_POSITION
            else -> return
        }
        prefs.edit().putInt(key, position).apply()
    }
    
    /**
     * 重置为默认设置
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
    
    /**
     * 按钮配置数据类
     */
    data class ButtonConfig(
        val id: String,
        val name: String,
        val iconRes: Int,
        var isVisible: Boolean,
        var position: Int
    )
    
    /**
     * 获取所有按钮配置
     */
    fun getAllButtonConfigs(): List<ButtonConfig> {
        return listOf(
            ButtonConfig(
                "back", "后退", 
                com.example.aifloatingball.R.drawable.ic_arrow_back,
                isButtonVisible("back"), 
                getButtonPosition("back")
            ),
            ButtonConfig(
                "refresh", "刷新", 
                com.example.aifloatingball.R.drawable.ic_refresh,
                isButtonVisible("refresh"), 
                getButtonPosition("refresh")
            ),
            ButtonConfig(
                "home", "首页", 
                com.example.aifloatingball.R.drawable.ic_home,
                isButtonVisible("home"), 
                getButtonPosition("home")
            ),
            ButtonConfig(
                "new", "新建", 
                com.example.aifloatingball.R.drawable.ic_add,
                isButtonVisible("new"), 
                getButtonPosition("new")
            )
        ).sortedBy { it.position }
    }
    
    /**
     * 保存按钮配置
     */
    fun saveButtonConfigs(configs: List<ButtonConfig>) {
        configs.forEach { config ->
            setButtonVisible(config.id, config.isVisible)
            setButtonPosition(config.id, config.position)
        }
    }
}
