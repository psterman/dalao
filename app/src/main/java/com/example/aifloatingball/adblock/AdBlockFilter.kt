package com.example.aifloatingball.adblock

import android.content.Context
import android.util.Log
import java.util.regex.Pattern

/**
 * AdBlock过滤器，用于拦截广告和跟踪器
 */
class AdBlockFilter(private val context: Context) {
    
    companion object {
        private const val TAG = "AdBlockFilter"
        
        // 常见广告域名模式
        private val AD_DOMAINS = setOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "googletagmanager.com",
            "google-analytics.com",
            "facebook.com/tr",
            "scorecardresearch.com",
            "quantserve.com",
            "outbrain.com",
            "taboola.com",
            "amazon-adsystem.com",
            "adsystem.amazon.com",
            "media.net",
            "adsense.google.com",
            "pagead2.googlesyndication.com",
            "tpc.googlesyndication.com",
            "partner.googleadservices.com",
            "googletagservices.com",
            "2mdn.net",
            "adsystem.amazon.co.uk",
            "adsystem.amazon.de",
            "adsystem.amazon.fr",
            "adsystem.amazon.it",
            "adsystem.amazon.es",
            "adsystem.amazon.ca",
            "adsystem.amazon.com.au",
            "adsystem.amazon.co.jp",
            "adsystem.amazon.in",
            "adsystem.amazon.com.br",
            "adsystem.amazon.com.mx",
            "facebook.com/plugins",
            "connect.facebook.net",
            "platform.twitter.com",
            "syndication.twitter.com",
            "analytics.twitter.com",
            "static.ads-twitter.com",
            "ads-api.twitter.com",
            "ads.yahoo.com",
            "gemini.yahoo.com",
            "analytics.yahoo.com",
            "flurry.com",
            "ads.linkedin.com",
            "analytics.pointdrive.linkedin.com",
            "px.ads.linkedin.com",
            "ads.pinterest.com",
            "analytics.pinterest.com",
            "log.pinterest.com",
            "ads.reddit.com",
            "rereddit.com",
            "redditstatic.com/ads",
            "ads.tiktok.com",
            "analytics.tiktok.com",
            "ads.snapchat.com",
            "tr.snapchat.com",
            "ads.youtube.com",
            "ads.instagram.com",
            "ads.whatsapp.com"
        )
        
        // 广告URL路径模式
        private val AD_PATHS = setOf(
            "/ads/",
            "/ad/",
            "/advertisement/",
            "/advertising/",
            "/adsense/",
            "/adnxs/",
            "/doubleclick/",
            "/googleads/",
            "/googlesyndication/",
            "/analytics/",
            "/tracking/",
            "/tracker/",
            "/metrics/",
            "/beacon/",
            "/pixel/",
            "/impression/",
            "/click/",
            "/conversion/",
            "/affiliate/",
            "/banner/",
            "/popup/",
            "/popunder/",
            "/interstitial/"
        )
        
        // 广告文件扩展名
        private val AD_EXTENSIONS = setOf(
            ".ads.js",
            ".analytics.js",
            ".tracking.js",
            ".tracker.js",
            ".metrics.js",
            ".beacon.js",
            ".pixel.js",
            ".advertisement.js",
            ".doubleclick.js",
            ".googleads.js",
            ".googlesyndication.js"
        )
        
        // 广告查询参数
        private val AD_PARAMS = setOf(
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_content",
            "utm_term",
            "gclid",
            "fbclid",
            "msclkid",
            "twclid",
            "li_fat_id",
            "_ga",
            "_gid",
            "_gat",
            "_gtm",
            "dclid",
            "zanpid",
            "affiliate_id",
            "ref_id",
            "tracking_id",
            "campaign_id",
            "ad_id",
            "banner_id"
        )
    }
    
    private var isEnabled = true
    private val customBlockList = mutableSetOf<String>()
    private val whiteList = mutableSetOf<String>()
    
    /**
     * 检查URL是否应该被拦截
     */
    fun shouldBlock(url: String): Boolean {
        if (!isEnabled) return false
        
        try {
            val lowerUrl = url.lowercase()
            
            // 检查白名单
            if (isWhitelisted(lowerUrl)) {
                return false
            }
            
            // 检查自定义拦截列表
            if (isInCustomBlockList(lowerUrl)) {
                Log.d(TAG, "Blocked by custom list: $url")
                return true
            }
            
            // 检查广告域名
            if (containsAdDomain(lowerUrl)) {
                Log.d(TAG, "Blocked ad domain: $url")
                return true
            }
            
            // 检查广告路径
            if (containsAdPath(lowerUrl)) {
                Log.d(TAG, "Blocked ad path: $url")
                return true
            }
            
            // 检查广告文件扩展名
            if (containsAdExtension(lowerUrl)) {
                Log.d(TAG, "Blocked ad extension: $url")
                return true
            }
            
            // 检查广告查询参数
            if (containsAdParams(lowerUrl)) {
                Log.d(TAG, "Blocked ad params: $url")
                return true
            }
            
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL: $url", e)
            return false
        }
    }
    
    private fun isWhitelisted(url: String): Boolean {
        return whiteList.any { url.contains(it) }
    }
    
    private fun isInCustomBlockList(url: String): Boolean {
        return customBlockList.any { url.contains(it) }
    }
    
    private fun containsAdDomain(url: String): Boolean {
        return AD_DOMAINS.any { url.contains(it) }
    }
    
    private fun containsAdPath(url: String): Boolean {
        return AD_PATHS.any { url.contains(it) }
    }
    
    private fun containsAdExtension(url: String): Boolean {
        return AD_EXTENSIONS.any { url.endsWith(it) }
    }
    
    private fun containsAdParams(url: String): Boolean {
        if (!url.contains("?")) return false
        
        val queryString = url.substringAfter("?")
        return AD_PARAMS.any { param ->
            queryString.contains("$param=") || queryString.contains("&$param=")
        }
    }
    
    /**
     * 启用/禁用AdBlock
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "AdBlock ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * 添加自定义拦截规则
     */
    fun addCustomBlockRule(rule: String) {
        customBlockList.add(rule.lowercase())
        Log.d(TAG, "Added custom block rule: $rule")
    }
    
    /**
     * 移除自定义拦截规则
     */
    fun removeCustomBlockRule(rule: String) {
        customBlockList.remove(rule.lowercase())
        Log.d(TAG, "Removed custom block rule: $rule")
    }
    
    /**
     * 添加白名单
     */
    fun addWhitelistRule(rule: String) {
        whiteList.add(rule.lowercase())
        Log.d(TAG, "Added whitelist rule: $rule")
    }
    
    /**
     * 移除白名单
     */
    fun removeWhitelistRule(rule: String) {
        whiteList.remove(rule.lowercase())
        Log.d(TAG, "Removed whitelist rule: $rule")
    }
    
    /**
     * 获取拦截统计
     */
    fun getBlockedCount(): Int {
        // 这里可以添加统计逻辑
        return 0
    }
    
    /**
     * 清除所有自定义规则
     */
    fun clearCustomRules() {
        customBlockList.clear()
        whiteList.clear()
        Log.d(TAG, "Cleared all custom rules")
    }
}
