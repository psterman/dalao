package com.example.dalao.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 真实图标提供器 - 提供多种获取真实AI图标的方案
 */
public class RealIconProvider {
    private static final String TAG = "RealIconProvider";
    private static RealIconProvider instance;
    private final Context context;
    private final Map<String, String> iconUrls;
    
    private RealIconProvider(Context context) {
        this.context = context.getApplicationContext();
        this.iconUrls = initIconUrls();
    }
    
    public static synchronized RealIconProvider getInstance(Context context) {
        if (instance == null) {
            instance = new RealIconProvider(context);
        }
        return instance;
    }
    
    /**
     * 初始化图标URL映射 - 使用多个可靠的图标源
     */
    private Map<String, String> initIconUrls() {
        Map<String, String> urls = new HashMap<>();
        
        // 方案1: 使用Google S2 API (最可靠)
        String googleS2 = "https://www.google.com/s2/favicons?domain=%s&sz=64";
        
        // 方案2: 使用DuckDuckGo图标API (备用)
        String duckduckgoApi = "https://icons.duckduckgo.com/ip3/%s.ico";
        
        // 方案3: 使用Favicon.io API (备用)
        String faviconIo = "https://favicons.githubusercontent.com/%s";
        
        // AI应用图标映射
        urls.put("chatgpt", String.format(googleS2, "chat.openai.com"));
        urls.put("claude", String.format(googleS2, "claude.ai"));
        urls.put("deepseek", String.format(googleS2, "chat.deepseek.com"));
        urls.put("zhipu", String.format(googleS2, "chatglm.cn"));
        urls.put("wenxin", String.format(googleS2, "yiyan.baidu.com"));
        urls.put("qianwen", String.format(googleS2, "tongyi.aliyun.com"));
        urls.put("gemini", String.format(googleS2, "gemini.google.com"));
        urls.put("kimi", String.format(googleS2, "kimi.moonshot.cn"));
        urls.put("doubao", String.format(googleS2, "www.doubao.com"));
        
        // 搜索引擎图标映射
        urls.put("baidu", String.format(googleS2, "www.baidu.com"));
        urls.put("google", String.format(googleS2, "www.google.com"));
        urls.put("bing", String.format(googleS2, "www.bing.com"));
        urls.put("sogou", String.format(googleS2, "www.sogou.com"));
        urls.put("360", String.format(googleS2, "www.so.com"));
        urls.put("quark", String.format(googleS2, "quark.sm.cn"));
        urls.put("duckduckgo", String.format(googleS2, "duckduckgo.com"));
        
        return urls;
    }
    
