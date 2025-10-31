package com.example.aifloatingball.manager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.aifloatingball.R

/**
 * URL Scheme处理管理器
 * 处理各种URL scheme链接，提供跳转提示、下载提示和转换方案
 */
class UrlSchemeHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "UrlSchemeHandler"
        
        // 已知的URL scheme映射
        private val KNOWN_SCHEMES = mapOf(
            "xhsdiscover" to AppSchemeInfo(
                appName = "小红书",
                packageName = "com.xingin.xhs",
                downloadUrl = "https://www.xiaohongshu.com/download",
                webUrl = "https://www.xiaohongshu.com",
                description = "小红书 - 发现美好生活"
            ),
            "zhihu" to AppSchemeInfo(
                appName = "知乎",
                packageName = "com.zhihu.android",
                downloadUrl = "https://www.zhihu.com/download",
                webUrl = "https://www.zhihu.com",
                description = "知乎 - 有问题，就会有答案"
            ),
            "douyin" to AppSchemeInfo(
                appName = "抖音",
                packageName = "com.ss.android.ugc.aweme",
                downloadUrl = "https://www.douyin.com/download",
                webUrl = "https://www.douyin.com",
                description = "抖音 - 记录美好生活"
            ),
            "kuaishou" to AppSchemeInfo(
                appName = "快手",
                packageName = "com.smile.gifmaker",
                downloadUrl = "https://www.kuaishou.com/download",
                webUrl = "https://www.kuaishou.com",
                description = "快手 - 拥抱每一种生活"
            ),
            "weibo" to AppSchemeInfo(
                appName = "微博",
                packageName = "com.sina.weibo",
                downloadUrl = "https://weibo.com/download",
                webUrl = "https://weibo.com",
                description = "微博 - 随时随地发现新鲜事"
            ),
            "taobao" to AppSchemeInfo(
                appName = "淘宝",
                packageName = "com.taobao.taobao",
                downloadUrl = "https://www.taobao.com/download",
                webUrl = "https://www.taobao.com",
                description = "淘宝 - 淘！我喜欢"
            ),
            "tmall" to AppSchemeInfo(
                appName = "天猫",
                packageName = "com.tmall.wireless",
                downloadUrl = "https://www.tmall.com/download",
                webUrl = "https://www.tmall.com",
                description = "天猫 - 理想生活上天猫"
            ),
            "jd" to AppSchemeInfo(
                appName = "京东",
                packageName = "com.jingdong.app.mall",
                downloadUrl = "https://www.jd.com/download",
                webUrl = "https://www.jd.com",
                description = "京东 - 多快好省，只为品质生活"
            ),
            "pinduoduo" to AppSchemeInfo(
                appName = "拼多多",
                packageName = "com.xunmeng.pinduoduo",
                downloadUrl = "https://www.pinduoduo.com/download",
                webUrl = "https://www.pinduoduo.com",
                description = "拼多多 - 拼着买，更便宜"
            ),
            
            // 社交媒体类
            "wechat" to AppSchemeInfo(
                appName = "微信",
                packageName = "com.tencent.mm",
                downloadUrl = "https://weixin.qq.com/download",
                webUrl = "https://weixin.qq.com",
                description = "微信 - 连接你我"
            ),
            "qq" to AppSchemeInfo(
                appName = "QQ",
                packageName = "com.tencent.mobileqq",
                downloadUrl = "https://im.qq.com/download",
                webUrl = "https://im.qq.com",
                description = "QQ - 每一天，乐在沟通"
            ),
            "bilibili" to AppSchemeInfo(
                appName = "哔哩哔哩",
                packageName = "tv.danmaku.bili",
                downloadUrl = "https://www.bilibili.com/download",
                webUrl = "https://www.bilibili.com",
                description = "哔哩哔哩 - 哔哩哔哩 (゜-゜)つロ 干杯~"
            ),
            "xiaohongshu" to AppSchemeInfo(
                appName = "小红书",
                packageName = "com.xingin.xhs",
                downloadUrl = "https://www.xiaohongshu.com/download",
                webUrl = "https://www.xiaohongshu.com",
                description = "小红书 - 发现美好生活"
            ),
            
            // 音乐类
            "netease" to AppSchemeInfo(
                appName = "网易云音乐",
                packageName = "com.netease.cloudmusic",
                downloadUrl = "https://music.163.com/download",
                webUrl = "https://music.163.com",
                description = "网易云音乐 - 音乐的力量"
            ),
            "qqmusic" to AppSchemeInfo(
                appName = "QQ音乐",
                packageName = "com.tencent.qqmusic",
                downloadUrl = "https://y.qq.com/download",
                webUrl = "https://y.qq.com",
                description = "QQ音乐 - 听我想听的歌"
            ),
            "kugou" to AppSchemeInfo(
                appName = "酷狗音乐",
                packageName = "com.kugou.android",
                downloadUrl = "https://www.kugou.com/download",
                webUrl = "https://www.kugou.com",
                description = "酷狗音乐 - 就是歌多"
            ),
            "kuwo" to AppSchemeInfo(
                appName = "酷我音乐",
                packageName = "cn.kuwo.player",
                downloadUrl = "https://www.kuwo.cn/download",
                webUrl = "https://www.kuwo.cn",
                description = "酷我音乐 - 好音质，用酷我"
            ),
            
            // 视频类
            "iqiyi" to AppSchemeInfo(
                appName = "爱奇艺",
                packageName = "com.qiyi.video",
                downloadUrl = "https://www.iqiyi.com/download",
                webUrl = "https://www.iqiyi.com",
                description = "爱奇艺 - 悦享品质"
            ),
            "tencentvideo" to AppSchemeInfo(
                appName = "腾讯视频",
                packageName = "com.tencent.qqlive",
                downloadUrl = "https://v.qq.com/download",
                webUrl = "https://v.qq.com",
                description = "腾讯视频 - 不负好时光"
            ),
            "youku" to AppSchemeInfo(
                appName = "优酷",
                packageName = "com.youku.phone",
                downloadUrl = "https://www.youku.com/download",
                webUrl = "https://www.youku.com",
                description = "优酷 - 这很优酷"
            ),
            "mgtv" to AppSchemeInfo(
                appName = "芒果TV",
                packageName = "com.hunantv.imgo.activity",
                downloadUrl = "https://www.mgtv.com/download",
                webUrl = "https://www.mgtv.com",
                description = "芒果TV - 看见好时光"
            ),
            
            // 生活服务类
            "meituan" to AppSchemeInfo(
                appName = "美团",
                packageName = "com.sankuai.meituan",
                downloadUrl = "https://www.meituan.com/download",
                webUrl = "https://www.meituan.com",
                description = "美团 - 吃喝玩乐全都有"
            ),
            "eleme" to AppSchemeInfo(
                appName = "饿了么",
                packageName = "me.ele",
                downloadUrl = "https://www.ele.me/download",
                webUrl = "https://www.ele.me",
                description = "饿了么 - 饿了别叫妈，叫饿了么"
            ),
            "dianping" to AppSchemeInfo(
                appName = "大众点评",
                packageName = "com.dianping.v1",
                downloadUrl = "https://www.dianping.com/download",
                webUrl = "https://www.dianping.com",
                description = "大众点评 - 发现品质生活"
            ),
            "ctrip" to AppSchemeInfo(
                appName = "携程",
                packageName = "ctrip.android.view",
                downloadUrl = "https://www.ctrip.com/download",
                webUrl = "https://www.ctrip.com",
                description = "携程 - 说走就走"
            ),
            "qunar" to AppSchemeInfo(
                appName = "去哪儿",
                packageName = "com.Qunar",
                downloadUrl = "https://www.qunar.com/download",
                webUrl = "https://www.qunar.com",
                description = "去哪儿 - 聪明你的旅行"
            ),
            
            // 地图导航类
            "amap" to AppSchemeInfo(
                appName = "高德地图",
                packageName = "com.autonavi.minimap",
                downloadUrl = "https://www.amap.com/download",
                webUrl = "https://www.amap.com",
                description = "高德地图 - 专业地图导航"
            ),
            "baidumap" to AppSchemeInfo(
                appName = "百度地图",
                packageName = "com.baidu.BaiduMap",
                downloadUrl = "https://map.baidu.com/download",
                webUrl = "https://map.baidu.com",
                description = "百度地图 - 更懂你的地图"
            ),
            "tencentmap" to AppSchemeInfo(
                appName = "腾讯地图",
                packageName = "com.tencent.map",
                downloadUrl = "https://map.qq.com/download",
                webUrl = "https://map.qq.com",
                description = "腾讯地图 - 精准定位，畅行无阻"
            ),
            
            // 支付金融类
            "alipay" to AppSchemeInfo(
                appName = "支付宝",
                packageName = "com.eg.android.AlipayGphone",
                downloadUrl = "https://www.alipay.com/download",
                webUrl = "https://www.alipay.com",
                description = "支付宝 - 生活好，支付宝"
            ),
            "unionpay" to AppSchemeInfo(
                appName = "云闪付",
                packageName = "com.unionpay",
                downloadUrl = "https://www.95516.com/download",
                webUrl = "https://www.95516.com",
                description = "云闪付 - 银联官方APP"
            ),
            "icbc" to AppSchemeInfo(
                appName = "工商银行",
                packageName = "com.icbc",
                downloadUrl = "https://www.icbc.com.cn/download",
                webUrl = "https://www.icbc.com.cn",
                description = "工商银行 - 工银融e联"
            ),
            "ccb" to AppSchemeInfo(
                appName = "建设银行",
                packageName = "com.ccb",
                downloadUrl = "https://www.ccb.com/download",
                webUrl = "https://www.ccb.com",
                description = "建设银行 - 建行手机银行"
            ),
            
            // 新闻资讯类
            "toutiao" to AppSchemeInfo(
                appName = "今日头条",
                packageName = "com.ss.android.article.news",
                downloadUrl = "https://www.toutiao.com/download",
                webUrl = "https://www.toutiao.com",
                description = "今日头条 - 你关心的，才是头条"
            ),
            "tencentnews" to AppSchemeInfo(
                appName = "腾讯新闻",
                packageName = "com.tencent.news",
                downloadUrl = "https://news.qq.com/download",
                webUrl = "https://news.qq.com",
                description = "腾讯新闻 - 事实派"
            ),
            "sina" to AppSchemeInfo(
                appName = "新浪新闻",
                packageName = "com.sina.news",
                downloadUrl = "https://news.sina.com.cn/download",
                webUrl = "https://news.sina.com.cn",
                description = "新浪新闻 - 新闻资讯"
            ),
            
            // 工具类
            "wps" to AppSchemeInfo(
                appName = "WPS Office",
                packageName = "cn.wps.moffice_eng",
                downloadUrl = "https://www.wps.com/download",
                webUrl = "https://www.wps.com",
                description = "WPS Office - 办公软件"
            ),
            "baidu" to AppSchemeInfo(
                appName = "百度",
                packageName = "com.baidu.searchbox",
                downloadUrl = "https://www.baidu.com/download",
                webUrl = "https://www.baidu.com",
                description = "百度 - 全球最大的中文搜索引擎"
            ),
            "sogou" to AppSchemeInfo(
                appName = "搜狗",
                packageName = "com.sogou.androidtool",
                downloadUrl = "https://www.sogou.com/download",
                webUrl = "https://www.sogou.com",
                description = "搜狗 - 搜狗搜索"
            ),
            "360" to AppSchemeInfo(
                appName = "360",
                packageName = "com.qihoo360.mobilesafe",
                downloadUrl = "https://www.360.cn/download",
                webUrl = "https://www.360.cn",
                description = "360 - 360安全卫士"
            ),
            
            // 游戏类
            "wegame" to AppSchemeInfo(
                appName = "WeGame",
                packageName = "com.tencent.wegame",
                downloadUrl = "https://www.wegame.com.cn/download",
                webUrl = "https://www.wegame.com.cn",
                description = "WeGame - 腾讯游戏平台"
            ),
            "tap" to AppSchemeInfo(
                appName = "TapTap",
                packageName = "com.taptap",
                downloadUrl = "https://www.taptap.com/download",
                webUrl = "https://www.taptap.com",
                description = "TapTap - 发现好游戏"
            ),
            
            // 教育类
            "xuetangx" to AppSchemeInfo(
                appName = "学堂在线",
                packageName = "com.xuetangx.mobile",
                downloadUrl = "https://www.xuetangx.com/download",
                webUrl = "https://www.xuetangx.com",
                description = "学堂在线 - 在线教育平台"
            ),
            "zhihuishu" to AppSchemeInfo(
                appName = "智慧树",
                packageName = "com.zhihuishu",
                downloadUrl = "https://www.zhihuishu.com/download",
                webUrl = "https://www.zhihuishu.com",
                description = "智慧树 - 在线教育"
            ),
            
            // 健康医疗类
            "pingan" to AppSchemeInfo(
                appName = "平安好医生",
                packageName = "com.pingan.gooddoctor",
                downloadUrl = "https://www.pingan.com/download",
                webUrl = "https://www.pingan.com",
                description = "平安好医生 - 在线医疗"
            ),
            "haodf" to AppSchemeInfo(
                appName = "好大夫在线",
                packageName = "com.haodf",
                downloadUrl = "https://www.haodf.com/download",
                webUrl = "https://www.haodf.com",
                description = "好大夫在线 - 在线医疗"
            ),
            
            // 汽车类
            "autohome" to AppSchemeInfo(
                appName = "汽车之家",
                packageName = "com.autohome.auto",
                downloadUrl = "https://www.autohome.com.cn/download",
                webUrl = "https://www.autohome.com.cn",
                description = "汽车之家 - 看车买车用车"
            ),
            "bitauto" to AppSchemeInfo(
                appName = "易车",
                packageName = "com.bitauto",
                downloadUrl = "https://www.bitauto.com/download",
                webUrl = "https://www.bitauto.com",
                description = "易车 - 汽车资讯"
            ),
            
            // 房产类
            "anjuke" to AppSchemeInfo(
                appName = "安居客",
                packageName = "com.anjuke.android.app",
                downloadUrl = "https://www.anjuke.com/download",
                webUrl = "https://www.anjuke.com",
                description = "安居客 - 房产信息"
            ),
            "lianjia" to AppSchemeInfo(
                appName = "链家",
                packageName = "com.lianjia.beike",
                downloadUrl = "https://www.lianjia.com/download",
                webUrl = "https://www.lianjia.com",
                description = "链家 - 房产服务"
            ),
            
            // 招聘类
            "boss" to AppSchemeInfo(
                appName = "BOSS直聘",
                packageName = "com.hpbr.bosszhipin",
                downloadUrl = "https://www.zhipin.com/download",
                webUrl = "https://www.zhipin.com",
                description = "BOSS直聘 - 找工作"
            ),
            "liepin" to AppSchemeInfo(
                appName = "猎聘",
                packageName = "com.liepin",
                downloadUrl = "https://www.liepin.com/download",
                webUrl = "https://www.liepin.com",
                description = "猎聘 - 高端人才招聘"
            ),
            "zhaopin" to AppSchemeInfo(
                appName = "智联招聘",
                packageName = "com.zhaopin",
                downloadUrl = "https://www.zhaopin.com/download",
                webUrl = "https://www.zhaopin.com",
                description = "智联招聘 - 求职招聘"
            ),
            
            // 运动健身类
            "keep" to AppSchemeInfo(
                appName = "Keep",
                packageName = "com.gotokeep.keep",
                downloadUrl = "https://www.gotokeep.com/download",
                webUrl = "https://www.gotokeep.com",
                description = "Keep - 运动健身"
            ),
            "mi" to AppSchemeInfo(
                appName = "小米运动",
                packageName = "com.xiaomi.hm.health",
                downloadUrl = "https://www.mi.com/download",
                webUrl = "https://www.mi.com",
                description = "小米运动 - 健康管理"
            ),
            
            // 天气类
            "weather" to AppSchemeInfo(
                appName = "天气",
                packageName = "com.tencent.weather",
                downloadUrl = "https://weather.qq.com/download",
                webUrl = "https://weather.qq.com",
                description = "天气 - 天气预报"
            ),
            "weathercn" to AppSchemeInfo(
                appName = "中国天气",
                packageName = "com.weathercn",
                downloadUrl = "https://www.weather.com.cn/download",
                webUrl = "https://www.weather.com.cn",
                description = "中国天气 - 天气预报"
            ),
            
            // 工具类
            "clash" to AppSchemeInfo(
                appName = "Clash",
                packageName = "com.github.kr328.clash",
                downloadUrl = "https://github.com/Kr328/ClashForAndroid/releases",
                webUrl = "https://github.com/Kr328/ClashForAndroid",
                description = "Clash - 网络代理工具"
            )
        )
    }
    
    data class AppSchemeInfo(
        val appName: String,
        val packageName: String,
        val downloadUrl: String,
        val webUrl: String,
        val description: String
    )
    
    /**
     * 处理URL scheme链接
     * @param url 原始URL
     * @param onSuccess 成功处理回调
     * @param onFailure 失败处理回调
     */
    fun handleUrlScheme(url: String, onSuccess: (() -> Unit)? = null, onFailure: (() -> Unit)? = null) {
        Log.d(TAG, "处理URL scheme: $url")
        
        try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme
            
            if (scheme == null) {
                Log.w(TAG, "无效的URL scheme: $url")
                onFailure?.invoke()
                return
            }
            
            val appInfo = KNOWN_SCHEMES[scheme]
            if (appInfo != null) {
                handleKnownScheme(url, appInfo, onSuccess, onFailure)
            } else {
                handleUnknownScheme(url, scheme, onSuccess, onFailure)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理URL scheme失败: $url", e)
            onFailure?.invoke()
        }
    }
    
    /**
     * 处理已知的URL scheme
     */
    private fun handleKnownScheme(url: String, appInfo: AppSchemeInfo, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        Log.d(TAG, "处理已知scheme: ${appInfo.appName}")
        
        // 检查应用是否已安装
        if (isAppInstalled(appInfo.packageName)) {
            // 应用已安装，尝试跳转
            tryJumpToApp(url, appInfo, onSuccess, onFailure)
        } else {
            // 应用未安装，显示下载提示
            showDownloadDialog(appInfo, onSuccess, onFailure)
        }
    }
    
    /**
     * 处理未知的URL scheme
     */
    private fun handleUnknownScheme(url: String, scheme: String, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        Log.d(TAG, "处理未知scheme: $scheme")
        
        // 尝试通用跳转
        tryGenericJump(url, onSuccess, onFailure)
    }
    
    /**
     * 尝试跳转到应用
     */
    private fun tryJumpToApp(url: String, appInfo: AppSchemeInfo, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 精准指定包名，避免落入其它同名 scheme 的应用
            try {
                intent.`package` = appInfo.packageName
            } catch (_: Exception) { }
            
            // 检查是否有应用可以处理这个intent
            val packageManager = context.packageManager
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (resolveInfo != null) {
                // 先显示确认对话框，而不是直接跳转
                showJumpConfirmDialog(url, appInfo, intent, onSuccess, onFailure)
            } else {
                // 没有应用可以处理，显示提示
                showAppNotFoundDialog(appInfo, onSuccess, onFailure)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "跳转到${appInfo.appName}失败", e)
            showAppNotFoundDialog(appInfo, onSuccess, onFailure)
        }
    }
    
    /**
     * 尝试通用跳转
     */
    private fun tryGenericJump(url: String, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val packageManager = context.packageManager
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (resolveInfo != null) {
                // 先显示确认对话框，而不是直接跳转
                showGenericJumpConfirmDialog(url, intent, onSuccess, onFailure)
            } else {
                showGenericNotFoundDialog(url, onSuccess, onFailure)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "通用跳转失败: $url", e)
            showGenericNotFoundDialog(url, onSuccess, onFailure)
        }
    }
    
    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 显示下载提示对话框
     */
    private fun showDownloadDialog(appInfo: AppSchemeInfo, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_url_scheme_handler, null)
        
        // 设置内容
        dialogView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_download)
        dialogView.findViewById<TextView>(R.id.dialog_title).text = "需要安装${appInfo.appName}"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = 
            "检测到${appInfo.appName}链接，但您的设备上未安装该应用。\n\n${appInfo.description}"
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 下载按钮
        dialogView.findViewById<Button>(R.id.btn_download).setOnClickListener {
            dialog.dismiss()
            openDownloadPage(appInfo.downloadUrl)
            onSuccess?.invoke()
        }
        
        // 网页版按钮
        dialogView.findViewById<Button>(R.id.btn_web).setOnClickListener {
            dialog.dismiss()
            openWebPage(appInfo.webUrl)
            onSuccess?.invoke()
        }
        
        // 取消按钮
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            onFailure?.invoke()
        }
        
        dialog.show()
    }
    
    /**
     * 显示跳转确认对话框（已知应用）
     */
    private fun showJumpConfirmDialog(
        url: String,
        appInfo: AppSchemeInfo,
        intent: Intent,
        onSuccess: (() -> Unit)?,
        onFailure: (() -> Unit)?
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_url_scheme_handler, null)
        
        // 设置内容
        dialogView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_warning)
        dialogView.findViewById<TextView>(R.id.dialog_title).text = "打开${appInfo.appName}"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = 
            "检测到${appInfo.appName}链接，是否要跳转到外部应用？\n\n链接: $url"
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 确认跳转按钮
        dialogView.findViewById<Button>(R.id.btn_download).apply {
            text = "打开应用"
            setOnClickListener {
                dialog.dismiss()
                try {
                    context.startActivity(intent)
                    onSuccess?.invoke()
                    Log.d(TAG, "成功跳转到${appInfo.appName}")
                } catch (e: Exception) {
                    Log.e(TAG, "跳转到${appInfo.appName}失败", e)
                    android.widget.Toast.makeText(context, "无法打开应用", android.widget.Toast.LENGTH_SHORT).show()
                    onFailure?.invoke()
                }
            }
        }
        
        // 网页版按钮
        dialogView.findViewById<Button>(R.id.btn_web).apply {
            text = "网页版"
            setOnClickListener {
                dialog.dismiss()
                openWebPage(appInfo.webUrl)
                onSuccess?.invoke()
            }
        }
        
        // 取消按钮
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            onFailure?.invoke()
        }
        
        dialog.show()
    }
    
    /**
     * 显示通用跳转确认对话框（未知应用）
     */
    private fun showGenericJumpConfirmDialog(
        url: String,
        intent: Intent,
        onSuccess: (() -> Unit)?,
        onFailure: (() -> Unit)?
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_url_scheme_handler, null)
        
        // 设置内容
        dialogView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_warning)
        
        // 尝试获取应用名称
        val packageManager = context.packageManager
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val appName = resolveInfo?.loadLabel(packageManager)?.toString() ?: "外部应用"
        
        dialogView.findViewById<TextView>(R.id.dialog_title).text = "打开外部应用"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = 
            "检测到特殊链接，是否要跳转到外部应用？\n\n应用: $appName\n链接: $url"
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 确认跳转按钮
        dialogView.findViewById<Button>(R.id.btn_download).apply {
            text = "打开应用"
            setOnClickListener {
                dialog.dismiss()
                try {
                    context.startActivity(intent)
                    onSuccess?.invoke()
                    Log.d(TAG, "通用跳转成功: $url")
                } catch (e: Exception) {
                    Log.e(TAG, "通用跳转失败: $url", e)
                    android.widget.Toast.makeText(context, "无法打开应用", android.widget.Toast.LENGTH_SHORT).show()
                    onFailure?.invoke()
                }
            }
        }
        
        // 隐藏网页版按钮（未知应用没有网页版）
        dialogView.findViewById<Button>(R.id.btn_web).visibility = View.GONE
        
        // 取消按钮
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            onFailure?.invoke()
        }
        
        dialog.show()
    }
    
    /**
     * 显示应用未找到对话框
     */
    private fun showAppNotFoundDialog(appInfo: AppSchemeInfo, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_url_scheme_handler, null)
        
        // 设置内容
        dialogView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_warning)
        dialogView.findViewById<TextView>(R.id.dialog_title).text = "无法打开${appInfo.appName}链接"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = 
            "检测到${appInfo.appName}链接，但无法在应用中打开。\n\n${appInfo.description}"
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 下载按钮
        dialogView.findViewById<Button>(R.id.btn_download).apply {
            text = "下载应用"
            setOnClickListener {
                dialog.dismiss()
                openDownloadPage(appInfo.downloadUrl)
                onSuccess?.invoke()
            }
        }
        
        // 网页版按钮
        dialogView.findViewById<Button>(R.id.btn_web).apply {
            text = "网页版"
            setOnClickListener {
                dialog.dismiss()
                openWebPage(appInfo.webUrl)
                onSuccess?.invoke()
            }
        }
        
        // 取消按钮
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            onFailure?.invoke()
        }
        
        dialog.show()
    }
    
    /**
     * 显示通用未找到对话框
     */
    private fun showGenericNotFoundDialog(url: String, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_url_scheme_handler, null)
        
        // 设置内容
        dialogView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_warning)
        dialogView.findViewById<TextView>(R.id.dialog_title).text = "无法打开链接"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = 
            "检测到特殊链接，但无法找到合适的应用来打开。\n\n链接: $url"
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 复制链接按钮
        dialogView.findViewById<Button>(R.id.btn_download).apply {
            text = "复制链接"
            setOnClickListener {
                dialog.dismiss()
                copyToClipboard(url)
                onSuccess?.invoke()
            }
        }
        
        // 隐藏网页版按钮
        dialogView.findViewById<Button>(R.id.btn_web).visibility = View.GONE
        
        // 取消按钮
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            onFailure?.invoke()
        }
        
        dialog.show()
    }
    
    /**
     * 打开下载页面
     */
    private fun openDownloadPage(downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "打开下载页面: $downloadUrl")
        } catch (e: Exception) {
            Log.e(TAG, "打开下载页面失败: $downloadUrl", e)
        }
    }
    
    /**
     * 打开网页版
     */
    private fun openWebPage(webUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "打开网页版: $webUrl")
        } catch (e: Exception) {
            Log.e(TAG, "打开网页版失败: $webUrl", e)
        }
    }
    
    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("URL", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "已复制到剪贴板: $text")
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败: $text", e)
        }
    }
}
