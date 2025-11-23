# ExoPlayer 高级功能使用指南

## 功能概览

本文档介绍 ExoPlayer 的四大高级功能：
1. **播放列表增强** - 拖拽排序、批量管理
2. **网络优化** - 预加载、缓存策略
3. **播放统计** - 播放次数、时长统计
4. **无障碍优化** - TalkBack 支持、焦点导航

## 1. 播放列表增强

### 功能特性

- ✅ **拖拽排序**：长按拖动调整播放顺序
- ✅ **批量管理**：多选、批量删除、批量移动
- ✅ **自动播放**：播放完成后自动播放下一个
- ✅ **循环模式**：单曲循环、列表循环、随机播放

### 使用示例

```kotlin
// 1. 创建播放列表适配器
val adapter = PlaylistAdapter(
    playlist = playlistManager.getPlaylist().toMutableList(),
    onItemClick = { item ->
        // 点击播放
        videoManager.show(item.url, item.title)
    },
    onItemDelete = { item ->
        // 删除项
        playlistManager.removeVideo(item.url)
    }
)

// 2. 设置 RecyclerView
recyclerView.adapter = adapter
recyclerView.layoutManager = LinearLayoutManager(context)

// 3. 添加拖拽支持
val itemTouchHelper = ItemTouchHelper(PlaylistItemTouchHelperCallback(adapter))
itemTouchHelper.attachToRecyclerView(recyclerView)

// 4. 监听项移动
adapter.setOnItemMovedListener { fromPos, toPos ->
    // 保存新的顺序
    playlistManager.savePlaylist(adapter.getPlaylist())
}

// 5. 批量管理
btnBatchManage.setOnClickListener {
    if (adapter.isInSelectionMode()) {
        adapter.exitSelectionMode()
    } else {
        adapter.enterSelectionMode()
    }
}

// 6. 批量删除
btnDeleteSelected.setOnClickListener {
    val selectedItems = adapter.getSelectedItems()
    selectedItems.forEach { playlistManager.removeVideo(it.url) }
    adapter.deleteSelected()
}
```

### 布局文件

播放列表项布局已创建在 `res/layout/item_playlist_video.xml`

## 2. 网络优化

### 功能特性

- ✅ **智能缓存**：100MB LRU 缓存
- ✅ **网络监听**：WiFi/移动网络自动切换
- ✅ **预加载**：WiFi 下预加载下一个视频
- ✅ **自适应码率**：根据网络状况调整

### 使用示例

```kotlin
// 1. 创建预加载管理器
val preloader = VideoPreloader(context)

// 2. 监听网络状态
preloader.setNetworkStateListener(object : VideoPreloader.NetworkStateListener {
    override fun onNetworkChanged(networkType: VideoPreloader.NetworkType) {
        when (networkType) {
            VideoPreloader.NetworkType.WIFI -> {
                Log.d(TAG, "切换到 WiFi，启用预加载")
                // 预加载下一个视频
                val nextVideo = playlistManager.getNextVideo()
                nextVideo?.let { preloader.preloadVideo(it.url) }
            }
            VideoPreloader.NetworkType.MOBILE -> {
                Log.d(TAG, "切换到移动网络，禁用预加载")
            }
            else -> {
                Log.d(TAG, "网络断开")
            }
        }
    }
    
    override fun onNetworkLost() {
        Log.d(TAG, "网络已断开")
        Toast.makeText(context, "网络已断开", Toast.LENGTH_SHORT).show()
    }
})

// 3. 使用缓存数据源
val cacheDataSourceFactory = preloader.createCacheDataSourceFactory()
exoPlayerManager.setDataSourceFactory(cacheDataSourceFactory)

// 4. 查看缓存大小
val cacheSize = preloader.getCacheSize()
Log.d(TAG, "缓存大小: ${preloader.formatCacheSize(cacheSize)}")

// 5. 清空缓存
btnClearCache.setOnClickListener {
    preloader.clearCache()
    Toast.makeText(context, "缓存已清空", Toast.LENGTH_SHORT).show()
}

// 6. 释放资源
override fun onDestroy() {
    super.onDestroy()
    preloader.release()
}
```

## 3. 播放统计

### 功能特性

