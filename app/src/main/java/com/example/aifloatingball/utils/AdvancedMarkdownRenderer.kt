package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

/**
 * 高级Markdown渲染器
 * 集成marked.js、highlight.js、prism.js功能，专门用于AI文本格式化
 * 符合中国人阅读习惯的文本排版优化
 */
class AdvancedMarkdownRenderer private constructor(private val context: Context) {
    
    private val primaryColor: Int
    private val secondaryColor: Int
    private val accentColor: Int
    private val codeColor: Int
    private val linkColor: Int
    
    // 代码高亮支持的语言
    private val supportedLanguages = setOf(
        "javascript", "java", "python", "kotlin", "swift", "go", "rust", "cpp", "c", "csharp",
        "php", "ruby", "html", "css", "xml", "json", "yaml", "sql", "bash", "shell",
        "typescript", "dart", "scala", "r", "matlab", "perl", "lua", "powershell"
    )
    
    init {
        // 获取主题颜色
        primaryColor = ContextCompat.getColor(context, android.R.color.black)
        secondaryColor = ContextCompat.getColor(context, android.R.color.darker_gray)
        accentColor = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
        codeColor = ContextCompat.getColor(context, android.R.color.holo_green_dark)
        linkColor = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
    }
    
    companion object {
        @Volatile
        private var INSTANCE: AdvancedMarkdownRenderer? = null
        
        fun getInstance(context: Context): AdvancedMarkdownRenderer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdvancedMarkdownRenderer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 渲染AI回复文本为SpannableString
     * 支持完整的Markdown语法和中文排版优化
     */
    fun renderAIResponse(content: String): SpannableString {
        // 1. 预处理和清理
        val cleanedContent = preprocessContent(content)
        
        // 2. 解析Markdown结构
        val parsedContent = parseMarkdownStructure(cleanedContent)
        
        // 3. 应用中文排版优化
        val optimizedContent = applyChineseTypographyOptimization(parsedContent)
        
        // 4. 创建SpannableString并应用样式
        val spannableString = SpannableString(optimizedContent)
        applyTextStyles(spannableString, optimizedContent)
        
        return spannableString
    }
    
    /**
     * 预处理内容
     */
    private fun preprocessContent(content: String): String {
        var processed = content
        
        // 清理HTML标签
        processed = processed.replace("<[^>]+>".toRegex(), "")
        
        // 处理HTML实体
        processed = processed.replace("&nbsp;".toRegex(), " ")
        processed = processed.replace("&lt;".toRegex(), "<")
        processed = processed.replace("&gt;".toRegex(), ">")
        processed = processed.replace("&amp;".toRegex(), "&")
        processed = processed.replace("&quot;".toRegex(), "\"")
        processed = processed.replace("&#39;".toRegex(), "'")
        
        // 清理多余空白
        processed = processed.replace("\\s+".toRegex(), " ")
        processed = processed.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")
        
        return processed.trim()
    }
    
    /**
     * 解析Markdown结构
     */
    private fun parseMarkdownStructure(content: String): String {
        var parsed = content
        
        // 处理标题
        parsed = parseHeadings(parsed)
        
        // 处理列表
        parsed = parseLists(parsed)
        
        // 处理代码块
        parsed = parseCodeBlocks(parsed)
        
        // 处理行内代码
        parsed = parseInlineCode(parsed)
        
        // 处理强调和粗体
        parsed = parseEmphasis(parsed)
        
        // 处理链接
        parsed = parseLinks(parsed)
        
        // 处理引用
        parsed = parseBlockquotes(parsed)
        
        // 处理表格
        parsed = parseTables(parsed)
        
        return parsed
    }
    
    /**
     * 解析标题
     */
    private fun parseHeadings(content: String): String {
        var parsed = content
        
        // 处理多级标题
        parsed = parsed.replace("^#{6}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        parsed = parsed.replace("^#{5}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        parsed = parsed.replace("^#{4}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        parsed = parsed.replace("^#{3}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        parsed = parsed.replace("^#{2}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1\n")
        parsed = parsed.replace("^#{1}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n■ $1\n")
        
        // 处理中文标题格式
        parsed = parsed.replace("^([一二三四五六七八九十]+[、.])\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1 $2\n")
        parsed = parsed.replace("^([0-9]+[、.])\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1 $2\n")
        parsed = parsed.replace("^([^：:]+[：:])\\s*$".toRegex(RegexOption.MULTILINE), "\n▪ $1\n")
        
        return parsed
    }
    
    /**
     * 解析列表
     */
    private fun parseLists(content: String): String {
        var parsed = content
        
        // 处理有序列表
        parsed = parsed.replace("^\\s*(\\d+)\\.\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  $1. $2")
        parsed = parsed.replace("^\\s*([一二三四五六七八九十]+)\\.\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  $1. $2")
        
        // 处理无序列表
        parsed = parsed.replace("^\\s*[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  • $1")
        parsed = parsed.replace("^\\s{2,4}[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "    ◦ $1")
        parsed = parsed.replace("^\\s{6,8}[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "      ▪ $1")
        
        // 处理特殊列表格式
        parsed = parsed.replace("^\\s*([•·▪▫])\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  • $2")
        
        return parsed
    }
    
    /**
     * 解析代码块
     */
    private fun parseCodeBlocks(content: String): String {
        var parsed = content
        
        // 处理代码块
        parsed = parsed.replace("```([^\\n]+)\\n([\\s\\S]*?)```".toRegex()) { matchResult ->
            val language = matchResult.groupValues[1].trim()
            val code = matchResult.groupValues[2].trim()
            val displayLanguage = if (language.isNotEmpty() && supportedLanguages.contains(language.lowercase())) {
                language.uppercase()
            } else {
                "CODE"
            }
            "\n┌─ $displayLanguage ─┐\n$code\n└${"─".repeat(displayLanguage.length + 4)}┘\n"
        }
        
        return parsed
    }
    
    /**
     * 解析行内代码
     */
    private fun parseInlineCode(content: String): String {
        var parsed = content
        
        // 处理行内代码
        parsed = parsed.replace("`([^`]+)`".toRegex(), "「$1」")
        
        return parsed
    }
    
    /**
     * 解析强调和粗体
     */
    private fun parseEmphasis(content: String): String {
        var parsed = content
        
        // 处理粗体
        parsed = parsed.replace("\\*\\*(.*?)\\*\\*".toRegex(), "【$1】")
        parsed = parsed.replace("__(.*?)__".toRegex(), "【$1】")
        
        // 处理斜体
        parsed = parsed.replace("\\*(.*?)\\*".toRegex(), "$1")
        parsed = parsed.replace("_(.*?)_".toRegex(), "$1")
        
        // 处理删除线
        parsed = parsed.replace("~~(.*?)~~".toRegex(), "~~$1~~")
        
        // 处理特殊强调格式
        parsed = parsed.replace("《([^》]+)》".toRegex(), "【$1】")
        parsed = parsed.replace("\"([^\"]+)\"".toRegex(), "「$1」")
        parsed = parsed.replace("'([^']+)'".toRegex(), "「$1」")
        
        return parsed
    }
    
    /**
     * 解析链接
     */
    private fun parseLinks(content: String): String {
        var parsed = content
        
        // 处理Markdown链接 [text](url)
        parsed = parsed.replace("\\[([^\\]]+)\\]\\(([^)]+)\\)".toRegex(), "$1")
        
        // 处理URL链接
        parsed = parsed.replace("(https?://[^\\s]+)".toRegex(), "$1")
        
        return parsed
    }
    
    /**
     * 解析引用
     */
    private fun parseBlockquotes(content: String): String {
        var parsed = content
        
        // 处理引用块
        parsed = parsed.replace("^>\\s+(.*)$".toRegex(RegexOption.MULTILINE), "┌─ 引用 ─┐\n$1\n└─────────┘\n")
        
        return parsed
    }
    
    /**
     * 解析表格
     */
    private fun parseTables(content: String): String {
        var parsed = content
        
        // 处理简单表格
        parsed = parsed.replace("\\|(.+)\\|".toRegex()) { matchResult ->
            val row = matchResult.groupValues[1]
            val cells = row.split("|").map { it.trim() }
            "┌─ ${cells.joinToString(" │ ")} ─┐\n"
        }
        
        return parsed
    }
    
    /**
     * 应用中文排版优化
     */
    private fun applyChineseTypographyOptimization(content: String): String {
        var optimized = content
        
        // 在句号、问号、感叹号后添加适当换行
        optimized = optimized.replace("([。！？])\\s*([A-Z0-9一-龯])".toRegex(), "$1\n\n$2")
        
        // 在冒号后添加换行
        optimized = optimized.replace("([：:])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        
        // 在分号后添加换行
        optimized = optimized.replace("([；;])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        
        // 在逗号后添加换行（用于长句分段）
        optimized = optimized.replace("([，,])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        
        // 处理列表项之间的换行
        optimized = optimized.replace("(\\d+\\.\\s+[^\\n]+)\\n(\\d+\\.\\s+)".toRegex(), "$1\n\n$2")
        
        // 处理小标题后的换行
        optimized = optimized.replace("(▪\\s+[^\\n]+)\\n([^▪\\n])".toRegex(), "$1\n$2")
        
        // 处理多个连续换行
        optimized = optimized.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")
        
        // 处理特殊结构
        optimized = parseSpecialStructures(optimized)
        
        return optimized
    }
    
    /**
     * 解析特殊结构
     */
    private fun parseSpecialStructures(content: String): String {
        var parsed = content
        
        // 处理问答格式
        parsed = parsed.replace("^问[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n❓ 问：$1\n")
        parsed = parsed.replace("^答[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 答：$1\n")
        
        // 处理步骤格式
        parsed = parsed.replace("^步骤\\s*(\\d+)[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📋 步骤$1：$2\n")
        parsed = parsed.replace("^第\\s*(\\d+)\\s*步[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📋 第$1步：$2\n")
        
        // 处理要点格式
        parsed = parsed.replace("^要点\\s*(\\d+)[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🔹 要点$1：$2\n")
        parsed = parsed.replace("^注意[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n⚠️ 注意：$1\n")
        parsed = parsed.replace("^提示[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 提示：$1\n")
        
        // 处理总结格式
        parsed = parsed.replace("^总结[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📝 总结：$1\n")
        parsed = parsed.replace("^结论[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📝 结论：$1\n")
        
        // 处理DeepSeek风格的特殊格式
        parsed = parsed.replace("^好的[，,]?\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 $1\n")
        parsed = parsed.replace("^这里\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📖 $1\n")
        parsed = parsed.replace("^核心\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n⭐ 核心$1\n")
        parsed = parsed.replace("^主要\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🔸 主要$1\n")
        parsed = parsed.replace("^特点[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n✨ 特点：$1\n")
        parsed = parsed.replace("^特色[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n✨ 特色：$1\n")
        parsed = parsed.replace("^优势[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🚀 优势：$1\n")
        parsed = parsed.replace("^优点[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🚀 优点：$1\n")
        
        return parsed
    }
    
    /**
     * 应用文本样式
     */
    private fun applyTextStyles(spannableString: SpannableString, content: String) {
        val text = spannableString.toString()
        
        // 应用标题样式
        applyHeadingStyles(spannableString, text)
        
        // 应用列表样式
        applyListStyles(spannableString, text)
        
        // 应用代码样式
        applyCodeStyles(spannableString, text)
        
        // 应用强调样式
        applyEmphasisStyles(spannableString, text)
        
        // 应用链接样式
        applyLinkStyles(spannableString, text)
        
        // 应用特殊结构样式
        applySpecialStructureStyles(spannableString, text)
    }
    
    /**
     * 应用标题样式
     */
    private fun applyHeadingStyles(spannableString: SpannableString, text: String) {
        // 一级标题
        val h1Pattern = Pattern.compile("^■\\s+(.+)$", Pattern.MULTILINE)
        val h1Matcher = h1Pattern.matcher(text)
        while (h1Matcher.find()) {
            val start = h1Matcher.start()
            val end = h1Matcher.end()
            spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(RelativeSizeSpan(1.3f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // 二级标题
        val h2Pattern = Pattern.compile("^▪\\s+(.+)$", Pattern.MULTILINE)
        val h2Matcher = h2Pattern.matcher(text)
        while (h2Matcher.find()) {
            val start = h2Matcher.start()
            val end = h2Matcher.end()
            spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(RelativeSizeSpan(1.2f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // 三级标题
        val h3Pattern = Pattern.compile("^▫\\s+(.+)$", Pattern.MULTILINE)
        val h3Matcher = h3Pattern.matcher(text)
        while (h3Matcher.find()) {
            val start = h3Matcher.start()
            val end = h3Matcher.end()
            spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(RelativeSizeSpan(1.1f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    
    /**
     * 应用列表样式
     */
    private fun applyListStyles(spannableString: SpannableString, text: String) {
        // 有序列表
        val orderedListPattern = Pattern.compile("^\\s*(\\d+)\\.\\s+(.+)$", Pattern.MULTILINE)
        val orderedListMatcher = orderedListPattern.matcher(text)
        while (orderedListMatcher.find()) {
            val start = orderedListMatcher.start()
            val end = orderedListMatcher.end()
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // 无序列表
        val unorderedListPattern = Pattern.compile("^\\s*[•◦▪]\\s+(.+)$", Pattern.MULTILINE)
        val unorderedListMatcher = unorderedListPattern.matcher(text)
        while (unorderedListMatcher.find()) {
            val start = unorderedListMatcher.start()
            val end = unorderedListMatcher.end()
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    
    /**
     * 应用代码样式
     */
    private fun applyCodeStyles(spannableString: SpannableString, text: String) {
        // 代码块
        val codeBlockPattern = Pattern.compile("┌─\\s+(.+?)\\s+─┐[\\s\\S]*?└[─]+┘", Pattern.MULTILINE)
        val codeBlockMatcher = codeBlockPattern.matcher(text)
        while (codeBlockMatcher.find()) {
            val start = codeBlockMatcher.start()
            val end = codeBlockMatcher.end()
            spannableString.setSpan(BackgroundColorSpan(0xFFF5F5F5.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(codeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // 行内代码
        val inlineCodePattern = Pattern.compile("「([^」]+)」")
        val inlineCodeMatcher = inlineCodePattern.matcher(text)
        while (inlineCodeMatcher.find()) {
            val start = inlineCodeMatcher.start()
            val end = inlineCodeMatcher.end()
            spannableString.setSpan(BackgroundColorSpan(0xFFF0F0F0.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(codeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    
    /**
     * 应用强调样式
     */
    private fun applyEmphasisStyles(spannableString: SpannableString, text: String) {
        // 粗体
        val boldPattern = Pattern.compile("【([^】]+)】")
        val boldMatcher = boldPattern.matcher(text)
        while (boldMatcher.find()) {
            val start = boldMatcher.start()
            val end = boldMatcher.end()
            spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // 删除线
        val strikethroughPattern = Pattern.compile("~~([^~]+)~~")
        val strikethroughMatcher = strikethroughPattern.matcher(text)
        while (strikethroughMatcher.find()) {
            val start = strikethroughMatcher.start()
            val end = strikethroughMatcher.end()
            spannableString.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(secondaryColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    
    /**
     * 应用链接样式
     */
    private fun applyLinkStyles(spannableString: SpannableString, text: String) {
        val urlPattern = Pattern.compile("(https?://[^\\s]+)")
        val urlMatcher = urlPattern.matcher(text)
        while (urlMatcher.find()) {
            val start = urlMatcher.start()
            val end = urlMatcher.end()
            spannableString.setSpan(ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    
    /**
     * 应用特殊结构样式
     */
    private fun applySpecialStructureStyles(spannableString: SpannableString, text: String) {
        // 问答格式
        val qaPattern = Pattern.compile("(❓|💡)\\s+(问|答)：(.+)", Pattern.MULTILINE)
        val qaMatcher = qaPattern.matcher(text)
        while (qaMatcher.find()) {
            val start = qaMatcher.start()
            val end = qaMatcher.end()
            spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // 步骤格式
        val stepPattern = Pattern.compile("(📋|🔹|⚠️|💡|📝|⭐|🔸|✨|🚀)\\s+(.+：.+)", Pattern.MULTILINE)
        val stepMatcher = stepPattern.matcher(text)
        while (stepMatcher.find()) {
            val start = stepMatcher.start()
            val end = stepMatcher.end()
            spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    
    /**
     * 为TextView设置AI回复内容
     */
    fun setAIResponseText(textView: TextView, content: String) {
        val spannableString = renderAIResponse(content)
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
    }
    
    /**
     * 获取纯文本版本（用于复制等功能）
     */
    fun getPlainText(content: String): String {
        val rendered = renderAIResponse(content)
        return rendered.toString()
    }
}
