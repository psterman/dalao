# AI服务多选实现方案

## 方案概述
将现有的单选Spinner改为多选CheckBox模式，支持用户同时选择多个AI服务，实现同步回答和对比查看。

## 技术实现方案

### 1. UI层改造

#### 1.1 替换Spinner为多选布局
```xml
<!-- 替换现有的Spinner -->
<LinearLayout
    android:id="@+id/ai_services_multiselect_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="8dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="选择AI服务（可多选）"
        android:textColor="@color/ai_assistant_text_primary_light"
        android:textSize="12sp"
        android:textStyle="bold"
        android:layout_marginBottom="8dp" />

    <LinearLayout
        android:id="@+id/ai_services_checkbox_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <!-- 动态添加CheckBox -->

    </LinearLayout>

</LinearLayout>
```

#### 1.2 添加全选/清空按钮
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:layout_marginTop="8dp">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_select_all_ai"
        android:layout_width="0dp"
        android:layout_height="32dp"
        android:layout_weight="1"
        android:layout_marginEnd="4dp"
        android:text="全选"
        android:textSize="10sp"
        app:cornerRadius="6dp"
        style="@style/Widget.Material3.Button.OutlinedButton" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_clear_all_ai"
        android:layout_width="0dp"
        android:layout_height="32dp"
        android:layout_weight="1"
        android:layout_marginStart="4dp"
        android:text="清空"
        android:textSize="10sp"
        app:cornerRadius="6dp"
        style="@style/Widget.Material3.Button.OutlinedButton" />

</LinearLayout>
```

### 2. 数据层改造

#### 2.1 创建AI服务选择管理器
```kotlin
class AIServiceSelectionManager(private val context: Context) {
    private val selectedServices = mutableSetOf<String>()
    private val availableServices = listOf(
        "DeepSeek", "智谱AI", "Kimi", "ChatGPT", 
        "Claude", "Gemini", "文心一言", "通义千问", "讯飞星火"
    )
    
    fun getSelectedServices(): List<String> = selectedServices.toList()
    
    fun toggleService(serviceName: String): Boolean {
        return if (selectedServices.contains(serviceName)) {
            selectedServices.remove(serviceName)
            false
        } else {
            selectedServices.add(serviceName)
            true
        }
    }
    
    fun selectAll() {
        selectedServices.addAll(availableServices)
    }
    
    fun clearAll() {
        selectedServices.clear()
    }
    
    fun isSelected(serviceName: String): Boolean = selectedServices.contains(serviceName)
}
```

#### 2.2 更新DynamicIslandService.kt
```kotlin
// 添加AI服务选择管理器
private val aiServiceSelectionManager = AIServiceSelectionManager(this)

// 替换setupAIServiceSpinner方法
private fun setupAIServiceMultiSelect() {
    val container = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_services_checkbox_container)
    container?.removeAllViews()
    
    aiServiceSelectionManager.availableServices.forEach { serviceName ->
        val checkBox = createAIServiceCheckBox(serviceName)
        container?.addView(checkBox)
    }
    
    // 设置全选/清空按钮
    setupAIServiceControlButtons()
}

private fun createAIServiceCheckBox(serviceName: String): CheckBox {
    val checkBox = CheckBox(this)
    val layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    layoutParams.marginEnd = 16.dpToPx()
    checkBox.layoutParams = layoutParams
    
    checkBox.text = serviceName
    checkBox.textSize = 10f
    checkBox.setTextColor(getColor(R.color.ai_assistant_text_primary_light))
    checkBox.isChecked = aiServiceSelectionManager.isSelected(serviceName)
    
    checkBox.setOnCheckedChangeListener { _, isChecked ->
        aiServiceSelectionManager.toggleService(serviceName)
        updateAIServiceStatus()
    }
    
    return checkBox
}

