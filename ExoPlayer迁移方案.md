# ExoPlayer 迁移方案 - 替代 VideoView

## 📊 VideoView vs ExoPlayer 对比

### 当前使用 VideoView 的限制

| 限制项 | VideoView 的问题 | ExoPlayer 的优势 |
|--------|----------------|-----------------|
| **格式支持** | 仅支持系统原生格式（H.264, H.265） | 支持更多格式（VP9, AV1, FLAC, Opus等） |
| **流媒体** | 不支持 DASH、HLS、SmoothStreaming | 原生支持所有主流流媒体协议 |
| **字幕** | 不支持外挂字幕 | 原生支持 SRT、VTT、ASS、SSA 等 |
| **性能** | 单线程解码，性能较差 | 多线程解码，硬件加速优化 |
| **自定义** | API 有限，难以扩展 | 高度可定制，模块化设计 |
| **错误处理** | 错误信息不详细 | 详细的错误报告和恢复机制 |
| **缓冲策略** | 固定缓冲策略 | 可自定义缓冲策略，节省流量 |
| **DRM 支持** | 仅支持 Widevine | 支持 Widevine、PlayReady、FairPlay |
| **播放速度** | Android 6.0+ 才支持 | 全版本支持，更流畅 |
| **音频处理** | 不支持音频增强 | 支持音频增强、音效处理 |

---

## 🚀 ExoPlayer 的核心优势

### 1. **更强大的格式支持**

```kotlin
// VideoView: 仅支持系统原生格式
videoView.setVideoURI(Uri.parse(url))

// ExoPlayer: 支持更多格式和协议
val dataSourceFactory = DefaultHttpDataSource.Factory()
    .setUserAgent("YourApp/1.0")
    .setAllowCrossProtocolRedirects(true)

val mediaItem = MediaItem.fromUri(url)
player.setMediaItem(mediaItem)
player.prepare()
```

**支持的格式：**
- **视频编码**：H.264, H.265 (HEVC), VP8, VP9, AV1
- **音频编码**：AAC, MP3, FLAC, Opus, Vorbis
- **容器格式**：MP4, WebM, MKV, TS, FLV, OGG
- **流媒体协议**：DASH, HLS, SmoothStreaming, RTSP

### 2. **原生字幕支持**

```kotlin
// ExoPlayer 原生支持多种字幕格式
val subtitleUri = Uri.parse("https://example.com/subtitle.srt")
val subtitleMediaItem = MediaItem.SubtitleConfiguration(subtitleUri)
    .setMimeType(MimeTypes.TEXT_VTT)
    .setLanguage("zh-CN")
    .setLabel("中文字幕")

val mediaItem = MediaItem.Builder()
    .setUri(videoUri)
    .setSubtitleConfigurations(listOf(subtitleMediaItem))
    .build()

player.setMediaItem(mediaItem)
```

**支持的字幕格式：**
- SRT (SubRip)
- VTT (WebVTT)
- ASS/SSA (Advanced SubStation Alpha)
- TTML (Timed Text Markup Language)
- SMI (SAMI)

**字幕功能：**
- 多语言字幕切换
- 字幕样式自定义（字体、大小、颜色、位置）
- 字幕同步调整
- 自动下载字幕

### 3. **自适应码率 (ABR) 流媒体**

```kotlin
// ExoPlayer 原生支持自适应码率
val dataSourceFactory = DefaultHttpDataSource.Factory()
val dashMediaSourceFactory = DashMediaSource.Factory(dataSourceFactory)
    .setDrmSessionManagerProvider { drmSessionManager }
    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy())

val mediaItem = MediaItem.fromUri(dashUrl)
val mediaSource = dashMediaSourceFactory.createMediaSource(mediaItem)
player.setMediaSource(mediaSource)
```

**优势：**
- 根据网络状况自动切换清晰度
- 节省流量（弱网时降低清晰度）
- 提升播放流畅度
- 支持 DASH、HLS 自适应流

### 4. **更强大的播放控制**

```kotlin
// ExoPlayer 提供更精细的控制
player.playbackParameters = PlaybackParameters(
    speed = 1.5f,  // 1.5倍速播放
    pitch = 1.0f   // 保持音调
)

// 音频增强
val audioAttributes = AudioAttributes.Builder()
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .setUsage(C.USAGE_MEDIA)
    .build()
player.setAudioAttributes(audioAttributes, true)

// 视频缩放模式
playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
```

