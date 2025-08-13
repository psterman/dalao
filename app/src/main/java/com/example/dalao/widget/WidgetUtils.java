package com.example.dalao.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;

/**
 * 小组件工具类
 */
public class WidgetUtils {
    
    private static final String TAG = "WidgetUtils";
    private static final String PREFS_NAME = "widget_config";
    
    /**
     * 保存小组件配置
     */
    public static void saveWidgetConfig(Context context, int appWidgetId, WidgetConfig config) {
        try {
            Log.d(TAG, "开始保存小组件配置: " + appWidgetId);
            Log.d(TAG, "配置内容 - 尺寸: " + config.size + ", 显示搜索框: " + config.showSearchBox);
            Log.d(TAG, "配置内容 - AI引擎数量: " + config.aiEngines.size() + ", 应用数量: " + config.appSearchItems.size() + ", 搜索引擎数量: " + config.searchEngines.size());

            String jsonString = config.toJson();
            Log.d(TAG, "配置JSON: " + jsonString);

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("widget_" + appWidgetId, jsonString);
            boolean success = editor.commit(); // 使用commit()而不是apply()来确保立即保存

            if (success) {
                Log.d(TAG, "保存小组件配置成功: " + appWidgetId);
                // 验证保存是否成功
                String savedConfig = prefs.getString("widget_" + appWidgetId, "");
                Log.d(TAG, "验证保存结果 - 已保存的配置长度: " + savedConfig.length());
            } else {
                Log.e(TAG, "保存小组件配置失败: commit()返回false");
            }
        } catch (JSONException e) {
            Log.e(TAG, "保存小组件配置失败 - JSON序列化错误", e);
        } catch (Exception e) {
            Log.e(TAG, "保存小组件配置失败 - 未知错误", e);
        }
    }
    
    /**
     * 获取小组件配置
     */
    public static WidgetConfig getWidgetConfig(Context context, int appWidgetId) {
        Log.d(TAG, "开始获取小组件配置: " + appWidgetId);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String configJson = prefs.getString("widget_" + appWidgetId, "");

        Log.d(TAG, "从SharedPreferences读取的配置JSON长度: " + configJson.length());
        if (!configJson.isEmpty()) {
            Log.d(TAG, "读取到的配置JSON: " + configJson);
        }

        if (configJson.isEmpty()) {
            Log.d(TAG, "没有找到保存的配置，使用默认配置");
            return getDefaultConfig();
        }

        try {
            WidgetConfig config = WidgetConfig.fromJson(configJson);
            Log.d(TAG, "成功解析配置 - 尺寸: " + config.size + ", 显示搜索框: " + config.showSearchBox);
            Log.d(TAG, "解析的配置 - AI引擎数量: " + config.aiEngines.size() + ", 应用数量: " + config.appSearchItems.size() + ", 搜索引擎数量: " + config.searchEngines.size());
            return config;
        } catch (Exception e) {
            Log.e(TAG, "解析小组件配置失败，使用默认配置", e);
            return getDefaultConfig();
        }
    }
    
    /**
     * 获取默认配置
     */
    public static WidgetConfig getDefaultConfig() {
        WidgetConfig config = new WidgetConfig();
        config.size = WidgetSize.MEDIUM;
        config.showSearchBox = true;

        // 默认显示智谱和DeepSeek
        config.aiEngines.add(new AppItem("智谱", "zhipu", "ic_zhipu"));
        config.aiEngines.add(new AppItem("DeepSeek", "deepseek", "ic_deepseek"));

        // 默认显示常用应用
        config.appSearchItems.add(new AppItem("微信", "com.tencent.mm", ""));
        config.appSearchItems.add(new AppItem("QQ", "com.tencent.mobileqq", ""));

        // 默认搜索引擎
        config.searchEngines.add(new AppItem("百度", "baidu", "ic_baidu"));
        config.searchEngines.add(new AppItem("Google", "google", "ic_google"));

        return config;
    }

    /**
     * 获取可用的AI引擎列表
     */
    public static java.util.List<AppItem> getAvailableAIEngines() {
        java.util.List<AppItem> engines = new java.util.ArrayList<>();
        engines.add(new AppItem("智谱", "zhipu", "ic_zhipu"));
        engines.add(new AppItem("DeepSeek", "deepseek", "ic_deepseek"));
        engines.add(new AppItem("文心一言", "wenxin", "ic_wenxin"));
        engines.add(new AppItem("通义千问", "tongyi", "ic_qianwen"));
        engines.add(new AppItem("ChatGPT", "chatgpt", "ic_chatgpt"));
        engines.add(new AppItem("Claude", "claude", "ic_claude"));
        engines.add(new AppItem("Gemini", "gemini", "ic_gemini"));
        engines.add(new AppItem("Kimi", "kimi", "ic_kimi"));
        return engines;
    }

    /**
     * 获取可用的应用列表
     */
    public static java.util.List<AppItem> getAvailableApps() {
        java.util.List<AppItem> apps = new java.util.ArrayList<>();
        apps.add(new AppItem("微信", "com.tencent.mm", "ic_wechat"));
        apps.add(new AppItem("QQ", "com.tencent.mobileqq", "ic_qq"));
        apps.add(new AppItem("支付宝", "com.eg.android.AlipayGphone", "ic_alipay"));
        apps.add(new AppItem("淘宝", "com.taobao.taobao", "ic_taobao"));
        apps.add(new AppItem("京东", "com.jingdong.app.mall", "ic_jd"));
        apps.add(new AppItem("美团", "com.sankuai.meituan", ""));
        apps.add(new AppItem("抖音", "com.ss.android.ugc.aweme", "ic_douyin"));
        apps.add(new AppItem("快手", "com.smile.gifmaker", "ic_kuaishou"));
        apps.add(new AppItem("微博", "com.sina.weibo", "ic_weibo"));
        apps.add(new AppItem("小红书", "com.xingin.xhs", "ic_xiaohongshu"));
        apps.add(new AppItem("知乎", "com.zhihu.android", "ic_zhihu"));
        apps.add(new AppItem("哔哩哔哩", "tv.danmaku.bili", "ic_bilibili"));
        return apps;
    }

    /**
     * 获取可用的搜索引擎列表
     */
    public static java.util.List<AppItem> getAvailableSearchEngines() {
        java.util.List<AppItem> engines = new java.util.ArrayList<>();
        engines.add(new AppItem("百度", "baidu", "ic_baidu"));
        engines.add(new AppItem("Google", "google", "ic_google"));
        engines.add(new AppItem("必应", "bing", "ic_bing"));
        engines.add(new AppItem("搜狗", "sogou", "ic_sogou"));
        engines.add(new AppItem("360搜索", "so360", "ic_360"));
        engines.add(new AppItem("夸克", "quark", "ic_quark"));
        engines.add(new AppItem("DuckDuckGo", "duckduckgo", "ic_duckduckgo"));
        return engines;
    }
    
    /**
     * 删除小组件配置
     */
    public static void deleteWidgetConfig(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("widget_" + appWidgetId);
        editor.apply();
        Log.d(TAG, "删除小组件配置: " + appWidgetId);
    }
    
    /**
     * 检查小组件是否已配置
     */
    public static boolean isWidgetConfigured(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains("widget_" + appWidgetId);
    }
}
