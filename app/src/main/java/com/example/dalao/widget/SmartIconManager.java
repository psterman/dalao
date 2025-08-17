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
import android.util.Log;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能图标管理器 - 提供高效的图标缓存和下载服务
 * 专门为小组件优化，确保显示真实的AI应用图标
 */
public class SmartIconManager {
    private static final String TAG = "SmartIconManager";
    private static SmartIconManager instance;
    private final Context context;
    private final ExecutorService executor;
    private final File cacheDir;
    private final Map<String, String> iconUrlMap;
    
    private SmartIconManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newFixedThreadPool(3);
        this.cacheDir = new File(context.getCacheDir(), "widget_icons");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        this.iconUrlMap = initIconUrlMap();
    }
    
    public static synchronized SmartIconManager getInstance(Context context) {
        if (instance == null) {
            instance = new SmartIconManager(context);
        }
        return instance;
    }
    
    /**
     * 初始化图标URL映射 - 使用多个可靠的图标源
     */
    private Map<String, String> initIconUrlMap() {
        Map<String, String> map = new HashMap<>();
        
        // 使用Google S2 Favicon API - 最可靠的图标源
        String googleS2 = "https://www.google.com/s2/favicons?domain=%s&sz=128";
        
        // AI应用官方域名映射
        map.put("chatgpt", String.format(googleS2, "chat.openai.com"));
        map.put("claude", String.format(googleS2, "claude.ai"));
        map.put("deepseek", String.format(googleS2, "chat.deepseek.com"));
        map.put("zhipu", String.format(googleS2, "chatglm.cn"));
        map.put("wenxin", String.format(googleS2, "yiyan.baidu.com"));
        map.put("qianwen", String.format(googleS2, "tongyi.aliyun.com"));
        map.put("gemini", String.format(googleS2, "gemini.google.com"));
        map.put("kimi", String.format(googleS2, "kimi.moonshot.cn"));
        map.put("doubao", String.format(googleS2, "www.doubao.com"));
        
        // 搜索引擎官方域名映射
        map.put("baidu", String.format(googleS2, "www.baidu.com"));
        map.put("google", String.format(googleS2, "www.google.com"));
        map.put("bing", String.format(googleS2, "www.bing.com"));
        map.put("sogou", String.format(googleS2, "www.sogou.com"));
        map.put("360", String.format(googleS2, "www.so.com"));
        map.put("quark", String.format(googleS2, "quark.sm.cn"));
        map.put("duckduckgo", String.format(googleS2, "duckduckgo.com"));
        
        return map;
    }
    
    /**
     * 为小组件加载智能图标
     */
    public void loadSmartIcon(RemoteViews views, int iconViewId, String appName, String packageName, Runnable onComplete) {
        String iconKey = getIconKey(appName, packageName);
        if (iconKey == null) {
            Log.w(TAG, "未找到图标映射: " + appName);
            if (onComplete != null) onComplete.run();
            return;
        }
        
        Log.d(TAG, "🔍 开始加载智能图标: " + appName + " -> " + iconKey);
        
        executor.execute(() -> {
            try {
                Bitmap iconBitmap = getOrDownloadIcon(iconKey, appName);
                if (iconBitmap != null) {
                    // 在主线程更新UI
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            views.setImageViewBitmap(iconViewId, iconBitmap);
                            Log.d(TAG, "✅ 智能图标设置成功: " + appName);
                            if (onComplete != null) onComplete.run();
                        } catch (Exception e) {
                            Log.e(TAG, "设置图标失败: " + appName, e);
                            if (onComplete != null) onComplete.run();
                        }
                    });
                } else {
                    Log.w(TAG, "❌ 智能图标加载失败: " + appName);
                    if (onComplete != null) onComplete.run();
                }
            } catch (Exception e) {
                Log.e(TAG, "智能图标处理异常: " + appName, e);
                if (onComplete != null) onComplete.run();
            }
        });
    }
    
    /**
     * 获取或下载图标
     */
    private Bitmap getOrDownloadIcon(String iconKey, String appName) {
        // 1. 检查缓存
        File cacheFile = new File(cacheDir, iconKey + ".png");
        if (cacheFile.exists()) {
            try {
                Bitmap cachedBitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (cachedBitmap != null) {
                    Log.d(TAG, "📁 使用缓存图标: " + appName);
                    return processIcon(cachedBitmap);
                }
            } catch (Exception e) {
                Log.w(TAG, "读取缓存图标失败: " + appName, e);
            }
        }
        
        // 2. 下载新图标
        String iconUrl = iconUrlMap.get(iconKey);
        if (iconUrl == null) {
            Log.w(TAG, "无图标URL: " + iconKey);
            return null;
        }
        
        try {
            Log.d(TAG, "📥 下载图标: " + iconUrl);
            
            URL url = new URL(iconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                connection.disconnect();
                
                if (originalBitmap != null) {
                    // 缓存原始图标
                    saveToCache(originalBitmap, cacheFile);
                    
                    // 处理并返回图标
                    Bitmap processedBitmap = processIcon(originalBitmap);
                    Log.d(TAG, "✅ 图标下载成功: " + appName);
                    return processedBitmap;
                } else {
                    Log.w(TAG, "图标解码失败: " + appName);
                }
            } else {
                Log.w(TAG, "图标下载失败，响应码: " + responseCode + " for " + appName);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "下载图标异常: " + appName, e);
        }
        
        return null;
    }
    
    /**
     * 处理图标：调整尺寸和样式
     */
    private Bitmap processIcon(Bitmap original) {
        if (original == null) return null;
        
        int targetSize = 128;
        
        // 调整尺寸
        Bitmap resized = Bitmap.createScaledBitmap(original, targetSize, targetSize, true);
        
        // 添加圆角效果
        return createRoundedIcon(resized);
    }
    
    /**
     * 创建圆角图标
     */
    private Bitmap createRoundedIcon(Bitmap bitmap) {
        int size = bitmap.getWidth();
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        // 绘制圆角背景
        Rect rect = new Rect(0, 0, size, size);
        RectF rectF = new RectF(rect);
        float roundPx = size * 0.1f; // 10%圆角
        
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        
        // 应用图标
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        
        return output;
    }
    
    /**
     * 保存图标到缓存
     */
    private void saveToCache(Bitmap bitmap, File cacheFile) {
        try {
            FileOutputStream fos = new FileOutputStream(cacheFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
            Log.d(TAG, "💾 图标已缓存: " + cacheFile.getName());
        } catch (Exception e) {
            Log.w(TAG, "缓存图标失败: " + cacheFile.getName(), e);
        }
    }
    
    /**
     * 获取图标键值
     */
    private String getIconKey(String appName, String packageName) {
        if (appName == null && packageName == null) return null;
        
        String name = appName != null ? appName.toLowerCase() : "";
        String pkg = packageName != null ? packageName.toLowerCase() : "";
        
        // AI应用映射
        if (name.contains("chatgpt") || pkg.contains("chatgpt") || pkg.contains("openai")) return "chatgpt";
        if (name.contains("claude") || pkg.contains("claude") || pkg.contains("anthropic")) return "claude";
        if (name.contains("deepseek") || pkg.contains("deepseek")) return "deepseek";
        if (name.contains("智谱") || name.contains("chatglm") || pkg.contains("zhipu")) return "zhipu";
        if (name.contains("文心") || name.contains("wenxin") || pkg.contains("yiyan")) return "wenxin";
        if (name.contains("通义") || name.contains("qianwen") || pkg.contains("tongyi")) return "qianwen";
        if (name.contains("gemini") || pkg.contains("gemini") || pkg.contains("bard")) return "gemini";
        if (name.contains("kimi") || pkg.contains("kimi") || pkg.contains("moonshot")) return "kimi";
        if (name.contains("豆包") || name.contains("doubao") || pkg.contains("doubao")) return "doubao";
        
        // 搜索引擎映射
        if (name.contains("百度") || pkg.contains("baidu")) return "baidu";
        if (name.contains("google") || name.contains("谷歌") || pkg.contains("google")) return "google";
        if (name.contains("必应") || name.contains("bing") || pkg.contains("bing")) return "bing";
        if (name.contains("搜狗") || name.contains("sogou") || pkg.contains("sogou")) return "sogou";
        if (name.contains("360") || pkg.contains("360") || pkg.contains("qihoo")) return "360";
        if (name.contains("夸克") || name.contains("quark") || pkg.contains("quark")) return "quark";
        if (name.contains("duckduckgo") || pkg.contains("duckduckgo")) return "duckduckgo";
        
        return null;
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        executor.execute(() -> {
            try {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.delete()) {
                            Log.d(TAG, "🗑️ 删除缓存文件: " + file.getName());
                        }
                    }
                }
                Log.d(TAG, "🧹 缓存清理完成");
            } catch (Exception e) {
                Log.e(TAG, "清理缓存失败", e);
            }
        });
    }
}
