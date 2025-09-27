# AI指令标签滚动修复测试指南

## 问题描述
AI助手tab的AI指令标签下对应的页面内容被固定了，无法上下滚动查看AI指令的所有内容。

## 问题原因
存在三层嵌套的ViewPager2导致滚动事件冲突：
1. AI助手中心主ViewPager2（基础信息、AI配置、个性化、任务）
2. AI配置子ViewPager2（AI指令、API设置）
3. AI指令子ViewPager2（核心指令、扩展配置、AI参数、个性化）

## 修复方案
1. **启用嵌套滚动支持**：为所有ViewPager2设置`android:nestedScrollingEnabled="true"`
2. **禁用过度滚动**：设置`android:overScrollMode="never"`避免滚动冲突
3. **优化ViewPager2配置**：
   - 启用用户输入：`viewPager.isUserInputEnabled = true`
   - 设置离屏页面限制：`viewPager.offscreenPageLimit = 1`

## 修复的文件
1. `app/src/main/java/com/example/aifloatingball/fragment/MasterPromptFragment.kt`
2. `app/src/main/java/com/example/aifloatingball/fragment/AIConfigFragment.kt`
3. `app/src/main/res/layout/fragment_master_prompt.xml`
4. `app/src/main/res/layout/fragment_ai_config.xml`
5. `app/src/main/res/layout/activity_simple_mode.xml`

## 测试步骤

### 1. 基础滚动测试
1. 打开应用，进入AI助手中心
2. 点击"AI配置"标签
3. 点击"AI指令"子标签
4. 验证页面内容可以正常上下滚动

### 2. 嵌套滚动测试
1. 在AI指令页面中，切换到"核心指令"标签
2. 验证内容可以正常滚动
3. 切换到"扩展配置"、"AI参数"、"个性化"标签
4. 验证每个标签页的内容都可以正常滚动

### 3. 边界情况测试
1. 快速滚动到页面顶部和底部
2. 验证没有滚动冲突或卡顿
3. 验证滚动动画流畅自然

### 4. 交互测试
1. 在可滚动内容中点击各种控件
2. 验证点击事件正常工作
3. 验证输入框可以正常输入

## 预期结果
- ✅ AI指令标签页面内容可以正常上下滚动
- ✅ 所有子标签页的内容都可以正常滚动
- ✅ 滚动操作流畅，无卡顿或冲突
- ✅ 页面交互功能正常

## 注意事项
- 如果仍有滚动问题，可能需要进一步调整ViewPager2的嵌套滚动行为
- 建议在不同设备上测试滚动性能
- 注意检查是否有其他ViewPager2嵌套导致类似问题

## 技术细节
修复的核心是解决Android中ViewPager2嵌套时的滚动事件分发问题：
- `nestedScrollingEnabled="true"`：启用嵌套滚动支持
- `overScrollMode="never"`：禁用过度滚动效果，避免滚动冲突
- `isUserInputEnabled = true`：确保用户输入事件正确处理
- `offscreenPageLimit = 1`：优化页面预加载，提升滚动性能






