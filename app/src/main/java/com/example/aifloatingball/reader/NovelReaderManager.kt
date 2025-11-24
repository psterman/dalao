package com.example.aifloatingball.reader

import android.content.Context
import android.webkit.WebView
import android.util.Log
import org.json.JSONObject

/**
 * 小说阅读模式管理器
 * 负责检测小说页面、解析内容、管理阅读状态
 */
class NovelReaderManager(private val context: Context) {
    companion object {
        private const val TAG = "NovelReaderManager"
        private var instance: NovelReaderManager? = null

        fun getInstance(context: Context): NovelReaderManager {
            if (instance == null) {
                instance = NovelReaderManager(context.applicationContext)
            }
            return instance!!
        }
    }

    // 是否处于阅读模式
    var isReaderModeActive = false
        private set

    // 当前阅读的WebView
    private var currentWebView: WebView? = null

    // 下一章URL
    private var nextChapterUrl: String = ""
    // 是否正在加载下一章
    private var isLoadingNext = false

    // 监听器
    interface ReaderModeListener {
        fun onReaderModeStateChanged(isActive: Boolean)
        fun onChapterLoaded(title: String, content: String, hasNext: Boolean, hasPrev: Boolean, isAppend: Boolean)
        fun onChapterLoadFailed(error: String)
    }

    private var listener: ReaderModeListener? = null

    fun setListener(listener: ReaderModeListener) {
        this.listener = listener
    }

