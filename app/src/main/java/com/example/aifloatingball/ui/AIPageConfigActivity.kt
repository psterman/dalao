package com.example.aifloatingball.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.AIPageConfigManager
import com.example.aifloatingball.model.AISearchEngine

/**
 * AI页面配置Activity
 */
class AIPageConfigActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AIPageConfigActivity"
    }
    
    private lateinit var aiPageConfigManager: AIPageConfigManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AIConfigAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_page_config)
        
        // 设置ActionBar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "AI页面配置"
        }
        
        initViews()
        initData()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_view_ai_configs)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun initData() {
        aiPageConfigManager = AIPageConfigManager(this)
        
        val configs = aiPageConfigManager.getAllCustomAIConfigs()
        adapter = AIConfigAdapter(configs) { config ->
            showConfigDetails(config)
        }
        recyclerView.adapter = adapter
    }
    
    private fun showConfigDetails(config: AISearchEngine) {
        val message = buildString {
            append("名称: ${config.name}\n")
            append("描述: ${config.description}\n")
            append("URL: ${config.url}\n")
            append("状态: ${aiPageConfigManager.getConfigStatus(config.name)}\n")
            
            if (config.customParams.isNotEmpty()) {
                append("\n自定义参数:\n")
                config.customParams.forEach { (key, value) ->
                    append("  $key: $value\n")
                }
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("AI配置详情")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

/**
 * AI配置适配器
 */
class AIConfigAdapter(
    private val configs: List<AISearchEngine>,
    private val onItemClick: (AISearchEngine) -> Unit
) : RecyclerView.Adapter<AIConfigAdapter.ViewHolder>() {
    
    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.text_name)
        val descriptionText: TextView = itemView.findViewById(R.id.text_description)
        val statusText: TextView = itemView.findViewById(R.id.text_status)
        val iconImage: ImageView = itemView.findViewById(R.id.image_icon)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_config, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configs[position]
        
        holder.nameText.text = config.name
        holder.descriptionText.text = config.description
        
        // 设置状态
        val configManager = AIPageConfigManager(holder.itemView.context)
        holder.statusText.text = configManager.getConfigStatus(config.name)
        
        // 设置图标
        if (config.iconResId != 0) {
            holder.iconImage.setImageResource(config.iconResId)
        } else {
            holder.iconImage.setImageResource(R.drawable.ic_web_default)
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(config)
        }
    }
    
    override fun getItemCount(): Int = configs.size
}
