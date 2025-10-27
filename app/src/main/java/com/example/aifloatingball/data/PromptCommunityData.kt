package com.example.aifloatingball.data

import com.example.aifloatingball.model.PromptCategory
import com.example.aifloatingball.model.PromptCommunityItem
import java.util.*

/**
 * Prompt社区示例数据
 */
object PromptCommunityData {
    
    /**
     * 生成示例Prompt社区数据
     */
    fun getSamplePrompts(): List<PromptCommunityItem> {
        return listOf(
            // 热门Prompt
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "专业文案写作助手",
                content = "你是一位资深的文案创作专家，擅长各种文体的写作...",
                author = "文案大师",
                authorId = "user1",
                tags = listOf("写作", "文案", "创意"),
                category = PromptCategory.CREATIVE_WRITING,
                scene = "文案创作",
                description = "帮助用户创作各类文案内容，包括广告语、宣传文案、微信公众号文章等。",
                likeCount = 1024,
                collectCount = 856,
                commentCount = 234,
                viewCount = 5000,
                isLiked = false,
                isCollected = false,
                publishTime = System.currentTimeMillis() - 86400_000L,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "代码审查专家",
                content = "你是一位经验丰富的代码审查专家，擅长发现代码中的问题...",
                author = "代码专家",
                authorId = "user2",
                tags = listOf("编程", "代码", "技术"),
                category = PromptCategory.DATA_ANALYSIS,
                scene = "代码开发",
                description = "提供专业的代码审查服务，发现潜在问题和改进建议。",
                likeCount = 892,
                collectCount = 634,
                commentCount = 156,
                viewCount = 3200,
                isLiked = false,
                isCollected = true,
                publishTime = System.currentTimeMillis() - 172800_000L,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "商业分析顾问",
                content = "你是一位资深的商业分析顾问，擅长市场分析和战略规划...",
                author = "商业顾问",
                authorId = "user3",
                tags = listOf("商业", "分析", "战略"),
                category = PromptCategory.DATA_ANALYSIS,
                scene = "商业决策",
                description = "提供专业的商业分析和战略建议，帮助做出明智的商业决策。",
                likeCount = 745,
                collectCount = 523,
                commentCount = 98,
                viewCount = 2800,
                isLiked = true,
                isCollected = false,
                publishTime = System.currentTimeMillis() - 259200_000L,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "教育内容创作",
                content = "你是一位专业的教育内容创作者，擅长制作优质的教育内容...",
                author = "教育专家",
                authorId = "user4",
                tags = listOf("教育", "内容", "学习"),
                category = PromptCategory.EDUCATION_STUDY,
                scene = "教育内容",
                description = "帮助创建各类教育内容和学习资料，提高教育质量。",
                likeCount = 634,
                collectCount = 456,
                commentCount = 87,
                viewCount = 2100,
                isLiked = false,
                isCollected = false,
                publishTime = System.currentTimeMillis() - 344400_000L,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "翻译润色专家",
                content = "你是一位专业的翻译和润色专家，擅长中英文互译...",
                author = "翻译达人",
                authorId = "user5",
                tags = listOf("翻译", "润色", "语言"),
                category = PromptCategory.TRANSLATION_CONVERSION,
                scene = "文本翻译",
                description = "提供专业的翻译和文本润色服务，确保语言的准确性和流畅性。",
                likeCount = 523,
                collectCount = 389,
                commentCount = 65,
                viewCount = 1900,
                isLiked = false,
                isCollected = false,
                publishTime = System.currentTimeMillis() - 432000_000L,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "数据分析师",
                content = "你是一位专业的数据分析师，擅长数据挖掘和洞察...",
                author = "数据专家",
                authorId = "user6",
                tags = listOf("数据", "分析", "洞察"),
                category = PromptCategory.DATA_ANALYSIS,
                scene = "数据分析",
                description = "提供专业的数据分析服务，挖掘数据背后的价值和洞察。",
                likeCount = 489,
                collectCount = 342,
                commentCount = 54,
                viewCount = 1650,
                isLiked = false,
                isCollected = false,
                publishTime = System.currentTimeMillis() - 518400_000L,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "职场简历优化",
                content = "你是一位资深的HR，擅长简历优化和面试指导...",
                author = "职场达人",
                authorId = "user7",
                tags = listOf("职场", "简历", "面试"),
                category = PromptCategory.WORKPLACE_OFFICE,
                scene = "职场办公",
                description = "提供专业的简历优化和求职指导服务，提升面试成功率。",
                likeCount = 1256,
                collectCount = 998,
                commentCount = 312,
                viewCount = 6800,
                isLiked = false,
                isCollected = true,
                publishTime = System.currentTimeMillis() - 3600_000L,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "生活旅游规划",
                content = "你是一位专业的旅游规划师，擅长制定个性化的旅行方案...",
                author = "旅行达人",
                authorId = "user8",
                tags = listOf("旅游", "规划", "生活"),
                category = PromptCategory.LIFE_SERVICE,
                scene = "生活服务",
                description = "帮助规划旅行路线、美食探店、生活技巧等各类生活服务。",
                likeCount = 342,
                collectCount = 267,
                commentCount = 43,
                viewCount = 1200,
                isLiked = false,
                isCollected = false,
                publishTime = System.currentTimeMillis() - 604800_000L,
                isOriginal = true
            )
        )
    }
    
    /**
     * 获取所有主分类
     */
    fun getAllCategories(): List<PromptCategory> {
        return PromptCategory.values().filter { it.isMainCategory }
    }
    
    /**
     * 根据筛选类型获取Prompts
     */
    fun getPromptsByFilter(filterType: com.example.aifloatingball.model.FilterType): List<PromptCommunityItem> {
        val prompts = getSamplePrompts()
        
        return when (filterType) {
            com.example.aifloatingball.model.FilterType.HOT -> 
                prompts.sortedByDescending { it.likeCount + it.collectCount }.take(20)
            com.example.aifloatingball.model.FilterType.LATEST -> 
                prompts.sortedByDescending { it.publishTime }.take(20)
            com.example.aifloatingball.model.FilterType.MY_COLLECTION -> 
                prompts.filter { it.isCollected }
            com.example.aifloatingball.model.FilterType.MY_UPLOAD -> 
                prompts.filter { it.isOriginal } // 示例：显示原创内容
        }
    }
    
    /**
     * 根据分类获取Prompts
     */
    fun getPromptsByCategory(category: PromptCategory): List<PromptCommunityItem> {
        return getSamplePrompts().filter { it.category == category }
    }
    
    /**
     * 搜索Prompts
     */
    fun searchPrompts(query: String): List<PromptCommunityItem> {
        val lowerQuery = query.lowercase()
        return getSamplePrompts().filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.description.lowercase().contains(lowerQuery) ||
            it.content.lowercase().contains(lowerQuery) ||
            it.tags.any { tag -> tag.lowercase().contains(lowerQuery) }
        }
    }
    
    /**
     * 获取热门搜索词
     */
    fun getHotKeywords(): List<String> {
        return listOf(
            "文案写作", "代码审查", "商业分析", 
            "教育内容", "翻译润色", "数据分析",
            "医疗咨询", "娱乐创作", "AI助手"
        )
    }
    
    /**
     * 获取搜索建议（根据输入内容）
     */
    fun getSearchSuggestions(query: String): List<String> {
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery.isEmpty()) {
            return getHotKeywords()
        }
        
        return (getSamplePrompts().flatMap { it.tags } + getHotKeywords())
            .distinct()
            .filter { it.lowercase().contains(lowerQuery) }
            .take(10)
    }
}