// 更新getSelectedAIServices方法
private fun getSelectedAIServices(): List<String> {
    return aiServiceSelectionManager.getSelectedServices()
}
```

### 3. 并发调用优化

#### 3.1 异步并发调用
```kotlin
private fun sendAIMessageToMultipleServices(query: String) {
    try {
        val selectedServices = getSelectedAIServices()
        
        if (selectedServices.isEmpty()) {
            Toast.makeText(this, "请先选择AI服务", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 清除之前的回复
        clearAIResponseCards()
        
        // 并发调用所有选中的AI服务
        val jobs = selectedServices.map { aiService ->
            CoroutineScope(Dispatchers.IO).async {
                sendAIMessageToServiceAsync(query, aiService)
            }
        }
        
        // 等待所有任务完成
        CoroutineScope(Dispatchers.Main).launch {
            jobs.awaitAll()
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "发送消息到多个AI服务失败", e)
        Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun sendAIMessageToServiceAsync(query: String, aiService: String) {
    withContext(Dispatchers.IO) {
        try {
            val serviceType = mapDisplayNameToServiceType(aiService)
            val aiApiManager = AIApiManager(this@DynamicIslandService)
            
            aiApiManager.sendMessage(
                serviceType = serviceType,
                message = query,
                conversationHistory = emptyList(),
                callback = object : AIApiManager.StreamingCallback {
                    override fun onChunkReceived(chunk: String) {
                        uiHandler.post {
                            updateAIResponseCard(aiService, chunk, false)
                        }
                    }
                    
                    override fun onComplete(fullResponse: String) {
                        uiHandler.post {
                            updateAIResponseCard(aiService, fullResponse, true)
                        }
                    }
                    
                    override fun onError(error: String) {
                        uiHandler.post {
                            updateAIResponseCard(aiService, "错误：$error", true)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "调用${aiService}失败", e)
            uiHandler.post {
                updateAIResponseCard(aiService, "调用失败：${e.message}", true)
            }
        }
    }
}
```

#### 3.2 流式响应优化
```kotlin
private fun updateAIResponseCard(aiService: String, content: String, isComplete: Boolean) {
    val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_response_container)
    val existingCard = findExistingResponseCard(aiService)
    
    if (existingCard != null) {
        // 更新现有卡片
        updateExistingResponseCard(existingCard, content, isComplete)
    } else {
        // 创建新卡片
        val newCard = createAIResponseCard(aiService, content, "")
        responseContainer?.addView(newCard)
        
        // 滚动到最新卡片
        scrollToLatestCard()
    }
}
```

### 4. 用户体验优化

#### 4.1 加载状态指示
```kotlin
private fun showLoadingState(aiService: String) {
    val loadingCard = createLoadingCard(aiService)
    val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_response_container)
    responseContainer?.addView(loadingCard)
}

private fun createLoadingCard(aiService: String): MaterialCardView {
    val card = MaterialCardView(this)
    // ... 卡片样式设置
    
    val progressBar = ProgressBar(this)
    progressBar.indeterminate = true
    
    val loadingText = TextView(this)
    loadingText.text = "$aiService 正在思考中..."
    
    // ... 添加到卡片
    return card
}
```

#### 4.2 错误处理和重试
```kotlin
private fun handleAIResponseError(aiService: String, error: String) {
    val errorCard = createErrorCard(aiService, error)
    val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_response_container)
    responseContainer?.addView(errorCard)
}

private fun createErrorCard(aiService: String, error: String): MaterialCardView {
    val card = MaterialCardView(this)
    // ... 错误卡片样式
    
    val retryButton = MaterialButton(this)
    retryButton.text = "重试"
    retryButton.setOnClickListener {
        // 重试逻辑
        retryAIService(aiService)
    }
    
    return card
}
```

## 实施步骤

### 阶段1：UI改造（1-2天）
1. 修改`ai_assistant_panel.xml`布局
2. 实现`AIServiceSelectionManager`类
3. 更新`setupAIServiceMultiSelect`方法

### 阶段2：并发调用（2-3天）
1. 优化`sendAIMessageToMultipleServices`方法
2. 实现异步并发调用
3. 添加流式响应处理

### 阶段3：用户体验（1-2天）
1. 添加加载状态指示
2. 实现错误处理和重试
3. 优化UI交互

### 阶段4：测试优化（1天）
1. 多AI并发测试
2. 性能优化
3. 边界情况处理

## 预期效果

1. **多选支持**：用户可同时选择多个AI服务
2. **并发调用**：所有选中的AI服务同时响应
3. **对比查看**：横向滑动查看不同AI的回答
4. **实时更新**：流式响应实时显示
5. **错误处理**：单个AI失败不影响其他AI
6. **性能优化**：异步并发，响应迅速

## 技术优势

1. **可扩展性**：易于添加新的AI服务
2. **可维护性**：模块化设计，职责分离
3. **用户体验**：直观的多选界面，流畅的交互
4. **性能**：并发调用，响应迅速
5. **稳定性**：错误隔离，单个失败不影响整体
