# 软件tab所有应用支持AI回复图标功能测试指南

## 功能概述

现在软件tab中的所有应用都支持加入到AI回复下方的图标中，不仅仅是预设的几个平台（抖音、小红书、快手、哔哩哔哩、豆瓣、微博、YouTube）。用户可以为任何应用启用AI回复图标功能，方便点击搜索。

## 主要改进

### 1. 支持所有应用的AI回复定制
- **之前**：只有预设的7个平台支持AI回复图标
- **现在**：软件tab中的所有应用都支持AI回复图标定制
- **包括**：AI应用、购物应用、社交应用、视频应用、音乐应用等所有分类

### 2. 动态应用支持
- **动态创建**：为未预设的应用动态创建PlatformInfo
- **自动识别**：自动获取应用名称和包名
- **通用URL scheme**：为未知应用提供通用的搜索URL scheme

### 3. 统一的定制管理
- **统一接口**：所有应用使用相同的定制接口
- **向后兼容**：保留原有平台相关方法，确保兼容性
- **灵活配置**：用户可以自由选择哪些应用显示在AI回复中

## 测试步骤

### 1. 基础功能测试
1. **进入软件tab**
   - 打开应用，进入软件tab
   - 查看所有应用列表

2. **测试预设平台**
   - 长按抖音应用图标
   - 确认菜单中显示"添加到AI回复"或"取消添加到AI回复"
   - 点击该选项，确认状态切换

3. **测试非预设应用**
   - 长按任意其他应用图标（如微信、淘宝、知乎等）
   - 确认菜单中显示"添加到AI回复"或"取消添加到AI回复"
   - 点击该选项，确认状态切换

### 2. AI回复图标显示测试
1. **启用多个应用**
   - 在软件tab中为多个应用启用AI回复功能
   - 包括预设平台和非预设应用

2. **发送AI问题**
   - 进入简易模式或聊天界面
   - 发送问题："推荐一些好用的应用"

3. **检查图标显示**
   - 确认AI回复下方显示了所有启用的应用图标
   - 检查图标是否正确显示
   - 确认图标数量与启用的应用数量一致

### 3. 图标点击跳转测试
1. **预设平台跳转测试**
   - 点击抖音图标
   - 确认跳转到抖音搜索结果页面
   - 检查搜索关键词是否正确

2. **非预设应用跳转测试**
   - 点击微信图标
   - 确认跳转到微信应用（如果支持搜索功能）
   - 或确认跳转到Web搜索页面

3. **通用应用跳转测试**
   - 点击其他应用图标
   - 确认跳转行为正常
   - 检查错误处理机制

### 4. 不同应用分类测试
1. **AI应用测试**
   - 启用ChatGPT、Claude等AI应用
   - 发送问题："如何学习编程"
   - 点击AI应用图标
   - 确认跳转行为

2. **购物应用测试**
   - 启用淘宝、京东、拼多多等购物应用
   - 发送问题："推荐一些好用的商品"
   - 点击购物应用图标
   - 确认跳转到相应的搜索结果页面

3. **社交应用测试**
   - 启用微信、QQ、微博等社交应用
   - 发送问题："有什么好玩的社交功能"
   - 点击社交应用图标
   - 确认跳转行为

4. **视频应用测试**
   - 启用哔哩哔哩、爱奇艺、腾讯视频等
   - 发送问题："推荐一些好看的视频"
   - 点击视频应用图标
   - 确认跳转到相应的搜索结果页面

5. **音乐应用测试**
   - 启用网易云音乐、QQ音乐、Spotify等
   - 发送问题："推荐一些好听的音乐"
   - 点击音乐应用图标
   - 确认跳转行为

### 5. 错误处理测试
1. **应用未安装测试**
   - 卸载某个已启用的应用
   - 点击该应用图标
   - 确认自动跳转到Web搜索页面
   - 检查Web搜索是否使用正确的关键词

