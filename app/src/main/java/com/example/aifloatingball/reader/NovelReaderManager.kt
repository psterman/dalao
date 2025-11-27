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

    // 目录链接
    private var catalogUrl: String = ""
    
    // 已解析的目录列表（从当前页面解析到的）
    private var cachedCatalog: List<CatalogItem> = emptyList()
    
    // 是否正在加载下一章
    private var isLoadingNext: Boolean = false
    
    // 下一章URL
    private var nextChapterUrl: String = ""
    
    // 上一章URL
    private var prevChapterUrl: String = ""
    
    // 后台WebView，用于加载目录
    private var backgroundWebView: WebView? = null

    // 目录数据类
    data class CatalogItem(val title: String, val url: String)

    // 监听器
    interface ReaderModeListener {
        fun onReaderModeStateChanged(isActive: Boolean)
        fun onChapterLoaded(title: String, content: String, hasNext: Boolean, hasPrev: Boolean, isAppend: Boolean)
        fun onChapterLoadFailed(error: String)
        fun onCatalogLoaded(catalog: List<CatalogItem>)
        fun onCatalogLoadFailed(error: String)
        fun onCatalogPageDetected(catalog: List<CatalogItem>) // 新增：检测到目录页面时回调
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
        prevChapterUrl = ""
        catalogUrl = ""
        cachedCatalog = emptyList() // 清空缓存的目录
        // 清理后台WebView
        backgroundWebView?.destroy()
        backgroundWebView = null
        
        listener?.onReaderModeStateChanged(false)
    }

    /**
     * 页面加载完成通知
     */
    fun onPageFinished(url: String) {
        if (!isReaderModeActive) return
        
        // 延迟执行，确保DOM完全加载和JS执行完成
        currentWebView?.postDelayed({
            // 如果是正在加载下一章，追加内容
            if (isLoadingNext) {
                parseCurrentChapter(isAppend = true)
                isLoadingNext = false
            } else {
                // 否则，重新解析当前页面（可能是点击目录章节跳转过来的）
                parseCurrentChapter(isAppend = false)
            }
        }, 500) // 延迟500ms，确保页面完全加载
    }

    /**
     * 解析当前章节内容
     * 支持目录页面识别和章节解析
     */
    private fun parseCurrentChapter(isAppend: Boolean) {
        val webView = currentWebView ?: return
        
        // 注入JS解析内容
        // 这里使用一个通用的解析脚本，尝试提取正文、标题、上一章、下一章链接、目录链接
        // 同时支持目录页面的章节列表解析
        val js = """
            (function() {
                function findMainContent() {
                    // 方法1: 尝试使用常见的内容选择器
                    var contentSelectors = [
                        '#content', '#chaptercontent', '.content', '.chapter-content',
                        '.text-content', '#novelcontent', '.novel-content', '.read-content',
                        '#text', '.text', 'article', '.article-content',
                        '#chapterContent', '.chapter-content', '#bookContent', '.book-content'
                    ];
                    
                    for (var i = 0; i < contentSelectors.length; i++) {
                        var element = document.querySelector(contentSelectors[i]);
                        if (element && element.innerText && element.innerText.trim().length > 200) {
                            return element.innerText.trim();
                        }
                    }
                    
                    // 方法2: 寻找包含最多文本的容器
                    var candidates = [];
                    var elements = document.body.getElementsByTagName('*');
                    
                    for (var i = 0; i < elements.length; i++) {
                        var el = elements[i];
                        // 忽略脚本、样式等
                        if (['SCRIPT', 'STYLE', 'NOSCRIPT', 'IFRAME', 'HEADER', 'FOOTER', 'NAV', 'ASIDE'].indexOf(el.tagName) > -1) continue;
                        
                        // 跳过明显不是内容的元素
                        var id = el.id || '';
                        var className = el.className || '';
                        if (id.indexOf('header') >= 0 || id.indexOf('footer') >= 0 || 
                            id.indexOf('nav') >= 0 || id.indexOf('menu') >= 0 ||
                            className.indexOf('header') >= 0 || className.indexOf('footer') >= 0 ||
                            className.indexOf('nav') >= 0 || className.indexOf('menu') >= 0) {
                            continue;
                        }
                        
                        // 计算所有P标签子元素的文本
                        var pTags = el.getElementsByTagName('p');
                        var textLen = 0;
                        for (var k = 0; k < pTags.length; k++) {
                            var pText = pTags[k].innerText.trim();
                            // 排除导航链接、广告等
                            if (pText.length > 5 && pText.indexOf('上一章') < 0 && 
                                pText.indexOf('下一章') < 0 && pText.indexOf('目录') < 0 &&
                                pText.indexOf('广告') < 0 && pText.indexOf('AD') < 0) {
                                textLen += pText.length;
                            }
                        }
                        
                        // 如果没有P标签，计算直接文本
                        if (textLen < 200) {
                            for (var j = 0; j < el.childNodes.length; j++) {
                                var node = el.childNodes[j];
                                if (node.nodeType === 3) { // Text node
                                    textLen += node.nodeValue.trim().length;
                                }
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
                        // 清理：移除导航链接、广告等
                        content = content.replace(/上一章|下一章|目录|返回|广告|AD/gi, '');
                        return content.trim();
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
                
                function findCatalogLink() {
                    var links = document.getElementsByTagName('a');
                    // 关键词优先级排序
                    var keywords = ['全部章节', '完整目录', '章节列表', '目录', 'Chapter List', 'Table of Contents', 'Index'];
                    
                    for (var k = 0; k < keywords.length; k++) {
                        var keyword = keywords[k];
                        for (var i = 0; i < links.length; i++) {
                            var text = links[i].innerText.trim();
                            // 精确匹配或包含匹配
                            if (text === keyword || (text.length < 10 && text.indexOf(keyword) > -1)) {
                                // 排除可能是"返回目录"的链接，通常我们更想要"查看目录"
                                // 但如果没有更好的，"返回目录"也可以
                                return links[i].href;
                            }
                        }
                    }
                    return "";
                }
                
                // 解析章节列表（用于目录页面识别）
                function parseChapterList() {
                    var chapters = [];
                    var candidates = [];
                    var elements = document.getElementsByTagName('*');
                    
                    // 定义需要排除的网站导航关键词
                    var excludeKeywords = ['首页', '主页', '分类', '搜索', '登录', '注册', '关于', '联系', 
                                         '帮助', '反馈', '设置', '个人中心', '我的', '书架', '推荐', 
                                         '排行榜', '热门', '最新', '完结', '连载', '免费', 'VIP',
                                         'home', 'index', 'category', 'search', 'login', 'register', 
                                         'about', 'contact', 'help', 'feedback', 'settings', 'user',
                                         'rank', 'hot', 'new', 'complete', 'serial', 'free', 'vip'];
                    
                    // 辅助函数：检查链接是否是章节链接
                    function isChapterLink(text, href) {
                        // 检查文本是否像章节标题
                        if (text.match(/第[\\d一二三四五六七八九十百千万]+[章节回]/) ||
                            text.match(/^[\\d\\.]+[、.\\s]/) ||
                            text.match(/^第\\d+[章节回]/)) {
                            return true;
                        }
                        
                        // 检查URL是否像章节URL
                        try {
                            var urlObj = new URL(href, window.location.href);
                            var path = urlObj.pathname.toLowerCase();
                            
                            if (path.indexOf('chapter') >= 0 || path.indexOf('chap') >= 0 ||
                                path.match(/\\d+\\.html/) || path.match(/chapter\\d+/)) {
                                return true;
                            }
                        } catch(e) {}
                        
                        return false;
                    }
                    
                    // 辅助函数：检查是否是导航链接
                    function isNavigationLink(text) {
                        var lowerText = text.toLowerCase();
                        for (var i = 0; i < excludeKeywords.length; i++) {
                            if (lowerText.indexOf(excludeKeywords[i].toLowerCase()) >= 0) {
                                return true;
                            }
                        }
                        return false;
                    }
                    
                    // 遍历所有元素，寻找包含大量章节链接的容器
                    for (var i = 0; i < elements.length; i++) {
                        var el = elements[i];
                        // 忽略明显无关的标签
                        if (['SCRIPT', 'STYLE', 'NOSCRIPT', 'HEADER', 'FOOTER', 'NAV'].indexOf(el.tagName) > -1) continue;
                        
                        var links = el.getElementsByTagName('a');
                        if (links.length < 10) continue; // 链接太少忽略

                        // 分析链接特征
                        var validLinks = [];
                        var chapterLikeCount = 0;
                        
                        for (var j = 0; j < links.length; j++) {
                            var link = links[j];
                            var text = link.innerText.trim();
                            var href = link.href;
                            
                            if (!href || text.length < 1) continue;
                            
                            // 排除明显无效的链接
                            if (href.indexOf('javascript:') === 0 || href.indexOf('#') === 0) continue;
                            
                            // 排除导航链接
                            if (isNavigationLink(text)) continue;
                            
                            // 检查是否是章节链接
                            if (isChapterLink(text, href) || 
                                (links.length >= 5 && text.length > 3 && text.length < 200)) {
                                validLinks.push({title: text, url: href});
                                if (isChapterLink(text, href)) {
                                    chapterLikeCount++;
                                }
                            }
                        }
                        
                        if (validLinks.length < 10) continue;

                        // 计算得分
                        var score = validLinks.length;
                        // 如果大部分链接都像章节，加分
                        if (chapterLikeCount / validLinks.length > 0.5) {
                            score += chapterLikeCount * 2;
                        }
                        
                        candidates.push({
                            element: el, 
                            chapters: validLinks, 
                            score: score,
                            count: validLinks.length
                        });
                    }
                    
                    // 按得分排序
                    candidates.sort(function(a, b) { return b.score - a.score; });
                    
                    if (candidates.length > 0) {
                        // 取最高分的容器
                        var best = candidates[0];
                        return best.chapters;
                    }
                    
                    return [];
                }
                
                function findChapterTitle() {
                    // 方法1: 查找h1-h3标签中的章节标题
                    for (var level = 1; level <= 3; level++) {
                        var hTag = document.querySelector('h' + level);
                        if (hTag && hTag.innerText.trim()) {
                            var text = hTag.innerText.trim();
                            // 检查是否像章节标题（包含"第X章"或长度适中）
                            if (text.length > 0 && text.length < 200 && 
                                (text.indexOf('第') >= 0 && text.indexOf('章') >= 0 || text.length < 50)) {
                                return text;
                            }
                        }
                    }
                    
                    // 方法2: 查找常见的章节标题选择器
                    var titleSelectors = ['h1', 'h2', '.title', '#title', '.chapter-title', 
                                         '.book-title', 'h2.title', '.content-title', 
                                         '.chapter-name', '#chapter-title'];
                    for (var i = 0; i < titleSelectors.length; i++) {
                        var element = document.querySelector(titleSelectors[i]);
                        if (element && element.innerText.trim()) {
                            var text = element.innerText.trim();
                            if (text.length > 0 && text.length < 200) {
                                // 优先选择包含"第X章"的标题
                                if (text.indexOf('第') >= 0 && text.indexOf('章') >= 0) {
                                    return text;
                                }
                            }
                        }
                    }
                    
                    // 方法3: 从内容的第一行提取（通常是章节标题）
                    var content = findMainContent();
                    if (content && content.length > 0) {
                        var lines = content.split(/[\\n\\r]+/);
                        for (var i = 0; i < Math.min(5, lines.length); i++) {
                            var line = lines[i].trim();
                            // 检查是否是章节标题（包含"第X章"或长度较短且居中显示）
                            if (line.length > 0 && line.length < 100 && 
                                (line.indexOf('第') >= 0 && line.indexOf('章') >= 0 || 
                                 (line.length < 30 && i === 0))) {
                                return line;
                            }
                        }
                    }
                    
                    // 方法4: 从document.title中提取（移除网站名称）
                    var docTitle = document.title || '';
                    // 移除常见的网站名称后缀
                    docTitle = docTitle.replace(/[-_|].*$/, '').trim();
                    // 如果标题包含"第X章"，直接返回
                    if (docTitle.indexOf('第') >= 0 && docTitle.indexOf('章') >= 0) {
                        return docTitle;
                    }
                    
                    // 方法5: 如果都找不到，返回document.title（作为最后手段）
                    return docTitle || '未知章节';
                }
                
                var content = findMainContent();
                var chapters = parseChapterList();
                var chapterTitle = findChapterTitle();
                
                return {
                    title: chapterTitle,
                    content: content,
                    nextUrl: findNextLink(),
                    prevUrl: findPrevLink(),
                    catalogUrl: findCatalogLink(),
                    chapters: chapters
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
                val catUrl = json.optString("catalogUrl")
                val chaptersArray = json.optJSONArray("chapters")
                
                // 解析章节列表
                val chapters = mutableListOf<CatalogItem>()
                if (chaptersArray != null) {
                    for (i in 0 until chaptersArray.length()) {
                        val chapterObj = chaptersArray.getJSONObject(i)
                        chapters.add(
                            CatalogItem(
                                title = chapterObj.optString("title", ""),
                                url = chapterObj.optString("url", "")
                            )
                        )
                    }
                }
                
                // 判断当前页面是目录页面还是章节页面
                // 目录页面特征：内容很少（<200字符）且章节列表很多（>=3个）
                val isCatalogPage = content.length < 200 && chapters.size >= 3
                
                if (isCatalogPage) {
                    // 目录页面：通知UI显示目录列表
                    Log.d(TAG, "✅ 检测到目录页面，章节数: ${chapters.size}")
                    cachedCatalog = chapters // 缓存目录
                    listener?.onCatalogPageDetected(chapters)
                } else if (content.isNotEmpty() && content.length >= 100) {
                    // 章节页面：正常处理（内容长度至少100字符才认为是有效内容）
                    Log.d(TAG, "✅ 检测到章节页面，标题: $title, 内容长度: ${content.length}, 章节数: ${chapters.size}")
                    nextChapterUrl = nextUrl
                    prevChapterUrl = prevUrl
                    if (catUrl.isNotEmpty()) {
                        catalogUrl = catUrl
                    }
                    // 如果章节页面也解析到了目录，保存起来供用户查看
                    if (chapters.size >= 3) {
                        Log.d(TAG, "📚 章节页面也解析到了目录，章节数: ${chapters.size}，已缓存")
                        cachedCatalog = chapters
                    }
                    listener?.onChapterLoaded(title, content, nextUrl.isNotEmpty(), prevUrl.isNotEmpty(), isAppend)
                } else {
                    // 如果内容为空或太短，尝试再次解析（可能是页面还没完全加载）
                    Log.w(TAG, "⚠️ 无法解析正文内容，内容长度: ${content.length}, 章节数: ${chapters.size}，尝试延迟重试")
                    // 延迟重试一次
                    currentWebView?.postDelayed({
                        if (isReaderModeActive) {
                            parseCurrentChapter(isAppend)
                        }
                    }, 1000)
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
    
    /**
     * 加载上一章
     */
    fun loadPrevChapter() {
        if (prevChapterUrl.isNotEmpty() && !isLoadingNext) {
            isLoadingNext = false // 上一章不是追加
            currentWebView?.loadUrl(prevChapterUrl)
        } else if (prevChapterUrl.isEmpty()) {
            listener?.onChapterLoadFailed("没有上一章链接")
        }
    }
    
    /**
     * 获取目录
     * 优先级：1. 已缓存的目录 2. 目录链接 3. 当前页面解析
     */
    fun fetchCatalog() {
        // 优先使用已缓存的目录（从章节页面解析到的）
        if (cachedCatalog.isNotEmpty()) {
            Log.d(TAG, "✅ 使用已缓存的目录，章节数: ${cachedCatalog.size}")
            listener?.onCatalogLoaded(cachedCatalog)
            return
        }
        
        if (catalogUrl.isNotEmpty()) {
            // 在后台加载目录页
            Log.d(TAG, "从目录链接加载目录: $catalogUrl")
            loadCatalogInBackground(catalogUrl)
        } else {
            // 尝试直接解析当前页面（可能当前页面就包含目录）
            Log.d(TAG, "尝试从当前页面解析目录")
            currentWebView?.let { webView ->
                // 延迟执行，确保页面完全加载
                webView.postDelayed({
                    parseCatalogFromWebView(webView)
                }, 500)
            }
        }
    }
    
    private fun loadCatalogInBackground(url: String) {
        if (backgroundWebView == null) {
            backgroundWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkImage = true // 不加载图片，加快速度
                // 设置UserAgent，防止被识别为爬虫
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.210 Mobile Safari/537.36"
                
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 延时，确保JS执行环境准备好和DOM完全加载
                        view?.postDelayed({
                            parseCatalogFromWebView(view)
                        }, 1000) // 增加到1秒，确保页面完全加载
                    }
                    
                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        listener?.onCatalogLoadFailed("加载目录页失败: $description")
                    }
                }
            }
        }
        backgroundWebView?.loadUrl(url)
    }
    
    private fun parseCatalogFromWebView(webView: WebView) {
        val js = """
            (function() {
                // 辅助函数：检查是否是章节链接
                function isChapterLink(text, href) {
                    // 检查文本是否像章节标题
                    if (text.match(/第[\\d一二三四五六七八九十百千万]+[章节回]/) ||
                        text.match(/^[\\d\\.]+[、.\\s]/) ||
                        text.match(/^第\\d+[章节回]/)) {
                        return true;
                    }
                    
                    // 检查URL是否像章节URL
                    try {
                        var urlObj = new URL(href, window.location.href);
                        var path = urlObj.pathname.toLowerCase();
                        
                        if (path.indexOf('chapter') >= 0 || path.indexOf('chap') >= 0 ||
                            path.match(/\\d+\\.html/) || path.match(/chapter\\d+/)) {
                            return true;
                        }
                    } catch(e) {}
                    
                    return false;
                }
                
                // 辅助函数：检查是否是导航链接
                function isNavigationLink(text) {
                    var excludeKeywords = ['首页', '主页', '分类', '搜索', '登录', '注册', '关于', '联系', 
                                         '帮助', '反馈', '设置', '个人中心', '我的', '书架', '推荐', 
                                         '排行榜', '热门', '最新', '完结', '连载', '免费', 'VIP',
                                         'home', 'index', 'category', 'search', 'login', 'register', 
                                         'about', 'contact', 'help', 'feedback', 'settings', 'user',
                                         'rank', 'hot', 'new', 'complete', 'serial', 'free', 'vip'];
                    var lowerText = text.toLowerCase();
                    for (var i = 0; i < excludeKeywords.length; i++) {
                        if (lowerText.indexOf(excludeKeywords[i].toLowerCase()) >= 0) {
                            return true;
                        }
                    }
                    return false;
                }

                var candidates = [];
                var elements = document.getElementsByTagName('*');
                
                // 遍历所有元素，寻找最佳的链接容器
                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];
                    // 忽略明显无关的标签
                    if (['SCRIPT', 'STYLE', 'NOSCRIPT', 'HEADER', 'FOOTER', 'NAV'].indexOf(el.tagName) > -1) continue;
                    
                    var links = el.getElementsByTagName('a');
                    if (links.length < 10) continue; // 链接太少忽略

                    // 分析链接特征
                    var validLinks = [];
                    var chapterLikeCount = 0;
                    
                    for (var j = 0; j < links.length; j++) {
                        var link = links[j];
                        var text = link.innerText.trim();
                        var href = link.href;
                        
                        if (!href || text.length < 1) continue;
                        
                        // 排除明显无效的链接
                        if (href.indexOf('javascript:') === 0 || href.indexOf('#') === 0) continue;
                        
                        // 排除导航链接
                        if (isNavigationLink(text)) continue;
                        
                        // 检查是否是章节链接
                        if (isChapterLink(text, href) || 
                            (links.length >= 5 && text.length > 3 && text.length < 200)) {
                            validLinks.push({title: text, url: href});
                            if (isChapterLink(text, href)) {
                                chapterLikeCount++;
                            }
                        }
                    }
                    
                    if (validLinks.length < 10) continue;

                    // 计算得分
                    // 1. 数量得分
                    var score = validLinks.length;
                    
                    // 2. 如果大部分链接都像章节，加分
                    if (chapterLikeCount / validLinks.length > 0.5) {
                        score += chapterLikeCount * 2;
                    }
                    
                    candidates.push({
                        element: el, 
                        chapters: validLinks, 
                        score: score,
                        count: validLinks.length
                    });
                }
                
                // 排序规则：得分高优先
                candidates.sort(function(a, b) { return b.score - a.score; });
                
                if (candidates.length > 0) {
                    // 取最高分的容器
                    var best = candidates[0];
                    var chapters = best.chapters;
                    
                    // 如果数量很大，我们假设它是正确的目录
                    return JSON.stringify(chapters);
                }
                return "[]";
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(js) { result ->
            try {
                var jsonStr = result
                if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                    jsonStr = jsonStr.substring(1, jsonStr.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                }
                
                val jsonArray = org.json.JSONArray(jsonStr)
                val catalog = ArrayList<CatalogItem>()
                
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    catalog.add(CatalogItem(item.getString("title"), item.getString("url")))
                }
                
                if (catalog.isNotEmpty()) {
                    listener?.onCatalogLoaded(catalog)
                } else {
                    listener?.onCatalogLoadFailed("未找到目录列表")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析目录失败", e)
                listener?.onCatalogLoadFailed("目录解析错误: ${e.message}")
            }
        }
    }
    
    /**
     * 加载指定章节
     */
    fun loadChapter(url: String) {
        if (!isReaderModeActive) {
            Log.w(TAG, "⚠️ 阅读模式未激活，无法加载章节")
            return
        }
        isLoadingNext = false // 重置状态，因为这是新章节，不是追加
        Log.d(TAG, "📖 加载章节: $url")
        currentWebView?.loadUrl(url)
        // 页面加载完成后会自动触发 onPageFinished -> parseCurrentChapter
    }
    
    /**
     * 获取当前WebView（用于无图模式等功能）
     */
    fun getCurrentWebView(): WebView? {
        return currentWebView
    }
}
