package com.example.aifloatingball.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import androidx.preference.Preference
import com.example.aifloatingball.R
import com.example.aifloatingball.utils.IconLoader

class SearchEngineListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {
    private val iconLoader: IconLoader by lazy { IconLoader(context) }
    private val engineIcons = mapOf(
        "baidu" to R.drawable.ic_baidu,
        "google" to R.drawable.ic_google,
        "bing" to R.drawable.ic_bing,
        "duckduckgo" to R.drawable.ic_duckduckgo,
        "360" to R.drawable.ic_360,
        "sogou" to R.drawable.ic_sogou,
        "zhihu" to R.drawable.ic_zhihu,
        "weibo" to R.drawable.ic_weibo,
        "douban" to R.drawable.ic_douban,
        "taobao" to R.drawable.ic_taobao,
        "jd" to R.drawable.ic_jd,
        "douyin" to R.drawable.ic_douyin,
        "xiaohongshu" to R.drawable.ic_xiaohongshu,
        "wechat" to R.drawable.ic_wechat,
        "qq" to R.drawable.ic_qq,
        "bilibili" to R.drawable.ic_bilibili
    )

    private val engineUrls = mapOf(
        "baidu" to "https://www.baidu.com",
        "google" to "https://www.google.com",
        "bing" to "https://www.bing.com",
        "duckduckgo" to "https://duckduckgo.com",
        "360" to "https://www.so.com",
        "sogou" to "https://www.sogou.com",
        "zhihu" to "https://www.zhihu.com",
        "weibo" to "https://weibo.com",
        "douban" to "https://www.douban.com",
        "taobao" to "https://www.taobao.com",
        "jd" to "https://www.jd.com",
        "douyin" to "https://www.douyin.com",
        "xiaohongshu" to "https://www.xiaohongshu.com",
        "wechat" to "https://weixin.qq.com",
        "qq" to "https://www.qq.com",
        "bilibili" to "https://www.bilibili.com"
    )

    init {
        entries = context.resources.getStringArray(R.array.search_engines)
        entryValues = context.resources.getStringArray(R.array.search_engine_values)
        setDefaultValue("baidu")
        
        // Set the summary to show the currently selected search engine
        summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
            preference.entry
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        val iconView = holder.findViewById(android.R.id.icon) as? ImageView
        iconView?.visibility = View.VISIBLE
        
        val currentValue = value
        val defaultIcon = R.drawable.ic_search
        
        // 获取当前选中的搜索引擎图标
        val iconResId = engineIcons[currentValue] ?: defaultIcon
        iconView?.setImageResource(iconResId)
        
        // 如果有对应的URL，尝试加载网站图标
        engineUrls[currentValue]?.let { url ->
            iconLoader.loadIcon(url, iconView!!, iconResId)
        }
    }

    companion object {
        fun getSearchEngineUrl(context: Context, value: String): String {
            val values = context.resources.getStringArray(R.array.search_engine_values)
            val urls = context.resources.getStringArray(R.array.search_engine_urls)
            val index = values.indexOf(value)
            return if (index >= 0) urls[index] else "https://www.baidu.com"
        }
    }
} 