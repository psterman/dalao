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
                tags = listOf("编程", "代码审查", "Clean Code"),
                category = PromptCategory.CODE_ASSISTANT,
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
                tags = listOf("职场", "简历", "面试", "STAR法则"),
                category = PromptCategory.RESUME_INTERVIEW,
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
            ),

            // 新增：SEO/营销
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "SEO标题与元描述生成",
                content = "根据页面主题生成高点击率的SEO标题和元描述，包含核心关键词，长度控制在最佳范围...",
                author = "增长黑客",
                authorId = "user9",
                tags = listOf("SEO", "元描述", "关键词", "CTR"),
                category = PromptCategory.SEO_MARKETING,
                scene = "搜索优化",
                description = "一键生成SEO友好的标题与描述，提高自然流量点击率。",
                likeCount = 911,
                collectCount = 702,
                commentCount = 77,
                viewCount = 4100,
                isOriginal = true
            ),

            // 新增：图像生成
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "产品图 Midjourney 提示词",
                content = "以[产品名称]为主体，白底，高对比，柔和阴影，工作室光，f1.8，超清，--ar 1:1 --v 6",
                author = "视觉设计师",
                authorId = "user10",
                tags = listOf("Midjourney", "电商主图", "白底图"),
                category = PromptCategory.IMAGE_GENERATION,
                scene = "电商主图",
                description = "标准化电商主图生成提示，提升转化率。",
                likeCount = 688,
                collectCount = 512,
                commentCount = 41,
                viewCount = 3001,
                isOriginal = true
            ),

            // 新增：法律咨询
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "合同条款风险扫描",
                content = "从以下合同文本中识别不对等或潜在风险条款，以项目列表输出，并给出修改建议...",
                author = "法务顾问",
                authorId = "user11",
                tags = listOf("合同", "风险", "审查"),
                category = PromptCategory.LEGAL_ADVICE,
                scene = "合同审查",
                description = "快速发现合同中的潜在风险，提供可操作的调整建议。",
                likeCount = 532,
                collectCount = 401,
                commentCount = 22,
                viewCount = 2099,
                isOriginal = true
            ),

            // 新增：医疗健康（免责声明类提示）
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "科普型用药咨询（非诊断）",
                content = "以下问题仅提供一般性医学科普信息，不构成诊断或治疗建议...",
                author = "健康顾问",
                authorId = "user12",
                tags = listOf("用药", "健康科普", "非诊断"),
                category = PromptCategory.MEDICAL_HEALTH,
                scene = "健康咨询",
                description = "面向大众的用药与健康常识问答模板，含安全提示。",
                likeCount = 477,
                collectCount = 350,
                commentCount = 19,
                viewCount = 1988,
                isOriginal = true
            ),

            // 新增：财务分析
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "现金流量表解读助手",
                content = "按照经营/投资/筹资活动拆解现金流变化，指出异常波动区间并给出可能原因...",
                author = "财务分析师",
                authorId = "user13",
                tags = listOf("报表分析", "现金流", "财务"),
                category = PromptCategory.FINANCE_ANALYSIS,
                scene = "财务报表",
                description = "快速定位现金流异常并产出业务可读的解读。",
                likeCount = 721,
                collectCount = 589,
                commentCount = 35,
                viewCount = 3302,
                isOriginal = true
            ),

            // 新增：客服回复
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "电商客服标准回复库生成",
                content = "根据店铺品类{店铺类型}和常见问题清单，生成品牌语气一致的FAQ标准回复，包含换货/退款/物流/售后...",
                author = "客服经理",
                authorId = "user14",
                tags = listOf("SOP", "客服", "FAQ", "语气一致性"),
                category = PromptCategory.CUSTOMER_SUPPORT,
                scene = "售后支持",
                description = "标准化客服话术库，显著缩短响应时间。",
                likeCount = 845,
                collectCount = 640,
                commentCount = 58,
                viewCount = 4200,
                isOriginal = true
            ),
            
            // 新增：带填空的实用 prompt
            // 文案创作类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "产品推广文案生成器",
                content = "请为{产品名称}撰写一篇吸引人的推广文案，目标受众是{目标用户}，重点突出{核心卖点}，风格要求{文案风格}。",
                author = "文案专家",
                authorId = "user15",
                tags = listOf("文案创作", "营销", "推广"),
                category = PromptCategory.CREATIVE_WRITING,
                scene = "文案创作",
                description = "快速生成个性化产品推广文案，只需填写关键信息。",
                likeCount = 1250,
                collectCount = 980,
                commentCount = 156,
                viewCount = 8500,
                isOriginal = true
            ),
            
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "邮件标题优化助手",
                content = "请为以下邮件主题优化标题：{原邮件主题}。要求：1) 吸引收件人注意 2) 清晰表达邮件目的 3) 长度控制在{标题长度}字以内。",
                author = "职场助手",
                authorId = "user16",
                tags = listOf("职场办公", "邮件", "沟通"),
                category = PromptCategory.WORKPLACE_OFFICE,
                scene = "职场办公",
                description = "一键优化邮件标题，提高邮件打开率。",
                likeCount = 890,
                collectCount = 720,
                commentCount = 98,
                viewCount = 6200,
                isOriginal = true
            ),
            
            // 代码助手类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "代码功能解释器",
                content = "请用通俗易懂的语言解释以下{编程语言}代码的功能：\n{代码片段}\n\n要求：1) 说明代码的主要作用 2) 解释关键步骤 3) 如果适用，提供改进建议。",
                author = "编程导师",
                authorId = "user17",
                tags = listOf("编程开发", "代码解释", "学习"),
                category = PromptCategory.CODE_ASSISTANT,
                scene = "代码开发",
                description = "快速理解代码功能，适合学习和代码审查。",
                likeCount = 1120,
                collectCount = 890,
                commentCount = 234,
                viewCount = 7800,
                isOriginal = true
            ),
            
            // 翻译转换类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "多语言翻译助手",
                content = "请将以下{源语言}文本翻译成{目标语言}，保持原文的语气和风格：\n{待翻译文本}\n\n如果文本涉及专业术语或特定文化背景，请提供适当的解释或注释。",
                author = "翻译专家",
                authorId = "user18",
                tags = listOf("翻译转换", "多语言", "本地化"),
                category = PromptCategory.TRANSLATION_CONVERSION,
                scene = "文本翻译",
                description = "专业级翻译服务，支持多种语言对和语境适配。",
                likeCount = 980,
                collectCount = 810,
                commentCount = 187,
                viewCount = 6900,
                isOriginal = true
            ),
            
            // 数据分析类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "数据报告生成器",
                content = "基于以下数据：\n{数据内容}\n\n请生成一份{报告类型}报告，包括：1) 数据概览 2) 关键指标分析 3) 趋势说明 4) 建议和改进方向。报告长度控制在{报告长度}字左右。",
                author = "数据分析师",
                authorId = "user19",
                tags = listOf("数据分析", "报告", "洞察"),
                category = PromptCategory.DATA_ANALYSIS,
                scene = "数据分析",
                description = "自动生成结构化数据报告，节省分析时间。",
                likeCount = 1050,
                collectCount = 860,
                commentCount = 143,
                viewCount = 7200,
                isOriginal = true
            ),
            
            // 教育学习类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "学习计划制定助手",
                content = "请为{学习主题}制定一份为期{学习时长}的学习计划，目标学员是{学员水平}。计划应包括：1) 学习目标 2) 阶段性任务 3) 时间安排 4) 评估方法 5) 推荐资源。",
                author = "教育专家",
                authorId = "user20",
                tags = listOf("教育学习", "学习计划", "课程设计"),
                category = PromptCategory.EDUCATION_STUDY,
                scene = "教育学习",
                description = "个性化学习计划生成，提高学习效率。",
                likeCount = 940,
                collectCount = 780,
                commentCount = 112,
                viewCount = 6500,
                isOriginal = true
            ),
            
            // 简历面试类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "简历优化建议",
                content = "请为应聘{职位名称}的简历提供优化建议。应聘者背景：{个人背景}。要求：1) 指出简历中的亮点 2) 提出改进建议 3) 针对目标职位优化关键词 4) 提供具体的修改示例。",
                author = "HR顾问",
                authorId = "user21",
                tags = listOf("简历面试", "求职", "职业发展"),
                category = PromptCategory.RESUME_INTERVIEW,
                scene = "职场办公",
                description = "专业的简历优化服务，提升面试通过率。",
                likeCount = 1580,
                collectCount = 1240,
                commentCount = 289,
                viewCount = 9200,
                isOriginal = true
            ),
            
            // 社媒内容类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "社交媒体文案创作",
                content = "请为{平台名称}创建一条关于{主题内容}的文案，目标受众是{目标用户}，风格要求{内容风格}，长度控制在{文案长度}字以内。要求：1) 吸引注意力 2) 引导互动 3) 包含相关标签。",
                author = "社媒运营",
                authorId = "user22",
                tags = listOf("社媒内容", "社交媒体", "内容创作"),
                category = PromptCategory.SOCIAL_MEDIA,
                scene = "社媒内容",
                description = "快速创作适合各平台的社交媒体文案。",
                likeCount = 1100,
                collectCount = 920,
                commentCount = 198,
                viewCount = 8100,
                isOriginal = true
            ),
            
            // 电商运营类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "产品详情页优化",
                content = "请为{产品名称}优化电商详情页文案，产品特点：{产品特点}，目标用户：{目标用户}。要求：1) 吸引人的标题 2) 清晰的产品卖点 3) 解决用户痛点的描述 4) 促进购买的文案。",
                author = "电商运营",
                authorId = "user23",
                tags = listOf("电商运营", "产品文案", "转化优化"),
                category = PromptCategory.ECOMMERCE,
                scene = "电商运营",
                description = "提升产品页面转化率，增加销售额。",
                likeCount = 1280,
                collectCount = 1050,
                commentCount = 234,
                viewCount = 8800,
                isOriginal = true
            ),
            
            // 生活服务类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "旅行攻略生成器",
                content = "请为{目的地}生成一份{旅行天数}天的旅行攻略，旅行者类型：{旅行者类型}，预算：{预算范围}。包括：1) 行程安排 2) 推荐景点 3) 美食推荐 4) 住宿建议 5) 交通指南 6) 注意事项。",
                author = "旅行达人",
                authorId = "user24",
                tags = listOf("生活服务", "旅行", "攻略"),
                category = PromptCategory.LIFE_SERVICE,
                scene = "生活服务",
                description = "个性化旅行攻略，让旅程更精彩。",
                likeCount = 860,
                collectCount = 690,
                commentCount = 87,
                viewCount = 5800,
                isOriginal = true
            ),
            
            // SEO营销类
            PromptCommunityItem(
                id = UUID.randomUUID().toString(),
                title = "SEO关键词优化",
                content = "请为{网页主题}生成SEO优化的标题和元描述，目标关键词：{关键词}，字数要求：标题{标题字数}字以内，描述{描述字数}字以内。要求：1) 包含目标关键词 2) 吸引点击 3) 准确描述内容。",
                author = "SEO专家",
                authorId = "user25",
                tags = listOf("营销推广", "SEO", "关键词优化"),
                category = PromptCategory.SEO_MARKETING,
                scene = "搜索优化",
                description = "提升搜索引擎排名，增加自然流量。",
                likeCount = 1020,
                collectCount = 840,
                commentCount = 156,
                viewCount = 7400,
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