**播放控制功能：**
- 播放速度调整（0.25x - 2.0x，更流畅）
- 音调保持（变速不变调）
- 音频增强（低音增强、均衡器）
- 视频缩放模式（适应、填充、缩放）
- 循环播放（单曲、列表、随机）

### 5. **更好的性能优化**

```kotlin
// ExoPlayer 性能优化配置
val renderersFactory = DefaultRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    .setEnableDecoderFallback(true)  // 解码器降级
    .setMediaCodecSelector(MediaCodecSelector.DEFAULT)

val player = ExoPlayer.Builder(context)
    .setRenderersFactory(renderersFactory)
    .setLoadControl(DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15000,  // 最小缓冲
            50000,  // 最大缓冲
            2500,   // 播放缓冲
            5000    // 重缓冲
        )
        .build())
    .build()
```

**性能优势：**
- **多线程解码**：充分利用多核 CPU
- **硬件加速**：自动使用硬件解码器
- **智能缓冲**：可自定义缓冲策略
- **内存优化**：更高效的内存管理
- **后台播放**：支持后台音频播放

### 6. **详细的播放状态和错误处理**

```kotlin
// ExoPlayer 提供详细的播放状态监听
player.addListener(object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> { /* 空闲 */ }
            Player.STATE_BUFFERING -> { /* 缓冲中 */ }
            Player.STATE_READY -> { /* 准备就绪 */ }
            Player.STATE_ENDED -> { /* 播放结束 */ }
        }
    }
    
    override fun onPlayerError(error: PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                // 网络连接失败
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                // HTTP 错误
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                // 格式错误
            }
            // ... 更多错误类型
        }
    }
    
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        // 视频尺寸变化
        val width = videoSize.width
        val height = videoSize.height
        val pixelAspectRatio = videoSize.pixelAspectRatio
    }
    
    override fun onTracksChanged(tracks: Tracks) {
        // 音视频轨道变化
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val track = group.getTrackFormat(i)
                if (track.codecs != null) {
                    // 处理音视频轨道
                }
            }
        }
    }
})
```

### 7. **DRM 内容保护支持**

```kotlin
// ExoPlayer 支持多种 DRM 方案
val drmSessionManager = DefaultDrmSessionManager.Builder()
    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm::class.java)
    .build(DefaultDrmSessionManager.PROVIDER_FRAMEWORK)

val mediaItem = MediaItem.Builder()
    .setUri(videoUri)
    .setDrmConfiguration(
        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(licenseUri)
            .setLicenseRequestHeaders(headers)
            .build()
    )
    .build()
```

**支持的 DRM：**
- Widevine (Google)
- PlayReady (Microsoft)
- FairPlay (Apple)
- ClearKey

### 8. **音频焦点和通知集成**

```kotlin
// ExoPlayer 自动处理音频焦点
player.setAudioAttributes(
    AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build(),
    true  // 自动处理音频焦点
)

// 与 MediaSession 集成，支持锁屏控制
val mediaSession = MediaSession.Builder(context, player)
    .setCallback(MediaSessionCallback())
    .build()
```

**功能：**
- 自动处理音频焦点（来电话时暂停）
- 锁屏控制（播放/暂停、上一首/下一首）
- 通知栏控制
- 蓝牙设备控制

### 9. **视频质量切换**

```kotlin
// ExoPlayer 支持动态切换视频质量
player.addListener(object : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        val videoTrackGroups = tracks.groups.filter { 
            it.type == C.TRACK_TYPE_VIDEO 
        }
        
        // 显示质量选择菜单
        showQualitySelector(videoTrackGroups) { selectedTrack ->
            val parameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    TrackSelectionOverride(selectedTrack.group, selectedTrack.indices)
                )
                .build()
            player.trackSelectionParameters = parameters
        }
    }
})
```

**质量切换功能：**
- 显示所有可用清晰度
- 动态切换（无需重新加载）
- 显示码率信息
- 自动选择最佳质量

### 10. **播放列表和队列管理**

```kotlin
// ExoPlayer 原生支持播放列表
val mediaItems = listOf(
    MediaItem.fromUri(video1Url),
    MediaItem.fromUri(video2Url),
    MediaItem.fromUri(video3Url)
)

player.addMediaItems(mediaItems)
player.prepare()

// 播放列表控制
player.seekToNext()      // 下一首
player.seekToPrevious()  // 上一首
player.seekTo(1, 0)      // 跳转到指定位置
```

---

## 📦 迁移步骤

### 步骤 1: 添加 ExoPlayer 依赖