2. **URL scheme失败测试**
   - 模拟URL scheme跳转失败
   - 确认自动回退到Web搜索
   - 检查错误处理机制

3. **网络异常测试**
   - 断网情况下点击应用图标
   - 确认显示适当的错误提示
   - 检查应用稳定性

### 6. 定制功能测试
1. **批量启用测试**
   - 为多个应用启用AI回复功能
   - 确认所有启用的应用都显示在AI回复中
   - 检查图标排列和显示效果

2. **批量禁用测试**
   - 禁用多个应用的AI回复功能
   - 确认禁用的应用不再显示在AI回复中
   - 检查图标更新是否及时

3. **重置功能测试**
   - 使用重置功能恢复默认设置
   - 确认只有预设平台显示在AI回复中
   - 检查设置是否正确重置

### 7. 性能测试
1. **大量应用测试**
   - 启用大量应用的AI回复功能
   - 检查AI回复加载速度
   - 确认图标显示性能

2. **内存使用测试**
   - 监控应用内存使用情况
   - 确认没有内存泄漏
   - 检查应用稳定性

## 预期结果

### 1. 功能完整性
- ✅ 所有应用都支持AI回复定制功能
- ✅ 预设平台和非预设应用都能正常启用/禁用
- ✅ AI回复中正确显示所有启用的应用图标

### 2. 跳转准确性
- ✅ 预设平台使用正确的URL scheme跳转
- ✅ 非预设应用使用通用URL scheme或Web搜索
- ✅ 所有跳转都使用用户原始问题作为搜索关键词

### 3. 用户体验
- ✅ 定制功能简单易用
- ✅ 图标显示清晰美观
- ✅ 跳转速度快，响应及时

### 4. 错误处理
- ✅ 应用未安装时自动回退到Web搜索
- ✅ URL scheme失败时有备选方案
- ✅ 网络异常时显示适当提示

## 技术实现

### 1. AppSearchGridAdapter修改
```kotlin
// 支持所有应用的AI回复定制
val customizationManager = PlatformIconCustomizationManager.getInstance(context)
val statusText = customizationManager.getAppStatusText(appConfig)
menuItems.add(statusText)

// 处理菜单项点击
val isEnabled = customizationManager.toggleApp(appConfig)
val message = if (isEnabled) "已添加到AI回复" else "已从AI回复中移除"
Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
```

### 2. PlatformIconCustomizationManager重构
```kotlin
// 支持所有应用的定制管理
class PlatformIconCustomizationManager {
    private val enabledApps: MutableSet<String> // 存储用户启用的应用包名
    
    fun toggleApp(appConfig: AppSearchConfig): Boolean
    fun isAppEnabled(packageName: String): Boolean
    fun getAppStatusText(appConfig: AppSearchConfig): String
    fun getEnabledApps(): Set<String>
}
```

### 3. PlatformJumpManager增强
```kotlin
// 支持动态应用创建
private fun createAppInfo(packageName: String): PlatformInfo? {
    val packageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
    
    val config = PlatformConfig(
        packageName = packageName,
        urlScheme = "$packageName://",
        searchUrl = "https://www.google.com/search?q=%s",
        iconRes = "ic_menu_search"
    )
    
    return PlatformInfo(name = appName, config = config, isInstalled = true)
}
```

### 4. 通用URL scheme支持
```kotlin
// 支持所有应用的URL scheme
val searchUrl = when (config.packageName) {
    // 预设平台使用特定URL scheme
    "com.ss.android.ugc.aweme" -> "snssdk1128://search/tabs?keyword=${Uri.encode(cleanQuery)}"
    // 其他应用使用通用URL scheme
    else -> "${config.urlScheme}search?keyword=${Uri.encode(cleanQuery)}"
}
```

## 注意事项
- 确保所有应用都能正确识别和定制
- 测试各种应用分类的跳转行为
- 验证错误处理和备选方案
- 检查性能和内存使用情况
- 确保向后兼容性
- 测试大量应用启用时的性能表现
