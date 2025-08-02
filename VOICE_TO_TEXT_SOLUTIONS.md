# 国内手机录音转文本解决方案

## 🎯 可用的内置方案

### 1. 系统语音输入Intent (推荐)
```kotlin
// 最通用的方案，适用于所有Android设备
private fun trySystemVoiceInput() {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    
    val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (activities.isNotEmpty()) {
        startActivityForResult(intent, VOICE_REQUEST_CODE)
    }
}
```

### 2. 输入法语音输入
```kotlin
// 调用系统输入法的语音输入功能
private fun triggerIMEVoiceInput() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    
    // 让EditText获取焦点
    editText.requestFocus()
    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    
    // 提示用户点击输入法的语音按钮
    Toast.makeText(this, "请点击输入法上的语音按钮", Toast.LENGTH_LONG).show()
}
```

### 3. 系统设置中的语音助手
```kotlin
// 调用系统语音助手设置
private fun openVoiceAssistantSettings() {
    try {
        val intent = Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // 备用方案：打开语音设置
            val settingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            startActivity(settingsIntent)
        }
    } catch (e: Exception) {
        // 最后备用方案：打开应用设置
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }
}
```

## 📱 国产手机特定方案

### OPPO ColorOS
```kotlin
private fun tryOppoVoiceInput() {
    try {
        // OPPO Breeno语音助手
        val intent = Intent().apply {
            setClassName("com.oppo.breeno.assistant", 
                       "com.oppo.breeno.assistant.VoiceActivity")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            return
        }
        
        // OPPO输入法语音
        val oppoIME = Intent().apply {
            setClassName("com.oppo.inputmethod", 
                       "com.oppo.inputmethod.voice.VoiceInputActivity")
        }
        if (oppoIME.resolveActivity(packageManager) != null) {
            startActivity(oppoIME)
            return
        }
        
        // 通用方案
        trySystemVoiceInput()
    } catch (e: Exception) {
        trySystemVoiceInput()
    }
}
```

### vivo FuntouchOS
```kotlin
private fun tryVivoVoiceInput() {
    try {
        // vivo Jovi语音助手
        val intent = Intent().apply {
            setClassName("com.vivo.assistant", 
                       "com.vivo.assistant.VoiceActivity")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            return
        }
        
        // vivo输入法语音
        val vivoIME = Intent().apply {
            setClassName("com.vivo.inputmethod", 
                       "com.vivo.inputmethod.voice.VoiceActivity")
        }
        if (vivoIME.resolveActivity(packageManager) != null) {
            startActivity(vivoIME)
            return
        }
        
        // 通用方案
        trySystemVoiceInput()
    } catch (e: Exception) {
        trySystemVoiceInput()
    }
}
```

### 小米MIUI
```kotlin
private fun tryMiuiVoiceInput() {
    try {
        // 小爱同学
        val intent = Intent().apply {
            setClassName("com.miui.voiceassist", 
                       "com.miui.voiceassist.VoiceAssistActivity")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            return
        }
        
        // 搜狗输入法小米版
        val sogouIME = Intent().apply {
            setClassName("com.sohu.inputmethod.sogou.xiaomi", 
                       "com.sohu.inputmethod.sogou.voice.VoiceActivity")
        }
        if (sogouIME.resolveActivity(packageManager) != null) {
            startActivity(sogouIME)
            return
        }
        
        trySystemVoiceInput()
    } catch (e: Exception) {
        trySystemVoiceInput()
    }
}
```

## 🔧 智能检测和调用方案

### 设备品牌检测
```kotlin
private fun getDeviceBrand(): String {
    return Build.BRAND.lowercase()
}

private fun getDeviceManufacturer(): String {
    return Build.MANUFACTURER.lowercase()
}

private fun tryBrandSpecificVoiceInput() {
    val brand = getDeviceBrand()
    val manufacturer = getDeviceManufacturer()
    
    when {
        brand.contains("oppo") || manufacturer.contains("oppo") -> {
            tryOppoVoiceInput()
        }
        brand.contains("vivo") || manufacturer.contains("vivo") -> {
            tryVivoVoiceInput()
        }
        brand.contains("xiaomi") || manufacturer.contains("xiaomi") -> {
            tryMiuiVoiceInput()
        }
        brand.contains("huawei") || manufacturer.contains("huawei") -> {
            tryHuaweiVoiceInput()
        }
        else -> {
            trySystemVoiceInput()
        }
    }
}
```

