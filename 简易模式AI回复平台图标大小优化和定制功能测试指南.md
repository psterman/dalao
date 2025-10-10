# 简易模式AI回复平台图标大小优化和定制功能测试指南

## 优化内容

### 1. 图标大小优化
- **问题**：图片中显示的图标太小，不够清晰
- **解决方案**：
  - 将图标尺寸从28dp增加到36dp
  - 调整图标间距从2dp增加到6dp
  - 调整图标内边距从4dp增加到6dp
  - 更新PlatformIconLoader中的目标尺寸为36dp

### 2. 软件tab应用长按菜单定制功能
- **问题**：用户无法定制AI回复中显示的图标
- **解决方案**：
  - 在软件tab的app长按菜单中添加"添加到AI回复/取消到AI回复"选项
  - 创建PlatformIconCustomizationManager管理用户定制设置
  - 支持用户选择显示哪些平台图标

## 测试步骤

### 1. 图标大小测试
1. **进入简易模式**
   - 打开应用，进入简易模式
   - 选择任意AI助手进行对话

2. **发送问题并检查图标大小**
   - 发送"推荐一些好看的电影"
   - 检查AI回复末尾的平台图标
   - 确认图标大小为36dp，清晰可见
   - 检查图标间距和内边距是否合适

3. **不同问题类型测试**
   - 发送视频相关问题："推荐一些好看的短视频"
   - 发送美妆问题："有什么好用的护肤品推荐"
   - 发送学习问题："如何学习编程"
   - 确认所有问题都显示合适大小的图标

### 2. 软件tab定制功能测试
1. **进入软件tab**
   - 打开应用，进入简易模式
   - 切换到软件tab

2. **长按平台应用测试**
   - 找到抖音、小红书、YouTube、哔哩哔哩、快手、微博、豆瓣等应用
   - 长按任意一个平台应用
   - 检查长按菜单是否显示"添加到AI回复"或"取消到AI回复"选项

3. **添加平台到AI回复**
   - 长按抖音应用
   - 选择"添加到AI回复"
   - 确认显示"已添加到AI回复"提示
   - 发送AI问题，检查抖音图标是否显示

4. **从AI回复中移除平台**
   - 长按已添加的平台应用
   - 选择"取消到AI回复"
   - 确认显示"已从AI回复中移除"提示
   - 发送AI问题，检查该平台图标是否不再显示

5. **测试所有平台**
   - 依次测试所有7个平台应用
   - 确认每个平台都可以添加到/从AI回复中移除
   - 检查定制设置是否正确保存

### 3. 定制功能集成测试
1. **定制设置生效测试**
   - 移除部分平台（如移除抖音、快手）
   - 发送AI问题
   - 确认只显示剩余的平台图标
   - 检查图标按优先级排序

2. **重置功能测试**
   - 移除所有平台
   - 发送AI问题
   - 确认不显示任何平台图标
   - 重新添加部分平台
   - 确认只显示添加的平台图标

3. **设置持久化测试**
   - 定制平台显示设置
   - 关闭应用
   - 重新打开应用
   - 发送AI问题
   - 确认定制设置仍然生效

### 4. 界面交互测试
1. **长按菜单显示测试**
   - 长按不同平台应用
   - 确认菜单选项正确显示
   - 检查菜单项文本是否正确（"添加到AI回复"或"取消到AI回复"）

2. **图标点击测试**
   - 点击AI回复中的平台图标
   - 确认正确跳转到对应平台
   - 检查跳转功能是否正常

3. **滚动功能测试**
   - 当显示多个平台图标时
   - 确认可以水平滚动查看所有图标
   - 检查滚动是否流畅

### 5. 边界情况测试
1. **空设置测试**
   - 移除所有平台
   - 发送AI问题
   - 确认不显示任何图标
   - 确认不出现错误

2. **全部添加测试**
   - 添加所有平台
   - 发送AI问题
   - 确认显示所有7个平台图标
   - 检查布局是否正常

3. **应用未安装测试**
   - 长按未安装的平台应用
   - 确认菜单仍然显示定制选项
   - 测试定制功能是否正常

## 预期结果

### 1. 图标大小
- ✅ 图标大小为36dp，清晰可见
- ✅ 图标间距6dp，内边距6dp
- ✅ 图标显示质量良好
- ✅ 支持水平滚动

### 2. 定制功能
- ✅ 软件tab长按菜单显示定制选项
- ✅ 可以添加/移除平台到AI回复
- ✅ 定制设置正确保存和生效
- ✅ 用户提示信息正确显示

### 3. 集成功能
- ✅ AI回复只显示用户选择的平台图标
- ✅ 图标按优先级排序
- ✅ 设置持久化保存
- ✅ 所有平台都支持定制

## 技术实现

### 1. 图标大小优化
```kotlin
// 尺寸资源更新
<dimen name="platform_icon_size">36dp</dimen>
<dimen name="platform_icon_margin">6dp</dimen>
<dimen name="platform_icon_padding">6dp</dimen>

// PlatformIconLoader目标尺寸更新
targetSize = 36
```

### 2. 定制管理器
```kotlin
class PlatformIconCustomizationManager {
    // 管理用户定制设置
    fun togglePlatform(platformName: String): Boolean
    fun getEnabledPlatforms(): Set<String>
    fun filterEnabledPlatforms(allPlatforms: List<PlatformInfo>): List<PlatformInfo>
}
```

### 3. 长按菜单集成
```kotlin
// AppSearchGridAdapter中添加定制选项
val platformName = getPlatformNameFromApp(appConfig)
if (platformName != null) {
    val statusText = customizationManager.getPlatformStatusText(platformName)
    menuItems.add(statusText)
}
```

### 4. 平台映射
```kotlin
private fun getPlatformNameFromApp(appConfig: AppSearchConfig): String? {
    return when (appConfig.packageName) {
        "com.ss.android.ugc.aweme" -> "抖音"
        "com.xingin.xhs" -> "小红书"
        "com.google.android.youtube" -> "YouTube"
        // 其他平台...
        else -> null
    }
}
```

## 注意事项
- 图标大小已优化为36dp，确保清晰可见
- 支持用户完全定制AI回复中显示的平台图标
- 定制设置会持久化保存
- 所有7个平台都支持定制功能
- 长按菜单会根据当前状态显示正确的选项文本
