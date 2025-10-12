package com.example.aifloatingball.download

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK安装管理器
 * 处理APK文件的检测、安装和管理
 */
class ApkInstallManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ApkInstallManager"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
    
    /**
     * 检查是否为APK文件
     */
    fun isApkFile(filePath: String): Boolean {
        return filePath.lowercase().endsWith(".apk")
    }
    
    /**
     * 检查是否为APK文件（通过URL）
     */
    fun isApkUrl(url: String): Boolean {
        return url.lowercase().endsWith(".apk")
    }
    
    /**
     * 获取APK包信息
     */
    fun getApkPackageInfo(apkPath: String): PackageInfo? {
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
        } catch (e: Exception) {
            Log.e(TAG, "获取APK包信息失败: $apkPath", e)
            null
        }
    }
    
    /**
     * 检查APK是否已安装
     */
    fun isApkInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 检查安装权限
     */
    fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 需要未知来源安装权限
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Android 8.0以下默认有权限
            true
        }
    }
    
    /**
     * 请求安装权限
     */
    fun requestInstallPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }
    
    /**
     * 安装APK文件
     */
    fun installApk(apkPath: String): Boolean {
        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                Log.e(TAG, "APK文件不存在: $apkPath")
                Toast.makeText(context, "APK文件不存在", Toast.LENGTH_SHORT).show()
                return false
            }
            
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
            
            if (packageInfo == null) {
                Log.e(TAG, "无法解析APK文件: $apkPath")
                Toast.makeText(context, "APK文件损坏或无法解析", Toast.LENGTH_SHORT).show()
                return false
            }
            
            val packageName = packageInfo.packageName
            val appName = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: "未知应用"
            
            Log.d(TAG, "准备安装APK: $appName ($packageName)")
            
            // 检查是否已安装
            if (isApkInstalled(packageName)) {
                Toast.makeText(context, "$appName 已安装", Toast.LENGTH_SHORT).show()
                return true
            }
            
            // 检查安装权限
            if (!hasInstallPermission()) {
                Toast.makeText(context, "需要安装权限，请在设置中允许安装未知来源应用", Toast.LENGTH_LONG).show()
                return false
            }
            
            // 创建安装Intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+ 使用FileProvider
                    try {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "FileProvider获取URI失败", e)
                        // 备用方案：使用content://
                        Uri.fromFile(apkFile)
                    }
                } else {
                    // Android 7.0以下直接使用file://
                    Uri.fromFile(apkFile)
                }
                
                setDataAndType(apkUri, APK_MIME_TYPE)
            }
            
            context.startActivity(intent)
            Toast.makeText(context, "正在安装 $appName", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "APK安装Intent已启动: $appName")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "安装APK失败: $apkPath", e)
            Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * 卸载应用
     */
    fun uninstallApp(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "正在卸载应用", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "卸载应用失败: $packageName", e)
            Toast.makeText(context, "卸载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * 打开应用
     */
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                true
            } else {
                Toast.makeText(context, "无法启动应用", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败: $packageName", e)
            Toast.makeText(context, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * 获取APK详细信息
     */
    fun getApkInfo(apkPath: String): ApkInfo? {
        val packageInfo = getApkPackageInfo(apkPath) ?: return null
        
        return try {
            val packageManager = context.packageManager
            val appInfo = packageInfo.applicationInfo
            val file = File(apkPath)
            
            ApkInfo(
                packageName = packageInfo.packageName,
                appName = appInfo?.loadLabel(packageManager)?.toString() ?: "未知应用",
                versionName = packageInfo.versionName ?: "未知版本",
                versionCode = packageInfo.longVersionCode,
                fileSize = file.length(),
                filePath = apkPath,
                isInstalled = isApkInstalled(packageInfo.packageName),
                installTime = file.lastModified()
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取APK信息失败: $apkPath", e)
            null
        }
    }
    
    /**
     * APK信息数据类
     */
    data class ApkInfo(
        val packageName: String,
        val appName: String,
        val versionName: String,
        val versionCode: Long,
        val fileSize: Long,
        val filePath: String,
        val isInstalled: Boolean,
        val installTime: Long
    )
}
