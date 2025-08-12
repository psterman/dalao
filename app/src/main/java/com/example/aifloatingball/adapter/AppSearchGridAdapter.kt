package com.example.aifloatingball.adapter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppCategory
import com.example.aifloatingball.model.AppSearchSettings

/**
 * 应用搜索网格适配器
 */
class AppSearchGridAdapter(
    private val context: Context,
    private var appConfigs: List<AppSearchConfig> = emptyList(),
    private val onAppClick: (AppSearchConfig, String) -> Unit,
    private val onAppSelected: ((AppSearchConfig) -> Unit)? = null,
    private val getCurrentQuery: (() -> String)? = null
) : RecyclerView.Adapter<AppSearchGridAdapter.AppViewHolder>() {

    private var searchQuery: String = ""

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val appStatusIndicator: View = itemView.findViewById(R.id.app_status_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_app_search_grid, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appConfig = appConfigs[position]
        
        // 设置应用名称
        holder.appName.text = appConfig.appName
        
        // 检查应用是否已安装
        val isInstalled = isAppInstalled(appConfig.packageName)
        
        // 设置应用图标
        val appIcon = getAppIcon(appConfig)
        if (appIcon != null) {
            holder.appIcon.setImageDrawable(appIcon)
            // 确保图标正确显示
            holder.appIcon.scaleType = ImageView.ScaleType.CENTER_CROP
            // 清除背景，显示真实图标
            holder.appIcon.background = null
        } else {
            // 使用默认图标
            holder.appIcon.setImageResource(appConfig.category.iconResId)
            holder.appIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            // 恢复背景
            holder.appIcon.setBackgroundResource(R.drawable.launcher_icon_background)
        }
        
        // 设置安装状态 - 绿点表示已安装，红点表示未安装
        holder.appStatusIndicator.visibility = View.VISIBLE
        if (isInstalled) {
            holder.appStatusIndicator.setBackgroundResource(R.drawable.status_indicator_installed)
            holder.appIcon.alpha = 1.0f
        } else {
            holder.appStatusIndicator.setBackgroundResource(R.drawable.status_indicator_not_installed)
            holder.appIcon.alpha = 0.6f
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            if (!isInstalled) {
                // 应用未安装，显示安装提示弹窗
                showInstallDialog(appConfig)
                return@setOnClickListener
            }

            // 通知应用被选中（仅在已安装时）
            onAppSelected?.invoke(appConfig)

            // 获取当前输入框的实时内容
            val currentQuery = getCurrentQuery?.invoke()?.trim() ?: searchQuery

            if (currentQuery.isNotEmpty()) {
                // 有搜索内容时直接搜索
                onAppClick(appConfig, currentQuery)
            }
            // 输入框为空时，只切换图标，不显示任何提示
        }
        
        // 长按显示菜单
        holder.itemView.setOnLongClickListener {
            showAppMenu(appConfig, isInstalled)
            true
        }
    }

    override fun getItemCount(): Int = appConfigs.size

    /**
     * 更新应用配置列表
     */
    fun updateAppConfigs(newConfigs: List<AppSearchConfig>) {
        appConfigs = newConfigs
        notifyDataSetChanged()
    }
    
    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        searchQuery = query
        notifyDataSetChanged()
    }

    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 获取应用图标
     */
    private fun getAppIcon(appConfig: AppSearchConfig): Drawable? {
        return try {
            if (isAppInstalled(appConfig.packageName)) {
                // 如果应用已安装，获取真实图标
                context.packageManager.getApplicationIcon(appConfig.packageName)
            } else {
                // 如果应用未安装，使用默认图标
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 显示应用菜单
     */
    private fun showAppMenu(appConfig: AppSearchConfig, isInstalled: Boolean) {
        val menuItems = mutableListOf<String>()
        menuItems.add("查看应用信息")

        if (isInstalled) {
            menuItems.add("添加到自定义分类")
        }

        // 如果是自定义分类中的应用，添加删除选项
        if (appConfig.category == AppCategory.CUSTOM) {
            menuItems.add("从自定义分类中删除")
        }

        AlertDialog.Builder(context)
            .setTitle(appConfig.appName)
            .setItems(menuItems.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showAppInfo(appConfig, isInstalled)
                    1 -> {
                        if (isInstalled) {
                            addToCustomCategory(appConfig)
                        } else if (appConfig.category == AppCategory.CUSTOM) {
                            removeFromCustomCategory(appConfig)
                        }
                    }
                    2 -> if (appConfig.category == AppCategory.CUSTOM) removeFromCustomCategory(appConfig)
                }
            }
            .show()
    }

    /**
     * 添加应用到自定义分类
     */
    private fun addToCustomCategory(appConfig: AppSearchConfig) {
        try {
            val appSearchSettings = AppSearchSettings.getInstance(context)
            val configs = appSearchSettings.getAppConfigs().toMutableList()

            // 创建自定义应用配置
            val customConfig = appConfig.copy(
                appId = "${appConfig.appId}_custom",
                category = AppCategory.CUSTOM,
                isEnabled = true,
                order = configs.maxOfOrNull { it.order }?.plus(1) ?: 1
            )

            // 检查是否已存在
            val existingIndex = configs.indexOfFirst {
                it.packageName == appConfig.packageName && it.category == AppCategory.CUSTOM
            }

            if (existingIndex == -1) {
                configs.add(customConfig)
                appSearchSettings.saveAppConfigs(configs)
                Toast.makeText(context, "已添加 ${appConfig.appName} 到自定义分类", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "${appConfig.appName} 已在自定义分类中", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从自定义分类中删除应用
     */
    private fun removeFromCustomCategory(appConfig: AppSearchConfig) {
        try {
            AlertDialog.Builder(context)
                .setTitle("删除确认")
                .setMessage("确定要从自定义分类中删除 ${appConfig.appName} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    val appSearchSettings = AppSearchSettings.getInstance(context)
                    val configs = appSearchSettings.getAppConfigs().toMutableList()

                    // 找到并删除对应的自定义配置
                    val wasRemoved = configs.removeAll {
                        it.appId == appConfig.appId && it.category == AppCategory.CUSTOM
                    }

                    if (wasRemoved) {
                        appSearchSettings.saveAppConfigs(configs)
                        Toast.makeText(context, "已从自定义分类中删除 ${appConfig.appName}", Toast.LENGTH_SHORT).show()

                        // 通知适配器更新数据
                        updateAppConfigs(configs.filter { it.category == AppCategory.CUSTOM })
                    } else {
                        Toast.makeText(context, "删除失败，未找到对应的配置", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示安装提示弹窗
     */
    private fun showInstallDialog(appConfig: AppSearchConfig) {
        try {
            // 确保context是Activity类型
            val activityContext = when (context) {
                is android.app.Activity -> context
                is androidx.appcompat.app.AppCompatActivity -> context
                else -> {
                    // 如果不是Activity context，直接显示Toast
                    Toast.makeText(context, "${appConfig.appName} 尚未安装", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            AlertDialog.Builder(activityContext)
                .setTitle("应用未安装")
                .setMessage("${appConfig.appName} 尚未安装，是否前往应用商店安装？")
                .setPositiveButton("去安装") { _, _ ->
                    try {
                        // 尝试打开应用商店
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("market://details?id=${appConfig.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            // 如果应用商店不可用，尝试打开浏览器
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://play.google.com/store/apps/details?id=${appConfig.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "无法打开应用商店", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            // 如果对话框创建失败，显示简单的Toast
            Toast.makeText(context, "${appConfig.appName} 尚未安装，请手动安装", Toast.LENGTH_LONG).show()
            android.util.Log.e("AppSearchGridAdapter", "显示安装对话框失败", e)
        }
    }

    /**
     * 显示应用信息
     */
    private fun showAppInfo(appConfig: AppSearchConfig, isInstalled: Boolean) {
        val message = buildString {
            append("应用名称: ${appConfig.appName}\n")
            append("包名: ${appConfig.packageName}\n")
            append("分类: ${appConfig.category.displayName}\n")
            append("状态: ${if (isInstalled) "已安装" else "未安装"}\n")
            if (appConfig.description.isNotEmpty()) {
                append("描述: ${appConfig.description}")
            }
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