- ✅ **播放次数**：记录每个视频的播放次数
- ✅ **播放时长**：统计总播放时长
- ✅ **完成率**：记录完整播放次数
- ✅ **统计报告**：生成详细的统计报告

### 使用示例

```kotlin
// 1. 创建统计管理器
val statistics = PlaybackStatistics(context)

// 2. 记录播放开始
exoPlayerManager.setOnPreparedListener {
    statistics.recordPlayStart(videoUrl, videoTitle)
}

// 3. 记录播放时长（定期调用）
var lastRecordTime = 0L
val recordInterval = 10000L // 每 10 秒记录一次

updateHandler.postDelayed(object : Runnable {
    override fun run() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRecordTime >= recordInterval) {
            statistics.recordPlayTime(videoUrl, recordInterval)
            lastRecordTime = currentTime
        }
        updateHandler.postDelayed(this, 1000)
    }
}, 1000)

// 4. 记录播放完成
exoPlayerManager.setOnCompletionListener {
    val watchPercentage = (currentPosition.toFloat() / duration.toFloat()) * 100
    statistics.recordPlayCompletion(videoUrl, watchPercentage)
}

// 5. 获取统计数据
val mostPlayed = statistics.getMostPlayedVideos(10)
mostPlayed.forEach { video ->
    Log.d(TAG, "${video.videoTitle}: ${video.playCount}次")
}

// 6. 生成统计报告
val report = statistics.generateReport()
Log.d(TAG, report)

// 7. 显示统计信息
btnShowStats.setOnClickListener {
    val totalPlayTime = statistics.getTotalPlayTime()
    val totalPlayCount = statistics.getTotalPlayCount()
    
    val message = """
        总播放次数: $totalPlayCount
        总播放时长: ${statistics.formatPlayTime(totalPlayTime)}
    """.trimIndent()
    
    AlertDialog.Builder(context)
        .setTitle("播放统计")
        .setMessage(message)
        .setPositiveButton("确定", null)
        .show()
}
```

## 4. 无障碍优化

### 功能特性

- ✅ **TalkBack 支持**：完整的屏幕阅读器支持
- ✅ **内容描述**：所有控件都有清晰的描述
- ✅ **焦点导航**：优化的焦点顺序
- ✅ **手势辅助**：自定义无障碍操作

### 使用示例

```kotlin
// 1. 创建无障碍辅助类
val accessibilityHelper = AccessibilityHelper(context)

// 2. 设置播放/暂停按钮
accessibilityHelper.setupPlayPauseButton(playPauseBtn, isPlaying = false)

// 更新播放状态时
playPauseBtn.setOnClickListener {
    val isPlaying = exoPlayerManager.isPlaying()
    exoPlayerManager.togglePlayPause()
    accessibilityHelper.setupPlayPauseButton(playPauseBtn, !isPlaying)
    accessibilityHelper.announcePlaybackStateChange(!isPlaying)
}

// 3. 设置进度条
accessibilityHelper.setupSeekBar(
    seekBar = progressBar,
    currentTime = formatTime(currentPosition),
    totalTime = formatTime(duration)
)

// 4. 设置其他控件
accessibilityHelper.setupVolumeButton(muteBtn, isMuted = false)
accessibilityHelper.setupFullscreenButton(fullscreenBtn, isFullscreen = false)
accessibilityHelper.setupSpeedButton(speedBtn, speed = 1.0f)
accessibilityHelper.setupLoopButton(loopBtn, isLooping = false)

// 5. 设置视频容器
accessibilityHelper.setupVideoContainer(videoContainer, videoTitle)

// 6. 播报状态变化
// 音量变化
accessibilityHelper.announceVolumeChange(volume = 50)

// 亮度变化
accessibilityHelper.announceBrightnessChange(brightness = 75)

// 播放速度变化
accessibilityHelper.announceSpeedChange(speed = 1.5f)

// 视频切换
accessibilityHelper.announceVideoChange(videoTitle = "新视频标题")

// 7. 设置焦点顺序
val controlButtons = listOf(
    playPauseBtn,
    muteBtn,
    fullscreenBtn,
    speedBtn,
    loopBtn
)
accessibilityHelper.setupFocusOrder(controlButtons)

// 8. 检查无障碍服务状态
if (accessibilityHelper.isTalkBackEnabled()) {
    Log.d(TAG, "TalkBack 已启用")
    // 可以根据需要调整 UI
}
```