    /**
     * 为小组件设置真实图标
     */
    public void setRealIcon(RemoteViews views, int iconViewId, String appName, String packageName, Runnable onComplete) {
        String key = getIconKey(appName, packageName);
        if (key == null) {
            Log.w(TAG, "未找到图标映射: " + appName);
            if (onComplete != null) onComplete.run();
            return;
        }
        
        // 异步加载图标
        new Thread(() -> {
            try {
                Bitmap iconBitmap = downloadAndProcessIcon(key);
                if (iconBitmap != null) {
                    // 在主线程更新UI
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            views.setImageViewBitmap(iconViewId, iconBitmap);
                            Log.d(TAG, "✅ 成功设置真实图标: " + appName);
                            if (onComplete != null) onComplete.run();
                        } catch (Exception e) {
                            Log.e(TAG, "设置图标失败: " + appName, e);
                            if (onComplete != null) onComplete.run();
                        }
                    });
                } else {
                    Log.w(TAG, "下载图标失败: " + appName);
                    if (onComplete != null) onComplete.run();
                }
            } catch (Exception e) {
                Log.e(TAG, "处理图标异常: " + appName, e);
                if (onComplete != null) onComplete.run();
            }
        }).start();
    }
    
    /**
     * 下载并处理图标
     */
    private Bitmap downloadAndProcessIcon(String key) {
        String iconUrl = iconUrls.get(key);
        if (iconUrl == null) return null;
        
        try {
            Log.d(TAG, "开始下载图标: " + iconUrl);
            
            URL url = new URL(iconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
            
            InputStream inputStream = connection.getInputStream();
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            connection.disconnect();
            
            if (originalBitmap != null) {
                // 处理图标：调整尺寸和添加圆角
                return processIcon(originalBitmap);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "下载图标失败: " + iconUrl, e);
        }
        
        return null;
    }
    
    /**
     * 处理图标：调整尺寸和样式
     */
    private Bitmap processIcon(Bitmap original) {
        if (original == null) return null;
        
        int targetSize = 128; // 目标尺寸
        
        // 调整尺寸
        Bitmap resized = Bitmap.createScaledBitmap(original, targetSize, targetSize, true);
        
        // 添加圆角和阴影效果
        return createStyledIcon(resized);
    }
    
    /**
     * 创建带样式的图标（圆角、阴影等）
     */
    private Bitmap createStyledIcon(Bitmap bitmap) {
        int size = bitmap.getWidth();
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        // 绘制圆角背景
        Rect rect = new Rect(0, 0, size, size);
        RectF rectF = new RectF(rect);
        float roundPx = size * 0.15f; // 15%圆角
        
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        
        // 应用图标
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        
        return output;
    }
    
    /**
     * 获取图标键值
     */
    private String getIconKey(String appName, String packageName) {
        if (appName == null && packageName == null) return null;
        
        // AI应用映射
        if (containsIgnoreCase(appName, "ChatGPT") || containsIgnoreCase(packageName, "chatgpt")) return "chatgpt";
        if (containsIgnoreCase(appName, "Claude") || containsIgnoreCase(packageName, "claude")) return "claude";
        if (containsIgnoreCase(appName, "DeepSeek") || containsIgnoreCase(packageName, "deepseek")) return "deepseek";
        if (containsIgnoreCase(appName, "智谱") || containsIgnoreCase(appName, "ChatGLM") || containsIgnoreCase(packageName, "zhipu")) return "zhipu";
        if (containsIgnoreCase(appName, "文心") || containsIgnoreCase(appName, "wenxin")) return "wenxin";
        if (containsIgnoreCase(appName, "通义") || containsIgnoreCase(appName, "qianwen")) return "qianwen";
        if (containsIgnoreCase(appName, "Gemini") || containsIgnoreCase(packageName, "gemini")) return "gemini";
        if (containsIgnoreCase(appName, "Kimi") || containsIgnoreCase(packageName, "kimi")) return "kimi";
        if (containsIgnoreCase(appName, "豆包") || containsIgnoreCase(packageName, "doubao")) return "doubao";
        
        // 搜索引擎映射
        if (containsIgnoreCase(appName, "百度") || containsIgnoreCase(packageName, "baidu")) return "baidu";
        if (containsIgnoreCase(appName, "Google") || containsIgnoreCase(appName, "谷歌") || containsIgnoreCase(packageName, "google")) return "google";
        if (containsIgnoreCase(appName, "必应") || containsIgnoreCase(appName, "Bing") || containsIgnoreCase(packageName, "bing")) return "bing";
        if (containsIgnoreCase(appName, "搜狗") || containsIgnoreCase(appName, "Sogou") || containsIgnoreCase(packageName, "sogou")) return "sogou";
        if (containsIgnoreCase(appName, "360") || containsIgnoreCase(packageName, "360")) return "360";
        if (containsIgnoreCase(appName, "夸克") || containsIgnoreCase(appName, "Quark") || containsIgnoreCase(packageName, "quark")) return "quark";
        if (containsIgnoreCase(appName, "DuckDuckGo") || containsIgnoreCase(packageName, "duckduckgo")) return "duckduckgo";
        
        return null;
    }
    
    /**
     * 忽略大小写的包含检查
     */
    private boolean containsIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) return false;
        return str.toLowerCase().contains(searchStr.toLowerCase());
    }
}
