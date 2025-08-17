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
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 官方图标管理器 - 从官方网站获取真实AI应用图标
 */
public class OfficialIconManager {
    private static final String TAG = "OfficialIconManager";
    private static final int CACHE_SIZE = 50; // 缓存50个图标
    private static final int ICON_SIZE = 128; // 图标尺寸
    
    private static OfficialIconManager instance;
    private final Context context;
    private final LruCache<String, Bitmap> memoryCache;
    private final Map<String, String> officialUrls;
    
    private OfficialIconManager(Context context) {
        this.context = context.getApplicationContext();
        this.memoryCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024; // 以KB为单位
            }
        };
        this.officialUrls = initOfficialUrls();
    }
    
    public static synchronized OfficialIconManager getInstance(Context context) {
        if (instance == null) {
            instance = new OfficialIconManager(context);
        }
        return instance;
    }
    
    /**
     * 初始化官方图标URL映射 - 使用Google S2 API和高质量图标源
     */
    private Map<String, String> initOfficialUrls() {
        Map<String, String> urls = new HashMap<>();

        // 使用Google S2 API获取高质量网站图标 (64x64)
        String googleS2Base = "https://www.google.com/s2/favicons?domain=";
        String googleS2Size = "&sz=64";

        // AI应用官方图标 - 使用Google S2 API
        urls.put("chatgpt", googleS2Base + "chat.openai.com" + googleS2Size);
        urls.put("claude", googleS2Base + "claude.ai" + googleS2Size);
        urls.put("deepseek", googleS2Base + "chat.deepseek.com" + googleS2Size);
        urls.put("zhipu", googleS2Base + "chatglm.cn" + googleS2Size);
        urls.put("wenxin", googleS2Base + "yiyan.baidu.com" + googleS2Size);
        urls.put("qianwen", googleS2Base + "tongyi.aliyun.com" + googleS2Size);
        urls.put("gemini", googleS2Base + "gemini.google.com" + googleS2Size);
        urls.put("kimi", googleS2Base + "kimi.moonshot.cn" + googleS2Size);
        urls.put("doubao", googleS2Base + "www.doubao.com" + googleS2Size);

        // 搜索引擎官方图标 - 使用Google S2 API
        urls.put("baidu", googleS2Base + "www.baidu.com" + googleS2Size);
        urls.put("google", googleS2Base + "www.google.com" + googleS2Size);
        urls.put("bing", googleS2Base + "www.bing.com" + googleS2Size);
        urls.put("sogou", googleS2Base + "www.sogou.com" + googleS2Size);
        urls.put("360", googleS2Base + "www.so.com" + googleS2Size);
        urls.put("quark", googleS2Base + "quark.sm.cn" + googleS2Size);
        urls.put("duckduckgo", googleS2Base + "duckduckgo.com" + googleS2Size);

        return urls;
    }
    
    /**
     * 异步获取官方图标
     */
    public void getOfficialIcon(String appName, String packageName, IconCallback callback) {
        String key = getIconKey(appName, packageName);
        
        // 先检查内存缓存
        Bitmap cachedIcon = memoryCache.get(key);
        if (cachedIcon != null) {
            Log.d(TAG, "从内存缓存获取图标: " + key);
            callback.onIconLoaded(cachedIcon);
            return;
        }
        
        // 检查磁盘缓存
        Bitmap diskCachedIcon = loadFromDiskCache(key);
        if (diskCachedIcon != null) {
            Log.d(TAG, "从磁盘缓存获取图标: " + key);
            memoryCache.put(key, diskCachedIcon);
            callback.onIconLoaded(diskCachedIcon);
            return;
        }
        
        // 异步下载官方图标
        new DownloadIconTask(key, callback).execute();
    }
    
    /**
     * 生成图标缓存键
     */
    private String getIconKey(String appName, String packageName) {
        // AI应用映射
        if (appName.contains("ChatGPT") || packageName.contains("chatgpt")) return "chatgpt";
        if (appName.contains("Claude") || packageName.contains("claude")) return "claude";
        if (appName.contains("DeepSeek") || packageName.contains("deepseek")) return "deepseek";
        if (appName.contains("智谱") || appName.contains("ChatGLM") || packageName.contains("zhipu")) return "zhipu";
        if (appName.contains("文心") || appName.contains("wenxin")) return "wenxin";
        if (appName.contains("通义") || appName.contains("qianwen")) return "qianwen";
        if (appName.contains("Gemini") || packageName.contains("gemini")) return "gemini";
        if (appName.contains("Kimi") || packageName.contains("kimi")) return "kimi";
        if (appName.contains("豆包") || packageName.contains("doubao")) return "doubao";
        
        // 搜索引擎映射
        if (appName.contains("百度") || packageName.contains("baidu")) return "baidu";
        if (appName.contains("Google") || appName.contains("谷歌") || packageName.contains("google")) return "google";
        if (appName.contains("必应") || appName.contains("Bing") || packageName.contains("bing")) return "bing";
        if (appName.contains("搜狗") || appName.contains("Sogou") || packageName.contains("sogou")) return "sogou";
        if (appName.contains("360") || packageName.contains("360")) return "360";
        if (appName.contains("夸克") || appName.contains("Quark") || packageName.contains("quark")) return "quark";
        if (appName.contains("DuckDuckGo") || packageName.contains("duckduckgo")) return "duckduckgo";
        
        return null;
    }
    
    /**
     * 从磁盘缓存加载图标
     */
    private Bitmap loadFromDiskCache(String key) {
        try {
            File cacheDir = new File(context.getCacheDir(), "official_icons");
            File iconFile = new File(cacheDir, key + ".png");
            if (iconFile.exists()) {
                return BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "从磁盘缓存加载图标失败: " + key, e);
        }
        return null;
    }
    
    /**
     * 保存图标到磁盘缓存
     */
    private void saveToDiskCache(String key, Bitmap bitmap) {
        try {
            File cacheDir = new File(context.getCacheDir(), "official_icons");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            File iconFile = new File(cacheDir, key + ".png");
            FileOutputStream fos = new FileOutputStream(iconFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            
            Log.d(TAG, "图标已保存到磁盘缓存: " + key);
        } catch (Exception e) {
            Log.e(TAG, "保存图标到磁盘缓存失败: " + key, e);
        }
    }
    
    /**
     * 下载图标的异步任务
     */
    private class DownloadIconTask extends AsyncTask<Void, Void, Bitmap> {
        private final String key;
        private final IconCallback callback;
        
        public DownloadIconTask(String key, IconCallback callback) {
            this.key = key;
            this.callback = callback;
        }
        
        @Override
        protected Bitmap doInBackground(Void... voids) {
            String iconUrl = officialUrls.get(key);
            if (iconUrl == null) {
                Log.w(TAG, "没有找到官方图标URL: " + key);
                return null;
            }
            
            return downloadIcon(iconUrl);
        }
        
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                // 处理图标（圆角、尺寸调整等）
                Bitmap processedBitmap = processIcon(bitmap);
                
                // 保存到缓存
                memoryCache.put(key, processedBitmap);
                saveToDiskCache(key, processedBitmap);
                
                Log.d(TAG, "✅ 官方图标下载成功: " + key);
                callback.onIconLoaded(processedBitmap);
            } else {
                Log.e(TAG, "❌ 官方图标下载失败: " + key);
                callback.onIconLoadFailed();
            }
        }
    }
    
    /**
     * 下载图标
     */
    private Bitmap downloadIcon(String iconUrl) {
        try {
            Log.d(TAG, "开始下载官方图标: " + iconUrl);
            
            URL url = new URL(iconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
            
            InputStream inputStream = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            connection.disconnect();
            
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "下载图标失败: " + iconUrl, e);
            return null;
        }
    }
    
    /**
     * 处理图标（调整尺寸、添加圆角等）
     */
    private Bitmap processIcon(Bitmap original) {
        if (original == null) return null;
        
        // 调整尺寸
        Bitmap resized = Bitmap.createScaledBitmap(original, ICON_SIZE, ICON_SIZE, true);
        
        // 添加圆角
        return createRoundedBitmap(resized);
    }
    
    /**
     * 创建圆角图标
     */
    private Bitmap createRoundedBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        RectF rectF = new RectF(rect);
        float roundPx = bitmap.getWidth() * 0.1f; // 10%圆角
        
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        
        return output;
    }
    
    /**
     * 图标加载回调接口
     */
    public interface IconCallback {
        void onIconLoaded(Bitmap icon);
        void onIconLoadFailed();
    }
}
