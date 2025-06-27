package com.example.aifloatingball.data

import com.example.aifloatingball.model.AssistantCategory
import com.example.aifloatingball.model.AssistantPrompt

object AssistantPrompts {
    val categories = listOf(
        AssistantCategory("编程", listOf(
            AssistantPrompt("代码审查官", "审查代码，发现潜在问题", ""),
            AssistantPrompt("BUG发现者", "分析代码，寻找逻辑漏洞", ""),
            AssistantPrompt("算法工程师", "优化算法，提升代码性能", "")
        )),
        AssistantCategory("情感", listOf(
            AssistantPrompt("关系顾问", "提供人际关系建议", ""),
            AssistantPrompt("解梦师", "分析梦境，探索潜在含义", ""),
            AssistantPrompt("共情倾听者", "提供一个安全的倾诉空间", "")
        )),
        AssistantCategory("教育", listOf(
            AssistantPrompt("苏格拉底", "通过提问引导深入思考", ""),
            AssistantPrompt("知识讲解员", "清晰地解释复杂概念", ""),
            AssistantPrompt("学习规划师", "帮助制定有效的学习计划", "")
        )),
        AssistantCategory("创意", listOf(
            AssistantPrompt("头脑风暴伙伴", "激发创意，提供新颖想法", ""),
            AssistantPrompt("剧本创作者", "构思引人入胜的故事情节", ""),
            AssistantPrompt("广告策划人", "设计有吸引力的广告语", "")
        )),
        AssistantCategory("学术", listOf(
            AssistantPrompt("论文助手", "协助构建论文框架和润色", ""),
            AssistantPrompt("研究助理", "搜集和整理学术资料", ""),
            AssistantPrompt("辩论者", "从不同角度分析和辩论观点", "")
        )),
        AssistantCategory("设计", listOf(
            AssistantPrompt("UI/UX设计师", "提供界面和用户体验改进建议", ""),
            AssistantPrompt("产品设计师", "构思新产品的功能和形态", ""),
            AssistantPrompt("室内设计师", "提供家居布局和装饰建议", "")
        )),
        AssistantCategory("艺术", listOf(
            AssistantPrompt("诗人", "创作优美的诗歌", ""),
            AssistantPrompt("小说家", "撰写引人入胜的小说", ""),
            AssistantPrompt("艺术家", "提供艺术创作灵感", "")
        )),
        AssistantCategory("娱乐", listOf(
            AssistantPrompt("游戏设计师", "构思新颖的游戏玩法", ""),
            AssistantPrompt("电影评论家", "撰写深刻的影评", ""),
            AssistantPrompt("音乐制作人", "提供音乐创作的建议", "")
        )),
        AssistantCategory("生活", listOf(
            AssistantPrompt("健身教练", "制定个性化的健身计划", ""),
            AssistantPrompt("营养师", "提供科学的饮食搭配建议", ""),
            AssistantPrompt("旅行规划师", "设计完美的旅行路线", "")
        )),
        AssistantCategory("医疗", listOf(
            AssistantPrompt("健康顾问", "提供基础健康知识和建议", ""),
            AssistantPrompt("心理咨询师", "提供心理支持和疏导", ""),
            AssistantPrompt("用药提醒助手", "提醒按时服药", "")
        )),
        AssistantCategory("游戏", listOf(
            AssistantPrompt("游戏攻略大师", "提供游戏通关技巧和策略", ""),
            AssistantPrompt("角色扮演伙伴", "在文字角色扮演游戏中互动", ""),
            AssistantPrompt("游戏世界构建师", "设计游戏的世界观和背景故事", "")
        )),
        AssistantCategory("翻译", listOf(
            AssistantPrompt("多语言翻译官", "提供精准的跨语言翻译", ""),
            AssistantPrompt("本地化专家", "使语言和内容符合本地文化", ""),
            AssistantPrompt("术语翻译器", "专注于特定领域的专业术语翻译", "")
        )),
        AssistantCategory("音乐", listOf(
            AssistantPrompt("作词人", "创作动人的歌词", ""),
            AssistantPrompt("作曲家", "提供旋律创作的灵感", ""),
            AssistantPrompt("乐理老师", "解释音乐理论知识", "")
        )),
        AssistantCategory("点评", listOf(
            AssistantPrompt("美食评论家", "撰写餐厅和菜品点评", ""),
            AssistantPrompt("产品体验官", "提供产品使用的详细评测", ""),
            AssistantPrompt("书籍评论家", "分享深刻的读书心得", "")
        )),
        AssistantCategory("文案", listOf(
            AssistantPrompt("社交媒体经理", "撰写吸引人的社交媒体帖子", ""),
            AssistantPrompt("营销文案写手", "创作有说服力的营销文案", ""),
            AssistantPrompt("技术文档工程师", "编写清晰易懂的技术文档", "")
        )),
        AssistantCategory("百科", listOf(
            AssistantPrompt("历史学家", "解答各种历史问题", ""),
            AssistantPrompt("科学家", "解释科学原理和现象", ""),
            AssistantPrompt("百科全书", "提供关于任何主题的知识", "")
        )),
        AssistantCategory("健康", listOf(
            AssistantPrompt("冥想引导师", "引导进行冥想和放松", ""),
            AssistantPrompt("睡眠助手", "提供改善睡眠质量的建议", ""),
            AssistantPrompt("瑜伽教练", "提供瑜伽体式和练习指导", "")
        )),
        AssistantCategory("营销", listOf(
            AssistantPrompt("市场分析师", "分析市场趋势和消费者行为", ""),
            AssistantPrompt("SEO专家", "提供搜索引擎优化建议", ""),
            AssistantPrompt("品牌策略师", "帮助建立和发展品牌形象", "")
        )),
        AssistantCategory("科学", listOf(
            AssistantPrompt("天文学家", "解答关于宇宙和天体的问题", ""),
            AssistantPrompt("生物学家", "解释生命科学领域的知识", ""),
            AssistantPrompt("物理学家", "阐述物理定律和理论", "")
        )),
        AssistantCategory("分析", listOf(
            AssistantPrompt("数据分析师", "从数据中提取洞见", ""),
            AssistantPrompt("财务分析师", "解读财务报告和市场数据", ""),
            AssistantPrompt("逻辑学家", "分析和评估论证的有效性", "")
        )),
        AssistantCategory("法律", listOf(
            AssistantPrompt("法律顾问", "提供基础的法律信息和建议", ""),
            AssistantPrompt("合同审查员", "帮助审查合同条款", ""),
            AssistantPrompt("法律科普作家", "用通俗的语言解释法律知识", "")
        )),
        AssistantCategory("咨询", listOf(
            AssistantPrompt("职业发展顾问", "提供职业规划和发展的建议", ""),
            AssistantPrompt("创业导师", "为初创企业提供指导和支持", ""),
            AssistantPrompt("管理顾问", "提供企业管理和运营的解决方案", "")
        ))
    )
} 