### 输入法检测
```kotlin
private fun detectAndUseIMEVoice() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledIMEs = imm.enabledInputMethodList
    
    for (ime in enabledIMEs) {
        val packageName = ime.packageName
        when {
            packageName.contains("sogou") -> {
                trySogouVoiceInput()
                return
            }
            packageName.contains("baidu") -> {
                tryBaiduVoiceInput()
                return
            }
            packageName.contains("iflytek") -> {
                tryIflytekVoiceInput()
                return
            }
        }
    }
    
    // 如果没有找到特定输入法，使用通用方案
    trySystemVoiceInput()
}
```

## 📋 完整的多层级解决方案

```kotlin
class VoiceInputManager(private val context: Context) {
    
    fun startVoiceInput() {
        // 第1层：尝试SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            startSpeechRecognizer()
            return
        }
        
        // 第2层：尝试品牌特定方案
        if (tryBrandSpecificVoiceInput()) {
            return
        }
        
        // 第3层：尝试系统语音输入Intent
        if (trySystemVoiceInput()) {
            return
        }
        
        // 第4层：尝试输入法语音输入
        if (tryIMEVoiceInput()) {
            return
        }
        
        // 第5层：提供手动输入选项
        showManualInputOption()
    }
    
    private fun tryBrandSpecificVoiceInput(): Boolean {
        return when (getDeviceBrand()) {
            "oppo" -> tryOppoVoiceInput()
            "vivo" -> tryVivoVoiceInput()
            "xiaomi" -> tryMiuiVoiceInput()
            "huawei" -> tryHuaweiVoiceInput()
            else -> false
        }
    }
    
    private fun tryIMEVoiceInput(): Boolean {
        val commonIMEs = listOf(
            "com.sohu.inputmethod.sogou",      // 搜狗输入法
            "com.baidu.input",                 // 百度输入法
            "com.iflytek.inputmethod",         // 讯飞输入法
            "com.tencent.qqpinyin",           // QQ输入法
            "com.google.android.inputmethod.latin" // Google输入法
        )
        
        for (imePackage in commonIMEs) {
            if (isPackageInstalled(imePackage)) {
                return trySpecificIMEVoice(imePackage)
            }
        }
        
        return false
    }
}
```

## 🎯 实际应用建议

### 1. 优先级策略
1. **SpeechRecognizer** - 最佳体验
2. **品牌特定方案** - 针对性优化
3. **系统语音Intent** - 通用兼容
4. **输入法语音** - 广泛支持
5. **手动输入** - 最后备用

### 2. 用户体验优化
```kotlin
private fun showVoiceInputOptions() {
    val options = mutableListOf<String>()
    val actions = mutableListOf<() -> Unit>()
    
    // 检测可用选项
    if (SpeechRecognizer.isRecognitionAvailable(this)) {
        options.add("使用系统语音识别")
        actions.add { startSpeechRecognizer() }
    }
    
    if (hasSystemVoiceInput()) {
        options.add("使用系统语音输入")
        actions.add { trySystemVoiceInput() }
    }
    
    if (hasIMEVoiceInput()) {
        options.add("使用输入法语音")
        actions.add { tryIMEVoiceInput() }
    }
    
    options.add("手动输入")
    actions.add { showManualInput() }
    
    // 显示选择对话框
    AlertDialog.Builder(this)
        .setTitle("选择输入方式")
        .setItems(options.toTypedArray()) { _, which ->
            actions[which].invoke()
        }
        .show()
}
```

这个方案确保了在各种国产手机上都能找到合适的语音转文本解决方案。
