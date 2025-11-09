package com.example.aifloatingball

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.SettingsManager
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream

/**
 * 首页综合设置Activity
 * 提供首页布局样式、自定义封面、app样式、网络设置等功能
 */
class HomeSettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "HomeSettingsActivity"
        private const val REQUEST_CODE_PICK_IMAGE_1 = 1001
        private const val REQUEST_CODE_PICK_IMAGE_2 = 1002
        private const val REQUEST_CODE_PICK_IMAGE_3 = 1003
    }
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var appStyleRecyclerView: RecyclerView
    private lateinit var floatingNetworkSpeedSwitch: SwitchCompat
    private lateinit var downloadProgressSwitch: SwitchCompat
    private lateinit var coverImageView1: ImageView
    private lateinit var coverImageView2: ImageView
    private lateinit var coverImageView3: ImageView
    private lateinit var layoutPreviewGrid: com.example.aifloatingball.views.DraggableButtonGrid
    
    // 布局样式选项
    private val layoutStyles = listOf(
        LayoutStyle("默认布局", "default", R.drawable.ic_settings),
        LayoutStyle("紧凑布局", "compact", R.drawable.ic_settings),
        LayoutStyle("宽松布局", "spacious", R.drawable.ic_settings),
        LayoutStyle("自定义布局", "custom", R.drawable.ic_settings)
    )
    
    // App样式选项（iOS风格：深色、浅色、跟随系统）
    private val appStyles = listOf(
        AppStyle("浅色", "light", R.drawable.ic_settings),
        AppStyle("深色", "dark", R.drawable.ic_settings),
        AppStyle("跟随系统", "system", R.drawable.ic_settings)
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        initViews()
        setupAppStyleSelector()
        setupFloatingWindow()
        setupCoverImage()
        setupLayoutPreviewGrid()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次显示设置页面时，刷新按钮配置（确保与首页同步）
        layoutPreviewGrid.refreshButtonConfig()
    }
    
    /**
     * 初始化视图
     */
    private fun initViews() {
        appStyleRecyclerView = findViewById(R.id.app_style_recycler_view)
        floatingNetworkSpeedSwitch = findViewById(R.id.floating_network_speed_switch)
        downloadProgressSwitch = findViewById(R.id.download_progress_switch)
        coverImageView1 = findViewById(R.id.cover_image_view_1)
        coverImageView2 = findViewById(R.id.cover_image_view_2)
        coverImageView3 = findViewById(R.id.cover_image_view_3)
        layoutPreviewGrid = findViewById(R.id.layout_preview_grid)
        
        // 设置返回按钮
        findViewById<ImageButton>(R.id.back_button)?.setOnClickListener {
            finish()
        }
        
        // 不再强制设置所有按钮为可见，而是加载已保存的配置
        // 这样用户的选择会被正确保留
        layoutPreviewGrid.post {
            // 刷新按钮配置，确保显示的是用户保存的状态
            layoutPreviewGrid.refreshButtonConfig()
        }
        
        // 设置按钮点击监听（不弹出预览模式弹窗，直接同步到首页）
        layoutPreviewGrid.setOnButtonClickListener { buttonType ->
            // 按钮点击已经在DraggableButtonGrid内部处理（切换选中状态）
            // 这里不需要额外处理，配置会自动保存并同步到首页
            Log.d(TAG, "按钮点击：$buttonType，配置已自动保存并同步")
        }
    }
    
    /**
     * 设置首页布局预览网格
     */
    private fun setupLayoutPreviewGrid() {
        // 当按钮可见性改变时，自动保存配置
        // DraggableButtonGrid 内部已经实现了自动保存，这里只需要确保UI更新
        // 如果需要，可以添加额外的监听逻辑
    }
    
    
    /**
     * 设置App样式选择器
     */
    private fun setupAppStyleSelector() {
        val adapter = AppStyleAdapter(appStyles) { style ->
            settingsManager.putString("app_theme_style", style.value)
            Log.d(TAG, "App样式已切换为: ${style.value}")
            
            // 立即应用主题
            applyTheme(style.value)
            
            // 重新创建Activity以应用主题
            recreate()
        }
        
        appStyleRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appStyleRecyclerView.adapter = adapter
        
        // 设置当前选中的样式
        val currentStyle = settingsManager.getString("app_theme_style", "system")
        val currentIndex = appStyles.indexOfFirst { it.value == currentStyle }
        if (currentIndex >= 0) {
            adapter.setSelectedIndex(currentIndex)
        }
    }
    
    /**
     * 设置悬浮窗功能
     */
    private fun setupFloatingWindow() {
        // 悬浮窗网速显示开关（合并功能）
        val isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
        floatingNetworkSpeedSwitch.isChecked = isNetworkSpeedEnabled
        floatingNetworkSpeedSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.putBoolean("network_speed_display_enabled", isChecked)
            Log.d(TAG, "悬浮窗网速显示开关: $isChecked")
            // 启动/停止网速显示服务
            val intent = Intent(this, com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java)
            if (isChecked) {
                // 检查悬浮窗权限（Android 6.0+需要）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!android.provider.Settings.canDrawOverlays(this)) {
                        // 没有权限，提示用户
                        Toast.makeText(this, "需要悬浮窗权限才能显示网速", Toast.LENGTH_LONG).show()
                        // 打开权限设置页面
                        val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        settingsIntent.data = Uri.parse("package:$packageName")
                        try {
                            startActivity(settingsIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "打开悬浮窗权限设置失败", e)
                        }
                        // 恢复开关状态
                        floatingNetworkSpeedSwitch.isChecked = false
                        settingsManager.putBoolean("network_speed_display_enabled", false)
                        return@setOnCheckedChangeListener
                    }
                }
                // 有权限，启动或重启服务（确保服务重新检查设置）
                try {
                    // 先停止服务（如果正在运行），然后重新启动，确保服务重新检查设置
                    stopService(intent)
                    // 延迟一小段时间后重新启动，确保服务完全停止
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                            Log.d(TAG, "悬浮窗网速服务已重新启动")
                        } catch (e: Exception) {
                            Log.e(TAG, "重新启动悬浮窗网速服务失败", e)
                            Toast.makeText(this, "启动服务失败：${e.message}", Toast.LENGTH_SHORT).show()
                            floatingNetworkSpeedSwitch.isChecked = false
                            settingsManager.putBoolean("network_speed_display_enabled", false)
                        }
                    }, 200)
                } catch (e: SecurityException) {
                    Log.e(TAG, "启动悬浮窗网速服务失败：缺少权限", e)
                    Toast.makeText(this, "启动服务失败：缺少权限", Toast.LENGTH_SHORT).show()
                    floatingNetworkSpeedSwitch.isChecked = false
                    settingsManager.putBoolean("network_speed_display_enabled", false)
                } catch (e: Exception) {
                    Log.e(TAG, "启动悬浮窗网速服务失败", e)
                    Toast.makeText(this, "启动服务失败：${e.message}", Toast.LENGTH_SHORT).show()
                    floatingNetworkSpeedSwitch.isChecked = false
                    settingsManager.putBoolean("network_speed_display_enabled", false)
                }
            } else {
                stopService(intent)
                Log.d(TAG, "悬浮窗网速服务已停止")
            }
        }
        
        // 下载进度显示开关
        val isDownloadProgressEnabled = settingsManager.getBoolean("download_progress_display_enabled", false)
        downloadProgressSwitch.isChecked = isDownloadProgressEnabled
        downloadProgressSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.putBoolean("download_progress_display_enabled", isChecked)
            Log.d(TAG, "下载进度显示开关: $isChecked")
            // 启动/停止下载进度显示服务
            val intent = Intent(this, com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java)
            if (isChecked) {
                // 先停止服务（如果正在运行），然后重新启动，确保服务重新检查设置
                stopService(intent)
                // 延迟一小段时间后重新启动，确保服务完全停止
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        Log.d(TAG, "下载进度显示服务已重新启动")
                    } catch (e: Exception) {
                        Log.e(TAG, "重新启动下载进度显示服务失败", e)
                        downloadProgressSwitch.isChecked = false
                        settingsManager.putBoolean("download_progress_display_enabled", false)
                    }
                }, 200)
            } else {
                stopService(intent)
                Log.d(TAG, "下载进度显示服务已停止")
            }
        }
    }
    
    /**
     * 设置封面图片
     */
    private fun setupCoverImage() {
        coverImageView1.setOnClickListener {
            // 打开图片选择器
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_1)
        }
        
        coverImageView2.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_2)
        }
        
        coverImageView3.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_3)
        }
        
        // 加载当前封面
        loadCoverImages()
    }
    
    /**
     * 加载封面图片
     */
    private fun loadCoverImages() {
        try {
            loadCoverImage(1, coverImageView1, "home_cover_image_path_1")
            loadCoverImage(2, coverImageView2, "home_cover_image_path_2")
            loadCoverImage(3, coverImageView3, "home_cover_image_path_3")
        } catch (e: Exception) {
            Log.e(TAG, "加载封面图片失败", e)
        }
    }
    
    /**
     * 加载单个封面图片
     */
    private fun loadCoverImage(index: Int, imageView: ImageView, key: String) {
        try {
            val coverPath = settingsManager.getString(key, "")
            if (coverPath?.isNotEmpty() == true) {
                val file = File(coverPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(coverPath)
                    imageView.setImageBitmap(bitmap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载封面图片${index}失败", e)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode != RESULT_OK || data == null) return
        
        val imageUri: Uri? = data.data
        if (imageUri == null) return
        
        val imageIndex = when (requestCode) {
            REQUEST_CODE_PICK_IMAGE_1 -> 1
            REQUEST_CODE_PICK_IMAGE_2 -> 2
            REQUEST_CODE_PICK_IMAGE_3 -> 3
            else -> return
        }
        
        val imageView = when (imageIndex) {
            1 -> coverImageView1
            2 -> coverImageView2
            3 -> coverImageView3
            else -> return
        }
        
        val key = "home_cover_image_path_$imageIndex"
        
        try {
            // 保存图片到应用目录
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // 保存到文件
            val coverDir = File(getFilesDir(), "covers")
            if (!coverDir.exists()) {
                coverDir.mkdirs()
            }
            val coverFile = File(coverDir, "home_cover_$imageIndex.jpg")
            val outputStream = FileOutputStream(coverFile)
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            
            // 保存路径到设置
            settingsManager.putString(key, coverFile.absolutePath)
            
            // 更新显示
            imageView.setImageBitmap(bitmap)
            
            Toast.makeText(this, "封面图片${imageIndex}设置成功", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "封面图片${imageIndex}已保存: ${coverFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "保存封面图片${imageIndex}失败", e)
            Toast.makeText(this, "封面图片${imageIndex}设置失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 应用主题
     */
    private fun applyTheme(themeValue: String) {
        val themeMode = when (themeValue) {
            "light" -> SettingsManager.THEME_MODE_LIGHT
            "dark" -> SettingsManager.THEME_MODE_DARK
            "system" -> SettingsManager.THEME_MODE_SYSTEM
            else -> SettingsManager.THEME_MODE_SYSTEM
        }
        
        // 使用SettingsManager的setThemeMode方法应用主题
        settingsManager.setThemeMode(themeMode)
        Log.d(TAG, "应用主题: $themeValue (mode: $themeMode)")
    }
    
    
    /**
     * 布局样式数据类
     */
    data class LayoutStyle(
        val name: String,
        val value: String,
        val iconRes: Int
    )
    
    /**
     * App样式数据类
     */
    data class AppStyle(
        val name: String,
        val value: String,
        val iconRes: Int
    )
    
    /**
     * 布局样式适配器
     */
    private class LayoutStyleAdapter(
        private val styles: List<LayoutStyle>,
        private val onItemClick: (LayoutStyle) -> Unit
    ) : RecyclerView.Adapter<LayoutStyleAdapter.ViewHolder>() {
        
        private var selectedIndex = 0
        
        fun setSelectedIndex(index: Int) {
            val oldIndex = selectedIndex
            selectedIndex = index
            notifyItemChanged(oldIndex)
            notifyItemChanged(selectedIndex)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_layout_style, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val style = styles[position]
            holder.nameText.text = style.name
            holder.iconImage.setImageResource(style.iconRes)
            holder.itemView.isSelected = position == selectedIndex
            
            holder.itemView.setOnClickListener {
                setSelectedIndex(position)
                onItemClick(style)
            }
        }
        
        override fun getItemCount() = styles.size
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.style_name_text)
            val iconImage: ImageView = itemView.findViewById(R.id.style_icon_image)
        }
    }
    
    /**
     * App样式适配器
     */
    private class AppStyleAdapter(
        private val styles: List<AppStyle>,
        private val onItemClick: (AppStyle) -> Unit
    ) : RecyclerView.Adapter<AppStyleAdapter.ViewHolder>() {
        
        private var selectedIndex = 0
        
        fun setSelectedIndex(index: Int) {
            val oldIndex = selectedIndex
            selectedIndex = index
            notifyItemChanged(oldIndex)
            notifyItemChanged(selectedIndex)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_style, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val style = styles[position]
            holder.nameText.text = style.name
            holder.iconImage.setImageResource(style.iconRes)
            holder.itemView.isSelected = position == selectedIndex
            
            holder.itemView.setOnClickListener {
                setSelectedIndex(position)
                onItemClick(style)
            }
        }
        
        override fun getItemCount() = styles.size
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.style_name_text)
            val iconImage: ImageView = itemView.findViewById(R.id.style_icon_image)
        }
    }
}