```gradle
// app/build.gradle
dependencies {
    // ExoPlayer 核心库
    implementation 'androidx.media3:media3-exoplayer:1.2.0'
    implementation 'androidx.media3:media3-ui:1.2.0'
    implementation 'androidx.media3:media3-common:1.2.0'
    
    // 可选：DASH 支持
    implementation 'androidx.media3:media3-exoplayer-dash:1.2.0'
    
    // 可选：HLS 支持
    implementation 'androidx.media3:media3-exoplayer-hls:1.2.0'
    
    // 可选：SmoothStreaming 支持
    implementation 'androidx.media3:media3-exoplayer-smoothstreaming:1.2.0'
    
    // 可选：字幕支持
    implementation 'androidx.media3:media3-extractor:1.2.0'
}
```

### 步骤 2: 创建 ExoPlayer 管理器

```kotlin
class ExoPlayerManager(private val context: Context) {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    
    fun initialize(playerView: PlayerView) {
        this.playerView = playerView
        
        player = ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 50000, 2500, 5000)
                .build())
            .build()
            .apply {
                // 设置音频属性
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                
                // 添加监听器
                addListener(PlayerEventListener())
            }
        
        playerView.player = player
    }
    
    fun play(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }
    
    fun release() {
        player?.release()
        player = null
    }
}
```

### 步骤 3: 替换 VideoView

```kotlin
// 之前：使用 VideoView
val videoView = VideoView(context)
videoView.setVideoURI(Uri.parse(url))

// 现在：使用 ExoPlayer
val playerView = PlayerView(context)
val exoPlayerManager = ExoPlayerManager(context)
exoPlayerManager.initialize(playerView)
exoPlayerManager.play(url)
```

---

## 🎯 迁移后的新功能

### 1. **字幕支持** ✅
- 支持 SRT、VTT、ASS 等格式
- 多语言字幕切换
- 字幕样式自定义

### 2. **自适应码率** ✅
- 自动根据网络切换清晰度
- 节省流量
- 提升播放流畅度

### 3. **更多格式支持** ✅
- VP9、AV1 视频编码
- FLAC、Opus 音频编码
- MKV、WebM 容器格式

### 4. **播放速度调整** ✅
- 更流畅的变速播放
- 音调保持功能
- 全版本支持

### 5. **视频质量切换** ✅
- 动态切换清晰度
- 显示码率信息
- 无需重新加载

### 6. **更好的性能** ✅
- 多线程解码
- 硬件加速
- 智能缓冲

### 7. **DRM 支持** ✅
- Widevine、PlayReady、FairPlay
- 支持加密内容播放

### 8. **音频增强** ✅
- 低音增强
- 均衡器
- 音效处理

### 9. **播放列表** ✅
- 原生播放列表支持
- 队列管理
- 自动播放下一首

### 10. **锁屏控制** ✅
- 通知栏控制
- 锁屏控制
- 蓝牙设备控制

---

## ⚠️ 注意事项

### 1. **APK 体积增加**
- ExoPlayer 会增加约 2-3MB 的 APK 体积
- 可以使用 ProGuard 优化

### 2. **学习曲线**
- ExoPlayer API 比 VideoView 复杂
- 需要学习新的 API

### 3. **兼容性**
- 最低支持 Android 5.0 (API 21)
- 某些功能需要更高版本

### 4. **迁移工作量**
- 需要重构现有代码
- 需要测试所有功能

---

## 📈 性能对比

| 指标 | VideoView | ExoPlayer |
|------|-----------|-----------|
| **启动速度** | 较慢 | 更快 |
| **内存占用** | 较高 | 较低 |
| **CPU 使用** | 较高 | 较低 |
| **网络优化** | 无 | 有（智能缓冲）|
| **格式支持** | 少 | 多 |
| **错误恢复** | 差 | 好 |

---

## 🎬 总结

**ExoPlayer 迁移的优势：**
1. ✅ **功能更强大**：字幕、自适应码率、DRM 等
2. ✅ **性能更好**：多线程解码、硬件加速
3. ✅ **格式支持更多**：VP9、AV1、FLAC 等
4. ✅ **可定制性更强**：模块化设计，易于扩展
5. ✅ **错误处理更好**：详细的错误信息和恢复机制

**建议：**
- 如果只需要基础播放功能，VideoView 足够
- 如果需要字幕、自适应码率、更多格式支持，建议迁移到 ExoPlayer
- 迁移可以分阶段进行，先迁移核心功能，再逐步添加新功能


