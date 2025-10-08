package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import androidx.core.content.ContextCompat

/**
 * 简化版Markdown渲染工具类
 * 专注于文本格式化和智能换行
 */
class SimpleMarkdownRenderer private constructor(private val context: Context) {
    
    private val primaryColor: Int
    private val secondaryColor: Int
    private val accentColor: Int
    
    init {
        // 获取主题颜色（使用系统默认颜色）
        primaryColor = ContextCompat.getColor(context, android.R.color.black)
        secondaryColor = ContextCompat.getColor(context, android.R.color.darker_gray)
        accentColor = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
    }
    
    companion object {
        @Volatile
        private var INSTANCE: SimpleMarkdownRenderer? = null
        
        fun getInstance(context: Context): SimpleMarkdownRenderer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SimpleMarkdownRenderer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 清理和优化文本格式
     */
    fun cleanAndOptimizeText(text: String): String {
        var cleaned = text
        
        // 1. 清理HTML标签
        cleaned = cleaned.replace("<[^>]+>".toRegex(), "")
        
        // 2. 清理多余的空白字符
        cleaned = cleaned.replace("\\s+".toRegex(), " ")
        
        // 3. 清理多余的空行
        cleaned = cleaned.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")
        
        // 4. 处理特殊字符
        cleaned = cleaned.replace("&nbsp;".toRegex(), " ")
        cleaned = cleaned.replace("&lt;".toRegex(), "<")
        cleaned = cleaned.replace("&gt;".toRegex(), ">")
        cleaned = cleaned.replace("&amp;".toRegex(), "&")
        
        return cleaned.trim()
    }
    
    /**
     * 智能换行处理
     * 参考主流AI的换行策略
     */
    fun processSmartLineBreaks(text: String): String {
        var processed = text
        
        // 1. 在句号、问号、感叹号后添加换行
        processed = processed.replace("([。！？])\\s*([一-龯A-Z0-9])".toRegex(), "$1\n\n$2")
        
        // 2. 在冒号后添加换行
        processed = processed.replace("([：:])\\s*([一-龯A-Z0-9])".toRegex(), "$1\n$2")
        
        // 3. 在分号后添加换行
        processed = processed.replace("([；;])\\s*([一-龯A-Z0-9])".toRegex(), "$1\n$2")
        
        // 4. 处理列表项之间的换行
        processed = processed.replace("(\\d+\\.\\s+[^\\n]+)\\n(\\d+\\.\\s+)".toRegex(), "$1\n\n$2")
        
        // 5. 处理段落之间的换行
        processed = processed.replace("([。！？])\\s*([一-龯A-Z0-9])".toRegex(), "$1\n\n$2")
        
        return processed
    }
    
    /**
     * 应用DeepSeek风格的格式化
     */
    fun applyDeepSeekFormatting(content: String): String {
        var formatted = content
        
        // 清理HTML标签
        formatted = cleanHtmlTags(formatted)
        
        // 去掉表情符号
        formatted = formatted.replace("[\uD83C-\uDBFF\uDC00-\uDFFF]+".toRegex(), "")
        
        // 应用格式化
        formatted = formatHeadings(formatted)
        formatted = formatLists(formatted)
        formatted = formatCodeBlocks(formatted)
        formatted = formatEmphasis(formatted)
        formatted = formatParagraphs(formatted)
        formatted = formatSpecialStructures(formatted)
        formatted = finalCleanup(formatted)
        
        return formatted
    }
    
    /**
     * 清理HTML标签
     */
    private fun cleanHtmlTags(content: String): String {
        var cleaned = content
        
        // 移除HTML标签
        cleaned = cleaned.replace("<[^>]+>".toRegex(), "")
        
        // 处理常见的HTML实体
        cleaned = cleaned.replace("&nbsp;".toRegex(), " ")
        cleaned = cleaned.replace("&lt;".toRegex(), "<")
        cleaned = cleaned.replace("&gt;".toRegex(), ">")
        cleaned = cleaned.replace("&amp;".toRegex(), "&")
        cleaned = cleaned.replace("&quot;".toRegex(), "\"")
        cleaned = cleaned.replace("&#39;".toRegex(), "'")
        
        return cleaned
    }
    
    /**
     * 格式化标题 - 参考DeepSeek格式
     */
    private fun formatHeadings(content: String): String {
        var formatted = content
        
        // 处理多级标题，使用更清晰的格式
        formatted = formatted.replace("^#{6}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{5}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{4}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{3}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{2}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1\n")
        formatted = formatted.replace("^#{1}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n■ $1\n")
        
        // 处理常见的小标题格式（参考DeepSeek）
        formatted = formatted.replace("^([一二三四五六七八九十]+[、.])\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1 $2\n")
        formatted = formatted.replace("^([0-9]+[、.])\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1 $2\n")
        
        // 处理带冒号的小标题
        formatted = formatted.replace("^([^：:]+[：:])\\s*$".toRegex(RegexOption.MULTILINE), "\n▪ $1\n")
        
        return formatted
    }
    
    /**
     * 格式化列表 - 参考DeepSeek格式
     */
    private fun formatLists(content: String): String {
        var formatted = content
        
        // 处理有序列表 - 保持数字序号，增强格式
        formatted = formatted.replace("^\\s*(\\d+)\\.\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  $1. $2")
        
        // 处理中文数字列表
        formatted = formatted.replace("^\\s*([一二三四五六七八九十]+)\\.\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  $1. $2")
        
        // 处理无序列表 - 使用不同符号区分层级
        formatted = formatted.replace("^\\s*[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  • $1")
        
        // 处理嵌套列表（二级）
        formatted = formatted.replace("^\\s{2,4}[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "    ◦ $1")
        
        // 处理嵌套列表（三级）
        formatted = formatted.replace("^\\s{6,8}[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "      ▪ $1")
        
        // 处理特殊列表格式（参考DeepSeek）
        formatted = formatted.replace("^\\s*([•·▪▫])\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  • $2")
        
        return formatted
    }
    
    /**
     * 格式化代码块
     */
    private fun formatCodeBlocks(content: String): String {
        var formatted = content
        
        // 处理代码块
        formatted = formatted.replace("```([^\\n]+)\\n([\\s\\S]*?)```".toRegex()) { matchResult ->
            val language = matchResult.groupValues[1]
            val code = matchResult.groupValues[2]
            "\n┌─ $language ─┐\n$code\n└─────────────┘\n"
        }
        
        // 处理行内代码
        formatted = formatted.replace("`([^`]+)`".toRegex(), "「$1」")
        
        return formatted
    }
    
    /**
     * 格式化强调和粗体 - 参考DeepSeek格式
     */
    private fun formatEmphasis(content: String): String {
        var formatted = content
        
        // 处理粗体 **text** 或 __text__
        formatted = formatted.replace("\\*\\*(.*?)\\*\\*".toRegex(), "【$1】")
        formatted = formatted.replace("__(.*?)__".toRegex(), "【$1】")
        
        // 处理斜体 *text* 或 _text_
        formatted = formatted.replace("\\*(.*?)\\*".toRegex(), "$1")
        formatted = formatted.replace("_(.*?)_".toRegex(), "$1")
        
        // 处理删除线 ~~text~~
        formatted = formatted.replace("~~(.*?)~~".toRegex(), "~~$1~~")
        
        // 处理特殊强调格式（参考DeepSeek）
        // 处理书名号《》
        formatted = formatted.replace("《([^》]+)》".toRegex(), "【$1】")
        
        // 处理引号""中的内容
        formatted = formatted.replace("\"([^\"]+)\"".toRegex(), "「$1」")
        
        // 处理单引号''中的内容
        formatted = formatted.replace("'([^']+)'".toRegex(), "「$1」")
        
        return formatted
    }
    
    /**
     * 格式化段落和换行 - 参考DeepSeek格式
     */
    private fun formatParagraphs(content: String): String {
        var formatted = content
        
        // 在句号、问号、感叹号后添加适当换行
        formatted = formatted.replace("([。！？])\\s*([A-Z0-9一-龯])".toRegex(), "$1\n\n$2")
        
        // 在冒号后添加换行（用于问答格式）
        formatted = formatted.replace("([：:])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        
        // 在分号后添加换行（用于长句分段）
        formatted = formatted.replace("([；;])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        
        // 在逗号后添加换行（用于长句分段，参考DeepSeek）
        formatted = formatted.replace("([，,])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        
        // 处理列表项之间的换行
        formatted = formatted.replace("(\\d+\\.\\s+[^\\n]+)\\n(\\d+\\.\\s+)".toRegex(), "$1\n\n$2")
        
        // 处理小标题后的换行
        formatted = formatted.replace("(▪\\s+[^\\n]+)\\n([^▪\\n])".toRegex(), "$1\n$2")
        
        // 处理多个连续换行
        formatted = formatted.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")
        
        return formatted
    }
    
    /**
     * 格式化特殊结构和分类 - 参考DeepSeek格式
     */
    private fun formatSpecialStructures(content: String): String {
        var formatted = content
        
        // 处理问答格式
        formatted = formatted.replace("^问[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n❓ 问：$1\n")
        formatted = formatted.replace("^答[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 答：$1\n")
        
        // 处理步骤格式
        formatted = formatted.replace("^步骤\\s*(\\d+)[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📋 步骤$1：$2\n")
        formatted = formatted.replace("^第\\s*(\\d+)\\s*步[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📋 第$1步：$2\n")
        
        // 处理要点格式
        formatted = formatted.replace("^要点\\s*(\\d+)[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🔹 要点$1：$2\n")
        formatted = formatted.replace("^注意[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n⚠️ 注意：$1\n")
        formatted = formatted.replace("^提示[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 提示：$1\n")
        
        // 处理总结格式
        formatted = formatted.replace("^总结[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📝 总结：$1\n")
        formatted = formatted.replace("^结论[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📝 结论：$1\n")
        
        // 处理DeepSeek风格的特殊格式
        // 处理"好的"开头
        formatted = formatted.replace("^好的[，,]?\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 $1\n")
        
        // 处理"这里"开头的介绍
        formatted = formatted.replace("^这里\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📖 $1\n")
        
        // 处理"核心"、"主要"等关键词
        formatted = formatted.replace("^核心\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n⭐ 核心$1\n")
        formatted = formatted.replace("^主要\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🔸 主要$1\n")
        
        // 处理"特点"、"特色"等
        formatted = formatted.replace("^特点[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n✨ 特点：$1\n")
        formatted = formatted.replace("^特色[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n✨ 特色：$1\n")
        
        // 处理"优势"、"优点"等
        formatted = formatted.replace("^优势[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🚀 优势：$1\n")
        formatted = formatted.replace("^优点[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🚀 优点：$1\n")
        
        return formatted
    }
    
    /**
     * 最终清理和优化
     */
    private fun finalCleanup(content: String): String {
        var cleaned = content
        
        // 清理多余的空行
        cleaned = cleaned.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")
        
        // 清理行首行尾空白
        cleaned = cleaned.split("\n").joinToString("\n") { it.trim() }
        
        // 确保文本以换行结尾
        if (!cleaned.endsWith("\n")) {
            cleaned += "\n"
        }
        
        return cleaned.trim()
    }
}
