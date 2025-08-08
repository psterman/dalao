package com.example.aifloatingball

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.aifloatingball.manager.DeepSeekApiHelper
import com.example.aifloatingball.SettingsManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * DeepSeek API诊断活动
 * 专门用于诊断和解决DeepSeek API连接问题
 */
class DeepSeekDiagnosticActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var deepSeekApiHelper: DeepSeekApiHelper
    
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var diagnoseButton: MaterialButton
    private lateinit var testConnectionButton: MaterialButton
    private lateinit var testModelsButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var resultCard: MaterialCardView
    private lateinit var resultText: TextView
    private lateinit var copyButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    
    companion object {
        private const val TAG = "DeepSeekDiagnostic"
        
        fun start(context: Context) {
            val intent = Intent(context, DeepSeekDiagnosticActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deepseek_diagnostic)
        
        initViews()
        initManagers()
        setupToolbar()
        loadCurrentApiKey()
        setupListeners()
    }
    
    private fun initViews() {
        apiKeyLayout = findViewById(R.id.api_key_layout)
        apiKeyInput = findViewById(R.id.api_key_input)
        diagnoseButton = findViewById(R.id.diagnose_button)
        testConnectionButton = findViewById(R.id.test_connection_button)
        testModelsButton = findViewById(R.id.test_models_button)
        progressBar = findViewById(R.id.progress_bar)
        resultCard = findViewById(R.id.result_card)
        resultText = findViewById(R.id.result_text)
        copyButton = findViewById(R.id.copy_button)
        shareButton = findViewById(R.id.share_button)
    }
    
    private fun initManagers() {
        settingsManager = SettingsManager.getInstance(this)
        deepSeekApiHelper = DeepSeekApiHelper(this)
    }
    
    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "DeepSeek API诊断"
        }
    }
    
    private fun loadCurrentApiKey() {
        val currentApiKey = settingsManager.getString("deepseek_api_key", "") ?: ""
        apiKeyInput.setText(currentApiKey)
    }
    
    private fun setupListeners() {
        diagnoseButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                startDiagnosis(apiKey)
            } else {
                showError("请输入DeepSeek API密钥")
            }
        }
        
        testConnectionButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                testConnection(apiKey)
            } else {
                showError("请输入DeepSeek API密钥")
            }
        }
        
        testModelsButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                testModels(apiKey)
            } else {
                showError("请输入DeepSeek API密钥")
            }
        }
        
        copyButton.setOnClickListener {
            copyResultToClipboard()
        }
        
        shareButton.setOnClickListener {
            shareResult()
        }
        
        // API密钥输入监听
        apiKeyInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val apiKey = s.toString().trim()
                validateApiKeyFormat(apiKey)
            }
        })
    }
    
    private fun validateApiKeyFormat(apiKey: String) {
        if (apiKey.isEmpty()) {
            apiKeyLayout.error = null
            return
        }
        
        val issues = mutableListOf<String>()
        
        if (!apiKey.startsWith("sk-")) {
            issues.add("应该以'sk-'开头")
        }
        
        if (apiKey.length < 20) {
            issues.add("长度不足（至少20个字符）")
        }
        
        if (apiKey.length > 100) {
            issues.add("长度过长（可能包含多余字符）")
        }
        
        val invalidChars = apiKey.filter { !it.isLetterOrDigit() && it != '-' && it != '_' }
        if (invalidChars.isNotEmpty()) {
            issues.add("包含无效字符: $invalidChars")
        }
        
        if (issues.isNotEmpty()) {
            apiKeyLayout.error = issues.joinToString("; ")
        } else {
            apiKeyLayout.error = null
        }
    }
    
    private fun startDiagnosis(apiKey: String) {
        showProgress(true)
        resultCard.visibility = View.GONE
        
        deepSeekApiHelper.diagnoseDeepSeekApi(apiKey) { result ->
            runOnUiThread {
                showProgress(false)
                displayDiagnosisResult(result)
            }
        }
    }
    
    private fun testConnection(apiKey: String) {
        showProgress(true)
        resultCard.visibility = View.GONE
        
        deepSeekApiHelper.testDeepSeekConnection(apiKey) { success, message ->
            runOnUiThread {
                showProgress(false)
                val result = if (success) {
                    "✓ 连接测试成功\n\n$message"
                } else {
                    "✗ 连接测试失败\n\n$message"
                }
                displayResult(result)
            }
        }
    }
    
    private fun testModels(apiKey: String) {
        showProgress(true)
        resultCard.visibility = View.GONE
        
        deepSeekApiHelper.getAvailableModels(apiKey) { success, models, message ->
            runOnUiThread {
                showProgress(false)
                val result = if (success) {
                    "✓ 模型列表获取成功\n\n可用模型:\n${models.joinToString("\n")}\n\n$message"
                } else {
                    "✗ 模型列表获取失败\n\n$message"
                }
                displayResult(result)
            }
        }
    }
    
    private fun displayDiagnosisResult(result: DeepSeekApiHelper.DiagnosisResult) {
        if (result.error != null) {
            displayResult("诊断过程出错:\n\n${result.error}")
            return
        }
        
        displayResult(result.report)
        
        // 如果发现401错误，显示特殊提示
        if (!result.authentication.isValid && result.authentication.message.contains("401")) {
            show401ErrorDialog()
        }
    }
    
    private fun displayResult(result: String) {
        resultText.text = result
        resultCard.visibility = View.VISIBLE
        copyButton.visibility = View.VISIBLE
        shareButton.visibility = View.VISIBLE
    }
    
    private fun show401ErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("发现401认证错误")
            .setMessage("您的DeepSeek API密钥可能存在问题。常见原因：\n\n" +
                    "1. API密钥格式错误\n" +
                    "2. API密钥已过期\n" +
                    "3. API密钥包含多余的空格或换行符\n" +
                    "4. 账户余额不足\n" +
                    "5. API密钥权限不足\n\n" +
                    "建议操作：\n" +
                    "• 重新生成API密钥\n" +
                    "• 检查账户状态\n" +
                    "• 确认API密钥格式")
            .setPositiveButton("重新生成密钥") { _, _ ->
                openDeepSeekWebsite()
            }
            .setNegativeButton("我知道了", null)
            .setNeutralButton("复制诊断报告") { _, _ ->
                copyResultToClipboard()
            }
            .show()
    }
    
    private fun openDeepSeekWebsite() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://platform.deepseek.com/"))
            startActivity(intent)
        } catch (e: Exception) {
            showError("无法打开DeepSeek网站: ${e.message}")
        }
    }
    
    private fun copyResultToClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("DeepSeek诊断报告", resultText.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "诊断报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError("复制失败: ${e.message}")
        }
    }
    
    private fun shareResult() {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "DeepSeek API诊断报告")
                putExtra(Intent.EXTRA_TEXT, resultText.text.toString())
            }
            startActivity(Intent.createChooser(shareIntent, "分享诊断报告"))
        } catch (e: Exception) {
            showError("分享失败: ${e.message}")
        }
    }
    
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        diagnoseButton.isEnabled = !show
        testConnectionButton.isEnabled = !show
        testModelsButton.isEnabled = !show
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        // 注意：DeepSeekApiHelper的scope会在其内部管理
    }
}
