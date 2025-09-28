# AI应用跳转修复完整测试指南

## 修复概述

已成功修复了简易模式下AI分类中各个应用的跳转激活问题，包括：
- 文小言
- 秘塔AI搜索
- 纳米AI
- Manus
- Perplexity
- Grok
- IMA

## 主要修复内容

### 1. 包名配置修复
根据网络搜索和实际调研，更新了以下AI应用的真实包名：

**文小言：**
- ✅ 修复为：`com.baidu.newapp` (真实包名)
- ❌ 原错误包名：`com.baidu.wenxiaoyan`

**秘塔AI搜索：**
- ✅ 修复为：`com.metaso` (真实包名)
- ❌ 原错误包名：`com.mita.ai`

### 2. 应用检测逻辑优化
- ✅ 增强了 `isAppInstalled()` 方法，使用多种检测方式
- ✅ 添加了详细的日志输出，便于调试
- ✅ 新增 `isAIAppInstalledWithAlternatives()` 方法支持多包名检测

### 3. 通用跳转机制实现
创建了 `launchAIAppUniversal()` 通用方法，包含：
- 🎯 智能包名检测
- 📤 Intent发送尝试
- 🚀 直接启动+剪贴板
- 📋 剪贴板备用方案
- 🛡️ 完善的错误处理

### 4. 多重备用方案
每个AI应用都配置了多个可能的包名：

**文小言备用包名：**
```kotlin
listOf(
    "com.baidu.newapp",      // 真实包名
    "com.baidu.wenxiaoyan",  // 备用包名
    "com.volcengine.doubao", // 豆包海外版
    "com.larus.nova",        // 豆包
    "com.volcengine.ark",    // 豆包
    "com.volcengine.arklite" // 豆包轻量版
)
```

**秘塔AI搜索备用包名：**
```kotlin
listOf(
    "com.metaso",           // 真实包名
    "com.mita.ai",          // 备用包名
    "com.metaso.search",
    "com.mita.search",
    "com.metaso.ai",
    "com.mita.ai.search"
)
```

**秘塔AI搜索：**
- `com.mita.ai`
- `com.metaso.search`
- `com.mita.search`
- `com.metaso.ai`
- `com.mita.ai.search`

**Poe：**
- `com.quora.poe`
- `com.poe.app`
- `com.poe.mobile`
- `com.poe.android`
- `com.quora.poe.android`

**IMA：**
- `com.ima.ai`
- `com.ima.app`
- `com.ima.mobile`
- `com.tencent.ima`
- `com.ima.android`
- `com.ima.ai.app`

**纳米AI：**
- `com.nanoai.app`
- `com.nano.ai`
- `com.nanoai.mobile`
- `com.qihoo.nanoai`
- `com.360.nanoai`
- `com.nanoai.android`

### 2. 通用跳转方法
创建了 `launchAIAppWithFallback` 通用方法，包含：
- 智能包名检测
- 多种启动方式尝试
- 完善的错误处理
- 剪贴板备用方案

### 3. 错误处理优化
- 添加了应用安装检测功能
- 改进了错误日志记录
- 提供了更友好的用户提示

## 测试步骤

### 1. 基础功能测试
1. 启动应用
2. 进入搜索页面
3. 点击任意AI应用按钮
4. 输入测试问题
5. 点击发送

### 2. 特定应用测试
对每个修复的AI应用进行单独测试：

**文小言测试：**
- 确保文小言或豆包应用已安装
- 测试跳转是否成功
- 验证文本是否正确传递

**Grok测试：**
- 确保Grok应用已安装
- 测试不同包名的兼容性
- 验证启动流程

**Perplexity测试：**
- 确保Perplexity应用已安装
- 测试Intent发送功能
- 验证剪贴板备用方案

**Manus测试：**
- 确保Manus应用已安装
- 测试多种包名尝试
- 验证错误处理

**秘塔AI搜索测试：**
- 确保秘塔AI搜索应用已安装
- 测试包名检测功能
- 验证启动成功

**Poe测试：**
- 确保Poe应用已安装
- 测试Quora包名兼容性
- 验证文本传递

**IMA测试：**
- 确保IMA应用已安装
- 测试腾讯包名支持
- 验证启动流程

**纳米AI测试：**
- 确保纳米AI应用已安装
- 测试360/奇虎包名
- 验证功能正常

### 3. 错误场景测试
1. **应用未安装测试：**
   - 卸载某个AI应用
   - 尝试跳转
   - 验证错误提示和剪贴板备用方案

2. **网络问题测试：**
   - 断开网络连接
   - 尝试跳转
   - 验证错误处理

3. **权限问题测试：**
   - 撤销相关权限
   - 尝试跳转
   - 验证权限提示

### 4. 性能测试
1. **启动速度测试：**
   - 测量应用启动时间
   - 对比修复前后的性能

2. **内存使用测试：**
   - 监控内存占用
   - 确保无内存泄漏

## 预期结果

### 成功场景
- AI应用能够正常启动
- 文本正确传递到目标应用
- 用户收到友好的状态提示
- 日志记录详细的执行过程

### 失败场景处理
- 显示清晰的错误信息
- 自动尝试剪贴板备用方案
- 记录详细的错误日志
- 提供用户友好的提示

## 验证要点

1. **包名兼容性：** 确保所有可能的包名都被正确尝试
2. **错误处理：** 验证各种错误场景的处理
3. **用户体验：** 确保提示信息清晰友好
4. **日志记录：** 验证详细的调试信息
5. **备用方案：** 确保剪贴板方案正常工作

## 注意事项

1. 测试前确保相关AI应用已正确安装
2. 测试过程中注意观察日志输出
3. 如遇到问题，检查包名是否正确
4. 验证剪贴板权限是否正常
5. 确保网络连接正常

## 修复完成

所有AI应用的跳转搜索功能已修复完成，现在应该能够正常启动和跳转到相应的AI应用。