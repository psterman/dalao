// 这是修复格式后的createAIEngineButtons方法
// 把这个方法复制到DualFloatingWebViewService.kt中，替换原来的方法

/*
private fun createAIEngineButtons(webView: WebView, aiContainer: LinearLayout, engineKeyPrefix: String, saveEngineFunction: (String) -> Unit) {
    // 清空容器
    aiContainer.removeAllViews()
    
    // 创建水平布局
    val buttonLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dpToPx(this@DualFloatingWebViewService), 8.dpToPx(this@DualFloatingWebViewService),
                  16.dpToPx(this@DualFloatingWebViewService), 8.dpToPx(this@DualFloatingWebViewService))
    }
    
    // AI搜索引擎列表
    val aiEngines = listOf(
        Triple("ChatGPT", "https://chat.openai.com/", R.drawable.ic_search),
        Triple("Claude", "https://claude.ai/", R.drawable.ic_search),
        Triple("文心一言", "https://yiyan.baidu.com/", R.drawable.ic_search),
        Triple("通义千问", "https://qianwen.aliyun.com/", R.drawable.ic_search),
        Triple("讯飞星火", "https://xinghuo.xfyun.cn/", R.drawable.ic_search)
    )
    
    // 为每个AI引擎创建按钮
    aiEngines.forEach { (name, url, defaultIconRes) ->
        // 创建图标
        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                56.dpToPx(this@DualFloatingWebViewService),
                56.dpToPx(this@DualFloatingWebViewService)
            ).apply {
                marginStart = 8.dpToPx(this@DualFloatingWebViewService)
                marginEnd = 8.dpToPx(this@DualFloatingWebViewService)
            }
            
            // 设置圆形背景
            background = ContextCompat.getDrawable(this@DualFloatingWebViewService, R.drawable.circle_button_background)
            // 设置四边的内边距
            setPadding(
                8.dpToPx(this@DualFloatingWebViewService),
                8.dpToPx(this@DualFloatingWebViewService),
                8.dpToPx(this@DualFloatingWebViewService),
                8.dpToPx(this@DualFloatingWebViewService)
            )
            
            // 加载Favicon
            loadFavicon(url, this, defaultIconRes)
            
            // 设置点击事件
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Toast.makeText(this@DualFloatingWebViewService, "正在打开: $name", Toast.LENGTH_SHORT).show()
                webView.loadUrl(url)
                saveEngineFunction("ai_${name.lowercase()}")
            }
            
            // 设置内容描述，便于无障碍访问
            contentDescription = name
        }
        
        // 直接添加图标到布局，不再添加文字
        buttonLayout.addView(iconView)
    }
    
    // 添加整个布局到容器
    aiContainer.addView(buttonLayout)
    
    // 记录日志
    Log.d("DualFloatingWebView", "已添加${aiEngines.size}个AI搜索引擎按钮到容器")
}
*/
