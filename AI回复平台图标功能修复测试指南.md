# AI回复平台图标功能修复测试指南

## 修复内容

### 1. ➕号跳转功能修复
**问题**：点击➕号没有跳转到软件tab
**解决方案**：
- 使用Intent广播机制替代反射调用
- 在SimpleModeActivity中注册广播接收器
- 提供备用方案确保跳转成功

**技术实现**：
```kotlin
// ChatMessageAdapter中发送广播
val intent = Intent("com.example.aifloatingball.SWITCH_TO_SOFTWARE_TAB")
intent.putExtra("source", "platform_icons_plus")
itemView.context.sendBroadcast(intent)

// SimpleModeActivity中接收广播
tabSwitchBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.example.aifloatingball.SWITCH_TO_SOFTWARE_TAB") {
            runOnUiThread { switchToSoftwareTab() }
        }
    }
}
```

### 2. 长按菜单逻辑同步修复
**问题**：在软件tab中长按app图标，如果选中图标已经在AI回复中显示，则可以在长按菜单中选择取消显示
**解决方案**：
- 修复重复的when分支
- 确保菜单项与处理逻辑正确对应
- 支持AI应用和非AI应用的不同菜单逻辑

**技术实现**：
```kotlin
when (which) {
    0 -> showAppInfo(appConfig, isInstalled)
    1 -> {
        if (appConfig.category == AppCategory.AI) {
            // 处理常用AI选项
        } else {
            // 处理AI回复定制功能（非AI应用）
        }
    }
    2 -> {
        if (appConfig.category == AppCategory.AI) {
            // AI应用的AI回复定制功能
        } else {
            // 非AI应用的自定义分类功能
        }
    }
}
```

### 3. 平台图标跳转带入用户问题修复
**问题**：从AI回复下方的图标跳转app，必须把用户问题代入到跳转app的搜索结果页面
**解决方案**：
- 确保PlatformJumpManager正确传递用户查询
- 在URL Scheme中正确编码用户问题
- 支持应用内搜索和Web搜索两种方式

**技术实现**：
```kotlin
// PlatformJumpManager中的跳转逻辑
fun jumpToPlatform(platformName: String, query: String) {
    val config = PLATFORM_CONFIGS[platformName]
    if (isAppInstalled(config.packageName)) {
        jumpToApp(config, query) // 应用内搜索
    } else {
        jumpToWebSearch(config, query) // Web搜索
    }
}

// 应用内搜索URL构建
val searchUrl = when (config.packageName) {
    "com.ss.android.ugc.aweme" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
    "com.xingin.xhs" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
    // ... 其他平台
}
```

## 测试步骤

### 1. ➕号跳转功能测试
1. **进入简易模式**
   - 打开应用，进入简易模式
   - 选择任意AI助手进行对话

2. **测试➕号跳转**
   - 发送问题："推荐一些好看的电影"
   - 查看AI回复末尾的平台图标区域
   - 点击最右边的➕号按钮
   - 确认应用切换到软件tab
   - 检查是否显示应用搜索界面

3. **多次测试**
   - 多次点击➕号按钮
   - 确认跳转功能稳定
   - 检查是否有重复跳转

### 2. 长按菜单逻辑同步测试
1. **进入软件tab**
   - 点击➕号跳转到软件tab
   - 或手动切换到软件tab

2. **测试平台应用长按菜单**
   - 找到抖音、小红书、YouTube等平台应用
   - 长按平台应用图标
   - 检查长按菜单显示：
     - "查看应用信息"
     - "添加到AI回复" 或 "取消到AI回复"
     - 其他相关选项

3. **测试菜单功能**
   - 点击"添加到AI回复"
   - 确认Toast提示"已添加到AI回复"
   - 点击"取消到AI回复"
   - 确认Toast提示"已从AI回复中移除"

4. **测试AI应用长按菜单**
   - 长按AI应用（如ChatGPT、Claude等）
   - 检查菜单显示：
     - "查看应用信息"
     - "设为常用AI" 或 "从常用AI中移除"
     - "添加到AI回复" 或 "取消到AI回复"

### 3. 平台图标跳转带入用户问题测试
1. **测试应用内跳转**
   - 发送问题："推荐一些好看的电影"
   - 点击抖音图标
   - 确认跳转到抖音应用
   - 检查是否自动搜索"推荐一些好看的电影"

2. **测试Web搜索跳转**
   - 发送问题："学习编程的方法"
   - 点击YouTube图标（如果未安装）
   - 确认跳转到浏览器
   - 检查是否打开YouTube搜索页面
   - 确认搜索关键词为"学习编程的方法"

3. **测试不同平台**
   - 测试抖音、小红书、YouTube、哔哩哔哩等平台
   - 确认每个平台都能正确跳转
   - 检查搜索关键词是否正确传递

4. **测试不同问题类型**
   - 发送视频相关问题："好看的短视频推荐"
   - 发送美妆问题："好用的护肤品推荐"
   - 发送学习问题："如何学习编程"
   - 确认每个问题都能正确传递给对应平台

### 4. 功能集成测试
1. **完整流程测试**
   - 发送AI问题
   - 查看AI回复的平台图标
   - 点击➕号跳转到软件tab
   - 长按平台应用进行定制
   - 返回对话tab查看效果
   - 点击平台图标测试跳转

2. **定制功能测试**
   - 在软件tab中禁用某个平台
   - 返回对话tab发送新问题
   - 确认被禁用的平台图标不显示
   - 重新启用平台
   - 确认平台图标重新显示

3. **状态同步测试**
   - 在软件tab中修改平台设置
   - 立即返回对话tab测试
   - 确认设置立即生效
   - 无需重启应用

## 预期结果

### 1. ➕号跳转功能
- ✅ 点击➕号按钮正确跳转到软件tab
- ✅ 跳转过程流畅，无卡顿
- ✅ 跳转后显示正确的界面
- ✅ 支持多次跳转，功能稳定

### 2. 长按菜单逻辑同步
- ✅ 长按菜单显示正确的选项
- ✅ 菜单项与处理逻辑正确对应
- ✅ 支持AI应用和非AI应用的不同逻辑
- ✅ 定制设置立即生效

### 3. 平台图标跳转带入用户问题
- ✅ 点击平台图标正确跳转到对应应用
- ✅ 用户问题正确传递给搜索功能
- ✅ 支持应用内搜索和Web搜索
- ✅ 搜索关键词正确编码和传递

### 4. 功能集成
- ✅ 所有功能完美集成
- ✅ 用户体验流畅
- ✅ 设置同步及时
- ✅ 功能稳定可靠

## 技术细节

### 1. 广播机制
- 使用Intent广播实现跨组件通信
- 避免直接依赖和反射调用
- 提供备用方案确保功能稳定

### 2. 菜单逻辑
- 根据应用类型显示不同菜单
- 支持动态菜单项状态
- 确保菜单项与处理逻辑一致

### 3. 跳转逻辑
- 优先使用应用内搜索
- 失败时自动降级到Web搜索
- 正确编码用户查询参数

### 4. 状态管理
- 使用SharedPreferences持久化设置
- 实时同步用户定制偏好
- 支持即时生效

## 注意事项
- 确保所有平台应用都能正确处理搜索参数
- 测试不同网络环境下的跳转功能
- 验证用户查询的特殊字符处理
- 确认定制设置的数据持久化
- 测试应用安装/卸载后的功能表现
