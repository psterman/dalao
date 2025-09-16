package com.example.aifloatingball

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class PermissionManagementActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            android.util.Log.d("PermissionManagement", "开始创建权限管理Activity")

            setContentView(R.layout.activity_permission_management)
            android.util.Log.d("PermissionManagement", "成功设置内容视图")

            // 设置工具栏
            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            if (toolbar != null) {
                setSupportActionBar(toolbar)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.title = "权限管理"
                android.util.Log.d("PermissionManagement", "成功设置工具栏")
            } else {
                android.util.Log.w("PermissionManagement", "找不到toolbar，使用默认ActionBar")
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.title = "权限管理"
            }

            // 检查Fragment容器是否存在
            val container = findViewById<android.widget.FrameLayout>(R.id.permission_fragment_container)
            if (container == null) {
                android.util.Log.e("PermissionManagement", "找不到Fragment容器")
                android.widget.Toast.makeText(this, "界面加载失败：找不到容器", android.widget.Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // 加载权限管理Fragment
            if (savedInstanceState == null) {
                try {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.permission_fragment_container, PermissionManagementFragment())
                        .commit()
                    android.util.Log.d("PermissionManagement", "成功加载Fragment")
                } catch (e: Exception) {
                    android.util.Log.e("PermissionManagement", "加载Fragment失败", e)
                    android.widget.Toast.makeText(this, "加载权限设置失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    finish()
                }
            }

            android.util.Log.d("PermissionManagement", "权限管理Activity创建完成")
        } catch (e: Exception) {
            android.util.Log.e("PermissionManagement", "创建权限管理Activity失败", e)
            android.widget.Toast.makeText(this, "加载权限管理失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    class PermissionManagementFragment : PreferenceFragmentCompat() {
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            try {
                android.util.Log.d("PermissionManagementFragment", "开始创建偏好设置")

                setPreferencesFromResource(R.xml.permission_management_preferences, rootKey)
                android.util.Log.d("PermissionManagementFragment", "成功加载偏好设置XML")

                setupPermissionPreferences()
                android.util.Log.d("PermissionManagementFragment", "成功设置权限偏好")

                android.util.Log.d("PermissionManagementFragment", "偏好设置创建完成")
            } catch (e: Exception) {
                android.util.Log.e("PermissionManagementFragment", "创建偏好设置失败", e)
                // 不要让Fragment崩溃，而是显示错误信息
                android.widget.Toast.makeText(context, "加载权限设置失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        private fun setupPermissionPreferences() {
            try {
                android.util.Log.d("PermissionManagementFragment", "开始设置权限偏好")

                // 悬浮窗权限
                val overlayPref = findPreference<Preference>("overlay_permission")
                if (overlayPref != null) {
                    overlayPref.setOnPreferenceClickListener {
                        requestOverlayPermission()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置悬浮窗权限偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到overlay_permission偏好")
                }

                // 录音权限
                val recordPref = findPreference<Preference>("record_audio_permission")
                if (recordPref != null) {
                    recordPref.setOnPreferenceClickListener {
                        requestRecordAudioPermission()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置录音权限偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到record_audio_permission偏好")
                }

                // 通知权限
                val notificationPref = findPreference<Preference>("notification_permission")
                if (notificationPref != null) {
                    notificationPref.setOnPreferenceClickListener {
                        requestNotificationPermission()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置通知权限偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到notification_permission偏好")
                }

                // 通知监听权限
                val listenerPref = findPreference<Preference>("notification_listener_permission")
                if (listenerPref != null) {
                    listenerPref.setOnPreferenceClickListener {
                        requestNotificationListenerPermission()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置通知监听权限偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到notification_listener_permission偏好")
                }

                // 无障碍服务权限
                val accessibilityPref = findPreference<Preference>("accessibility_service_permission")
                if (accessibilityPref != null) {
                    accessibilityPref.setOnPreferenceClickListener {
                        requestAccessibilityServicePermission()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置无障碍服务权限偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到accessibility_service_permission偏好")
                }

                // 自启动权限
                val autoStartPref = findPreference<Preference>("auto_start_permission")
                if (autoStartPref != null) {
                    autoStartPref.setOnPreferenceClickListener {
                        requestAutoStartPermission()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置自启动权限偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到auto_start_permission偏好")
                }

                // 电池优化白名单
                val batteryPref = findPreference<Preference>("battery_optimization_permission")
                if (batteryPref != null) {
                    batteryPref.setOnPreferenceClickListener {
                        requestBatteryOptimizationPermission()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置电池优化权限偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到battery_optimization_permission偏好")
                }

                // 一键授权
                val oneClickPref = findPreference<Preference>("one_click_authorization")
                if (oneClickPref != null) {
                    oneClickPref.setOnPreferenceClickListener {
                        performOneClickAuthorization()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置一键授权偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到one_click_authorization偏好")
                }

                // 剪贴板测试
                val clipboardTestPref = findPreference<Preference>("clipboard_test")
                if (clipboardTestPref != null) {
                    clipboardTestPref.setOnPreferenceClickListener {
                        startClipboardTest()
                        true
                    }
                    android.util.Log.d("PermissionManagementFragment", "成功设置剪贴板测试偏好")
                } else {
                    android.util.Log.w("PermissionManagementFragment", "找不到clipboard_test偏好")
                }

                // 更新权限状态
                updatePermissionStatus()
                android.util.Log.d("PermissionManagementFragment", "权限偏好设置完成")
            } catch (e: Exception) {
                android.util.Log.e("PermissionManagementFragment", "设置权限偏好失败", e)
                android.widget.Toast.makeText(context, "设置权限选项失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun updatePermissionStatus() {
            val context = requireContext()
            
            // 更新悬浮窗权限状态
            val overlayPref = findPreference<Preference>("overlay_permission")
            if (overlayPref != null) {
                val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else {
                    true
                }
                overlayPref.summary = if (hasOverlay) "✓ 已授权" else "✗ 未授权"
            }
            
            // 更新录音权限状态
            val recordPref = findPreference<Preference>("record_audio_permission")
            if (recordPref != null) {
                val hasRecord = ContextCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                recordPref.summary = if (hasRecord) "✓ 已授权" else "✗ 未授权"
            }
            
            // 更新通知权限状态
            val notificationPref = findPreference<Preference>("notification_permission")
            if (notificationPref != null) {
                val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, 
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
                notificationPref.summary = if (hasNotification) "✓ 已授权" else "✗ 未授权"
            }
            
            // 更新通知监听权限状态
            val listenerPref = findPreference<Preference>("notification_listener_permission")
            if (listenerPref != null) {
                val hasListener = NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
                listenerPref.summary = if (hasListener) "✓ 已授权" else "✗ 未授权"
            }

            // 更新无障碍服务权限状态
            val accessibilityPref = findPreference<Preference>("accessibility_service_permission")
            if (accessibilityPref != null) {
                val hasAccessibility = isAccessibilityServiceEnabled(context)
                accessibilityPref.summary = if (hasAccessibility) "✓ 已授权" else "✗ 未授权"
            }
            
            // 更新电池优化状态
            val batteryPref = findPreference<Preference>("battery_optimization_permission")
            if (batteryPref != null) {
                val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true
                }
                batteryPref.summary = if (isIgnoringBatteryOptimizations) "✓ 已添加到白名单" else "✗ 未添加到白名单"
            }
        }
        
        private fun requestOverlayPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(requireContext())) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:" + requireContext().packageName)
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "悬浮窗权限已授权", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        private fun requestRecordAudioPermission() {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), 
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    1001
                )
            } else {
                Toast.makeText(requireContext(), "录音权限已授权", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(), 
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        1002
                    )
                } else {
                    Toast.makeText(requireContext(), "通知权限已授权", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 对于低版本Android，跳转到应用信息页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + requireContext().packageName)
                startActivity(intent)
            }
        }
        
        private fun requestNotificationListenerPermission() {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
        
        private fun requestAutoStartPermission() {
            try {
                val intent = getAutoStartIntent(requireContext())
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开自启动设置，请手动设置", Toast.LENGTH_LONG).show()
            }
        }
        
        private fun requestBatteryOptimizationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
                    val manufacturer = Build.MANUFACTURER.lowercase()
                    if (manufacturer.contains("vivo")) {
                        // Show a guide dialog for Vivo users
                        AlertDialog.Builder(requireContext())
                            .setTitle("Vivo 手机设置引导")
                            .setMessage("为了确保应用在后台稳定运行，请在接下来的页面中，按以下步骤操作：\n\n1. 点击【电量】\n2. 点击【后台耗电管理】\n3. 选择【允许后台高耗电】")
                            .setPositiveButton("去设置") { dialog, _ ->
                                launchBatterySettings()
                                dialog.dismiss()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        // For other manufacturers, go directly
                        launchBatterySettings()
                    }
                } else {
                    Toast.makeText(requireContext(), "已在电池优化白名单中", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        private fun launchBatterySettings() {
            val intent = getBatteryOptimizationIntent(requireContext())
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开电池优化设置，请手动设置", Toast.LENGTH_LONG).show()
            }
        }
        
        private fun performOneClickAuthorization() {
            // 检查并请求所有必要权限
            val permissionsToRequest = mutableListOf<String>()
            
            // 录音权限
            if (ContextCompat.checkSelfPermission(
                    requireContext(), 
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
            }
            
            // 通知权限 (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(), 
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            
            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    permissionsToRequest.toTypedArray(),
                    1000
                )
            } else {
                // 如果基础权限都已授权，检查特殊权限
                var needsSpecialPermissions = false
                
                // 检查悬浮窗权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
                    needsSpecialPermissions = true
                    requestOverlayPermission()
                }
                
                if (!needsSpecialPermissions) {
                    Toast.makeText(requireContext(), "所有权限已授权", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun startClipboardTest() {
            android.util.Log.d("PermissionManagementFragment", "启动剪贴板测试")
            try {
                val intent = Intent(requireContext(), com.example.aifloatingball.debug.ClipboardTestActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("PermissionManagementFragment", "启动剪贴板测试失败", e)
                // 显示错误提示
                Toast.makeText(requireContext(), "启动测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun getAutoStartIntent(context: Context): Intent {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = Intent()
            
            return when {
                manufacturer.contains("xiaomi") -> {
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                    intent
                }
                manufacturer.contains("huawei") -> {
                    intent.component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                    intent
                }
                manufacturer.contains("oppo") -> {
                    intent.component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                    intent
                }
                manufacturer.contains("vivo") -> {
                    intent.component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                    intent
                }
                else -> {
                    // 默认跳转到应用信息页面
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:" + context.packageName)
                    }
                }
            }
        }
        
        private fun getBatteryOptimizationIntent(context: Context): Intent {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = Intent()
            val packageName = context.packageName

            when {
                manufacturer.contains("vivo") -> {
                    // 对于Vivo，直接跳转到本应用的应用详情页，引导用户手动设置
                    // 用户提供路径: 应用管理 -> ai悬浮球 -> 电量 -> 后台耗电管理 -> 允许后台高耗电
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.fromParts("package", context.packageName, null)
                }
                // 可以为其他厂商添加更多case
                // manufacturer.contains("oppo") -> { ... }
                // manufacturer.contains("huawei") -> { ... }
                else -> {
                    // 对于其他厂商，使用标准的Android设置
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        intent.data = Uri.parse("package:$packageName")
                    }
                }
            }
            return intent
        }
        
        private fun requestAccessibilityServicePermission() {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(requireContext(), "请在无障碍设置中找到并开启本应用的无障碍服务", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开无障碍设置页面", Toast.LENGTH_SHORT).show()
            }
        }

        private fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val serviceName = "${context.packageName}/${context.packageName}.service.MyAccessibilityService"
            return enabledServices?.contains(serviceName) == true
        }

        override fun onResume() {
            super.onResume()
            // 当从系统设置返回时，更新权限状态
            updatePermissionStatus()
        }
    }
} 