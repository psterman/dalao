package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.service.SimpleModeService

/**
 * 测试活动，用于验证简易模式的最小化和关闭功能
 */
class TestMinimizeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_minimize)

        val statusText = findViewById<TextView>(R.id.status_text)
        val startButton = findViewById<Button>(R.id.start_service_button)
        val minimizeButton = findViewById<Button>(R.id.test_minimize_button)
        val closeButton = findViewById<Button>(R.id.test_close_button)

        updateStatus(statusText)

        startButton.setOnClickListener {
            if (!SimpleModeService.isRunning(this)) {
                val intent = Intent(this, SimpleModeService::class.java)
                startService(intent)
                Toast.makeText(this, "简易模式服务已启动", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "简易模式服务已经在运行", Toast.LENGTH_SHORT).show()
            }
            updateStatus(statusText)
        }

        minimizeButton.setOnClickListener {
            if (SimpleModeService.isRunning(this)) {
                val intent = Intent("com.example.aifloatingball.ACTION_MINIMIZE_SIMPLE_MODE")
                sendBroadcast(intent)
                Toast.makeText(this, "已发送最小化命令", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "简易模式服务未运行", Toast.LENGTH_SHORT).show()
            }
        }

        closeButton.setOnClickListener {
            if (SimpleModeService.isRunning(this)) {
                val intent = Intent("com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE")
                sendBroadcast(intent)
                Toast.makeText(this, "已发送关闭命令", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "简易模式服务未运行", Toast.LENGTH_SHORT).show()
            }
            updateStatus(statusText)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.status_text))
    }

    private fun updateStatus(statusText: TextView) {
        val isRunning = SimpleModeService.isRunning(this)
        statusText.text = "简易模式服务状态: ${if (isRunning) "运行中" else "未运行"}"
    }
} 