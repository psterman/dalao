package com.example.aifloatingball.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * 屏幕录制权限请求Activity
 * 用于处理MediaProjection权限请求
 */
class ScreenCapturePermissionActivity : Activity() {
    
    companion object {
        private const val TAG = "ScreenCapturePermission"
        private const val REQUEST_MEDIA_PROJECTION = 1001
        const val ACTION_PERMISSION_RESULT = "com.example.aifloatingball.SCREEN_CAPTURE_PERMISSION_RESULT"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 获取权限请求Intent
        val captureIntent = intent.getParcelableExtra<Intent>("capture_intent")
        
        if (captureIntent != null) {
            // 启动权限请求
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
        } else {
            Log.e(TAG, "没有找到权限请求Intent")
            sendPermissionResult(RESULT_CANCELED, null)
            finish()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            sendPermissionResult(resultCode, data)
        }
        
        finish()
    }
    
    /**
     * 发送权限请求结果
     */
    private fun sendPermissionResult(resultCode: Int, data: Intent?) {
        val intent = Intent(ACTION_PERMISSION_RESULT).apply {
            putExtra(EXTRA_RESULT_CODE, resultCode)
            if (data != null) {
                putExtra(EXTRA_RESULT_DATA, data)
            }
        }
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "权限请求结果已发送: resultCode=$resultCode")
    }
}