    /**
     * 检测当前页面是否为小说页面
     * @param webView 当前WebView
     * @param url 当前URL
     * @param title 页面标题
     * @param htmlContent 页面HTML内容（可选，如果能获取到）
     */
    fun detectNovelPage(webView: WebView, url: String, title: String?, callback: (Boolean) -> Unit) {
        // 简单的关键词检测
        val isNovel = title?.let {
            it.contains("章") || it.contains("节") || it.contains("阅读") || it.contains("小说")
        } ?: false

        // 如果标题包含关键词，进一步通过JS检测内容结构
        if (isNovel) {
            // 注入JS检测主要文本内容长度和结构
            val js = """
                (function() {
                    // 简单的启发式算法
                    var pTags = document.getElementsByTagName('p');
                    var textLength = 0;
                    for (var i = 0; i < pTags.length; i++) {
                        textLength += pTags[i].innerText.length;
                    }
                    // 如果P标签文本总长度超过1000字，且包含"章"字，可能是小说
                    var hasChapterKeyword = document.title.indexOf('章') > -1;
                    return {
                        isNovel: textLength > 800 && hasChapterKeyword,
                        textLength: textLength,
                        title: document.title
                    };
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) { result ->
                try {
                    val json = JSONObject(result)
                    val confirmed = json.optBoolean("isNovel", false)
                    callback(confirmed)
                } catch (e: Exception) {
                    Log.e(TAG, "检测小说页面失败", e)
                    callback(false)
                }
            }
        } else {
            callback(false)
        }
    }

    /**
     * 进入阅读模式
     */
    fun enterReaderMode(webView: WebView) {
        if (isReaderModeActive && currentWebView == webView && !isLoadingNext) return
        
        currentWebView = webView
        isReaderModeActive = true
        isLoadingNext = false // 重置加载状态
        listener?.onReaderModeStateChanged(true)
        
        // 解析当前章节
        android.widget.Toast.makeText(context, "正在进入阅读模式...", android.widget.Toast.LENGTH_SHORT).show()
        parseCurrentChapter(isAppend = false)
    }

    /**
     * 退出阅读模式
     */
    fun exitReaderMode() {
        if (!isReaderModeActive) return
        
        isReaderModeActive = false
        currentWebView = null
        isLoadingNext = false
        nextChapterUrl = ""
        listener?.onReaderModeStateChanged(false)
    }

    /**
     * 页面加载完成通知
     */
    fun onPageFinished(url: String) {
        if (isReaderModeActive && isLoadingNext) {
            // 如果是正在加载下一章，且URL匹配（或者只是加载完成了新页面）
            // 解析新章节并追加
            parseCurrentChapter(isAppend = true)
            isLoadingNext = false
        }
    }

    /**
     * 解析当前章节内容
     */
    private fun parseCurrentChapter(isAppend: Boolean) {
        val webView = currentWebView ?: return
        
        // 注入JS解析内容
        // 这里使用一个通用的解析脚本，尝试提取正文、标题、上一章、下一章链接
        val js = """
            (function() {
                function findMainContent() {
                    // 寻找包含最多文本的容器
                    var candidates = [];
                    var elements = document.body.getElementsByTagName('*');
                    
                    for (var i = 0; i < elements.length; i++) {
                        var el = elements[i];
                        // 忽略脚本、样式等
                        if (['SCRIPT', 'STYLE', 'NOSCRIPT', 'IFRAME'].indexOf(el.tagName) > -1) continue;
                        
                        // 计算直接子文本节点的长度
                        var textLen = 0;
                        for (var j = 0; j < el.childNodes.length; j++) {
                            var node = el.childNodes[j];
                            if (node.nodeType === 3) { // Text node
                                textLen += node.nodeValue.trim().length;
                            }
                        }
                        
                        // 如果直接文本很少，计算所有P标签子元素的文本
                        if (textLen < 200) {
                            var pTags = el.getElementsByTagName('p');
                            for (var k = 0; k < pTags.length; k++) {
                                textLen += pTags[k].innerText.length;
                            }
                        }
                        
                        if (textLen > 500) {
                            candidates.push({element: el, length: textLen});
                        }
                    }
                    
                    // 按长度排序
                    candidates.sort(function(a, b) { return b.length - a.length; });
                    
                    if (candidates.length > 0) {
                        // 提取文本，保留换行
                        var content = candidates[0].element.innerText;
                        // 简单的清理
                        return content;
                    }
                    return "";
                }
                
                function findNextLink() {
                    var links = document.getElementsByTagName('a');
                    for (var i = 0; i < links.length; i++) {
                        var text = links[i].innerText;
                        if (text.indexOf('下一章') > -1 || text.indexOf('下页') > -1) {
                            return links[i].href;
                        }
                    }
                    return "";
                }
                
                function findPrevLink() {
                    var links = document.getElementsByTagName('a');
                    for (var i = 0; i < links.length; i++) {
                        var text = links[i].innerText;
                        if (text.indexOf('上一章') > -1 || text.indexOf('上页') > -1) {
                            return links[i].href;
                        }
                    }
                    return "";
                }
                
                return {
                    title: document.title,
                    content: findMainContent(),
                    nextUrl: findNextLink(),
                    prevUrl: findPrevLink()
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            try {
                // result 是 JSON 字符串，可能被引号包裹
                var jsonStr = result
                if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                    jsonStr = jsonStr.substring(1, jsonStr.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                }
                
                val json = JSONObject(jsonStr)
                val title = json.optString("title")
                val content = json.optString("content")
                val nextUrl = json.optString("nextUrl")
                val prevUrl = json.optString("prevUrl")
                
                nextChapterUrl = nextUrl
                
                if (content.isNotEmpty()) {
                    listener?.onChapterLoaded(title, content, nextUrl.isNotEmpty(), prevUrl.isNotEmpty(), isAppend)
                } else {
                    listener?.onChapterLoadFailed("无法解析正文内容")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析章节失败", e)
                listener?.onChapterLoadFailed("解析错误: ${e.message}")
            }
        }
    }
    
    /**
     * 加载下一章
     */
    fun loadNextChapter() {
        if (nextChapterUrl.isNotEmpty() && !isLoadingNext) {
            isLoadingNext = true
            currentWebView?.loadUrl(nextChapterUrl)
        } else if (nextChapterUrl.isEmpty()) {
            listener?.onChapterLoadFailed("没有下一章链接")
        }
    }
}
