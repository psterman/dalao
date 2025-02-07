class SettingsManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("ai_floating_ball_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    fun saveEngineOrder(engines: List<SearchEngine>) {
        prefs.edit().putString("engines", gson.toJson(engines)).apply()
    }
    
    fun getEngineOrder(): List<SearchEngine> {
        val defaultEngines = listOf(
            SearchEngine("DeepSeek", "https://deepseek.com/search?q=", 0),
            SearchEngine("豆包", "https://www.doubao.com/search?q=", 1),
            SearchEngine("Kimi", "https://kimi.moonshot.cn/search?q=", 2)
        )
        
        val enginesJson = prefs.getString("engines", null) ?: return defaultEngines
        return try {
            gson.fromJson(enginesJson, object : TypeToken<List<SearchEngine>>() {}.type)
        } catch (e: Exception) {
            defaultEngines
        }
    }
    
    fun setAutoHide(autoHide: Boolean) {
        prefs.edit().putBoolean("auto_hide", autoHide).apply()
    }
    
    fun getAutoHide(): Boolean = prefs.getBoolean("auto_hide", false)
} 