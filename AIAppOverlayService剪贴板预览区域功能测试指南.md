# AIAppOverlayService剪贴板预览区域功能测试指南

## 功能概述

在AIAppOverlayService的悬浮窗面板上添加了剪贴板预览区域，用户可以实时查看剪贴板内容，并进行复制和清空操作。

## 功能特性

### 1. 剪贴板预览区域
- **位置**：悬浮窗中应用名称和按钮之间
- **显示内容**：当前剪贴板中的文本内容
- **显示限制**：最多显示3行，超出部分用省略号表示
- **自动隐藏**：当剪贴板为空时自动隐藏

### 2. 实时更新功能
- **监听器**：注册剪贴板内容变化监听器
- **自动更新**：剪贴板内容变化时自动更新预览区域
- **线程安全**：在主线程中更新UI

### 3. 操作按钮
- **复制按钮**：将预览内容复制到剪贴板
- **清空按钮**：清空剪贴板内容并隐藏预览区域

## 技术实现

### 1. 布局文件修改
**文件**：`app/src/main/res/layout/ai_app_overlay.xml`

```xml
<!-- 剪贴板预览区域 -->
<LinearLayout
    android:id="@+id/clipboard_preview_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/clipboard_preview_background"
    android:padding="8dp"
    android:layout_marginBottom="8dp"
    android:visibility="gone">

    <!-- 剪贴板标题 -->
    <TextView
        android:id="@+id/clipboard_title"
        android:text="剪贴板内容"
        android:textSize="10sp" />

    <!-- 剪贴板内容预览 -->
    <TextView
        android:id="@+id/clipboard_content"
        android:maxLines="3"
        android:ellipsize="end" />

    <!-- 操作按钮 -->
    <LinearLayout android:orientation="horizontal">
        <Button android:id="@+id/clipboard_copy_button" android:text="复制" />
        <Button android:id="@+id/clipboard_clear_button" android:text="清空" />
    </LinearLayout>
</LinearLayout>
```

### 2. 背景样式文件
**文件**：`app/src/main/res/drawable/clipboard_preview_background.xml`
- 浅灰色背景 `#FFF5F5F5`
- 圆角边框 `6dp`
- 浅灰色边框 `#FFE0E0E0`

**文件**：`app/src/main/res/drawable/clipboard_button_background.xml`
- 按钮按下效果
- 蓝色高亮 `#FFE3F2FD`
- 蓝色边框 `#FF1976D2`

### 3. 服务代码实现
**文件**：`app/src/main/java/com/example/aifloatingball/service/AIAppOverlayService.kt`

#### 核心方法：
1. **setupClipboardPreview()** - 设置剪贴板预览区域
2. **updateClipboardPreview()** - 更新剪贴板预览内容
3. **registerClipboardListener()** - 注册剪贴板监听器
4. **unregisterClipboardListener()** - 取消注册剪贴板监听器

#### 生命周期管理：
- **显示悬浮窗时**：注册剪贴板监听器
- **隐藏悬浮窗时**：取消注册剪贴板监听器
- **服务销毁时**：确保取消注册监听器

## 测试步骤

### 步骤1：基础功能测试
1. 打开应用，进入对话tab
2. 在输入框中输入问题，例如："你好"
3. 点击发送，等待AI回复
4. 在AI回复下方点击豆包图标
5. 等待悬浮窗显示

**预期结果**：
- 悬浮窗正常显示
- 剪贴板预览区域显示"你好"
- 预览区域可见

### 步骤2：剪贴板内容测试
1. 在任意应用中复制一段文本
2. 返回悬浮窗界面
3. 观察剪贴板预览区域

**预期结果**：
- 剪贴板预览区域自动显示复制的文本
- 文本最多显示3行
- 超出部分显示省略号

### 步骤3：复制按钮测试
1. 确保剪贴板预览区域有内容
2. 点击"复制"按钮
3. 在任意应用中粘贴

**预期结果**：
- 显示Toast提示："已复制到剪贴板"
- 粘贴的内容与预览内容一致

### 步骤4：清空按钮测试
1. 确保剪贴板预览区域有内容
2. 点击"清空"按钮
3. 观察预览区域

**预期结果**：
- 显示Toast提示："剪贴板已清空"
- 剪贴板预览区域隐藏
- 剪贴板内容被清空

### 步骤5：实时更新测试
1. 确保悬浮窗显示
2. 在任意应用中复制新内容
3. 观察剪贴板预览区域

**预期结果**：
- 预览区域自动更新显示新内容
- 无需手动刷新

### 步骤6：空剪贴板测试
1. 清空剪贴板内容
2. 观察剪贴板预览区域

**预期结果**：
- 剪贴板预览区域自动隐藏
- 不显示任何内容

## 验证要点

### 1. UI显示
- ✅ 剪贴板预览区域位置正确
- ✅ 背景样式美观
- ✅ 文本显示格式正确
- ✅ 按钮样式一致

### 2. 功能完整性
- ✅ 剪贴板内容正确显示
- ✅ 复制功能正常
- ✅ 清空功能正常
- ✅ 实时更新正常

### 3. 性能表现
- ✅ 监听器注册/取消注册正常
- ✅ 内存泄漏检查通过
- ✅ UI更新流畅

### 4. 异常处理
- ✅ 空剪贴板处理正确
- ✅ 长文本截断正确
- ✅ 异常情况处理完善

## 日志关键词

- `剪贴板预览区域已显示` - 预览区域显示成功
- `剪贴板内容发生变化` - 剪贴板内容变化
- `剪贴板预览内容已更新` - 预览内容更新成功
- `剪贴板监听器已注册` - 监听器注册成功
- `剪贴板监听器已取消注册` - 监听器取消注册成功
- `剪贴板内容已复制` - 复制操作成功
- `剪贴板已清空` - 清空操作成功

## 故障排除

### 如果剪贴板预览区域不显示
1. 检查剪贴板是否有内容
2. 检查布局文件是否正确加载
3. 查看日志确认setupClipboardPreview()是否被调用

### 如果实时更新不工作
1. 检查剪贴板监听器是否正确注册
2. 查看日志确认监听器状态
3. 检查主线程更新是否正常

### 如果按钮点击无响应
1. 检查按钮点击事件是否正确设置
2. 查看日志确认点击事件触发
3. 检查剪贴板操作权限

## 技术细节

### 剪贴板监听器实现
```kotlin
clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
    Log.d(TAG, "剪贴板内容发生变化")
    // 在主线程中更新UI
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        updateClipboardPreview()
    }
}
```

### 内容更新逻辑
```kotlin
if (text.isNotEmpty()) {
    clipboardContent.text = text
    clipboardContainer.visibility = View.VISIBLE
} else {
    clipboardContainer.visibility = View.GONE
}
```

### 生命周期管理
- 显示悬浮窗 → 注册监听器
- 隐藏悬浮窗 → 取消注册监听器
- 服务销毁 → 确保清理资源

这个剪贴板预览功能为用户提供了便捷的剪贴板内容查看和管理功能，增强了悬浮窗的实用性。


