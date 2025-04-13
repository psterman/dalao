package com.example.aifloatingball.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.IconLoader
import android.util.Log

/**
 * 搜索引擎列表适配器
 * 
 * @param context 上下文
 * @param engines 搜索引擎列表
 * @param enabledEngines 已启用的搜索引擎集合
 * @param onEngineToggled 搜索引擎切换回调
 */
class SearchEngineAdapter<T : SearchEngine>(
    private val context: Context,
    private val engines: List<T>,
    private val enabledEngines: MutableSet<String>,
    private val onEngineToggled: (String, Boolean) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter<T>.ViewHolder>() {
    
    private val iconLoader = IconLoader(context)
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.engine_name)
        val iconImageView: ImageView = view.findViewById(R.id.engine_icon)
        val toggleSwitch: Switch = view.findViewById(R.id.engine_toggle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }
    
    override fun getItemCount(): Int = engines.size
    
    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        
        try {
            // 设置搜索引擎名称
            holder.nameTextView.text = engine.name
            
            // 首先设置默认图标作为占位符
            holder.iconImageView.setImageResource(engine.iconResId)
            
            // 准备URL进行图标加载
            val url = prepareIconUrl(engine)
            
            // 使用更可靠的方式加载图标
            iconLoader.loadIcon(url, holder.iconImageView, engine.iconResId)
            
            // 设置切换状态
            holder.toggleSwitch.isChecked = enabledEngines.contains(engine.name)
            
            // 设置点击事件
            holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    enabledEngines.add(engine.name)
                } else {
                    enabledEngines.remove(engine.name)
                }
                onEngineToggled(engine.name, isChecked)
            }
            
            // 允许点击项目来切换开关
            holder.itemView.setOnClickListener {
                holder.toggleSwitch.isChecked = !holder.toggleSwitch.isChecked
            }
        } catch (e: Exception) {
            Log.e("SearchEngineAdapter", "Error binding item $position: ${e.message}", e)
        }
    }
    
    private fun prepareIconUrl(engine: T): String {
        // 优化URL处理以确保正确加载图标
        return when {
            // 为特定引擎返回确切的图标URL
            engine.name == "ChatGPT" -> "https://chat.openai.com"
            engine.name == "Claude" -> "https://claude.ai"
            engine.name == "Gemini" -> "https://gemini.google.com"
            engine.name == "文心一言" -> "https://yiyan.baidu.com"
            engine.name == "智谱清言" -> "https://chatglm.cn"
            engine.name == "通义千问" -> "https://tongyi.aliyun.com"
            engine.name == "讯飞星火" -> "https://xinghuo.xfyun.cn"
            engine.name == "Perplexity" -> "https://perplexity.ai"
            engine.name == "Grok" -> "https://grok.x.ai"
            engine.name == "Poe" -> "https://poe.com"
            
            // AI搜索引擎特殊处理
            engine is AISearchEngine -> engine.url
            
            // 普通搜索引擎，移除URL中的查询部分
            engine.url.contains("?") -> engine.url.substring(0, engine.url.indexOf("?"))
            
            // 原始URL
            else -> engine.url
        }
    }
} 