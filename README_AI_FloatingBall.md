# AI悬浮球 - 智能搜索助手

## 项目概述

AI悬浮球是一个Android智能搜索助手应用，提供三种核心显示模式：**悬浮球模式**、**灵动岛模式**和**简易模式**。应用集成了多种AI引擎和搜索引擎，支持语音识别、多窗口浮动WebView、应用内搜索等功能。

## 核心架构

### 三大核心服务

#### 1. SimpleModeService (简易模式服务)

**主要功能：**
- 全屏简易搜索界面，类似手机桌面launcher
- 12宫格AI引擎快速启动
- 应用内搜索图标集成（淘宝、抖音、小红书等）
- 语音搜索集成
- 最小化到边缘功能
- 搜索历史管理

**设计思路：**
- 采用全屏覆盖窗口，提供沉浸式搜索体验
- 宫格布局设计，支持AI引擎和应用搜索的快速切换
- 智能模式切换：可自动切换到DualFloatingWebViewService进行搜索
- 边缘最小化设计：支持拖拽到屏幕边缘最小化为小球

**核心组件：**
```kotlin
// 主要布局文件
- simple_mode_layout.xml: 全屏搜索界面
- 12个宫格项目支持AI引擎配置
- 底部Tab导航（首页、搜索、语音、个人）
- 顶部搜索栏和控制按钮
```

#### 2. DualFloatingWebViewService (双窗口浮动WebView服务)

**主要功能：**
- 多窗口浮动WebView（支持1-3个窗口）
- 搜索引擎管理和切换
- 窗口拖拽、缩放、贴边隐藏
- 文本选择和快捷操作
- 搜索历史记录
- 键盘自适应调整

**设计思路：**
- 采用WindowManager实现真正的系统级浮动窗口
- 多窗口架构：支持同时打开多个搜索引擎进行对比
- 智能窗口管理：自动保存窗口位置、大小状态
- 响应式设计：支持横竖屏切换和键盘弹出适配

**核心组件：**
```kotlin
// 主要管理器
- FloatingWindowManager: 浮动窗口生命周期管理
- WebViewManager: WebView实例管理和搜索执行
- TextSelectionManager: 文本选择和操作菜单
- CustomWebView: 自定义WebView组件
```

#### 3. DynamicIslandService (灵动岛服务)

**主要功能：**
- 仿iPhone灵动岛交互设计
- 紧凑态和展开态切换
- 搜索引擎卡槽配置
- 应用搜索图标显示
- 通知集成显示
- 助手身份选择

**设计思路：**
- 模仿iPhone灵动岛的交互模式
- 状态栏区域悬浮显示
- 智能展开收缩动画
- 支持拖拽配置搜索引擎

## UI设计风格

### 设计原则
- **Material Design 3**：遵循Google最新设计规范
- **深色/浅色主题**：支持系统主题跟随
- **圆角卡片设计**：大量使用MaterialCardView
- **毛玻璃效果**：部分界面采用半透明背景
- **动画流畅性**：所有交互都有平滑的过渡动画

### 颜色系统
```xml
<!-- 主要颜色 -->
<color name="colorPrimary">#4A90E2</color>
<color name="colorPrimaryDark">#357ABD</color>
<color name="colorAccent">#FF5722</color>

<!-- 浮动窗口颜色 -->
<color name="floating_window_background">#FFFFFF</color>
<color name="floating_text_primary">#212121</color>
<color name="floating_menu_text_secondary">#757575</color>
```

### 尺寸规范
```xml
<!-- 浮动窗口尺寸 -->
<dimen name="floating_window_min_width">200dp</dimen>
<dimen name="floating_window_min_height">300dp</dimen>
<dimen name="floating_window_corner_radius">16dp</dimen>

<!-- 灵动岛尺寸 -->
<dimen name="dynamic_island_compact_height">40dp</dimen>
<dimen name="dynamic_island_expanded_height">150dp</dimen>

<!-- 搜索引擎图标 -->
<dimen name="search_engine_icon_size">40dp</dimen>
```

