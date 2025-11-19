package com.example.aifloatingball.viewer

import java.io.Serializable

/**
 * 阅读器数据模型
 */

/**
 * 书签数据
 */
data class Bookmark(
    val id: String,
    val filePath: String,
    val pageIndex: Int,
    val position: Int, // 在页面中的位置（字符索引）
    val text: String, // 书签位置的文本内容
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * 划线/高亮数据
 */
data class Highlight(
    val id: String,
    val filePath: String,
    val pageIndex: Int,
    val startPosition: Int, // 起始位置
    val endPosition: Int, // 结束位置
    val text: String, // 划线的文本内容
    val color: String = "#FFEB3B", // 高亮颜色
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * 笔记数据
 */
data class Note(
    val id: String,
    val filePath: String,
    val pageIndex: Int,
    val position: Int, // 笔记位置
    val text: String, // 笔记关联的文本
    val noteContent: String, // 笔记内容
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * 章节数据
 */
data class Chapter(
    val id: String,
    val title: String,
    val pageIndex: Int, // 章节起始页码
    val position: Int // 章节在文本中的位置
) : Serializable

/**
 * 阅读进度
 */
data class ReadingProgress(
    val filePath: String,
    val currentPage: Int,
    val totalPages: Int,
    val position: Int, // 当前阅读位置
    val lastReadTime: Long = System.currentTimeMillis()
) : Serializable

/**
 * 阅读器设置
 */
data class ReaderSettings(
    var fontSize: Int = 18, // 字体大小（sp）
    var lineHeight: Float = 1.6f, // 行距
    var marginHorizontal: Int = 20, // 水平边距（dp）
    var marginVertical: Int = 20, // 垂直边距（dp）
    var backgroundColor: String = "#FFFFFF", // 背景颜色
    var textColor: String = "#333333", // 文字颜色
    var theme: ReaderTheme = ReaderTheme.LIGHT, // 主题
    var brightness: Float = 1.0f, // 亮度（0.0-1.0）
    var autoReadSpeed: Int = 3000, // 自动翻页速度（毫秒）
    var ttsSpeed: Float = 1.0f, // TTS语速
    var ttsPitch: Float = 1.0f, // TTS音调
    var pageAnimationDuration: Int = 0, // 页面动画持续时间（毫秒，0表示无动画）
    var fontFamily: String = "sans-serif", // 字体家族：sans-serif, serif, monospace
    var keepScreenOn: Boolean = false, // 保持屏幕常亮
    var isAutoReadEnabled: Boolean = false // 自动阅读是否启用
) : Serializable

/**
 * 阅读器主题
 */
enum class ReaderTheme {
    LIGHT, // 浅色主题
    DARK, // 深色主题
    SEPIA, // 护眼模式
    GREEN // 绿色护眼
}

