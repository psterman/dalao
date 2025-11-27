package com.example.aifloatingball.reader

import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.JavascriptInterface
import org.json.JSONObject
import org.json.JSONArray

/**
 * 小说阅读模式管理器
 * 
 * 参考Alook浏览器的阅读模式功能，实现：
 * 1. 自动检测小说网站并进入阅读模式
 * 2. 解析页面内容，提取章节和目录
 * 3. 自动续接阅读（无限滚动）
 * 4. 退出阅读模式
 * 
 * @author AI Floating Ball
 */
class NovelReaderModeManager(private val context: Context) {
    
    companion object {
        private const val TAG = "NovelReaderModeManager"
        
        // 常见小说网站域名模式
        private val NOVEL_DOMAINS = listOf(
            "qidian.com", "zongheng.com", "17k.com", "book.qidian.com",
            "read.qidian.com", "www.qidian.com", "m.qidian.com",
            "www.zongheng.com", "m.zongheng.com",
            "www.17k.com", "m.17k.com",
            "biquge.com", "www.biquge.com", "m.biquge.com",
            "booktxt.net", "www.booktxt.net",
            "uukanshu.com", "www.uukanshu.com",
            "x23us.com", "www.x23us.com",
            "novel", "小说", "read", "book", "chapter"
        )
        
        // 章节链接选择器
        private val CHAPTER_LINK_SELECTORS = listOf(
            "a[href*='chapter']",
            "a[href*='Chapter']",
            "a[href*='chap']",
            "a[href*='read']",
            ".chapter-list a",
            ".chapter a",
            "#chapter-list a",
            ".list-group a",
            ".chapter-item a",
            "dd a",
            "li a[href*='/']"
        )
        
        // 正文内容选择器
        private val CONTENT_SELECTORS = listOf(
            "#content",
            "#chaptercontent",
            ".content",
            ".chapter-content",
            ".text-content",
            "#novelcontent",
            ".novel-content",
            ".read-content",
            "#text",
            ".text",
            "article",
            ".article-content"
        )
        
        // 标题选择器
        private val TITLE_SELECTORS = listOf(
            "h1",
            ".title",
            "#title",
            ".chapter-title",
            ".book-title",
            "h2.title",
            ".content-title"
        )
    }
    
    private var isReaderModeActive = false
    private var isNoImageMode = false // 无图模式标志
    private var currentUrl: String? = null
    private var originalUrl: String? = null // 保存原始URL，用于退出时恢复
    private var chapterList: List<ChapterInfo> = emptyList()
    private var currentChapterIndex = -1
    private var bookBaseUrl: String? = null // 书籍基础URL，用于识别同书籍的章节
    
    // SharedPreferences用于持久化目录信息
    private val sharedPreferences by lazy {
        context.getSharedPreferences("novel_reader_mode", android.content.Context.MODE_PRIVATE)
    }
    
    /**
     * 章节信息
     */
    data class ChapterInfo(
        val title: String,
        val url: String,
        val index: Int
    )
    
    /**
     * 阅读模式状态监听器
     */
    interface ReaderModeListener {
        fun onReaderModeEntered()
        fun onReaderModeExited()
        fun onChapterLoaded(chapter: ChapterInfo)
        fun onNextChapterRequested()
        fun onScroll(scrollTop: Int, scrollDelta: Int, isAtTop: Boolean, isAtBottom: Boolean)
    }
    
    private var listener: ReaderModeListener? = null
    
    fun setListener(listener: ReaderModeListener?) {
        this.listener = listener
    }
    
    /**
     * 检测当前页面是否为小说网站
     */
    fun isNovelSite(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        
        val lowerUrl = url.lowercase()
        return NOVEL_DOMAINS.any { domain ->
            lowerUrl.contains(domain)
        }
    }
    
    /**
     * 进入阅读模式
     * @param webView WebView实例
     * @param url 当前URL
     * @param useNoImageMode 是否使用无图模式（无广告、无图片）
     */
    fun enterReaderMode(webView: WebView, url: String?, useNoImageMode: Boolean = false) {
        val targetUrl = url ?: webView.url
        
        // 如果已经处于阅读模式且URL相同，不重复进入
        if (isReaderModeActive && currentUrl == targetUrl) {
            Log.d(TAG, "阅读模式已激活，URL相同，跳过")
            return
        }
        
        // 如果是第一次进入，保存原始URL用于退出时恢复
        if (originalUrl == null) {
            originalUrl = targetUrl
        }
        
        currentUrl = targetUrl
        isReaderModeActive = true
        isNoImageMode = useNoImageMode
        
        // 注入JavaScript接口
        try {
            webView.removeJavascriptInterface("ReaderMode")
        } catch (e: Exception) {
            // 忽略错误
        }
        webView.addJavascriptInterface(ReaderModeJSInterface(webView), "ReaderMode")
        
        // 如果使用无图模式，先清理页面
        if (useNoImageMode) {
            enterNoImageMode(webView)
        } else {
            // 延迟执行，确保页面完全加载
            webView.postDelayed({
                extractAndDisplayContent(webView)
            }, 500)
        }
        
        listener?.onReaderModeEntered()
        Log.d(TAG, "进入阅读模式: $url, 无图模式: $useNoImageMode, 原始URL: $originalUrl")
    }
    