### 动画效果
- **窗口展开/收缩**：使用AccelerateDecelerateInterpolator
- **边缘贴附**：弹簧动画效果
- **菜单显示**：淡入淡出 + 缩放动画
- **页面切换**：滑动过渡动画

## 设置选项规划

### 1. 通用设置
- **显示模式**：悬浮球/灵动岛/简易模式
- **主题模式**：跟随系统/浅色/深色
- **左手模式**：界面布局镜像翻转
- **剪贴板监听**：自动检测复制内容
- **自动粘贴**：搜索时自动填入剪贴板内容

### 2. 悬浮球设置
- **透明度调节**：0-100%可调
- **单击操作**：浮动菜单/语音识别/双窗搜索/灵动岛面板
- **长按操作**：同上选项
- **自动贴边隐藏**：无操作后自动隐藏
- **主菜单显示内容**：普通搜索/AI搜索/应用搜索/多组合搜索

### 3. 搜索引擎设置
- **默认搜索引擎**：百度/Google/必应等
- **窗口1/2/3引擎**：分别配置不同窗口的默认引擎
- **AI引擎管理**：启用/禁用特定AI引擎
- **搜索引擎分组**：自定义分组管理
- **应用内搜索**：管理支持的应用列表

### 4. 灵动岛设置
- **单击/长按操作**：自定义交互行为
- **通知监听**：选择显示哪些应用通知
- **卡槽配置**：搜索引擎拖拽配置

### 5. AI助手设置
- **API密钥配置**：DeepSeek/ChatGPT API
- **身份档案管理**：多个AI助手身份
- **提示词模板**：预设对话模板

## iOS版本开发要点

### 1. 系统限制与解决方案

#### 悬浮窗实现
```swift
// iOS无法实现真正的系统级悬浮窗，需要替代方案：

// 方案1：使用Picture in Picture (PiP)
import AVKit
// 利用AVPictureInPictureController实现类似效果

// 方案2：使用App Extension + Widget
// 通过Today Extension或Widget实现快速访问

// 方案3：使用Shortcuts App集成
// 通过Siri Shortcuts提供语音搜索入口
```

#### 后台运行限制
```swift
// iOS后台运行受限，需要合理设计：

// 使用Background App Refresh
func application(_ application: UIApplication, 
                performFetchWithCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
    // 后台数据同步
}

// 使用Silent Push Notifications
// 通过推送唤醒应用更新数据
```

### 2. UI适配要点

#### 灵动岛适配
```swift
// 仅iPhone 14 Pro及以上支持真正的Dynamic Island
// 其他设备需要模拟实现

import UIKit

class DynamicIslandView: UIView {
    override func layoutSubviews() {
        super.layoutSubviews()
        
        // 检测设备类型
        if #available(iOS 16.1, *) {
            // iPhone 14 Pro系列适配
            setupRealDynamicIsland()
        } else {
            // 其他设备模拟实现
            setupSimulatedDynamicIsland()
        }
    }
}
```

#### 多窗口支持
```swift
// iOS 13+支持多窗口，但需要特殊处理
import UIKit

@available(iOS 13.0, *)
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        // 多窗口WebView实现
        setupMultiWindowWebView()
    }
}
```

### 3. 核心功能实现

#### 语音识别
```swift
import Speech

class VoiceRecognitionManager {
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-CN"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    
    func startRecording() {
        // 请求权限
        SFSpeechRecognizer.requestAuthorization { authStatus in
            // 实现语音识别
        }
    }
}
```

#### WebView管理
```swift
import WebKit

class CustomWebView: WKWebView {
    override init(frame: CGRect, configuration: WKWebViewConfiguration) {
        super.init(frame: frame, configuration: configuration)
        setupWebView()
    }
    
    private func setupWebView() {
        // 配置User Agent
        customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)"
        
        // 配置内容控制器
        configuration.userContentController.add(self, name: "messageHandler")
    }
}
```

