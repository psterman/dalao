# 灵动岛搜索过滤功能调试指南

## 问题描述

灵动岛输入框出现了无法过滤app的问题，比如搜索"微信"应该匹配微信、微信读书、微信输入法这三个app，但实际无法匹配到。

## 修复方案

### 1. 优化字符串标准化
**问题**: `normalizeString` 方法过于严格，移除了太多字符

**修复**:
```kotlin
// 修改前：只保留字母、数字和空格
.replace(Regex("[^\\p{L}\\p{N}\\s]"), "")

// 修改后：保留字母、数字、空格和中文字符
.replace(Regex("[^\\p{L}\\p{N}\\s\\u4e00-\\u9fa5]"), "")
```

### 2. 添加简单搜索作为备用
**问题**: 复杂搜索算法可能在某些情况下失效

**修复**: 添加简单搜索方法作为备用
```kotlin
private fun performSimpleSearch(query: String): List<AppInfo> {
    val queryLower = query.lowercase()
    val results = mutableListOf<AppInfo>()
    
    appList.forEach { app ->
        val appNameLower = app.label.lowercase()
        if (appNameLower.contains(queryLower)) {
            results.add(app)
            Log.d(TAG, "简单搜索匹配: ${app.label}")
        }
    }
    
    return results.sortedBy { it.label }
}
```

### 3. 优化搜索流程
**修复**: 先尝试简单搜索，如果失败再使用复杂算法
```kotlin
fun search(query: String): List<AppInfo> {
    // 首先尝试简单搜索
    val simpleResults = performSimpleSearch(query)
    if (simpleResults.isNotEmpty()) {
        Log.d(TAG, "简单搜索找到 ${simpleResults.size} 个应用")
        return simpleResults
    }
    
    // 如果简单搜索没有结果，使用复杂搜索算法
    // ... 复杂搜索逻辑
}
```

### 4. 增强调试信息
**修复**: 添加详细的调试日志
```kotlin
Log.d(TAG, "未匹配应用: ${app.label}, 查询: '$normalizedQuery', 标准化后: '${normalizeString(app.label)}'")
Log.d(TAG, "简单搜索匹配: ${app.label}")
```

## 测试用例

### 测试用例1: 基础中文搜索
```
输入: "微信"
预期结果: 匹配到微信、微信读书、微信输入法等应用
验证方法: 检查日志中的"简单搜索匹配"信息
```

### 测试用例2: 部分匹配搜索
```
输入: "微"
预期结果: 匹配到所有包含"微"的应用
验证方法: 检查搜索结果数量
```

### 测试用例3: 英文搜索
```
输入: "chrome"
预期结果: 匹配到Chrome浏览器
验证方法: 检查搜索结果
```

### 测试用例4: 混合搜索
```
输入: "weixin"
预期结果: 匹配到微信相关应用
验证方法: 检查拼音匹配结果
```

## 调试步骤

### 1. 启用详细日志
```bash
adb logcat | grep "AppInfoManager"
```

### 2. 检查关键日志点
- `开始搜索: '[查询内容]', 应用总数: [数量]`
- `简单搜索找到 [数量] 个应用`
- `简单搜索匹配: [应用名]`
- `未匹配应用: [应用名], 查询: '[查询内容]', 标准化后: '[标准化内容]'`

### 3. 验证搜索流程
1. **检查应用加载**: 确认 `应用总数` 大于0
2. **检查简单搜索**: 确认 `简单搜索找到` 有结果
3. **检查匹配逻辑**: 确认 `简单搜索匹配` 有正确的应用
4. **检查标准化**: 确认 `标准化后` 的内容正确

## 预期结果

修复后的搜索功能应该能够：

- ✅ **正确匹配中文应用**: 搜索"微信"能匹配到微信、微信读书、微信输入法
- ✅ **支持部分匹配**: 搜索"微"能匹配到所有包含"微"的应用
- ✅ **支持英文搜索**: 搜索"chrome"能匹配到Chrome浏览器
- ✅ **支持拼音搜索**: 搜索"weixin"能匹配到微信相关应用
- ✅ **提供详细日志**: 便于调试和问题排查

## 故障排查

### 常见问题

1. **搜索结果为空**
   - 检查应用列表是否已加载
   - 检查简单搜索是否正常工作
   - 检查字符串标准化是否正确

2. **匹配不准确**
   - 检查简单搜索的匹配逻辑
   - 检查复杂搜索算法的阈值设置
   - 检查缓存是否影响结果

3. **性能问题**
   - 检查搜索缓存是否正常工作
   - 检查应用列表大小是否合理
   - 检查搜索算法的复杂度

### 调试命令

```bash
# 查看搜索相关日志
adb logcat | grep "AppInfoManager" | grep "搜索"

# 查看应用加载日志
adb logcat | grep "AppInfoManager" | grep "应用总数"

# 查看匹配结果日志
adb logcat | grep "AppInfoManager" | grep "匹配"
```

## 总结

通过以下修复措施，灵动岛搜索功能应该能够正常工作：

1. **优化字符串标准化**: 保留中文字符，避免过度过滤
2. **添加简单搜索**: 作为复杂算法的备用方案
3. **优化搜索流程**: 先简单后复杂，确保搜索成功
4. **增强调试信息**: 便于问题排查和性能优化

这些修复应该能够解决搜索"微信"无法匹配到微信、微信读书、微信输入法的问题。
