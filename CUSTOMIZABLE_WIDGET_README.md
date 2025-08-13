# 🎨 可定制小组件功能说明

## 📋 功能概述

可定制小组件是一个智能的桌面小组件，允许用户根据个人需求自定义显示内容，包括：

- **AI助手快捷访问** - 智谱、DeepSeek等AI引擎
- **常用应用快速启动** - 微信、QQ、支付宝等
- **搜索引擎快捷入口** - 百度、Google、必应等
- **可伸缩尺寸** - 小(2×1)、中(4×2)、大(4×3)三种尺寸

## 🏗️ 架构设计

### 核心组件

1. **CustomizableWidgetProvider** - 小组件提供者
2. **WidgetConfigActivity** - 配置界面
3. **WidgetConfig** - 配置数据模型
4. **WidgetUtils** - 工具类
5. **ConfigItemAdapter** - 配置项适配器

### 文件结构

```
app/src/main/java/com/example/dalao/widget/
├── CustomizableWidgetProvider.java    # 小组件提供者
├── WidgetConfigActivity.java          # 配置页面
├── WidgetConfig.java                  # 配置数据类
├── WidgetUtils.java                   # 工具类
├── ConfigItemAdapter.java             # 配置适配器
└── WidgetDemoActivity.java            # 演示页面

app/src/main/res/layout/
├── customizable_widget_small.xml      # 小尺寸布局
├── customizable_widget_medium.xml     # 中尺寸布局
├── customizable_widget_large.xml      # 大尺寸布局
├── activity_widget_config.xml         # 配置页面布局
├── item_config_app.xml               # 配置项布局
└── activity_widget_demo.xml          # 演示页面布局

app/src/main/res/xml/
└── customizable_widget_info.xml       # 小组件信息
```

## 🚀 使用方法

### 1. 添加小组件到桌面

**方法一：通过演示页面**
```java
// 启动演示Activity
Intent intent = new Intent(context, WidgetDemoActivity.class);
startActivity(intent);
```

**方法二：手动添加**
1. 长按桌面空白处
2. 选择"小组件"或"Widget"
3. 找到"智能定制小组件"
4. 拖拽到桌面

### 2. 配置小组件

添加小组件后会自动打开配置页面，可以设置：

- **尺寸选择**：小、中、大三种尺寸
- **搜索框**：显示/隐藏搜索框
- **AI助手**：选择要显示的AI引擎
- **常用应用**：选择要显示的应用
- **搜索引擎**：选择要显示的搜索引擎

### 3. 使用小组件

- **点击搜索框**：打开搜索界面
- **点击AI图标**：启动对应的AI助手
- **点击应用图标**：启动对应的应用
- **点击搜索引擎图标**：使用对应搜索引擎搜索

## 🔧 技术实现

### 配置存储

使用SharedPreferences存储配置：
```java
// 保存配置
WidgetUtils.saveWidgetConfig(context, appWidgetId, config);

// 读取配置
WidgetConfig config = WidgetUtils.getWidgetConfig(context, appWidgetId);
```

### 动态布局

根据配置动态选择布局：
```java
private static int getLayoutForSize(WidgetSize size) {
    switch (size) {
        case SMALL: return R.layout.customizable_widget_small;
        case LARGE: return R.layout.customizable_widget_large;
        case MEDIUM:
        default: return R.layout.customizable_widget_medium;
    }
}
```

### 点击事件处理

使用PendingIntent处理点击事件：
```java
Intent clickIntent = new Intent(context, SimpleModeActivity.class);
clickIntent.putExtra("widget_type", "ai_chat");
clickIntent.putExtra("ai_engine", item.packageName);

PendingIntent pendingIntent = PendingIntent.getActivity(
    context, requestCode, clickIntent, 
    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
);

views.setOnClickPendingIntent(iconViewId, pendingIntent);
```

## 📱 支持的内容类型

### AI助手
- 智谱 (zhipu)
- DeepSeek (deepseek)
- 文心一言 (wenxin)
- 通义千问 (tongyi)
- ChatGPT (chatgpt)
- Claude (claude)
- Gemini (gemini)
- Kimi (kimi)

### 常用应用
- 微信 (com.tencent.mm)
- QQ (com.tencent.mobileqq)
- 支付宝 (com.eg.android.AlipayGphone)
- 淘宝 (com.taobao.taobao)
- 京东 (com.jingdong.app.mall)
- 抖音 (com.ss.android.ugc.aweme)
- 快手 (com.smile.gifmaker)
- 等等...

### 搜索引擎
- 百度 (baidu)
- Google (google)
- 必应 (bing)
- 搜狗 (sogou)
- 360搜索 (so360)
- 夸克 (quark)
- DuckDuckGo (duckduckgo)

## 🎯 特色功能

1. **完全可定制** - 用户可以自由选择显示的内容
2. **多尺寸支持** - 适应不同的桌面空间需求
3. **智能布局** - 根据尺寸自动调整图标排列
4. **配置持久化** - 配置信息永久保存
5. **实时更新** - 支持动态更新小组件内容

## 🔄 更新机制

小组件支持以下更新方式：

1. **配置更新** - 修改配置后自动更新
2. **手动刷新** - 通过演示页面刷新
3. **系统更新** - 系统重启后自动恢复

## 🛠️ 开发扩展

### 添加新的AI引擎

在`WidgetUtils.getAvailableAIEngines()`中添加：
```java
engines.add(new AppItem("新AI", "new_ai", "ic_new_ai"));
```

### 添加新的应用

在`WidgetUtils.getAvailableApps()`中添加：
```java
apps.add(new AppItem("新应用", "com.example.newapp", "ic_newapp"));
```

### 添加新的搜索引擎

在`WidgetUtils.getAvailableSearchEngines()`中添加：
```java
engines.add(new AppItem("新搜索", "newsearch", "ic_newsearch"));
```

## 📝 注意事项

1. **权限要求** - 需要小组件相关权限
2. **启动器兼容性** - 部分启动器可能不支持动态添加
3. **图标资源** - 确保对应的图标资源存在
4. **包名正确性** - 应用包名必须正确才能启动

## 🎉 总结

可定制小组件提供了一个灵活、强大的桌面定制解决方案，让用户能够根据自己的使用习惯创建个性化的桌面体验。通过简单的配置，用户可以快速访问最常用的AI助手、应用和搜索引擎，大大提升使用效率。
