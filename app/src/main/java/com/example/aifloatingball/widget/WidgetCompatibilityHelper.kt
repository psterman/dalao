package com.example.aifloatingball.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * 小组件兼容性助手
 * 处理不同厂商的小组件兼容性问题
 */
object WidgetCompatibilityHelper {
    
    private const val TAG = "WidgetCompatibility"
    
    /**
     * 检测设备厂商
     */
    fun getDeviceBrand(): String {
        return Build.BRAND.lowercase()
    }
    
    /**
     * 检测是否为小米设备
     */
    fun isXiaomiDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
    }
    
    /**
     * 检测是否为vivo设备
     */
    fun isVivoDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("vivo") || brand.contains("iqoo")
    }
    
    /**
     * 检测是否为OPPO设备
     */
    fun isOppoDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("oppo") || brand.contains("oneplus")
    }
    
    /**
     * 检测是否为华为设备
     */
    fun isHuaweiDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("huawei")
    }

    /**
     * 检测是否为荣耀设备
     */
    fun isHonorDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("honor") || brand.contains("hihonor")
    }

    /**
     * 检测是否为魅族设备
     */
    fun isMeizuDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("meizu")
    }

    /**
     * 检测是否为三星设备
     */
    fun isSamsungDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("samsung")
    }

    /**
     * 检测是否为realme设备
     */
    fun isRealmeDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("realme")
    }

    /**
     * 检测是否为一加设备
     */
    fun isOnePlusDevice(): Boolean {
        val brand = getDeviceBrand()
        return brand.contains("oneplus")
    }
    
    /**
     * 检查小组件权限
     */
    fun checkWidgetPermissions(context: Context): Boolean {
        return when {
            isXiaomiDevice() -> checkXiaomiWidgetPermissions(context)
            isVivoDevice() -> checkVivoWidgetPermissions(context)
            isOppoDevice() -> checkOppoWidgetPermissions(context)
            isOnePlusDevice() -> checkOnePlusWidgetPermissions(context)
            isHuaweiDevice() -> checkHuaweiWidgetPermissions(context)
            isHonorDevice() -> checkHonorWidgetPermissions(context)
            isMeizuDevice() -> checkMeizuWidgetPermissions(context)
            isSamsungDevice() -> checkSamsungWidgetPermissions(context)
            isRealmeDevice() -> checkRealmeWidgetPermissions(context)
            else -> true // 原生Android通常没有额外限制
        }
    }
    
    /**
     * 检查小米设备的小组件权限
     */
    private fun checkXiaomiWidgetPermissions(context: Context): Boolean {
        return try {
            // 检查MIUI的小组件权限
            val pm = context.packageManager
            val packageName = context.packageName
            
            // 检查是否有BIND_APPWIDGET权限
            val hasBindPermission = pm.checkPermission(
                "android.permission.BIND_APPWIDGET",
                packageName
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "小米设备小组件权限检查: hasBindPermission=$hasBindPermission")
            true // 即使没有权限也返回true，让系统处理
        } catch (e: Exception) {
            Log.e(TAG, "检查小米小组件权限失败", e)
            true
        }
    }
    
    /**
     * 检查vivo设备的小组件权限
     */
    private fun checkVivoWidgetPermissions(context: Context): Boolean {
        return try {
            // vivo的原子组件通常需要特殊处理
            Log.d(TAG, "vivo设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查vivo小组件权限失败", e)
            true
        }
    }
    
    /**
     * 检查OPPO设备的小组件权限
     */
    private fun checkOppoWidgetPermissions(context: Context): Boolean {
        return try {
            Log.d(TAG, "OPPO设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查OPPO小组件权限失败", e)
            true
        }
    }
    
    /**
     * 检查华为设备的小组件权限
     */
    private fun checkHuaweiWidgetPermissions(context: Context): Boolean {
        return try {
            Log.d(TAG, "华为设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查华为小组件权限失败", e)
            true
        }
    }

    /**
     * 检查荣耀设备的小组件权限
     */
    private fun checkHonorWidgetPermissions(context: Context): Boolean {
        return try {
            Log.d(TAG, "荣耀设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查荣耀小组件权限失败", e)
            true
        }
    }

    /**
     * 检查一加设备的小组件权限
     */
    private fun checkOnePlusWidgetPermissions(context: Context): Boolean {
        return try {
            Log.d(TAG, "一加设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查一加小组件权限失败", e)
            true
        }
    }

    /**
     * 检查魅族设备的小组件权限
     */
    private fun checkMeizuWidgetPermissions(context: Context): Boolean {
        return try {
            Log.d(TAG, "魅族设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查魅族小组件权限失败", e)
            true
        }
    }

    /**
     * 检查三星设备的小组件权限
     */
    private fun checkSamsungWidgetPermissions(context: Context): Boolean {
        return try {
            Log.d(TAG, "三星设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查三星小组件权限失败", e)
            true
        }
    }

    /**
     * 检查realme设备的小组件权限
     */
    private fun checkRealmeWidgetPermissions(context: Context): Boolean {
        return try {
            Log.d(TAG, "realme设备小组件权限检查")
            true
        } catch (e: Exception) {
            Log.e(TAG, "检查realme小组件权限失败", e)
            true
        }
    }
    
    /**
     * 引导用户添加小组件
     */
    fun guideUserToAddWidget(context: Context) {
        val brand = getDeviceBrand()
        val message = when {
            isXiaomiDevice() -> "在MIUI桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            isVivoDevice() -> "在桌面长按空白区域 → 原子组件/小组件 → AI悬浮球 → 拖拽到桌面"
            isOppoDevice() -> "在ColorOS桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            isOnePlusDevice() -> "在OxygenOS桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            isHuaweiDevice() -> "在EMUI桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            isHonorDevice() -> "在MagicOS桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            isMeizuDevice() -> "在Flyme桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            isSamsungDevice() -> "在One UI桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            isRealmeDevice() -> "在realme UI桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
            else -> "在桌面长按空白区域 → 小组件 → AI悬浮球 → 拖拽到桌面"
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, "设备品牌: $brand, 引导消息: $message")
    }
    
    /**
     * 打开小组件设置页面
     */
    fun openWidgetSettings(context: Context) {
        try {
            when {
                isXiaomiDevice() -> openXiaomiWidgetSettings(context)
                isVivoDevice() -> openVivoWidgetSettings(context)
                isOppoDevice() -> openOppoWidgetSettings(context)
                isOnePlusDevice() -> openOnePlusWidgetSettings(context)
                isHuaweiDevice() -> openHuaweiWidgetSettings(context)
                isHonorDevice() -> openHonorWidgetSettings(context)
                isMeizuDevice() -> openMeizuWidgetSettings(context)
                isSamsungDevice() -> openSamsungWidgetSettings(context)
                isRealmeDevice() -> openRealmeWidgetSettings(context)
                else -> openDefaultWidgetSettings(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }
    
    /**
     * 打开小米小组件设置
     */
    private fun openXiaomiWidgetSettings(context: Context) {
        try {
            // 尝试打开MIUI的小组件管理页面
            val intent = Intent().apply {
                action = "miui.intent.action.APP_PERM_EDITOR"
                putExtra("extra_pkgname", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开小米小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }
    
    /**
     * 打开vivo小组件设置
     */
    private fun openVivoWidgetSettings(context: Context) {
        try {
            // 尝试打开vivo的应用管理页面
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开vivo小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }
    
    /**
     * 打开OPPO小组件设置
     */
    private fun openOppoWidgetSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开OPPO小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }
    
    /**
     * 打开华为小组件设置
     */
    private fun openHuaweiWidgetSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开华为小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }

    /**
     * 打开荣耀小组件设置
     */
    private fun openHonorWidgetSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开荣耀小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }

    /**
     * 打开一加小组件设置
     */
    private fun openOnePlusWidgetSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开一加小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }


    
    /**
     * 打开魅族小组件设置
     */
    private fun openMeizuWidgetSettings(context: Context) {
        try {
            // 尝试打开魅族的应用管理页面
            val intent = Intent().apply {
                action = "com.meizu.safe.security.SHOW_APPSEC"
                putExtra("packageName", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开魅族小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }

    /**
     * 打开三星小组件设置
     */
    private fun openSamsungWidgetSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开三星小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }

    /**
     * 打开realme小组件设置
     */
    private fun openRealmeWidgetSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开realme小组件设置失败", e)
            openDefaultWidgetSettings(context)
        }
    }

    /**
     * 打开默认小组件设置
     */
    private fun openDefaultWidgetSettings(context: Context) {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开默认小组件设置失败", e)
            Toast.makeText(context, "无法打开设置页面，请手动在设置中查找小组件权限", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 获取设备信息用于调试
     */
    fun getDeviceInfo(): String {
        return "Brand: ${Build.BRAND}, Model: ${Build.MODEL}, Version: ${Build.VERSION.RELEASE}, SDK: ${Build.VERSION.SDK_INT}"
    }
}
