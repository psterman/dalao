package com.example.aifloatingball.utils

import android.util.Log
import com.example.aifloatingball.BuildConfig

/**
 * 语音识别日志工具类
 * 在 Release 包中自动移除日志，避免字符串常量增大 APK 并影响性能
 * 
 * 使用 BuildConfig.DEBUG 控制日志输出：
 * - Debug 构建：正常输出日志
 * - Release 构建：日志代码会被编译器优化移除，字符串常量不会被打包
 */
object VoiceLog {
    private const val TAG = "VoiceRecognition"
    
    /**
     * 输出 Debug 级别日志
     * 
     * @param message 日志消息
     */
    @JvmStatic
    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
    
    /**
     * 输出 Error 级别日志
     * 
     * @param message 日志消息
     * @param throwable 可选的异常对象
     */
    @JvmStatic
    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }
    
    /**
     * 输出 Warning 级别日志
     * 
     * @param message 日志消息
     */
    @JvmStatic
    fun w(message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message)
        }
    }
    
    /**
     * 输出 Info 级别日志
     * 
     * @param message 日志消息
     */
    @JvmStatic
    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, message)
        }
    }
    
    /**
     * 输出 Verbose 级别日志
     * 
     * @param message 日志消息
     */
    @JvmStatic
    fun v(message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, message)
        }
    }
}