#### 搜索引擎管理
```swift
struct SearchEngine {
    let name: String
    let url: String
    let iconName: String
    
    func getSearchURL(query: String) -> URL? {
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: url.replacingOccurrences(of: "{query}", with: encodedQuery))
    }
}

class SearchEngineManager {
    static let shared = SearchEngineManager()
    
    private let engines = [
        SearchEngine(name: "百度", url: "https://www.baidu.com/s?wd={query}", iconName: "baidu"),
        SearchEngine(name: "Google", url: "https://www.google.com/search?q={query}", iconName: "google"),
        // 更多引擎...
    ]
}
```

### 4. 权限处理

```swift
import AVFoundation
import Speech

class PermissionManager {
    static func requestMicrophonePermission(completion: @escaping (Bool) -> Void) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async {
                completion(granted)
            }
        }
    }
    
    static func requestSpeechRecognitionPermission(completion: @escaping (Bool) -> Void) {
        SFSpeechRecognizer.requestAuthorization { authStatus in
            DispatchQueue.main.async {
                completion(authStatus == .authorized)
            }
        }
    }
}
```

### 5. 数据持久化

```swift
import CoreData

class CoreDataManager {
    lazy var persistentContainer: NSPersistentContainer = {
        let container = NSPersistentContainer(name: "DataModel")
        container.loadPersistentStores { _, error in
            if let error = error {
                fatalError("Core Data error: \(error)")
            }
        }
        return container
    }()
    
    func saveContext() {
        let context = persistentContainer.viewContext
        if context.hasChanges {
            try? context.save()
        }
    }
}
```

### 6. 设计要求

#### 界面适配
- **安全区域适配**：适配iPhone X系列的刘海和底部安全区域
- **Dark Mode支持**：完整的深色模式适配
- **动态字体**：支持系统动态字体大小
- **无障碍访问**：VoiceOver和其他辅助功能支持

#### 性能优化
- **内存管理**：合理使用ARC，避免循环引用
- **网络优化**：使用URLSession进行网络请求
- **图片缓存**：使用SDWebImage或类似库
- **启动优化**：减少启动时间，延迟加载非必要组件

#### 用户体验
- **手势交互**：充分利用iOS手势系统
- **Haptic Feedback**：适当的触觉反馈
- **动画流畅**：使用Core Animation实现流畅动画
- **响应式设计**：适配不同屏幕尺寸的iPhone和iPad

### 7. 技术栈建议

```swift
// 推荐的iOS技术栈
- UI框架：UIKit + SwiftUI (混合开发)
- 网络：URLSession + Alamofire
- 图片：SDWebImage
- 数据库：Core Data + UserDefaults
- 语音：Speech Framework
- WebView：WKWebView
- 动画：Core Animation + Lottie
- 架构：MVVM + Combine
```

## 开发注意事项

### Android特有功能在iOS的替代方案

1. **系统级悬浮窗** → **Picture in Picture + Widget**
2. **无限制后台运行** → **Background App Refresh + Push Notifications**
3. **系统广播接收** → **URL Schemes + Universal Links**
4. **自定义键盘** → **Keyboard Extension**
5. **应用间直接调用** → **URL Schemes协议**

### 上架App Store注意事项

1. **隐私说明**：详细说明语音识别、网络访问等权限用途
2. **功能限制**：避免实现可能被拒绝的功能（如模拟系统UI）
3. **内容审核**：确保搜索结果不包含违规内容
4. **性能标准**：确保应用启动时间、内存使用符合Apple标准

## 总结

AI悬浮球项目在Android平台实现了完整的系统级搜索助手功能，在移植到iOS时需要根据iOS系统特性进行适当的架构调整和功能替代。重点关注用户体验的连贯性，通过iOS原生的交互方式实现类似的功能效果。 