    /**
     * 进入无图模式（无广告、无图片）
     */
    private fun enterNoImageMode(webView: WebView) {
        val noImageScript = """
            (function() {
                try {
                    // 移除所有图片
                    var images = document.querySelectorAll('img');
                    images.forEach(function(img) {
                        img.style.display = 'none';
                    });
                    
                    // 移除所有广告元素
                    var adSelectors = [
                        '[id*="ad"]', '[class*="ad"]', '[id*="ads"]', '[class*="ads"]',
                        '[id*="advertisement"]', '[class*="advertisement"]',
                        '[id*="banner"]', '[class*="banner"]',
                        '[id*="popup"]', '[class*="popup"]',
                        '[id*="sponsor"]', '[class*="sponsor"]',
                        'iframe[src*="ads"]', 'iframe[src*="doubleclick"]',
                        'iframe[src*="googlesyndication"]', 'iframe[src*="googleadservices"]',
                        '.ad', '.ads', '.advertisement', '.banner', '.popup', '.sponsor'
                    ];
                    
                    adSelectors.forEach(function(selector) {
                        try {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                el.style.display = 'none';
                            });
                        } catch(e) {}
                    });
                    
                    // 移除导航栏、侧边栏等非内容元素
                    var nonContentSelectors = [
                        'nav', 'header', 'footer', 'aside', '.sidebar', '.navigation',
                        '.menu', '.navbar', '.header', '.footer'
                    ];
                    
                    nonContentSelectors.forEach(function(selector) {
                        try {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                el.style.display = 'none';
                            });
                        } catch(e) {}
                    });
                    
                    // 优化正文显示
                    var contentSelectors = ${CONTENT_SELECTORS.map { "\"$it\"" }.joinToString(", ", "[", "]")};
                    for (var i = 0; i < contentSelectors.length; i++) {
                        var element = document.querySelector(contentSelectors[i]);
                        if (element && element.innerText.trim().length > 100) {
                            // 滚动到内容区域
                            element.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            break;
                        }
                    }
                    
                    // 设置页面样式
                    var style = document.createElement('style');
                    style.innerHTML = `
                        body {
                            max-width: 800px;
                            margin: 0 auto;
                            padding: 20px;
                            line-height: 2;
                            font-size: 18px;
                        }
                        img {
                            display: none !important;
                        }
                        .ad, .ads, .advertisement, .banner, .popup, .sponsor {
                            display: none !important;
                        }
                    `;
                    document.head.appendChild(style);
                    
                    console.log('无图模式已启用');
                } catch (e) {
                    console.error('启用无图模式失败:', e);
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(noImageScript) {
            Log.d(TAG, "无图模式已启用")
        }
    }
    
    /**
     * 退出阅读模式
     */
    fun exitReaderMode(webView: WebView) {
        if (!isReaderModeActive) {
            return
        }
        
        isReaderModeActive = false
        isNoImageMode = false
        // 不清空章节列表，保留以便下次使用
        // chapterList = emptyList()
        currentChapterIndex = -1
        
        // 恢复原始URL
        val urlToLoad = originalUrl ?: currentUrl
        if (urlToLoad != null) {
            webView.loadUrl(urlToLoad)
            currentUrl = urlToLoad
        } else {
            webView.reload()
        }
        
        originalUrl = null
        
        listener?.onReaderModeExited()
        Log.d(TAG, "退出阅读模式，恢复URL: $urlToLoad")
    }
    
    /**
     * 提取并显示阅读模式内容
     */
    private fun extractAndDisplayContent(webView: WebView) {
        val extractScript = """
            (function() {
                try {
                    // 提取标题 - 使用更智能的算法
                    var title = '';
                    var titleSelectors = ${TITLE_SELECTORS.map { "\"$it\"" }.joinToString(", ", "[", "]")};
                    
                    // 方法1: 按选择器查找
                    for (var i = 0; i < titleSelectors.length; i++) {
                        var element = document.querySelector(titleSelectors[i]);
                        if (element && element.innerText.trim()) {
                            var text = element.innerText.trim();
                            if (text.length > 0 && text.length < 200) { // 标题不应该太长
                                title = text;
                                break;
                            }
                        }
                    }
                    
                    // 方法2: 查找h1-h3标签
                    if (!title) {
                        for (var level = 1; level <= 3; level++) {
                            var hTag = document.querySelector('h' + level);
                            if (hTag && hTag.innerText.trim()) {
                                var text = hTag.innerText.trim();
                                if (text.length > 0 && text.length < 200) {
                                    title = text;
                                    break;
                                }
                            }
                        }
                    }
                    
                    // 方法3: 使用document.title
                    if (!title) {
                        title = document.title || '未知标题';
                        // 清理标题（移除网站名称等）
                        title = title.replace(/[-_|].*$/, '').trim();
                    }
                    
                    // 提取正文内容 - 使用更智能的算法
                    var content = '';
                    var contentSelectors = ${CONTENT_SELECTORS.map { "\"$it\"" }.joinToString(", ", "[", "]")};
                    
                    // 方法1: 按选择器查找
                    for (var i = 0; i < contentSelectors.length; i++) {
                        try {
                            var element = document.querySelector(contentSelectors[i]);
                            if (element) {
                                // 使用 innerText 而不是 textContent，确保获取的是可见文本
                                var text = element.innerText || element.textContent || '';
                                text = text.trim();
                                
                                // 检查文本长度和段落数量
                                var paragraphs = text.split(/\\n\\n|\\r\\n\\r\\n|\\n/).filter(function(p) { return p.trim().length > 10; });
                                if (text.length > 100 && paragraphs.length >= 1) {
                                    content = text;
                                    break;
                                }
                            }
                        } catch(e) {
                            // 忽略选择器错误
                        }
                    }
                    
                    // 方法2: 查找最大的文本块
                    if (!content || content.length < 200) {
                        var allElements = document.querySelectorAll('div, article, section, p');
                        var maxLength = 0;
                        var bestElement = null;
                        
                        for (var i = 0; i < allElements.length; i++) {
                            var el = allElements[i];
                            // 跳过明显不是正文的元素
                            if (el.id && (el.id.indexOf('ad') >= 0 || el.id.indexOf('nav') >= 0 || el.id.indexOf('menu') >= 0 || el.id.indexOf('header') >= 0 || el.id.indexOf('footer') >= 0)) {
                                continue;
                            }
                            if (el.className && (el.className.indexOf('ad') >= 0 || el.className.indexOf('nav') >= 0 || el.className.indexOf('menu') >= 0 || el.className.indexOf('header') >= 0 || el.className.indexOf('footer') >= 0)) {
                                continue;
                            }
                            
                            var text = el.innerText.trim();
                            var paragraphs = text.split(/\\n\\n|\\r\\n\\r\\n/).filter(function(p) { return p.trim().length > 20; });
                            
                            // 计算文本质量分数
                            var score = text.length;
                            if (paragraphs.length >= 3) score += paragraphs.length * 100;
                            if (text.indexOf('。') >= 0 || text.indexOf('！') >= 0 || text.indexOf('？') >= 0) score += 50;
                            
                            if (score > maxLength && text.length > 200) {
                                maxLength = score;
                                bestElement = el;
                            }
                        }
                        
                        if (bestElement) {
                            content = bestElement.innerText.trim();
                        }
                    }
                    
                    // 方法3: 从body中提取并清理
                    if (!content || content.length < 200) {
                        var body = document.body;
                        if (body) {
                            // 创建副本以避免修改原DOM
                            var clone = body.cloneNode(true);
                            
                            // 移除不需要的元素
                            var toRemove = clone.querySelectorAll('script, style, nav, header, footer, aside, .ad, .advertisement, .comment, .sidebar, .menu, .navigation, [class*="ad"], [id*="ad"], [class*="nav"], [id*="nav"], iframe, noscript');
                            toRemove.forEach(function(el) { 
                                try { el.remove(); } catch(e) {}
                            });
                            
                            // 移除空元素
                            var emptyElements = clone.querySelectorAll('div:empty, span:empty, p:empty');
                            emptyElements.forEach(function(el) { 
                                try { el.remove(); } catch(e) {}
                            });
                            
                            content = clone.innerText.trim();
                            
                            // 清理内容：移除过多的空白行
                            content = content.replace(/\\n{3,}/g, '\\n\\n');
                        }
                    }
                    
                    // 提取章节列表 - 改进算法，只解析当前书籍的目录，排除网站导航
                    var chapters = [];
                    var currentUrlObj = new URL(window.location.href);
                    var currentDomain = currentUrlObj.hostname;
                    var currentPath = currentUrlObj.pathname;
                    
                    // 获取当前页面的基础路径（用于匹配同书籍的章节）
                    var basePath = currentPath;
                    // 移除文件名，保留目录路径
                    if (basePath.lastIndexOf('/') > 0) {
                        basePath = basePath.substring(0, basePath.lastIndexOf('/') + 1);
                    }
                    
                    // 定义需要排除的网站导航关键词
                    var excludeKeywords = ['首页', '主页', '分类', '搜索', '登录', '注册', '关于', '联系', 
                                         '帮助', '反馈', '设置', '个人中心', '我的', '书架', '推荐', 
                                         '排行榜', '热门', '最新', '完结', '连载', '免费', 'VIP',
                                         'home', 'index', 'category', 'search', 'login', 'register', 
                                         'about', 'contact', 'help', 'feedback', 'settings', 'user',
                                         'rank', 'hot', 'new', 'complete', 'serial', 'free', 'vip'];
                    
                    // 定义需要排除的容器选择器（网站导航区域）
                    var excludeContainers = 'header, nav, footer, .header, .nav, .navbar, .navigation, .menu, .sidebar, .footer, .top-bar, .bottom-bar';
                    
                    // 辅助函数：检查链接是否是网站导航
                    function isNavigationLink(text, href, parentElement) {
                        // 检查文本是否包含导航关键词
                        var lowerText = text.toLowerCase();
                        for (var i = 0; i < excludeKeywords.length; i++) {
                            if (lowerText.indexOf(excludeKeywords[i].toLowerCase()) >= 0) {
                                return true;
                            }
                        }
                        
                        // 检查是否在导航容器中
                        if (parentElement) {
                            var parent = parentElement;
                            while (parent && parent !== document.body) {
                                var tagName = parent.tagName ? parent.tagName.toLowerCase() : '';
                                var className = parent.className || '';
                                var id = parent.id || '';
                                
                                if (tagName === 'nav' || tagName === 'header' || tagName === 'footer' ||
                                    className.indexOf('nav') >= 0 || className.indexOf('menu') >= 0 ||
                                    className.indexOf('header') >= 0 || className.indexOf('footer') >= 0 ||
                                    id.indexOf('nav') >= 0 || id.indexOf('menu') >= 0 ||
                                    id.indexOf('header') >= 0 || id.indexOf('footer') >= 0) {
                                    return true;
                                }
                                parent = parent.parentElement;
                            }
                        }
                        
                        // 检查URL是否指向网站功能页面
                        try {
                            var urlObj = new URL(href, window.location.href);
                            var path = urlObj.pathname.toLowerCase();
                            if (path === '/' || path === '/index.html' || path === '/index.htm' ||
                                path.indexOf('/category') >= 0 || path.indexOf('/search') >= 0 ||
                                path.indexOf('/login') >= 0 || path.indexOf('/register') >= 0 ||
                                path.indexOf('/about') >= 0 || path.indexOf('/contact') >= 0) {
                                return true;
                            }
                        } catch(e) {
                            // URL解析失败，跳过
                        }
                        
                        return false;
                    }
                    
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
                            
                            // 检查URL路径是否包含章节相关关键词
                            if (path.indexOf('chapter') >= 0 || path.indexOf('chap') >= 0 ||
                                path.indexOf('ch') >= 0 || path.match(/\\d+\\.html/) ||
                                path.match(/\\d+\\.htm/) || path.match(/chapter\\d+/) ||
                                path.match(/chap\\d+/)) {
                                return true;
                            }
                            
                            // 检查URL是否在同一域名和相似路径下（同书籍的章节）
                            if (urlObj.hostname === currentDomain) {
                                var linkPath = urlObj.pathname;
                                // 如果路径相似（在同一目录下），可能是章节
                                if (linkPath.indexOf(basePath) === 0 || 
                                    linkPath.substring(0, linkPath.lastIndexOf('/')) === basePath.substring(0, basePath.length - 1)) {
                                    return true;
                                }
                            }
                        } catch(e) {
                            // URL解析失败
                        }
                        
                        return false;
                    }
                    
                    // 方法1: 查找专门的章节列表容器（排除导航区域）
                    var chapterSelectors = ${CHAPTER_LINK_SELECTORS.map { "\"$it\"" }.joinToString(", ", "[", "]")};
                    for (var i = 0; i < chapterSelectors.length; i++) {
                        try {
                            // 排除导航区域的链接
                            var allLinks = document.querySelectorAll(chapterSelectors[i]);
                            var validLinks = [];
                            
                            allLinks.forEach(function(link) {
                                var text = link.innerText.trim();
                                var href = link.href;
                                
                                // 排除导航链接
                                if (isNavigationLink(text, href, link.parentElement)) {
                                    return;
                                }
                                
                                // 检查是否是章节链接
                                if (href && text && text.length >= 2 && text.length < 200 &&
                                    (isChapterLink(text, href) || 
                                     // 如果链接很多且在同一容器中，可能是章节列表
                                     (allLinks.length >= 5 && text.length > 3))) {
                                    validLinks.push({
                                        title: text,
                                        url: href,
                                        index: validLinks.length
                                    });
                                }
                            });
                            
                            // 如果找到足够的章节链接（至少3个），使用这个结果
                            if (validLinks.length >= 3) {
                                chapters = validLinks;
                                break;
                            }
                        } catch(e) {
                            // 忽略选择器错误
                        }
                    }
                    
                    // 方法2: 查找包含"章节"、"目录"等关键词的容器（排除导航区域）
                    if (chapters.length === 0) {
                        // 排除导航容器
                        var allContainers = document.querySelectorAll('div, ul, ol, dl, section');
                        var excludeElements = document.querySelectorAll(excludeContainers);
                        var excludeSet = new Set();
                        excludeElements.forEach(function(el) {
                            excludeSet.add(el);
                            // 也排除其所有子元素
                            var children = el.querySelectorAll('*');
                            children.forEach(function(child) {
                                excludeSet.add(child);
                            });
                        });
                        
                        for (var i = 0; i < allContainers.length; i++) {
                            try {
                                var container = allContainers[i];
                                
                                // 跳过排除的容器
                                if (excludeSet.has(container)) {
                                    continue;
                                }
                                
                                var containerText = container.innerText || '';
                                var containerId = container.id || '';
                                var containerClass = container.className || '';
                                
                                // 检查是否是章节目录容器
                                var isChapterContainer = false;
                                
                                // 检查文本内容（必须包含章节相关关键词）
                                if ((containerText.indexOf('章') >= 0 || 
                                     containerText.indexOf('节') >= 0 || 
                                     containerText.indexOf('目录') >= 0) &&
                                    containerText.split(/第[\\d一二三四五六七八九十百千万]+[章节回]/).length >= 3) {
                                    isChapterContainer = true;
                                }
                                
                                // 检查ID和类名（必须是明确的章节目录标识）
                                if ((containerId.indexOf('chapter') >= 0 || 
                                     containerId.indexOf('catalog') >= 0 ||
                                     containerId.indexOf('chapter-list') >= 0 ||
                                     containerId.indexOf('chapterlist') >= 0) &&
                                    containerId.indexOf('nav') < 0 &&
                                    containerId.indexOf('menu') < 0) {
                                    isChapterContainer = true;
                                }
                                
                                if ((containerClass.indexOf('chapter') >= 0 || 
                                     containerClass.indexOf('catalog') >= 0 ||
                                     containerClass.indexOf('chapter-list') >= 0) &&
                                    containerClass.indexOf('nav') < 0 &&
                                    containerClass.indexOf('menu') < 0) {
                                    isChapterContainer = true;
                                }
                                
                                if (isChapterContainer) {
                                    var links = container.querySelectorAll('a');
                                    if (links.length >= 3) {
                                        var validLinks = [];
                                        links.forEach(function(link) {
                                            var text = link.innerText.trim();
                                            var href = link.href;
                                            
                                            // 排除导航链接
                                            if (isNavigationLink(text, href, link.parentElement)) {
                                                return;
                                            }
                                            
                                            // 检查是否是章节链接
                                            if (href && text && text.length >= 2 && text.length < 200 &&
                                                (isChapterLink(text, href) || validLinks.length < 20)) {
                                                validLinks.push({
                                                    title: text,
                                                    url: href,
                                                    index: validLinks.length
                                                });
                                            }
                                        });
                                        
                                        // 如果找到足够的章节链接（至少3个），使用这个结果
                                        if (validLinks.length >= 3) {
                                            chapters = validLinks;
                                            break;
                                        }
                                    }
                                }
                            } catch(e) {
                                // 忽略错误
                            }
                        }
                    }
                    
                    // 方法3: 如果还是没找到，尝试从正文区域附近查找章节链接
                    if (chapters.length === 0) {
                        // 查找正文内容区域
                        var contentArea = null;
                        var contentSelectors = ${CONTENT_SELECTORS.map { "\"$it\"" }.joinToString(", ", "[", "]")};
                        for (var i = 0; i < contentSelectors.length; i++) {
                            var element = document.querySelector(contentSelectors[i]);
                            if (element && element.innerText.trim().length > 200) {
                                contentArea = element;
                                break;
                            }
                        }
                        
                        if (contentArea) {
                            // 在正文区域附近查找章节链接
                            var parent = contentArea.parentElement;
                            if (parent) {
                                var nearbyLinks = parent.querySelectorAll('a');
                                var validLinks = [];
                                
                                nearbyLinks.forEach(function(link) {
                                    var text = link.innerText.trim();
                                    var href = link.href;
                                    
                                    // 排除导航链接
                                    if (isNavigationLink(text, href, link.parentElement)) {
                                        return;
                                    }
                                    
                                    // 检查是否是章节链接
                                    if (href && text && isChapterLink(text, href)) {
                                        validLinks.push({
                                            title: text,
                                            url: href,
                                            index: validLinks.length
                                        });
                                    }
                                });
                                
                                if (validLinks.length >= 3) {
                                    chapters = validLinks;
                                }
                            }
                        }
                    }
                    
                    // 方法4: 在章节页面，尝试查找"目录"链接，然后访问目录页面获取完整目录
                    if (chapters.length === 0) {
                        // 查找包含"目录"、"章节目录"等文本的链接
                        var catalogLinks = document.querySelectorAll('a');
                        for (var i = 0; i < catalogLinks.length; i++) {
                            var link = catalogLinks[i];
                            var text = link.innerText.trim().toLowerCase();
                            var href = link.href;
                            
                            // 检查是否是目录链接
                            if ((text.indexOf('目录') >= 0 || text.indexOf('章节目录') >= 0 || 
                                 text.indexOf('catalog') >= 0 || text.indexOf('chapter') >= 0 ||
                                 href.indexOf('catalog') >= 0 || href.indexOf('chapter') >= 0 ||
                                 href.indexOf('list') >= 0 || href.indexOf('index') >= 0) &&
                                !isNavigationLink(text, href, link.parentElement)) {
                                // 找到目录链接，但这里不直接访问，而是返回提示信息
                                // 实际访问应该在Native代码中处理
                                console.log('找到目录链接: ' + href);
                                break;
                            }
                        }
                    }
                    
                    // 返回结果
                    return JSON.stringify({
                        title: title,
                        content: content,
                        chapters: chapters,
                        currentUrl: window.location.href,
                        hasContent: content.length >= 200
                    });
                } catch (e) {
                    return JSON.stringify({
                        error: e.toString(),
                        title: document.title || '未知标题',
                        content: document.body ? document.body.innerText.trim() : '',
                        chapters: [],
                        currentUrl: window.location.href,
                        hasContent: false
                    });
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(extractScript) { result ->
            try {
                // 移除JSON字符串的引号
                val jsonStr = result.removeSurrounding("\"").replace("\\\"", "\"")
                val json = JSONObject(jsonStr)
                
                val title = json.optString("title", "未知标题")
                val content = json.optString("content", "")
                val chaptersArray = json.optJSONArray("chapters")
                val hasContent = json.optBoolean("hasContent", false)
                
                // 解析章节列表
                val chapters = mutableListOf<ChapterInfo>()
                if (chaptersArray != null) {
                    for (i in 0 until chaptersArray.length()) {
                        val chapterObj = chaptersArray.getJSONObject(i)
                        chapters.add(
                            ChapterInfo(
                                title = chapterObj.optString("title", ""),
                                url = chapterObj.optString("url", ""),
                                index = chapterObj.optInt("index", i)
                            )
                        )
                    }
                }
                
                // 如果当前页面没有找到目录，尝试从保存的目录中加载
                if (chapters.isEmpty()) {
                    val savedChapters = loadChapterList()
                    if (savedChapters.isNotEmpty()) {
                        chapters.addAll(savedChapters)
                        Log.d(TAG, "从保存的目录中加载了 ${savedChapters.size} 个章节")
                    }
                } else {
                    // 如果找到了新目录，保存它
                    saveChapterList(chapters)
                }
                
                // 更新章节列表
                chapterList = chapters
                
                // 查找当前章节索引（通过URL匹配）
                val currentPageUrl = json.optString("currentUrl", "")
                currentChapterIndex = findCurrentChapterIndex(currentPageUrl)
                
                // 如果没找到，尝试通过URL相似性匹配
                if (currentChapterIndex < 0 && chapterList.isNotEmpty()) {
                    currentChapterIndex = findChapterIndexByUrlSimilarity(currentPageUrl)
                }
                
                Log.d(TAG, "当前章节索引: $currentChapterIndex, 总章节数: ${chapterList.size}")
                
                // 判断当前页面是目录页面还是章节页面
                val isCatalogPage = content.length < 200 && chapters.isNotEmpty() && chapters.size >= 3
                
                // 如果内容提取成功，显示阅读模式界面
                if (hasContent && content.length >= 200) {
                    // 章节页面：显示内容
                    displayReaderMode(webView, title, content, chapters)
                    
                    // 通知监听器
                    if (currentChapterIndex >= 0 && currentChapterIndex < chapters.size) {
                        listener?.onChapterLoaded(chapters[currentChapterIndex])
                    }
                } else if (isCatalogPage) {
                    // 目录页面：显示目录列表，保留可点击的目录
                    displayCatalogMode(webView, title, chapters)
                } else {
                    // 内容提取失败，使用无图模式作为备选方案
                    Log.w(TAG, "内容提取失败，切换到无图模式")
                    if (!isNoImageMode) {
                        // 重新进入无图模式
                        enterNoImageMode(webView)
                        isNoImageMode = true
                    } else {
                        // 如果已经是无图模式，显示简化版阅读模式
                        displaySimpleReaderMode(webView)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "解析阅读模式内容失败", e)
                // 解析失败，尝试使用无图模式
                if (!isNoImageMode) {
                    enterNoImageMode(webView)
                    isNoImageMode = true
                } else {
                    displaySimpleReaderMode(webView)
                }
            }
        }
    }
    
    /**
     * 显示阅读模式界面
     */
    private fun displayReaderMode(
        webView: WebView,
        title: String,
        content: String,
        chapters: List<ChapterInfo>
    ) {
        val readerModeHtml = generateReaderModeHTML(title, content, chapters)
        webView.loadDataWithBaseURL(currentUrl, readerModeHtml, "text/html", "UTF-8", null)
    }
    
    /**
     * 显示目录模式界面（在目录页面进入阅读模式时）
     */
    private fun displayCatalogMode(
        webView: WebView,
        title: String,
        chapters: List<ChapterInfo>
    ) {
        // 确保JavaScript接口已注入
        try {
            webView.removeJavascriptInterface("ReaderMode")
        } catch (e: Exception) {
            // 忽略错误
        }
        webView.addJavascriptInterface(ReaderModeJSInterface(webView), "ReaderMode")
        
        val catalogHtml = generateCatalogModeHTML(title, chapters)
        webView.loadDataWithBaseURL(currentUrl, catalogHtml, "text/html", "UTF-8", null)
        
        // 页面加载完成后，再次确保接口可用
        webView.postDelayed({
            webView.evaluateJavascript("""
                (function() {
                    console.log('目录模式页面加载完成，检查ReaderMode接口...');
                    console.log('window.ReaderMode:', typeof window.ReaderMode);
                    if (typeof window.ReaderMode === 'undefined') {
                        console.warn('ReaderMode接口未注入，尝试重新设置章节项');
                    }
                })();
            """.trimIndent(), null)
        }, 500)
    }
    
    /**
     * 生成目录模式HTML（显示可点击的目录列表）
     */
    private fun generateCatalogModeHTML(
        title: String,
        chapters: List<ChapterInfo>
    ): String {
        val chaptersHTML = if (chapters.isNotEmpty()) {
            chapters.joinToString("") { chapter ->
                // 转义URL，确保在onclick中正确使用
                val escapedUrl = chapter.url
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "&quot;")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                val escapedTitle = chapter.title
                    .replace("'", "&#39;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                
                """
                <div class="chapter-item" 
                     data-url="$escapedUrl" 
                     data-title="$escapedTitle">
                    $escapedTitle
                </div>
                """
            }
        } else {
            "<div class='no-chapters'>暂无目录</div>"
        }
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <title>$title - 目录</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    html {
                        background-color: #ffffff;
                        width: 100%;
                        height: 100%;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
                        background-color: #ffffff;
                        color: #333;
                        line-height: 1.8;
                        padding: 0;
                        margin: 0;
                        width: 100%;
                        height: 100%;
                        min-height: 100vh;
                    }
                    
                    .reader-container {
                        max-width: 800px;
                        margin: 0 auto;
                        background: white;
                        min-height: 100vh;
                        padding: 10px;
                        box-shadow: 0 0 10px rgba(0,0,0,0.1);
                    }
                    
                    .reader-header {
                        position: sticky;
                        top: 0;
                        background: white;
                        padding: 8px 0;
                        border-bottom: 1px solid #e0e0e0;
                        z-index: 100;
                        margin-bottom: 8px;
                    }
                    
                    .reader-title {
                        font-size: 18px;
                        font-weight: bold;
                        color: #333;
                        margin-bottom: 6px;
                        text-align: center;
                    }
                    
                    .reader-controls {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        gap: 8px;
                    }
                    
                    .control-btn {
                        padding: 6px 12px;
                        background: #007AFF;
                        color: white;
                        border: none;
                        border-radius: 6px;
                        font-size: 13px;
                        cursor: pointer;
                        min-width: 60px;
                    }
                    
                    .control-btn:active {
                        background: #0056CC;
                    }
                    
                    .control-btn.exit {
                        background: #ff3b30;
                    }
                    
                    .catalog-title {
                        font-size: 16px;
                        font-weight: bold;
                        color: #333;
                        margin: 8px 0;
                        text-align: center;
                        padding-bottom: 6px;
                        border-bottom: 2px solid #007AFF;
                    }
                    
                    .chapter-list-content {
                        max-height: calc(100vh - 150px);
                        overflow-y: auto;
                        padding: 0;
                        -webkit-overflow-scrolling: touch;
                    }
                    
                    .chapter-item {
                        padding: 8px 12px;
                        border-bottom: 1px solid #e0e0e0;
                        cursor: pointer;
                        transition: background 0.15s;
                        font-size: 14px;
                        line-height: 1.5;
                        user-select: none;
                        -webkit-user-select: none;
                        -webkit-tap-highlight-color: rgba(0,0,0,0.1);
                        touch-action: manipulation;
                        pointer-events: auto;
                        -webkit-touch-callout: none;
                    }
                    
                    .chapter-item:active {
                        background: #e0e0e0;
                    }
                    
                    .chapter-item:hover {
                        background: #f0f0f0;
                    }
                    
                    .chapter-item:last-child {
                        border-bottom: none;
                    }
                    
                    .no-chapters {
                        padding: 20px;
                        text-align: center;
                        color: #999;
                    }
                </style>
            </head>
            <body>
                <div class="reader-container">
                    <div class="reader-header">
                        <div class="reader-title">$title</div>
                        <div class="reader-controls">
                            <button class="control-btn exit" onclick="if(window.ReaderMode) ReaderMode.exitReaderMode(); else location.reload()">退出</button>
                        </div>
                    </div>
                    
                    <div class="catalog-title">章节目录</div>
                    <div class="chapter-list-content">
                        $chaptersHTML
                    </div>
                </div>
                
                <script>
                    // 确保ReaderMode接口可用
                    console.log('目录模式初始化，检查ReaderMode接口...');
                    console.log('window.ReaderMode:', typeof window.ReaderMode);
                    
                    // 处理章节点击
                    function handleChapterClick(url, title) {
                        console.log('点击章节: ' + title + ', URL: ' + url);
                        if (!url) {
                            console.error('章节URL为空');
                            return;
                        }
                        
                        try {
                            // 检查ReaderMode接口
                            if (window.ReaderMode && typeof window.ReaderMode.loadChapter === 'function') {
                                console.log('调用ReaderMode.loadChapter: ' + url);
                                window.ReaderMode.loadChapter(url);
                            } else {
                                // 如果ReaderMode不可用，尝试等待后重试
                                console.warn('ReaderMode不可用，等待后重试...');
                                var retryCount = 0;
                                var maxRetries = 5;
                                var retryInterval = setInterval(function() {
                                    retryCount++;
                                    if (window.ReaderMode && typeof window.ReaderMode.loadChapter === 'function') {
                                        console.log('重试成功，调用ReaderMode.loadChapter: ' + url);
                                        clearInterval(retryInterval);
                                        window.ReaderMode.loadChapter(url);
                                    } else if (retryCount >= maxRetries) {
                                        console.warn('ReaderMode仍然不可用，直接跳转');
                                        clearInterval(retryInterval);
                                        window.location.href = url;
                                    }
                                }, 200);
                            }
                        } catch(e) {
                            console.error('加载章节失败:', e);
                            console.error('错误堆栈:', e.stack);
                            // 回退到直接跳转
                            try {
                                window.location.href = url;
                            } catch(e2) {
                                console.error('直接跳转也失败:', e2);
                            }
                        }
                    }
                    
                    // 确保所有章节项都可以点击
                    function setupChapterItems() {
                        console.log('设置章节项样式...');
                        var chapterItems = document.querySelectorAll('.chapter-item');
                        console.log('找到 ' + chapterItems.length + ' 个章节项');
                        
                        chapterItems.forEach(function(item, index) {
                            var url = item.getAttribute('data-url');
                            var title = item.getAttribute('data-title');
                            
                            if (url) {
                                // 确保样式正确
                                item.style.cursor = 'pointer';
                                item.style.userSelect = 'none';
                                item.style.webkitUserSelect = 'none';
                                item.style.touchAction = 'manipulation';
                                item.style.pointerEvents = 'auto';
                                item.style.webkitTouchCallout = 'none';
                                
                                // 确保onclick事件存在
                                if (!item.onclick) {
                                    item.onclick = function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        var chapterUrl = this.getAttribute('data-url');
                                        var chapterTitle = this.getAttribute('data-title');
                                        console.log('点击章节: ' + chapterTitle + ', URL: ' + chapterUrl);
                                        handleChapterClick(chapterUrl, chapterTitle);
                                        return false;
                                    };
                                }
                                
                                // 添加触摸事件处理，确保触摸也能触发点击
                                item.addEventListener('touchstart', function(e) {
                                    this.style.backgroundColor = '#e0e0e0';
                                }, { passive: true });
                                
                                item.addEventListener('touchend', function(e) {
                                    this.style.backgroundColor = '';
                                    e.preventDefault();
                                    e.stopPropagation();
                                    var chapterUrl = this.getAttribute('data-url');
                                    var chapterTitle = this.getAttribute('data-title');
                                    console.log('触摸点击章节: ' + chapterTitle + ', URL: ' + chapterUrl);
                                    handleChapterClick(chapterUrl, chapterTitle);
                                    return false;
                                }, { passive: false });
                                
                                // 添加鼠标按下效果（视觉反馈）
                                item.addEventListener('mousedown', function(e) {
                                    this.style.backgroundColor = '#e0e0e0';
                                });
                                item.addEventListener('mouseup', function(e) {
                                    this.style.backgroundColor = '';
                                });
                                item.addEventListener('mouseleave', function(e) {
                                    this.style.backgroundColor = '';
                                });
                            } else {
                                console.warn('章节项缺少URL: ' + title);
                            }
                        });
                        
                        console.log('章节项样式设置完成');
                    }
                    
                    // 页面加载完成后设置章节项
                    function initChapterItems() {
                        if (document.readyState === 'loading') {
                            document.addEventListener('DOMContentLoaded', setupChapterItems);
                        } else {
                            setupChapterItems();
                        }
                        
                        // 延迟再次检查，确保接口已注入
                        setTimeout(function() {
                            console.log('延迟检查ReaderMode接口...');
                            console.log('window.ReaderMode:', typeof window.ReaderMode);
                            if (typeof window.ReaderMode === 'undefined') {
                                console.warn('ReaderMode接口仍未注入，可能需要重新设置');
                            }
                        }, 1000);
                    }
                    
                    initChapterItems();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * 生成阅读模式HTML
     */
    private fun generateReaderModeHTML(
        title: String,
        content: String,
        chapters: List<ChapterInfo>
    ): String {
        val chaptersHTML = if (chapters.isNotEmpty()) {
            chapters.joinToString("") { chapter ->
                // 转义URL和标题中的特殊字符
                val escapedUrl = chapter.url
                    .replace("'", "\\'")
                    .replace("\"", "&quot;")
                val escapedTitle = chapter.title
                    .replace("'", "&#39;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                
                """
                <div class="chapter-item" 
                     data-url="$escapedUrl" 
                     data-title="$escapedTitle"
                     onclick="if(window.ReaderMode && ReaderMode.loadChapter) { ReaderMode.loadChapter('$escapedUrl'); ReaderMode.hideChapterList(); } else { window.location.href='$escapedUrl'; } return false;"
                     ontouchend="if(window.ReaderMode && ReaderMode.loadChapter) { ReaderMode.loadChapter('$escapedUrl'); ReaderMode.hideChapterList(); } else { window.location.href='$escapedUrl'; } return false;">
                    $escapedTitle
                </div>
                """
            }
        } else {
            "<div class='no-chapters'>暂无目录</div>"
        }
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <title>$title</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
                        background-color: #f5f5f5;
                        color: #333;
                        line-height: 1.8;
                        padding: 0;
                        margin: 0;
                    }
                    
                    .reader-container {
                        max-width: 800px;
                        margin: 0 auto;
                        background: white;
                        min-height: 100vh;
                        padding: 20px;
                        box-shadow: 0 0 10px rgba(0,0,0,0.1);
                    }
                    
                    .reader-header {
                        position: fixed;
                        top: 0;
                        left: 0;
                        right: 0;
                        background: white;
                        padding: 15px 0;
                        border-bottom: 1px solid #e0e0e0;
                        z-index: 100;
                        margin-bottom: 20px;
                        transform: translateY(0);
                        opacity: 1;
                        transition: transform 0.3s ease, opacity 0.3s ease;
                    }
                    
                    .reader-header.hidden {
                        transform: translateY(-100%);
                        opacity: 0;
                    }
                    
                    .reader-title {
                        font-size: 16px;
                        font-weight: bold;
                        color: #333;
                        margin-bottom: 10px;
                        text-align: center;
                    }
                    
                    .reader-controls {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        flex-wrap: wrap;
                        gap: 10px;
                    }
                    
                    .control-btn {
                        padding: 8px 16px;
                        background: #007AFF;
                        color: white;
                        border: none;
                        border-radius: 6px;
                        font-size: 14px;
                        cursor: pointer;
                        flex: 1;
                        min-width: 80px;
                    }
                    
                    .control-btn:active {
                        background: #0056CC;
                    }
                    
                    .control-btn.exit {
                        background: #ff3b30;
                    }
                    
                    .control-btn.exit:active {
                        background: #cc2e24;
                    }
                    
                    .reader-content {
                        font-size: 18px;
                        line-height: 2.2;
                        color: #333;
                        padding: 20px 0;
                        word-wrap: break-word;
                        text-align: justify;
                    }
                    
                    .reader-content .content-paragraph {
                        margin-bottom: 1.5em;
                        text-indent: 2em;
                        line-height: 2.2;
                    }
                    
                    .reader-content .content-title {
                        font-size: 20px;
                        font-weight: bold;
                        margin: 2em 0 1em 0;
                        text-align: center;
                        color: #007AFF;
                        text-indent: 0;
                        padding: 10px 0;
                        border-bottom: 2px solid #e0e0e0;
                    }
                    
                    .reader-content .content-paragraph:first-child {
                        margin-top: 0;
                    }
                    
                    .chapter-list {
                        display: none;
                        position: fixed;
                        top: 50%;
                        left: 50%;
                        transform: translate(-50%, -50%);
                        background: white;
                        border-radius: 12px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
                        z-index: 1000;
                        width: 90%;
                        max-width: 500px;
                        max-height: 70vh;
                        overflow: hidden;
                        opacity: 0;
                        transition: opacity 0.3s ease;
                    }
                    
                    .chapter-list.show {
                        display: block;
                        opacity: 1;
                    }
                    
                    .chapter-list.hiding {
                        opacity: 0;
                        transition: opacity 0.3s ease;
                    }
                    
                    .chapter-list-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 15px 20px;
                        border-bottom: 1px solid #e0e0e0;
                        background: #f8f8f8;
                    }
                    
                    .chapter-list-header h3 {
                        margin: 0;
                        font-size: 18px;
                        color: #333;
                    }
                    
                    .close-chapter-list {
                        padding: 5px 15px;
                        background: #ff3b30;
                        color: white;
                        border: none;
                        border-radius: 6px;
                        font-size: 14px;
                        cursor: pointer;
                    }
                    
                    .chapter-list-content {
                        max-height: calc(70vh - 60px);
                        overflow-y: auto;
                        padding: 10px 0;
                    }
                    
                    .chapter-item {
                        padding: 12px 20px;
                        border-bottom: 1px solid #e0e0e0;
                        cursor: pointer;
                        transition: background 0.2s;
                        font-size: 15px;
                        user-select: none;
                        -webkit-user-select: none;
                        -webkit-tap-highlight-color: rgba(0,0,0,0.1);
                        touch-action: manipulation;
                        pointer-events: auto;
                    }
                    
                    .chapter-item:hover {
                        background: #f0f0f0;
                    }
                    
                    .chapter-item:active {
                        background: #e0e0e0;
                    }
                    
                    .chapter-item:last-child {
                        border-bottom: none;
                    }
                    
                    .no-chapters {
                        padding: 20px;
                        text-align: center;
                        color: #999;
                    }
                    
                    .loading {
                        text-align: center;
                        padding: 40px;
                        color: #999;
                    }
                    
                    @media (prefers-color-scheme: dark) {
                        body {
                            background-color: #1c1c1e;
                            color: #f2f2f7;
                        }
                        
                        .reader-container {
                            background: #2c2c2e;
                            color: #f2f2f7;
                        }
                        
                        .reader-header {
                            background: #2c2c2e;
                            border-bottom-color: #3a3a3c;
                        }
                        
                        .reader-title {
                            color: #f2f2f7;
                        }
                        
                        .chapter-list {
                            background: #2c2c2e;
                        }
                        
                        .chapter-item {
                            border-bottom-color: #3a3a3c;
                        }
                        
                        .chapter-item:hover {
                            background: #3a3a3c;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="reader-container">
                    <div class="reader-header">
                        <div class="reader-title">$title</div>
                        <div class="reader-controls">
                            <button class="control-btn" onclick="if(window.ReaderMode) ReaderMode.showChapters(); else alert('目录功能暂不可用')">目录</button>
                            <button class="control-btn" onclick="if(window.ReaderMode) ReaderMode.loadPrevChapter(); else alert('上一章功能暂不可用')">上一章</button>
                            <button class="control-btn" onclick="if(window.ReaderMode) ReaderMode.loadNextChapter(); else alert('下一章功能暂不可用')">下一章</button>
                            <button class="control-btn exit" onclick="if(window.ReaderMode) ReaderMode.exitReaderMode(); else location.reload()">退出</button>
                        </div>
                    </div>
                    
                    <div class="chapter-list" id="chapterList">
                        <div class="chapter-list-header">
                            <h3>目录</h3>
                            <button class="close-chapter-list" onclick="if(window.ReaderMode) ReaderMode.showChapters();">关闭</button>
                        </div>
                        <div class="chapter-list-content">
                            $chaptersHTML
                        </div>
                    </div>
                    
                    <div class="reader-content" id="readerContent">
                        ${formatContent(content)}
                    </div>
                </div>
                
                <script>
                    // 确保ReaderMode接口可用
                    if (typeof ReaderMode === 'undefined') {
                        console.warn('ReaderMode interface not available');
                    }
                    
                    // 确保章节项可以点击
                    function setupChapterItemsClickable() {
                        var chapterItems = document.querySelectorAll('.chapter-item');
                        console.log('设置章节项可点击，找到 ' + chapterItems.length + ' 个章节项');
                        
                        chapterItems.forEach(function(item) {
                            // 确保元素可点击
                            item.style.pointerEvents = 'auto';
                            item.style.userSelect = 'none';
                            item.style.webkitUserSelect = 'none';
                            item.style.touchAction = 'manipulation';
                            item.style.cursor = 'pointer';
                            
                            // 确保onclick事件存在
                            if (!item.onclick) {
                                var url = item.getAttribute('data-url');
                                if (url) {
                                    item.onclick = function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        var chapterUrl = this.getAttribute('data-url');
                                        if (window.ReaderMode && typeof window.ReaderMode.loadChapter === 'function') {
                                            window.ReaderMode.loadChapter(chapterUrl);
                                            if (typeof window.ReaderMode.hideChapterList === 'function') {
                                                window.ReaderMode.hideChapterList();
                                            }
                                        } else {
                                            window.location.href = chapterUrl;
                                        }
                                        return false;
                                    };
                                }
                            }
                            
                            // 添加触摸事件支持
                            item.addEventListener('touchstart', function(e) {
                                this.style.backgroundColor = '#e0e0e0';
                            }, { passive: true });
                            
                            item.addEventListener('touchend', function(e) {
                                this.style.backgroundColor = '';
                                // 触发点击事件
                                if (this.onclick) {
                                    this.onclick(e);
                                }
                            }, { passive: true });
                        });
                    }
                    
                    // 页面加载完成后设置章节项
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', setupChapterItemsClickable);
                    } else {
                        setupChapterItemsClickable();
                    }
                    
                    // 延迟再次设置，确保目录列表显示时也能点击
                    setTimeout(setupChapterItemsClickable, 500);
                    
                    // 自动滚动到底部时加载下一章，并控制顶部header显示/隐藏
                    var lastScrollTop = 0;
                    var scrollTimer = null;
                    var isLoadingNext = false; // 防止重复加载
                    var headerVisible = true; // 顶部header是否可见
                    var scrollThreshold = 15; // 滚动阈值，降低以提高响应速度
                    
                    // 节流相关变量，优化性能
                    var lastScrollTime = 0;
                    var scrollThrottleDelay = 16; // 约60fps，16ms
                    var pendingScrollUpdate = false;
                    
                    function updateHeaderVisibility(show) {
                        var header = document.querySelector('.reader-header');
                        if (header) {
                            if (show && !headerVisible) {
                                // 显示header
                                header.classList.remove('hidden');
                                header.style.transform = 'translateY(0)';
                                header.style.opacity = '1';
                                headerVisible = true;
                            } else if (!show && headerVisible) {
                                // 隐藏header
                                header.classList.add('hidden');
                                header.style.transform = 'translateY(-100%)';
                                header.style.opacity = '0';
                                headerVisible = false;
                            }
                        }
                    }
                    
                    // 初始化header样式
                    var header = document.querySelector('.reader-header');
                    if (header) {
                        header.style.transition = 'transform 0.18s ease, opacity 0.18s ease';
                        header.style.transform = 'translateY(0)';
                        header.style.opacity = '1';
                        header.classList.remove('hidden');
                    }
                    
                    // 优化的滚动处理函数，添加节流机制
                    var scrollHandler = function() {
                        var currentTime = performance.now();
                        
                        // 节流：限制调用频率
                        if (currentTime - lastScrollTime < scrollThrottleDelay) {
                            pendingScrollUpdate = true;
                            return;
                        }
                        
                        lastScrollTime = currentTime;
                        pendingScrollUpdate = false;
                        
                        var scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
                        var scrollDelta = scrollTop - lastScrollTop;
                        
                        // 如果滚动距离太小，跳过处理
                        if (Math.abs(scrollDelta) < 1) {
                            return;
                        }
                        
                        var scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight;
                        var clientHeight = document.documentElement.clientHeight || window.innerHeight;
                        var isAtBottom = (scrollHeight - scrollTop - clientHeight) < 50; // 距离底部50px内认为到底部
                        var isAtTop = scrollTop < 50; // 距离顶部50px内认为在顶部
                        
                        // 控制顶部header显示/隐藏
                        if (isAtTop || isAtBottom) {
                            // 在顶部或底部时，强制显示header
                            if (!headerVisible) {
                                updateHeaderVisibility(true);
                            }
                        } else if (Math.abs(scrollDelta) > scrollThreshold) {
                            // 只有在滚动距离超过阈值时才改变header状态
                            if (scrollDelta > 0) {
                                // 向下滚动，隐藏header
                                if (headerVisible) {
                                    updateHeaderVisibility(false);
                                }
                            } else {
                                // 向上滚动，显示header
                                if (!headerVisible) {
                                    updateHeaderVisibility(true);
                                }
                            }
                        }
                        
                        lastScrollTop = scrollTop;
                        
                        // 通知Android端滚动状态变化（节流处理）
                        if (window.ReaderMode && typeof ReaderMode.onScroll === 'function') {
                            try {
                                var scrollData = {
                                    scrollTop: scrollTop,
                                    scrollDelta: scrollDelta,
                                    isAtTop: isAtTop,
                                    isAtBottom: isAtBottom,
                                    scrollHeight: scrollHeight,
                                    clientHeight: clientHeight
                                };
                                window.ReaderMode.onScroll(JSON.stringify(scrollData));
                            } catch(e) {
                                console.error('调用ReaderMode.onScroll失败:', e);
                            }
                        }
                        
                        // 检查是否需要加载下一章
                        clearTimeout(scrollTimer);
                        scrollTimer = setTimeout(function() {
                            if (isLoadingNext) return; // 如果正在加载，不重复触发
                            
                            // 滚动到接近底部（距离底部200px以内）
                            if (scrollHeight - scrollTop - clientHeight < 200) {
                                if (window.ReaderMode && typeof ReaderMode.loadNextChapter === 'function') {
                                    isLoadingNext = true;
                                    console.log('触发自动加载下一章');
                                    ReaderMode.loadNextChapter();
                                    // 3秒后重置加载标志，防止卡死
                                    setTimeout(function() {
                                        isLoadingNext = false;
                                    }, 3000);
                                }
                            }
                        }, 500);
                    };
                    
                    // 使用requestAnimationFrame优化滚动监听，替代多种监听方式
                    var lastRAFScrollTop = 0;
                    var rafActive = true;
                    
                    function rafScrollCheck() {
                        if (!rafActive) return;
                        
                        var currentScrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
                        
                        // 只有当滚动位置真正改变时才处理
                        if (Math.abs(currentScrollTop - lastRAFScrollTop) > 0.5) {
                            lastRAFScrollTop = currentScrollTop;
                            scrollHandler();
                            
                            // 如果有待处理的滚动更新，立即处理
                            if (pendingScrollUpdate) {
                                scrollHandler();
                            }
                        }
                        
                        requestAnimationFrame(rafScrollCheck);
                    }
                    
                    // 启动RAF滚动监听
                    requestAnimationFrame(rafScrollCheck);
                    
                    // 作为备用，添加scroll事件监听（但使用节流）
                    var throttledScrollHandler = function() {
                        if (pendingScrollUpdate) {
                            scrollHandler();
                        }
                    };
                    window.addEventListener('scroll', throttledScrollHandler, { passive: true });
                    
                    // 监听页面可见性变化，确保在页面重新可见时重置加载标志
                    document.addEventListener('visibilitychange', function() {
                        if (!document.hidden) {
                            isLoadingNext = false;
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * 格式化内容文本，正确解析换行符和段落
     * 修复：处理字面量 \n 字符串，将其转换为真正的换行符
     */
    private fun formatContent(content: String): String {
        if (content.isEmpty()) {
            return "<div class='loading'>正在加载内容...</div>"
        }
        
        // 第一步：处理字面量 \n 字符串，将其转换为真正的换行符
        var processedContent = content
            .replace("\\n", "\n")  // 将字面量 \n 转换为换行符
            .replace("\\r\\n", "\n")  // 将字面量 \r\n 转换为换行符
            .replace("\\r", "\n")  // 将字面量 \r 转换为换行符
        
        // 第二步：转义HTML特殊字符（在换行符处理之后）
        processedContent = processedContent
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
        
        // 第三步：统一换行符格式
        val normalized = processedContent
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        
        // 第四步：按双换行符分割段落（空行分隔）
        var paragraphs = normalized.split("\n\n", "\n \n", "\n\t\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // 第五步：如果段落数量少，尝试按单换行符分割
        val finalParagraphs = if (paragraphs.size < 3) {
            normalized.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length > 5 }
        } else {
            paragraphs
        }
        
        // 第六步：格式化段落：识别标题和正文
        return finalParagraphs.joinToString("") { paragraph ->
            when {
                // 识别标题（短文本，可能包含数字或特殊字符）
                paragraph.length < 50 && (
                    paragraph.matches(Regex("^第[\\d一二三四五六七八九十百千万]+[章节回].*")) ||
                    paragraph.matches(Regex("^[\\d\\.]+\\s+.*")) ||
                    paragraph.matches(Regex("^[\\d]+、.*"))
                ) -> {
                    "<h3 class='content-title'>$paragraph</h3>"
                }
                // 普通段落
                else -> {
                    "<p class='content-paragraph'>$paragraph</p>"
                }
            }
        }
    }
    
    /**
     * 显示简化版阅读模式（当解析失败时）
     */
    private fun displaySimpleReaderMode(webView: WebView) {
        val simpleHTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>阅读模式</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, sans-serif;
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 20px;
                        line-height: 2;
                        background: #f5f5f5;
                    }
                    .header {
                        background: white;
                        padding: 15px;
                        border-radius: 8px;
                        margin-bottom: 20px;
                        text-align: center;
                    }
                    .control-btn {
                        padding: 10px 20px;
                        background: #007AFF;
                        color: white;
                        border: none;
                        border-radius: 6px;
                        margin: 5px;
                        cursor: pointer;
                    }
                    .control-btn.exit {
                        background: #ff3b30;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h2>阅读模式</h2>
                    <button class="control-btn exit" onclick="ReaderMode.exitReaderMode()">退出阅读模式</button>
                </div>
                <div id="content">正在加载...</div>
                <script>
                    setTimeout(function() {
                        document.getElementById('content').innerHTML = document.body.innerText;
                    }, 500);
                </script>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL(currentUrl, simpleHTML, "text/html", "UTF-8", null)
    }
    
    /**
     * JavaScript接口，用于与WebView交互
     */
    private inner class ReaderModeJSInterface(private val webView: WebView) {
        @JavascriptInterface
        fun showChapters() {
            Log.d(TAG, "显示/隐藏目录")
            webView.post {
                webView.evaluateJavascript("""
                    (function() {
                        var chapterList = document.getElementById('chapterList');
                        if (chapterList) {
                            if (chapterList.classList.contains('show')) {
                                chapterList.classList.remove('show');
                                chapterList.classList.add('hiding');
                                setTimeout(function() {
                                    chapterList.classList.remove('hiding');
                                    chapterList.style.display = 'none';
                                }, 300);
                            } else {
                                chapterList.style.display = 'block';
                                setTimeout(function() {
                                    chapterList.classList.add('show');
                                    // 确保章节项可以点击
                                    var chapterItems = document.querySelectorAll('.chapter-item');
                                    chapterItems.forEach(function(item) {
                                        item.style.pointerEvents = 'auto';
                                        item.style.userSelect = 'none';
                                        item.style.webkitUserSelect = 'none';
                                        item.style.touchAction = 'manipulation';
                                        item.style.cursor = 'pointer';
                                    });
                                }, 10);
                            }
                        }
                    })();
                """.trimIndent(), null)
            }
        }
        
        @JavascriptInterface
        fun hideChapterList() {
            Log.d(TAG, "隐藏目录")
            webView.post {
                webView.evaluateJavascript("""
                    (function() {
                        var chapterList = document.getElementById('chapterList');
                        if (chapterList && chapterList.classList.contains('show')) {
                            chapterList.classList.remove('show');
                            chapterList.classList.add('hiding');
                            setTimeout(function() {
                                chapterList.classList.remove('hiding');
                                chapterList.style.display = 'none';
                            }, 300);
                        }
                    })();
                """.trimIndent(), null)
            }
        }
        
        @JavascriptInterface
        fun loadPrevChapter() {
            Log.d(TAG, "加载上一章")
            webView.post {
                loadPrevChapter(webView)
            }
        }
        
        @JavascriptInterface
        fun loadChapter(url: String) {
            Log.d(TAG, "加载章节: $url")
            webView.post {
                try {
                    // 保存原始URL（如果还没有保存）
                    if (originalUrl == null) {
                        originalUrl = currentUrl
                    }
                    
                    // 解析URL，处理相对路径
                    val resolvedUrl = resolveUrl(url, currentUrl ?: bookBaseUrl)
                    Log.d(TAG, "解析后的URL: $resolvedUrl (原始: $url)")
                    
                    // 查找章节在目录中的位置
                    var chapterIndex = chapterList.indexOfFirst { chapter ->
                        val chapterResolvedUrl = resolveUrl(chapter.url, bookBaseUrl)
                        chapterResolvedUrl == resolvedUrl || 
                        chapterResolvedUrl.equals(resolvedUrl, ignoreCase = true) ||
                        resolvedUrl.endsWith(chapterResolvedUrl) || 
                        chapterResolvedUrl.endsWith(resolvedUrl) ||
                        // 也检查原始URL
                        chapter.url == url ||
                        chapter.url.equals(url, ignoreCase = true) ||
                        url.endsWith(chapter.url) ||
                        chapter.url.endsWith(url)
                    }
                    
                    if (chapterIndex >= 0) {
                        // 找到章节，更新索引和URL
                        currentChapterIndex = chapterIndex
                        currentUrl = resolvedUrl
                        val chapter = chapterList[chapterIndex]
                        
                        Log.d(TAG, "找到章节: ${chapter.title}, 索引: $chapterIndex")
                        
                        // 无缝加载章节内容
                        loadChapterContentSeamlessly(webView, chapter)
                    } else {
                        // 如果找不到，尝试通过URL相似性查找
                        val similarIndex = findChapterIndexByUrlSimilarity(resolvedUrl)
                        if (similarIndex >= 0) {
                            currentChapterIndex = similarIndex
                            currentUrl = resolvedUrl
                            val chapter = chapterList[similarIndex]
                            
                            Log.d(TAG, "通过相似性找到章节: ${chapter.title}, 索引: $similarIndex")
                            
                            // 无缝加载章节内容
                            loadChapterContentSeamlessly(webView, chapter)
                        } else {
                            // 如果还是找不到，直接加载URL并进入阅读模式
                            Log.w(TAG, "未找到章节，直接加载URL并进入阅读模式: $resolvedUrl")
                            currentUrl = resolvedUrl
                            
                            // 直接加载URL，然后进入阅读模式
                            webView.loadUrl(resolvedUrl)
                            webView.postDelayed({
                                // 确保阅读模式已激活
                                if (!isReaderModeActive) {
                                    isReaderModeActive = true
                                    // 重新注入接口
                                    try {
                                        webView.removeJavascriptInterface("ReaderMode")
                                    } catch (e: Exception) {
                                        // 忽略错误
                                    }
                                    webView.addJavascriptInterface(ReaderModeJSInterface(webView), "ReaderMode")
                                }
                                // 提取并显示内容
                                extractAndDisplayContent(webView)
                            }, 1500)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载章节失败", e)
                    // 回退：直接加载URL
                    try {
                        val resolvedUrl = resolveUrl(url, currentUrl ?: bookBaseUrl)
                        webView.loadUrl(resolvedUrl)
                        webView.postDelayed({
                            if (isReaderModeActive) {
                                extractAndDisplayContent(webView)
                            }
                        }, 1500)
                    } catch (e2: Exception) {
                        Log.e(TAG, "回退加载也失败", e2)
                    }
                }
            }
        }
        
        @JavascriptInterface
        fun loadNextChapter() {
            Log.d(TAG, "加载下一章")
            webView.post {
                loadNextChapter(webView)
            }
        }
        
        @JavascriptInterface
        fun exitReaderMode() {
            Log.d(TAG, "退出阅读模式（从JS调用）")
            webView.post {
                exitReaderMode(webView)
            }
        }
        
        @JavascriptInterface
        fun onScroll(scrollDataJson: String) {
            try {
                val scrollData = org.json.JSONObject(scrollDataJson)
                val scrollTop = scrollData.getInt("scrollTop")
                val scrollDelta = scrollData.getInt("scrollDelta")
                val isAtTop = scrollData.getBoolean("isAtTop")
                val isAtBottom = scrollData.getBoolean("isAtBottom")
                
                webView.post {
                    listener?.onScroll(scrollTop, scrollDelta, isAtTop, isAtBottom)
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析滚动数据失败", e)
            }
        }
    }
    
    /**
     * 加载下一章（无缝切换，不重新加载页面）
     */
    fun loadNextChapter(webView: WebView) {
        // 如果章节列表为空，尝试从保存的目录中加载
        if (chapterList.isEmpty()) {
            val savedChapters = loadChapterList()
            if (savedChapters.isNotEmpty()) {
                chapterList = savedChapters
                Log.d(TAG, "从保存的目录中加载了 ${savedChapters.size} 个章节")
            }
        }
        
        // 如果还是没有章节列表，尝试从页面查找
        if (chapterList.isEmpty()) {
            Log.w(TAG, "没有章节列表，尝试从页面查找下一章链接")
            loadNextChapterFromPage(webView)
            return
        }
        
        // 如果当前章节索引无效，尝试重新查找
        if (currentChapterIndex < 0 || currentChapterIndex >= chapterList.size) {
            val currentPageUrl = webView.url ?: currentUrl
            if (currentPageUrl != null) {
                currentChapterIndex = findCurrentChapterIndex(currentPageUrl)
                if (currentChapterIndex < 0) {
                    currentChapterIndex = findChapterIndexByUrlSimilarity(currentPageUrl)
                }
            }
        }
        
        // 计算下一章索引（确保基于正确的当前章节索引）
        val nextIndex = if (currentChapterIndex >= 0) {
            currentChapterIndex + 1
        } else {
            // 如果找不到当前章节，尝试通过URL重新查找
            val currentPageUrl = webView.url ?: currentUrl
            if (currentPageUrl != null) {
                val foundIndex = findCurrentChapterIndex(currentPageUrl)
                if (foundIndex >= 0) {
                    currentChapterIndex = foundIndex
                    foundIndex + 1
                } else {
                    val similarIndex = findChapterIndexByUrlSimilarity(currentPageUrl)
                    if (similarIndex >= 0) {
                        currentChapterIndex = similarIndex
                        similarIndex + 1
                    } else {
                        // 如果还是找不到，假设是第一章，下一章是第二章
                        1
                    }
                }
            } else {
                // 如果找不到当前章节，假设是第一章
                1
            }
        }
        
        if (nextIndex >= chapterList.size) {
            Log.d(TAG, "已经是最后一章，当前索引: $currentChapterIndex, 下一章索引: $nextIndex, 总章节数: ${chapterList.size}")
            android.widget.Toast.makeText(context, "已经是最后一章", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val nextChapter = chapterList[nextIndex]
        
        // 保存原始URL（如果还没有保存）
        if (originalUrl == null) {
            originalUrl = currentUrl
        }
        
        // 更新当前URL和索引（在加载之前更新，确保索引正确）
        currentUrl = nextChapter.url
        currentChapterIndex = nextIndex
        
        Log.d(TAG, "准备加载下一章: ${nextChapter.title}, 索引: $nextIndex, 当前索引已更新为: $currentChapterIndex")
        
        // 使用隐藏的WebView加载下一章内容，然后无缝更新当前阅读模式
        loadChapterContentSeamlessly(webView, nextChapter)
        
        listener?.onChapterLoaded(nextChapter)
        Log.d(TAG, "加载下一章: ${nextChapter.title}, URL: ${nextChapter.url}, 索引: $nextIndex")
    }
    
    /**
     * 加载上一章（无缝切换，不重新加载页面）
     */
    fun loadPrevChapter(webView: WebView) {
        // 如果章节列表为空，尝试从保存的目录中加载
        if (chapterList.isEmpty()) {
            val savedChapters = loadChapterList()
            if (savedChapters.isNotEmpty()) {
                chapterList = savedChapters
                Log.d(TAG, "从保存的目录中加载了 ${savedChapters.size} 个章节")
            }
        }
        
        if (chapterList.isEmpty()) {
            Log.w(TAG, "没有章节列表，无法加载上一章")
            android.widget.Toast.makeText(context, "没有章节列表", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // 如果当前章节索引无效，尝试重新查找
        if (currentChapterIndex < 0 || currentChapterIndex >= chapterList.size) {
            val currentPageUrl = webView.url ?: currentUrl
            if (currentPageUrl != null) {
                currentChapterIndex = findCurrentChapterIndex(currentPageUrl)
                if (currentChapterIndex < 0) {
                    currentChapterIndex = findChapterIndexByUrlSimilarity(currentPageUrl)
                }
            }
        }
        
        // 计算上一章索引
        val prevIndex = if (currentChapterIndex > 0) {
            currentChapterIndex - 1
        } else {
            Log.d(TAG, "已经是第一章")
            android.widget.Toast.makeText(context, "已经是第一章", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        if (prevIndex < 0) {
            Log.d(TAG, "已经是第一章")
            android.widget.Toast.makeText(context, "已经是第一章", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val prevChapter = chapterList[prevIndex]
        
        // 保存原始URL（如果还没有保存）
        if (originalUrl == null) {
            originalUrl = currentUrl
        }
        
        // 更新当前URL和索引
        currentUrl = prevChapter.url
        currentChapterIndex = prevIndex
        
        // 使用隐藏的WebView加载上一章内容，然后无缝更新当前阅读模式
        loadChapterContentSeamlessly(webView, prevChapter)
        
        listener?.onChapterLoaded(prevChapter)
        Log.d(TAG, "加载上一章: ${prevChapter.title}, URL: ${prevChapter.url}, 索引: $prevIndex")
    }
    
    /**
     * 从页面查找下一章链接（备用方案）
     */
    private fun loadNextChapterFromPage(webView: WebView) {
        webView.post {
            webView.evaluateJavascript("""
                (function() {
                    // 查找包含"下一章"、"下一页"等文本的链接
                    var links = document.querySelectorAll('a');
                    for (var i = 0; i < links.length; i++) {
                        var link = links[i];
                        var text = link.innerText || link.textContent || '';
                        if (text.match(/下一[章节页]/) || text.match(/下[一章页]/) || 
                            link.href.indexOf('next') >= 0 || link.href.indexOf('下') >= 0) {
                            return link.href;
                        }
                    }
                    return null;
                })();
            """.trimIndent()) { nextUrl ->
                if (!nextUrl.isNullOrEmpty() && nextUrl != "null") {
                    val cleanUrl = nextUrl.removeSurrounding("\"").removeSurrounding("'")
                    Log.d(TAG, "找到下一章链接: $cleanUrl")
                    
                    // 创建临时章节信息
                    val tempChapter = ChapterInfo("下一章", cleanUrl, -1)
                    currentUrl = cleanUrl
                    loadChapterContentSeamlessly(webView, tempChapter)
                } else {
                    Log.w(TAG, "未找到下一章链接")
                    android.widget.Toast.makeText(context, "未找到下一章", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 无缝加载章节内容（不重新加载页面）
     */
    private fun loadChapterContentSeamlessly(webView: WebView, chapter: ChapterInfo) {
        // 显示加载提示
        webView.post {
            webView.evaluateJavascript("""
                (function() {
                    var contentDiv = document.getElementById('readerContent');
                    if (contentDiv) {
                        contentDiv.innerHTML = '<div class="loading">正在加载下一章...</div>';
                        // 滚动到顶部
                        window.scrollTo(0, 0);
                    }
                })();
            """.trimIndent(), null)
        }
        
        // 创建隐藏的WebView来加载下一章内容
        val tempWebView = android.webkit.WebView(context)
        tempWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = webView.settings.userAgentString
        }
        
        // 设置WebViewClient来提取内容
        tempWebView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // 提取内容
                view?.evaluateJavascript(extractContentScript()) { result ->
                    try {
                        // 解析结果
                        val jsonStr = result.removeSurrounding("\"").replace("\\\"", "\"")
                        val json = JSONObject(jsonStr)
                        
                        val title = json.optString("title", chapter.title)
                        val content = json.optString("content", "")
                        
                        if (content.isNotEmpty() && content.length >= 100) {
                            // 更新当前阅读模式的内容
                            updateReaderContent(webView, title, content)
                            
                            // 确保章节索引已正确更新（在loadNextChapter中已更新，这里只是确认）
                            Log.d(TAG, "成功加载章节内容: $title, 当前索引: $currentChapterIndex")
                        } else {
                            // 如果提取失败，回退到重新加载页面
                            Log.w(TAG, "内容提取失败，回退到重新加载页面")
                            webView.loadUrl(chapter.url)
                            webView.postDelayed({
                                if (isReaderModeActive) {
                                    extractAndDisplayContent(webView)
                                }
                            }, 2000)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析下一章内容失败", e)
                        // 回退到重新加载页面
                        webView.loadUrl(chapter.url)
                        webView.postDelayed({
                            if (isReaderModeActive) {
                                extractAndDisplayContent(webView)
                            }
                        }, 2000)
                    } finally {
                        // 清理临时WebView
                        try {
                            tempWebView.destroy()
                        } catch (e: Exception) {
                            Log.e(TAG, "清理临时WebView失败", e)
                        }
                    }
                }
            }
        }
        
        // 加载下一章URL
        tempWebView.loadUrl(chapter.url)
    }
    
    /**
     * 提取内容的JavaScript脚本
     */
    private fun extractContentScript(): String {
        return """
            (function() {
                try {
                    // 提取标题
                    var title = '';
                    var titleSelectors = ${TITLE_SELECTORS.map { "\"$it\"" }.joinToString(", ", "[", "]")};
                    for (var i = 0; i < titleSelectors.length; i++) {
                        var element = document.querySelector(titleSelectors[i]);
                        if (element && element.innerText.trim()) {
                            var text = element.innerText.trim();
                            if (text.length > 0 && text.length < 200) {
                                title = text;
                                break;
                            }
                        }
                    }
                    if (!title) {
                        for (var level = 1; level <= 3; level++) {
                            var hTag = document.querySelector('h' + level);
                            if (hTag && hTag.innerText.trim()) {
                                var text = hTag.innerText.trim();
                                if (text.length > 0 && text.length < 200) {
                                    title = text;
                                    break;
                                }
                            }
                        }
                    }
                    if (!title) {
                        title = document.title || '未知标题';
                        title = title.replace(/[-_|].*$/, '').trim();
                    }
                    
                    // 提取正文内容
                    var content = '';
                    var contentSelectors = ${CONTENT_SELECTORS.map { "\"$it\"" }.joinToString(", ", "[", "]")};
                    for (var i = 0; i < contentSelectors.length; i++) {
                        var element = document.querySelector(contentSelectors[i]);
                        if (element) {
                            var text = element.innerText.trim();
                            var paragraphs = text.split(/\\n\\n|\\r\\n\\r\\n|\\n/).filter(function(p) { return p.trim().length > 10; });
                            if (text.length > 100 && paragraphs.length >= 1) {
                                content = text;
                                break;
                            }
                        }
                    }
                    
                    if (!content || content.length < 100) {
                        var body = document.body;
                        if (body) {
                            var clone = body.cloneNode(true);
                            var toRemove = clone.querySelectorAll('script, style, nav, header, footer, aside, .ad, .advertisement, .comment, .sidebar, .menu, .navigation, [class*="ad"], [id*="ad"], [class*="nav"], [id*="nav"], iframe, noscript');
                            toRemove.forEach(function(el) { 
                                try { el.remove(); } catch(e) {}
                            });
                            content = clone.innerText.trim();
                            content = content.replace(/\\n{3,}/g, '\\n\\n');
                        }
                    }
                    
                    return JSON.stringify({
                        title: title,
                        content: content,
                        hasContent: content.length >= 100
                    });
                } catch (e) {
                    return JSON.stringify({
                        error: e.toString(),
                        title: document.title || '未知标题',
                        content: document.body ? document.body.innerText.trim() : '',
                        hasContent: false
                    });
                }
            })();
        """.trimIndent()
    }
    
    /**
     * 更新阅读模式内容（不重新加载页面）
     */
    private fun updateReaderContent(webView: WebView, title: String, content: String) {
        webView.post {
            // 格式化内容
            val formattedContent = formatContent(content)
            
            // 更新标题和内容
            val updateScript = """
                (function() {
                    try {
                        // 更新标题
                        var titleDiv = document.querySelector('.reader-title');
                        if (titleDiv) {
                            titleDiv.textContent = ${title.toJsonString()};
                        }
                        
                        // 更新内容
                        var contentDiv = document.getElementById('readerContent');
                        if (contentDiv) {
                            contentDiv.innerHTML = ${formattedContent.toJsonString()};
                            // 滚动到顶部
                            window.scrollTo({ top: 0, behavior: 'smooth' });
                        }
                    } catch(e) {
                        console.error('更新内容失败:', e);
                    }
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(updateScript, null)
        }
    }
    
    /**
     * 将字符串转换为JSON安全字符串（用于JavaScript）
     */
    private fun String.toJsonString(): String {
        // 转义特殊字符
        val escaped = this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("'", "\\'")
        return "\"$escaped\""
    }
    
    /**
     * 查找当前章节索引（精确匹配URL）
     */
    private fun findCurrentChapterIndex(url: String): Int {
        if (url.isEmpty() || chapterList.isEmpty()) return -1
        
        return chapterList.indexOfFirst { chapter ->
            chapter.url == url || 
            chapter.url.equals(url, ignoreCase = true) ||
            // 处理相对路径和绝对路径
            url.endsWith(chapter.url) || chapter.url.endsWith(url)
        }
    }
    
    /**
     * 通过URL相似性查找章节索引
     */
    private fun findChapterIndexByUrlSimilarity(url: String): Int {
        if (url.isEmpty() || chapterList.isEmpty()) return -1
        
        try {
            val urlObj = java.net.URL(url)
            val urlPath = urlObj.path
            val urlQuery = urlObj.query
            
            // 尝试从URL中提取章节编号
            val chapterNumPattern = Regex("(\\d+)(?:\\.html|\\.htm|/|$)")
            val chapterNumMatch = chapterNumPattern.find(urlPath)
            val chapterNum = chapterNumMatch?.groupValues?.get(1)?.toIntOrNull()
            
            if (chapterNum != null) {
                // 查找URL中包含相同数字的章节
                val matchedIndex = chapterList.indexOfFirst { chapter ->
                    chapter.url.contains(chapterNum.toString()) ||
                    chapter.title.contains("第${chapterNum}") ||
                    chapter.title.contains("${chapterNum}章")
                }
                if (matchedIndex >= 0) return matchedIndex
            }
            
            // 通过URL路径相似性匹配
            val urlPathParts = urlPath.split("/").filter { it.isNotEmpty() }
            if (urlPathParts.isNotEmpty()) {
                val lastPart = urlPathParts.last()
                val matchedIndex = chapterList.indexOfFirst { chapter ->
                    try {
                        val chapterUrlObj = java.net.URL(chapter.url)
                        val chapterPath = chapterUrlObj.path
                        chapterPath.contains(lastPart) || lastPart.contains(chapterPath.split("/").lastOrNull() ?: "")
                    } catch (e: Exception) {
                        false
                    }
                }
                if (matchedIndex >= 0) return matchedIndex
            }
        } catch (e: Exception) {
            Log.e(TAG, "URL解析失败", e)
        }
        
        return -1
    }
    
    /**
     * 保存章节列表到SharedPreferences
     */
    private fun saveChapterList(chapters: List<ChapterInfo>) {
        try {
            val jsonArray = org.json.JSONArray()
            chapters.forEach { chapter ->
                val jsonObject = org.json.JSONObject()
                jsonObject.put("title", chapter.title)
                jsonObject.put("url", chapter.url)
                jsonObject.put("index", chapter.index)
                jsonArray.put(jsonObject)
            }
            
            // 保存书籍基础URL
            val baseUrl = chapters.firstOrNull()?.url?.let { url ->
                try {
                    val urlObj = java.net.URL(url)
                    "${urlObj.protocol}://${urlObj.host}${urlObj.path.substringBeforeLast("/")}"
                } catch (e: Exception) {
                    null
                }
            }
            
            sharedPreferences.edit()
                .putString("chapter_list", jsonArray.toString())
                .putString("book_base_url", baseUrl)
                .putLong("chapter_list_saved_time", System.currentTimeMillis())
                .apply()
            
            bookBaseUrl = baseUrl
            Log.d(TAG, "保存了 ${chapters.size} 个章节到SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "保存章节列表失败", e)
        }
    }
    
    /**
     * 从SharedPreferences加载章节列表
     */
    private fun loadChapterList(): List<ChapterInfo> {
        try {
            val jsonStr = sharedPreferences.getString("chapter_list", null)
            if (jsonStr.isNullOrEmpty()) return emptyList()
            
            val jsonArray = org.json.JSONArray(jsonStr)
            val chapters = mutableListOf<ChapterInfo>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                chapters.add(
                    ChapterInfo(
                        title = jsonObject.optString("title", ""),
                        url = jsonObject.optString("url", ""),
                        index = jsonObject.optInt("index", i)
                    )
                )
            }
            
            bookBaseUrl = sharedPreferences.getString("book_base_url", null)
            Log.d(TAG, "从SharedPreferences加载了 ${chapters.size} 个章节")
            return chapters
        } catch (e: Exception) {
            Log.e(TAG, "加载章节列表失败", e)
            return emptyList()
        }
    }
    
    /**
     * 解析URL，将相对URL转换为绝对URL
     */
    private fun resolveUrl(url: String, baseUrl: String?): String {
        if (url.isBlank()) return url
        
        // 如果已经是绝对URL，直接返回
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        
        // 如果没有baseUrl，返回原始URL
        if (baseUrl.isNullOrBlank()) {
            return url
        }
        
        try {
            // 使用Java的URL类解析相对URL
            val base = java.net.URL(baseUrl)
            val resolved = java.net.URL(base, url)
            return resolved.toString()
        } catch (e: Exception) {
            Log.w(TAG, "解析URL失败: $url (base: $baseUrl)", e)
            // 如果解析失败，尝试简单的字符串拼接
            return when {
                url.startsWith("/") -> {
                    // 绝对路径
                    val protocol = if (baseUrl.startsWith("https://")) "https://" else "http://"
                    val host = try {
                        java.net.URL(baseUrl).host
                    } catch (e2: Exception) {
                        return url
                    }
                    "$protocol$host$url"
                }
                else -> {
                    // 相对路径
                    val basePath = baseUrl.substringBeforeLast("/")
                    "$basePath/$url"
                }
            }
        }
    }
    
    /**
     * 检测当前页面是否为目录页
     * 使用多层判断逻辑，参考Alook浏览器的实现方案
     * @param webView WebView实例
     * @param url 当前URL
     * @param callback 检测结果回调，true表示是目录页
     */
    fun detectCatalogPage(webView: WebView, url: String?, callback: (Boolean) -> Unit) {
        if (url.isNullOrEmpty()) {
            callback(false)
            return
        }
        
        // 🔧 放宽条件：即使URL特征不匹配，也继续检测页面内容
        // 第一层：URL特征判断（仅作为加分项，不强制要求）
        val isCatalogUrl = checkUrlFeatures(url)
        val urlBonus = if (isCatalogUrl) 0.2 else 0.0
        Log.d(TAG, "URL特征判断: isCatalogUrl=$isCatalogUrl, urlBonus=$urlBonus")
        
        // 第二层：页面内容检测（核心验证）
        // 使用重试机制，确保异步内容加载完成
        var retryCount = 0
        val maxRetries = 3
        
        fun performDetection() {
            val detectionScript = generateCatalogDetectionScript()
            webView.evaluateJavascript(detectionScript) { result ->
                try {
                    val jsonStr = result.removeSurrounding("\"").replace("\\\"", "\"")
                    val json = JSONObject(jsonStr)
                    val isCatalog = json.optBoolean("isCatalogPage", false)
                    var confidence = json.optDouble("confidence", 0.0)
                    val reasons = json.optJSONArray("reasons")
                    
                    // 添加URL特征加分
                    confidence += urlBonus
                    
                    Log.d(TAG, "目录页检测结果 (重试$retryCount/$maxRetries): isCatalog=$isCatalog, confidence=$confidence")
                    if (reasons != null) {
                        val reasonsList = mutableListOf<String>()
                        for (i in 0 until reasons.length()) {
                            reasonsList.add(reasons.getString(i))
                        }
                        Log.d(TAG, "检测原因: ${reasonsList.joinToString(", ")}")
                    }
                    
                    // 🔧 放宽置信度阈值：从0.6降到0.3
                    val finalResult = isCatalog && confidence >= 0.3
                    
                    if (finalResult) {
                        Log.d(TAG, "✅ 判定为目录页，置信度: $confidence")
                        callback(true)
                    } else if (retryCount < maxRetries && confidence < 0.3) {
                        // 如果置信度不够，且还有重试次数，继续重试
                        retryCount++
                        val delay = 1000L * retryCount // 递增延迟：1s, 2s, 3s
                        Log.d(TAG, "置信度不足($confidence < 0.3)，${delay}ms后重试 ($retryCount/$maxRetries)")
                        webView.postDelayed({
                            performDetection()
                        }, delay)
                    } else {
                        Log.d(TAG, "❌ 判定为非目录页，置信度: $confidence")
                        callback(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析目录页检测结果失败", e)
                    if (retryCount < maxRetries) {
                        retryCount++
                        val delay = 1000L * retryCount
                        Log.d(TAG, "检测失败，${delay}ms后重试 ($retryCount/$maxRetries)")
                        webView.postDelayed({
                            performDetection()
                        }, delay)
                    } else {
                        callback(false)
                    }
                }
            }
        }
        
        // 首次检测延迟1秒
        webView.postDelayed({
            performDetection()
        }, 1000)
    }
    
    /**
     * 第一层：URL特征判断
     */
    private fun checkUrlFeatures(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // 正向匹配关键词
        val positiveKeywords = listOf(
            "目录", "章节列表", "chapters?", "catalog", "directory", "list",
            "zhangjie", "chap", "type=chapter", "page=chapter", "cid=list"
        )
        
        // 反向排除关键词
        val negativeKeywords = listOf(
            "read", "view", "chapterid", "cid=\\d+", "page=read",
            "info", "intro", "profile", "detail",
            "index", "home", "search", "s=", "q="
        )
        
        // 检查正向关键词
        val hasPositive = positiveKeywords.any { keyword ->
            try {
                lowerUrl.contains(keyword, ignoreCase = true) || 
                Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(lowerUrl)
            } catch (e: Exception) {
                lowerUrl.contains(keyword, ignoreCase = true)
            }
        }
        
        // 检查反向关键词（如果包含则直接否定）
        val hasNegative = negativeKeywords.any { keyword ->
            try {
                lowerUrl.contains(keyword, ignoreCase = true) || 
                Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(lowerUrl)
            } catch (e: Exception) {
                lowerUrl.contains(keyword, ignoreCase = true)
            }
        }
        
        return hasPositive && !hasNegative
    }
    
    /**
     * 生成目录页检测的JavaScript脚本
     * 实现多层判断逻辑：章节文本特征、章节链接特征、DOM结构特征、排除误判
     */
    private fun generateCatalogDetectionScript(): String {
        return """
            (function() {
                try {
                    var result = {
                        isCatalogPage: false,
                        confidence: 0.0,
                        reasons: []
                    };
                    
                    // 第二层：章节文本特征检测（最核心）
                    var chapterTextPatterns = [
                        /第[零一二三四五六七八九十百千万\\d]+[章回卷节集]/g,
                        /序章|楔子|后记|番外|特别篇/g,
                        /^\\d+[.、-]\\s?[^\\d]+/gm
                    ];
                    
                    var bodyText = document.body ? document.body.innerText || '' : '';
                    var chapterTextMatches = 0;
                    chapterTextPatterns.forEach(function(pattern) {
                        var matches = bodyText.match(pattern);
                        if (matches) {
                            chapterTextMatches += matches.length;
                        }
                    });
                    
                    // 🔧 放宽阈值：章节文本数量从5降到3
                    var hasEnoughChapterText = chapterTextMatches >= 3;
                    if (hasEnoughChapterText) {
                        result.confidence += 0.3;
                        result.reasons.push('章节文本数量: ' + chapterTextMatches);
                    } else if (chapterTextMatches > 0) {
                        // 即使不够3个，只要有章节文本就给予部分分数
                        result.confidence += 0.15;
                        result.reasons.push('章节文本数量较少: ' + chapterTextMatches);
                    }
                    
                    // 章节链接特征检测
                    var allLinks = document.querySelectorAll('a');
                    var chapterLinks = [];
                    var chapterLinkPattern = /第[\\d一二三四五六七八九十百千万]+[章回卷节]|^\\d+[.、-]/;
                    var readPagePattern = /read|chapterid|cid=\\d+/i;
                    
                    for (var i = 0; i < allLinks.length; i++) {
                        var link = allLinks[i];
                        var text = (link.textContent || link.innerText || '').trim();
                        var href = link.href || '';
                        
                        // 检查是否是章节链接
                        if (chapterLinkPattern.test(text) && readPagePattern.test(href)) {
                            chapterLinks.push({
                                text: text,
                                href: href
                            });
                        }
                    }
                    
                    // 🔧 放宽阈值：章节链接数量从5降到3，且放宽链接匹配条件
                    // 如果链接文本匹配章节模式，即使href不匹配阅读页模式也接受
                    var relaxedChapterLinks = [];
                    for (var i = 0; i < allLinks.length; i++) {
                        var link = allLinks[i];
                        var text = (link.textContent || link.innerText || '').trim();
                        var href = link.href || '';
                        
                        // 放宽条件：只要文本匹配章节模式即可
                        if (chapterLinkPattern.test(text)) {
                            relaxedChapterLinks.push({
                                text: text,
                                href: href
                            });
                        }
                    }
                    
                    // 使用更宽松的链接列表（优先使用relaxedChapterLinks，因为它包含更多链接）
                    var finalChapterLinks = relaxedChapterLinks.length > chapterLinks.length ? relaxedChapterLinks : chapterLinks;
                    var hasEnoughChapterLinks = finalChapterLinks.length >= 3;
                    
                    if (hasEnoughChapterLinks) {
                        result.confidence += 0.3;
                        result.reasons.push('章节链接数量: ' + finalChapterLinks.length);
                    } else if (finalChapterLinks.length > 0) {
                        // 即使不够3个，只要有章节链接就给予部分分数
                        result.confidence += 0.15;
                        result.reasons.push('章节链接数量较少: ' + finalChapterLinks.length);
                    }
                    
                    // DOM结构特征检测
                    var catalogContainerPattern = /chapter-?list|catalog|directory|zhangjie/i;
                    var containers = document.querySelectorAll('div, ul, ol, section');
                    var catalogContainerFound = false;
                    
                    for (var i = 0; i < containers.length; i++) {
                        var container = containers[i];
                        var id = container.id || '';
                        var className = container.className || '';
                        var classList = className.split(' ');
                        
                        // 检查ID或class是否包含目录关键词
                        if (catalogContainerPattern.test(id) || 
                            classList.some(function(cls) { return catalogContainerPattern.test(cls); })) {
                            
                            // 检查容器内是否有足够的链接
                            var linksInContainer = container.querySelectorAll('a');
                            if (linksInContainer.length >= 5) {
                                catalogContainerFound = true;
                                break;
                            }
                        }
                    }
                    
                    if (catalogContainerFound) {
                        result.confidence += 0.2;
                        result.reasons.push('找到目录容器');
                    }
                    
                    // 页面标题特征（辅助）
                    var title = document.title || '';
                    var titlePattern = /目录|章节列表|chapters? list/i;
                    if (titlePattern.test(title)) {
                        result.confidence += 0.1;
                        result.reasons.push('标题包含目录关键词');
                    }
                    
                    // 第三层：排除误判
                    // 排除详情页：检查是否有封面、简介等元素
                    var hasCover = document.querySelector('img[alt*="封面"], img[alt*="cover"]') !== null;
                    var hasIntro = bodyText.indexOf('简介') >= 0 || bodyText.indexOf('作者') >= 0;
                    var hasRating = bodyText.indexOf('评分') >= 0 || bodyText.indexOf('星级') >= 0;
                    
                    if (hasCover && hasIntro && chapterLinks.length <= 5) {
                        // 可能是详情页的"最新章节"模块，不是完整目录页
                        result.confidence -= 0.3;
                        result.reasons.push('疑似详情页（有封面和简介）');
                    }
                    
                    // 排除阅读页：检查是否有阅读控制按钮
                    var hasReadControls = bodyText.indexOf('上一章') >= 0 || 
                                         bodyText.indexOf('下一章') >= 0 ||
                                         bodyText.indexOf('字体') >= 0 ||
                                         bodyText.indexOf('夜间模式') >= 0;
                    
                    // 检查正文容器长度
                    var contentSelectors = ['#content', '.content', '.chapter-content', 
                                          '.text-content', '#novelcontent', '.novel-content', 
                                          '.read-content', '#text', '.text', 'article', '.article-content'];
                    var contentLength = 0;
                    for (var i = 0; i < contentSelectors.length; i++) {
                        var element = document.querySelector(contentSelectors[i]);
                        if (element) {
                            var text = element.textContent || element.innerText || '';
                            if (text.length > contentLength) {
                                contentLength = text.length;
                            }
                        }
                    }
                    
                    // 🔧 放宽排除条件：正文长度阈值从2000提高到3000
                    if (hasReadControls || contentLength >= 3000) {
                        result.confidence -= 0.3; // 减少扣分，从0.4降到0.3
                        result.reasons.push('疑似阅读页（有阅读控制或长正文: ' + contentLength + '字）');
                    }
                    
                    // 排除分类页/搜索结果页：检查列表项是否包含小说信息而非章节
                    var listItems = document.querySelectorAll('li, .item, .list-item');
                    var novelInfoCount = 0;
                    for (var i = 0; i < Math.min(listItems.length, 10); i++) {
                        var item = listItems[i];
                        var text = (item.textContent || item.innerText || '').toLowerCase();
                        if (text.indexOf('小说') >= 0 || text.indexOf('全文') >= 0 || 
                            text.indexOf('完结') >= 0 || text.indexOf('作者') >= 0) {
                            novelInfoCount++;
                        }
                    }
                    
                    // 如果大部分列表项包含小说信息，可能是分类页
                    if (listItems.length > 0 && novelInfoCount / listItems.length > 0.5) {
                        result.confidence -= 0.3;
                        result.reasons.push('疑似分类页（列表项包含小说信息）');
                    }
                    
                    // 最终判断：置信度阈值
                    result.isCatalogPage = result.confidence >= 0.6;
                    
                    return JSON.stringify(result);
                } catch (e) {
                    return JSON.stringify({
                        isCatalogPage: false,
                        confidence: 0.0,
                        error: e.toString()
                    });
                }
            })();
        """.trimIndent()
    }
    
    /**
     * 检查是否处于阅读模式
     */
    fun isReaderModeActive(): Boolean = isReaderModeActive
    
    /**
     * 获取当前章节列表
     */
    fun getChapterList(): List<ChapterInfo> = chapterList
    
    /**
     * 获取当前章节索引
     */
    fun getCurrentChapterIndex(): Int = currentChapterIndex
}

