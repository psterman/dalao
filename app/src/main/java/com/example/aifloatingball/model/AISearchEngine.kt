package com.example.aifloatingball.model

import android.os.Parcel
import android.os.Parcelable
import com.example.aifloatingball.R
import android.util.Log

data class AISearchEngine(
    override val name: String,
    override val url: String,
    override val iconResId: Int = 0,
    override val description: String = "",
    override val searchUrl: String = url,
    val isChatMode: Boolean = false,
    var isEnabled: Boolean = false
) : BaseSearchEngine, Parcelable {
    override val displayName: String
        get() = name

    override fun getSearchUrl(query: String): String {
        return searchUrl.replace("{query}", query)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
        parcel.writeInt(iconResId)
        parcel.writeString(description)
        parcel.writeString(searchUrl)
        parcel.writeBoolean(isChatMode)
        parcel.writeBoolean(isEnabled)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val DEFAULT_AI_ENGINES = listOf(
            AISearchEngine(
                name = "ChatGPT",
                url = "https://chat.openai.com",
                iconResId = R.drawable.ic_web_default,
                description = "OpenAI ChatGPT",
                searchUrl = "https://chat.openai.com/search?q={query}"
            ),
            AISearchEngine(
                name = "Claude",
                url = "https://claude.ai",
                iconResId = R.drawable.ic_web_default,
                description = "Anthropic Claude",
                searchUrl = "https://claude.ai/search?q={query}"
            ),
            AISearchEngine(
                name = "Gemini",
                url = "https://gemini.google.com",
                iconResId = R.drawable.ic_web_default,
                description = "Google Gemini",
                searchUrl = "https://gemini.google.com/search?q={query}"
            ),
            AISearchEngine(
                name = "文心一言",
                url = "https://yiyan.baidu.com",
                iconResId = R.drawable.ic_web_default,
                description = "百度文心一言",
                searchUrl = "https://yiyan.baidu.com/search?query={query}"
            ),
            AISearchEngine(
                name = "智谱清言",
                url = "https://chatglm.cn",
                iconResId = R.drawable.ic_web_default,
                description = "智谱AI清言",
                searchUrl = "https://chatglm.cn/main/explore?query={query}"
            ),
            AISearchEngine(
                name = "通义千问",
                url = "https://qianwen.aliyun.com",
                iconResId = R.drawable.ic_web_default,
                description = "阿里通义千问",
                searchUrl = "https://qianwen.aliyun.com/search?query={query}"
            ),
            AISearchEngine(
                name = "讯飞星火",
                url = "https://xinghuo.xfyun.cn",
                iconResId = R.drawable.ic_web_default,
                description = "讯飞星火认知大模型",
                searchUrl = "https://xinghuo.xfyun.cn/desk?q={query}"
            ),
            AISearchEngine(
                name = "Perplexity",
                url = "https://perplexity.ai",
                iconResId = R.drawable.ic_web_default,
                description = "Perplexity AI",
                searchUrl = "https://perplexity.ai/search?q={query}"
            ),
            AISearchEngine(
                name = "Phind",
                url = "https://phind.com",
                iconResId = R.drawable.ic_web_default,
                description = "Phind - AI搜索引擎",
                searchUrl = "https://phind.com/search?q={query}"
            ),
            AISearchEngine(
                name = "Poe",
                url = "https://poe.com",
                iconResId = R.drawable.ic_web_default,
                description = "Poe - 多模型聊天平台",
                searchUrl = "https://poe.com/search?q={query}"
            ),
            AISearchEngine(
                name = "天工AI",
                url = "https://www.tiangong.cn",
                iconResId = R.drawable.ic_web_default, // Placeholder icon
                description = "昆仑万维 天工AI搜索",
                searchUrl = "https://www.tiangong.cn/search?q={query}"
            ),
            AISearchEngine(
                name = "秘塔AI搜索",
                url = "https://metaso.cn",
                iconResId = R.drawable.ic_web_default, // Placeholder icon
                description = "秘塔科技 AI搜索",
                searchUrl = "https://metaso.cn/?q={query}"
            ),
            AISearchEngine(
                name = "夸克AI",
                url = "https://www.quark.cn",
                iconResId = R.drawable.ic_web_default,
                description = "夸克AI搜索",
                searchUrl = "https://www.quark.cn/s?q={query}"
            ),
            AISearchEngine(
                name = "360AI搜索",
                url = "https://sou.ai.360.cn",
                iconResId = R.drawable.ic_web_default,
                description = "360AI搜索",
                searchUrl = "https://sou.ai.360.cn/?q={query}"
            ),
            AISearchEngine(
                name = "百度AI",
                url = "https://www.baidu.com",
                iconResId = R.drawable.ic_web_default,
                description = "百度AI搜索",
                searchUrl = "https://www.baidu.com/s?wd={query}"
            ),
            AISearchEngine(
                name = "You.com",
                url = "https://you.com",
                iconResId = R.drawable.ic_web_default, // Placeholder icon
                description = "You.com AI Search",
                searchUrl = "https://you.com/search?q={query}"
            ),
            AISearchEngine(
                name = "Brave Search",
                url = "https://search.brave.com",
                iconResId = R.drawable.ic_web_default,
                description = "Brave AI Search",
                searchUrl = "https://search.brave.com/search?q={query}"
            ),
            AISearchEngine(
                name = "WolframAlpha",
                url = "https://www.wolframalpha.com",
                iconResId = R.drawable.ic_web_default,
                description = "WolframAlpha 计算知识引擎",
                searchUrl = "https://www.wolframalpha.com/input?i={query}"
            ),
            // API 对话模式
            AISearchEngine(
                name = "ChatGPT (API)",
                url = "chat://chatgpt",
                iconResId = R.drawable.ic_chatgpt,
                description = "使用API进行ChatGPT对话",
                searchUrl = "chat://chatgpt",
                isChatMode = true
            ),
            AISearchEngine(
                name = "DeepSeek (API)",
                url = "chat://deepseek",
                iconResId = R.drawable.ic_deepseek,
                description = "使用API进行DeepSeek对话",
                searchUrl = "chat://deepseek",
                isChatMode = true
            ),
            // New Engines Added
            AISearchEngine(
                name = "Kimi",
                url = "https://kimi.moonshot.cn/",
                iconResId = R.drawable.ic_web_default,
                description = "Moonshot AI Kimi",
                searchUrl = "https://kimi.moonshot.cn/"
            ),
            AISearchEngine(
                name = "DeepSeek (Web)",
                url = "https://chat.deepseek.com/",
                iconResId = R.drawable.ic_web_default,
                description = "DeepSeek大模型网页版",
                searchUrl = "https://chat.deepseek.com/"
            ),
            AISearchEngine(
                name = "万知",
                url = "https://www.wanzhi.com/",
                iconResId = R.drawable.ic_web_default,
                description = "万知AI",
                searchUrl = "https://www.wanzhi.com/search?q={query}"
            ),
            AISearchEngine(
                name = "百小应",
                url = "https://ying.baidu.com/",
                iconResId = R.drawable.ic_web_default,
                description = "百度出品，AI伙伴",
                searchUrl = "https://ying.baidu.com/"
            ),
            AISearchEngine(
                name = "跃问",
                url = "https://www.stepfun.com/yewen",
                iconResId = R.drawable.ic_web_default,
                description = "阶跃星辰 跃问",
                searchUrl = "https://www.stepfun.com/yewen"
            ),
            AISearchEngine(
                name = "豆包",
                url = "https://www.doubao.com/",
                iconResId = R.drawable.ic_web_default,
                description = "字节跳动 豆包",
                searchUrl = "https://www.doubao.com/chat/"
            ),
            AISearchEngine(
                name = "Cici",
                url = "https://ciciai.com/",
                iconResId = R.drawable.ic_web_default,
                description = "Cici AI",
                searchUrl = "https://ciciai.com/"
            ),
            AISearchEngine(
                name = "海螺",
                url = "https://hailuoyun.com.cn/",
                iconResId = R.drawable.ic_web_default,
                description = "OPPO AI 海螺",
                searchUrl = "https://hailuoyun.com.cn/"
            ),
            AISearchEngine(
                name = "Groq",
                url = "https://groq.com/",
                iconResId = R.drawable.ic_web_default,
                description = "Groq LPU 推理引擎",
                searchUrl = "https://groq.com/"
            ),
            AISearchEngine(
                name = "腾讯元宝",
                url = "https://yuanbao.tencent.com/",
                iconResId = R.drawable.ic_web_default,
                description = "腾讯元宝",
                searchUrl = "https://yuanbao.tencent.com/"
            ),
            AISearchEngine(
                name = "商量",
                url = "https://shangliang.sensetime.com/",
                iconResId = R.drawable.ic_web_default,
                description = "商汤商量",
                searchUrl = "https://shangliang.sensetime.com/"
            ),
            AISearchEngine(
                name = "DEVV",
                url = "https://devv.ai/",
                iconResId = R.drawable.ic_web_default,
                description = "为开发者打造的AI搜索引擎",
                searchUrl = "https://devv.ai/search?q={query}"
            ),
            AISearchEngine(
                name = "HuggingChat",
                url = "https://huggingface.co/chat/",
                iconResId = R.drawable.ic_web_default,
                description = "Hugging Face Chat",
                searchUrl = "https://huggingface.co/chat/"
            ),
            AISearchEngine(
                name = "纳米AI搜索",
                url = "https://www.nainami.com/",
                iconResId = R.drawable.ic_web_default,
                description = "纳米AI搜索",
                searchUrl = "https://www.nainami.com/search?text={query}"
            ),
            AISearchEngine(
                name = "ThinkAny",
                url = "https://www.thinkany.ai/",
                iconResId = R.drawable.ic_web_default,
                description = "ThinkAny AI Search",
                searchUrl = "https://www.thinkany.ai/?q={query}"
            ),
            AISearchEngine(
                name = "Hika",
                url = "https://web.hika.app/",
                iconResId = R.drawable.ic_web_default,
                description = "Hika AI",
                searchUrl = "https://web.hika.app/"
            ),
            AISearchEngine(
                name = "Genspark",
                url = "https://www.genspark.com/",
                iconResId = R.drawable.ic_web_default,
                description = "Genspark AI Search",
                searchUrl = "https://www.genspark.com/search?q={query}"
            ),
            AISearchEngine(
                name = "Grok",
                url = "https://grok.x.ai/",
                iconResId = R.drawable.ic_web_default,
                description = "xAI Grok",
                searchUrl = "https://grok.x.ai/"
            ),
            AISearchEngine(
                name = "Flowith",
                url = "https://flowith.me/",
                iconResId = R.drawable.ic_web_default,
                description = "Flowith AI智能体",
                searchUrl = "https://flowith.me/"
            ),
            AISearchEngine(
                name = "NotebookLM",
                url = "https://notebooklm.google.com/",
                iconResId = R.drawable.ic_web_default,
                description = "Google NotebookLM",
                searchUrl = "https://notebooklm.google.com/"
            ),
            AISearchEngine(
                name = "Coze",
                url = "https://www.coze.com/",
                iconResId = R.drawable.ic_web_default,
                description = "Coze AI 聊天机器人平台",
                searchUrl = "https://www.coze.com/"
            ),
            AISearchEngine(
                name = "Dify",
                url = "https://dify.ai/",
                iconResId = R.drawable.ic_web_default,
                description = "Dify LLM应用开发平台",
                searchUrl = "https://dify.ai/"
            ),
            AISearchEngine(
                name = "WPS灵感",
                url = "https://ai.wps.cn/",
                iconResId = R.drawable.ic_web_default,
                description = "WPS AI 灵感",
                searchUrl = "https://ai.wps.cn/"
            ),
            AISearchEngine(
                name = "LeChat",
                url = "https://lechat.minmax.com.cn/",
                iconResId = R.drawable.ic_web_default,
                description = "海螺问问(LeChat)",
                searchUrl = "https://lechat.minmax.com.cn/"
            ),
            AISearchEngine(
                name = "Monica",
                url = "https://monica.im/",
                iconResId = R.drawable.ic_web_default,
                description = "Monica - AI Copilot",
                searchUrl = "https://monica.im/"
            ),
            AISearchEngine(
                name = "知乎",
                url = "https://www.zhihu.com/",
                iconResId = R.drawable.ic_web_default,
                description = "在知乎中搜索",
                searchUrl = "https://www.zhihu.com/search?type=content&q={query}"
            )
        )

        @JvmField
        val CREATOR = object : Parcelable.Creator<AISearchEngine> {
            override fun createFromParcel(parcel: Parcel): AISearchEngine {
                return AISearchEngine(
                    name = parcel.readString()!!,
                    url = parcel.readString()!!,
                    iconResId = parcel.readInt(),
                    description = parcel.readString()!!,
                    searchUrl = parcel.readString()!!,
                    isChatMode = parcel.readBoolean(),
                    isEnabled = parcel.readBoolean()
                )
            }

            override fun newArray(size: Int): Array<AISearchEngine?> {
                return arrayOfNulls(size)
            }
        }
    }
}