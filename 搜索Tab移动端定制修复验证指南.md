# 搜索Tab移动端定制修复验证指南

## 修复内容概述

本次修复解决了搜索tab中WebView移动端定制没有完全生效的问题，主要包含以下改进：

### 1. SearchActivity移动端适配修复
- **User-Agent修复**: 从桌面版Chrome改为移动版Chrome User-Agent
- **Viewport注入**: 自动注入viewport meta标签确保移动版显示
- **移动端优化**: 添加文本缩放、最小字体等移动端优化设置

### 2. 搜索URL构建修复
- **百度搜索**: 从PC版 `https://www.baidu.com/s?wd={query}` 改为移动版 `https://m.baidu.com/s?wd={query}`
- **搜狗搜索**: 从PC版改为移动版 `https://m.sogou.com/web?query={query}`
- **360搜索**: 从PC版改为移动版 `https://m.so.com/s?q={query}`
- **HomeActivity**: 修复硬编码的百度搜索URL

### 3. 搜索结果页面点击处理
- **链接拦截**: 拦截移动应用URL scheme重定向
- **搜索结果处理**: 正确处理搜索结果页面的链接点击
- **重定向处理**: 将App重定向转换为Web版本搜索

## 验证步骤

### 步骤1: 测试搜索功能移动端显示
1. 打开应用，进入搜索tab
2. 在搜索框输入任意关键词（如"Android开发"）
3. 点击搜索按钮
4. **验证结果**:
   - 页面应显示移动版百度搜索结果
   - 页面宽度适配屏幕，无需手动缩放
   - 文字大小适合移动设备阅读
   - 搜索结果布局为移动端样式

### 步骤2: 验证User-Agent设置
1. 在搜索tab中访问 `https://httpbin.org/user-agent`
2. **验证结果**:
   - 返回的User-Agent应包含 "Mobile Safari"
   - User-Agent版本为Chrome 123.0.0.0
   - 不应包含 "Windows NT 10.0"

### 步骤3: 测试搜索结果页面点击
1. 在搜索tab中搜索任意关键词
2. 点击搜索结果中的任意链接
3. **验证结果**:
   - 链接应在WebView中正常加载
   - 不会跳转到外部浏览器
   - 页面保持移动端显示

### 步骤4: 测试搜索引擎重定向拦截
1. 在搜索tab中搜索关键词
2. 如果出现App重定向提示，点击"在浏览器中打开"
3. **验证结果**:
   - 应保持在WebView中加载
   - 重定向到对应的移动版搜索页面
   - 不会跳转到外部应用

### 步骤5: 测试不同搜索引擎
1. 切换到不同的搜索引擎（搜狗、360等）
2. 进行搜索测试
3. **验证结果**:
   - 所有搜索引擎都应显示移动版
   - 搜索结果页面适配移动端
   - 点击链接正常加载

## 预期效果

### ✅ 修复前问题
- 搜索结果显示PC版页面，需要手动缩放
- 页面宽度超出屏幕，需要左右滑动
- 搜索结果点击可能跳转到外部应用
- 搜索引擎重定向到App而不是Web版

### ✅ 修复后效果
- 搜索结果显示移动版页面
- 页面宽度完美适配屏幕
- 搜索结果点击在WebView中正常加载
- 搜索引擎重定向保持在WebView中
- 支持手势缩放（最大3倍）

## 技术实现细节

### SearchActivity User-Agent设置
```kotlin
userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
```

### Viewport注入脚本
```javascript
// 检查并更新viewport meta标签
var viewportMeta = document.querySelector('meta[name="viewport"]');
if (viewportMeta) {
    viewportMeta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');
} else {
    var meta = document.createElement('meta');
    meta.name = 'viewport';
    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
    document.head.appendChild(meta);
}
```

### 搜索URL修复
```kotlin
// 百度移动版搜索URL
"https://m.baidu.com/s?wd={query}"

// 搜狗移动版搜索URL  
"https://m.sogou.com/web?query={query}"

// 360移动版搜索URL
"https://m.so.com/s?q={query}"
```

### 搜索结果点击处理
```kotlin
override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val url = request?.url?.toString()
    return handleSearchResultClick(view, url)
}
```

## 注意事项

1. **兼容性**: 修复适用于所有Android版本
2. **性能**: 不影响搜索加载性能
3. **安全性**: 保持原有的安全设置
4. **用户体验**: 提供更好的移动端搜索体验

## 故障排除

如果仍有问题，请检查：
1. 确认WebView设置正确应用
2. 检查JavaScript是否被禁用
3. 验证网络连接正常
4. 清除WebView缓存重新测试
5. 检查搜索引擎设置是否正确
