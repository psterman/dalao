# Kimi显示问题调试步骤

## 问题描述
用户复制文本激活灵动岛后，Kimi没有显示在AI服务列表中，尽管Kimi已配置API密钥。

## 调试步骤

### 步骤1：清空日志并激活灵动岛
```bash
adb logcat -c
```
然后复制任意文本激活灵动岛。

### 步骤2：检查已启用的AI引擎
```bash
adb logcat | findstr "已启用的AI引擎"
```
**预期结果**：应该看到包含"Kimi"的列表

### 步骤3：检查Kimi的映射
```bash
adb logcat | findstr "找到映射.*Kimi"
```
**预期结果**：应该看到"找到映射: Kimi -> Kimi, API密钥名: kimi_api_key"

### 步骤4：检查Kimi API密钥获取
```bash
adb logcat | findstr "Kimi API密钥获取"
```
**预期结果**：应该看到"Kimi API密钥获取: 成功 (XX字符)"或"失败"

### 步骤5：检查Kimi API密钥验证
```bash
adb logcat | findstr "检查 Kimi API密钥"
```
**预期结果**：应该看到"检查 Kimi API密钥: 已配置 (XX字符)"

### 步骤6：检查Kimi是否添加到列表
```bash
adb logcat | findstr "Kimi.*已配置，添加到可用列表"
```
**预期结果**：应该看到"✅ Kimi API密钥已配置，添加到可用列表"

### 步骤7：检查最终AI服务列表
```bash
adb logcat | findstr "最终配置好的AI服务"
```
**预期结果**：应该看到包含"Kimi"的列表

## 可能的问题和解决方案

### 问题1：Kimi不在已启用的AI引擎中
**症状**：步骤2中没有看到Kimi
**解决方案**：
1. 检查应用设置中的AI引擎配置
2. 确保Kimi已启用
3. 重新启动应用

### 问题2：Kimi映射失败
**症状**：步骤3中没有看到Kimi映射
**解决方案**：
1. 检查`aiEngineMapping`中是否包含"Kimi"映射
2. 确保映射格式正确

### 问题3：Kimi API密钥获取失败
**症状**：步骤4中显示"失败"
**解决方案**：
1. 检查Kimi API密钥是否已配置
2. 检查`settingsManager.getKimiApiKey()`方法是否正常
3. 验证API密钥格式

### 问题4：Kimi API密钥验证失败
**症状**：步骤5中显示"未配置"
**解决方案**：
1. 检查API密钥长度（应该≥10字符）
2. 检查API密钥格式
3. 验证`isValidApiKey`方法

### 问题5：Kimi没有添加到列表
**症状**：步骤6中没有看到添加成功的日志
**解决方案**：
1. 检查API密钥验证逻辑
2. 检查`configuredServices.add(displayName)`调用

## 快速测试命令

```bash
# 一键检查所有相关日志
adb logcat | findstr -E "(已启用的AI引擎|找到映射.*Kimi|Kimi API密钥|最终配置好的AI服务)"
```

## 预期结果

如果一切正常，应该看到以下日志序列：
```
DynamicIslandService: 已启用的AI引擎: [DeepSeek (API), ChatGPT (Custom), Claude (Custom), 通义千问 (Custom), 智谱AI (Custom), Kimi, ChatGPT, Claude, Gemini]
DynamicIslandService: 处理已启用的AI引擎: Kimi
DynamicIslandService: 找到映射: Kimi -> Kimi, API密钥名: kimi_api_key
DynamicIslandService: Kimi API密钥获取: 成功 (32字符)
DynamicIslandService: 检查 Kimi API密钥: 已配置 (32字符)
DynamicIslandService: ✅ Kimi API密钥已配置，添加到可用列表
DynamicIslandService: 最终配置好的AI服务: [DeepSeek, 智谱AI, Kimi]
```

如果看到这个序列，说明Kimi应该能正常显示在灵动岛中。