## 综合使用示例

```kotlin
class EnhancedVideoPlayerActivity : AppCompatActivity() {
    
    private lateinit var videoManager: SystemOverlayVideoManager
    private lateinit var preloader: VideoPreloader
    private lateinit var statistics: PlaybackStatistics
    private lateinit var accessibilityHelper: AccessibilityHelper
    private lateinit var playlistAdapter: PlaylistAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_video_player)
        
        // 初始化所有组件
        initializeComponents()
        
        // 设置播放列表
        setupPlaylist()
        
        // 设置网络监听
        setupNetworkMonitoring()
        
        // 设置统计
        setupStatistics()
        
        // 设置无障碍
        setupAccessibility()
    }
    
    private fun initializeComponents() {
        videoManager = SystemOverlayVideoManager(this)
        preloader = VideoPreloader(this)
        statistics = PlaybackStatistics(this)
        accessibilityHelper = AccessibilityHelper(this)
    }
    
    private fun setupPlaylist() {
        playlistAdapter = PlaylistAdapter(
            playlist = playlistManager.getPlaylist().toMutableList(),
            onItemClick = { item ->
                playVideo(item)
            },
            onItemDelete = { item ->
                playlistManager.removeVideo(item.url)
            }
        )
        
        recyclerView.adapter = playlistAdapter
        val itemTouchHelper = ItemTouchHelper(PlaylistItemTouchHelperCallback(playlistAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
    
    private fun setupNetworkMonitoring() {
        preloader.setNetworkStateListener(object : VideoPreloader.NetworkStateListener {
            override fun onNetworkChanged(networkType: VideoPreloader.NetworkType) {
                // 处理网络变化
            }
            
            override fun onNetworkLost() {
                // 处理网络断开
            }
        })
    }
    
    private fun setupStatistics() {
        // 记录播放开始
        videoManager.setOnVideoStartListener { url, title ->
            statistics.recordPlayStart(url, title)
        }
        
        // 记录播放完成
        videoManager.setOnVideoCompleteListener { url, watchPercentage ->
            statistics.recordPlayCompletion(url, watchPercentage)
        }
    }
    
    private fun setupAccessibility() {
        // 设置所有控件的无障碍描述
        accessibilityHelper.setupPlayPauseButton(playPauseBtn, false)
        accessibilityHelper.setupSeekBar(progressBar, "00:00", "00:00")
        // ... 其他控件
    }
    
    private fun playVideo(item: VideoPlaylistManager.PlaylistItem) {
        videoManager.show(item.url, item.title)
        statistics.recordPlayStart(item.url, item.title)
        accessibilityHelper.announceVideoChange(item.title)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        preloader.release()
    }
}
```

## 性能优化建议

### 1. 缓存管理
- 定期清理缓存（建议每周）
- 监控缓存大小，避免占用过多空间
- WiFi 下预加载，移动网络下禁用

### 2. 统计数据
- 批量保存，减少 I/O 操作
- 定期清理旧数据（建议保留 3 个月）
- 异步处理，避免阻塞主线程

### 3. 无障碍
- 只在启用无障碍服务时播报
- 避免过于频繁的播报
- 提供清晰简洁的描述

## 常见问题

### Q1: 拖拽排序不生效？
A: 确保 RecyclerView 已设置 LayoutManager，并且 ItemTouchHelper 已正确绑定。

### Q2: 缓存占用空间过大？
A: 调整 CACHE_SIZE 常量，或定期调用 clearCache() 清理缓存。

### Q3: 统计数据丢失？
A: 确保在适当的时机调用 saveStatistics()，建议在 onPause() 中保存。

### Q4: TalkBack 播报不正确？
A: 检查 contentDescription 是否正确设置，并确保在状态变化时更新。

## 总结

这四大高级功能为 ExoPlayer 提供了：
- 🎯 **更好的用户体验**（拖拽排序、批量管理）
- 🚀 **更快的加载速度**（预加载、缓存）
- 📊 **数据洞察**（播放统计）
- ♿ **无障碍支持**（TalkBack、焦点导航）

所有功能都已实现并可以直接使用！
