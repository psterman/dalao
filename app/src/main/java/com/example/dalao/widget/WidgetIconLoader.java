package com.example.dalao.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 小组件图标加载器
 * 使用iTunes API获取高质量应用图标
 */
public class WidgetIconLoader {
    
    private static final String TAG = "WidgetIconLoader";
    private static final String ITUNES_SEARCH_URL = "https://itunes.apple.com/search";
    private static final int TIMEOUT_MS = 5000;
    
    // 缓存已加载的图标，避免重复网络请求
    private static final Map<String, Bitmap> iconCache = new HashMap<>();
    // URL缓存 - 避免重复搜索
    private static final Map<String, List<String>> urlCache = new HashMap<>();
    // 失败缓存 - 避免重复尝试失败的URL
    private static final Set<String> failedUrls = new HashSet<>();
    
    /**
     * 异步加载图标并设置到RemoteViews
     */
    public static void loadIconFromiTunes(Context context, RemoteViews views, int viewId, 
                                         String appName, String packageName, int defaultIconRes) {
        
        String cacheKey = packageName + "_" + appName;
        
        // 检查缓存
        if (iconCache.containsKey(cacheKey)) {
            Bitmap cachedIcon = iconCache.get(cacheKey);
            if (cachedIcon != null) {
                views.setImageViewBitmap(viewId, cachedIcon);
                Log.d(TAG, "使用缓存图标: " + appName);
                return;
            }
        }
        
        // 异步加载图标
        new IconLoadTask(context, views, viewId, appName, packageName, defaultIconRes, cacheKey).execute();
    }
    
    /**
     * 异步图标加载任务
     */
    private static class IconLoadTask extends AsyncTask<Void, Void, Bitmap> {
        
        private final Context context;
        private final RemoteViews views;
        private final int viewId;
        private final String appName;
        private final String packageName;
        private final int defaultIconRes;
        private final String cacheKey;
        
        public IconLoadTask(Context context, RemoteViews views, int viewId, String appName, 
                           String packageName, int defaultIconRes, String cacheKey) {
            this.context = context;
            this.views = views;
            this.viewId = viewId;
            this.appName = appName;
            this.packageName = packageName;
            this.defaultIconRes = defaultIconRes;
            this.cacheKey = cacheKey;
        }
        
        @Override
        protected Bitmap doInBackground(Void... voids) {
            try {
                // 1. 首先检查预定义的图标映射
                String predefinedUrl = getPredefinedIconUrl(packageName, appName);
                if (predefinedUrl != null) {
                    Bitmap icon = downloadIcon(predefinedUrl);
                    if (icon != null) {
                        Log.d(TAG, "使用预定义图标: " + appName);
                        return icon;
                    }
                }

                // 2. 尝试从iTunes获取图标URL
                List<String> iconUrls = searchiTunesForIcons(appName);

                // 3. 下载第一个可用的图标
                for (String iconUrl : iconUrls) {
                    Bitmap icon = downloadIcon(iconUrl);
                    if (icon != null) {
                        Log.d(TAG, "使用iTunes图标: " + appName);
                        return icon;
                    }
                }

                // 4. 尝试多个备用图标源
                List<String> fallbackUrls = getFallbackIconUrls(packageName, appName);
                for (String fallbackUrl : fallbackUrls) {
                    Bitmap icon = downloadIcon(fallbackUrl);
                    if (icon != null) {
                        Log.d(TAG, "使用备用图标源: " + appName);
                        return icon;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "加载图标失败: " + appName, e);
            }

            return null;
        }
        
        @Override
        protected void onPostExecute(Bitmap icon) {
            if (icon != null) {
                // 应用Material Design风格的图标处理
                Bitmap processedIcon = applyMaterialDesignStyle(icon);

                // 缓存处理后的图标
                iconCache.put(cacheKey, processedIcon);

                // 设置图标到RemoteViews
                views.setImageViewBitmap(viewId, processedIcon);
                Log.d(TAG, "成功加载并处理图标: " + appName);

                // 更新小组件
                updateWidget(context, views);
            } else {
                // 使用默认图标
                views.setImageViewResource(viewId, defaultIconRes);
                Log.d(TAG, "使用默认图标: " + appName);
                
                // 更新小组件
                updateWidget(context, views);
            }
        }
    }
    
    /**
     * 从多个源获取图标URL（改进版）
     */
    private static List<String> searchiTunesForIcons(String appName) {
        List<String> iconUrls = new ArrayList<>();

        try {
            // 1. 首先尝试精确的应用名称搜索
            String encodedName = URLEncoder.encode(appName, "UTF-8");
            String searchUrl = ITUNES_SEARCH_URL + "?term=" + encodedName +
                              "&media=software&entity=software&limit=10";

            String response = downloadText(searchUrl);
            if (response != null) {
                iconUrls = parseiTunesResponse(response, appName);
            }

            // 2. 如果没找到，尝试关键词搜索
            if (iconUrls.isEmpty()) {
                List<String> keywords = generateSearchKeywords(appName);
                for (String keyword : keywords) {
                    if (iconUrls.size() >= 3) break; // 限制数量

                    String keywordUrl = ITUNES_SEARCH_URL + "?term=" +
                                       URLEncoder.encode(keyword, "UTF-8") +
                                       "&media=software&entity=software&limit=5";

                    String keywordResponse = downloadText(keywordUrl);
                    if (keywordResponse != null) {
                        List<String> keywordIcons = parseiTunesResponse(keywordResponse, appName);
                        iconUrls.addAll(keywordIcons);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "iTunes搜索失败: " + appName, e);
        }

        return iconUrls;
    }

    /**
     * 生成搜索关键词
     */
    private static List<String> generateSearchKeywords(String appName) {
        List<String> keywords = new ArrayList<>();

        // 移除常见后缀
        String cleanName = appName.replaceAll("(?i)\\s*(app|应用|软件|客户端)$", "").trim();
        if (!cleanName.equals(appName)) {
            keywords.add(cleanName);
        }

        // 提取英文部分
        String englishPart = appName.replaceAll("[^a-zA-Z\\s]", "").trim();
        if (!englishPart.isEmpty() && !englishPart.equals(appName)) {
            keywords.add(englishPart);
        }

        // 提取中文部分
        String chinesePart = appName.replaceAll("[a-zA-Z0-9\\s]", "").trim();
        if (!chinesePart.isEmpty() && !chinesePart.equals(appName)) {
            keywords.add(chinesePart);
        }

        return keywords;
    }
    
    /**
     * 解析iTunes API响应
     */
    private static List<String> parseiTunesResponse(String response, String targetAppName) {
        List<String> iconUrls = new ArrayList<>();
        
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray results = jsonObject.getJSONArray("results");
            
            for (int i = 0; i < results.length(); i++) {
                JSONObject app = results.getJSONObject(i);
                String trackName = app.optString("trackName", "");
                
                // 简单的名称匹配
                if (isAppNameMatch(trackName, targetAppName)) {
                    // 获取不同尺寸的图标
                    String icon512 = app.optString("artworkUrl512");
                    String icon100 = app.optString("artworkUrl100");
                    String icon60 = app.optString("artworkUrl60");
                    
                    if (!icon512.isEmpty()) iconUrls.add(icon512);
                    if (!icon100.isEmpty()) iconUrls.add(icon100);
                    if (!icon60.isEmpty()) iconUrls.add(icon60);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "解析iTunes响应失败", e);
        }
        
        return iconUrls;
    }
    
    /**
     * 改进的应用名称匹配算法
     */
    private static boolean isAppNameMatch(String trackName, String targetName) {
        if (trackName == null || targetName == null) return false;

        String track = trackName.toLowerCase().trim();
        String target = targetName.toLowerCase().trim();

        // 1. 完全匹配
        if (track.equals(target)) return true;

        // 2. 清理后的匹配
        String trackClean = cleanAppName(track);
        String targetClean = cleanAppName(target);

        if (trackClean.equals(targetClean)) return true;

        // 3. 核心词匹配
        String trackCore = extractCoreWords(trackClean);
        String targetCore = extractCoreWords(targetClean);

        if (trackCore.equals(targetCore) && !trackCore.isEmpty()) return true;

        // 4. 包含匹配（但要求长度合理）
        if (trackClean.length() >= 3 && targetClean.length() >= 3) {
            if (trackClean.contains(targetClean) || targetClean.contains(trackClean)) {
                // 计算相似度，避免误匹配
                double similarity = calculateSimilarity(trackClean, targetClean);
                return similarity > 0.6; // 60%以上相似度
            }
        }

        // 5. 英文名匹配
        String trackEn = extractEnglishWords(track);
        String targetEn = extractEnglishWords(target);
        if (!trackEn.isEmpty() && !targetEn.isEmpty() && trackEn.equals(targetEn)) {
            return true;
        }

        return false;
    }

    /**
     * 清理应用名称
     */
    private static String cleanAppName(String name) {
        return name.replaceAll("(?i)\\s*(app|应用|软件|客户端|lite|pro|plus|premium|free)\\s*", "")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    /**
     * 提取核心词汇
     */
    private static String extractCoreWords(String name) {
        // 移除常见的修饰词
        return name.replaceAll("(?i)\\b(the|for|and|or|with|by|in|on|at|to|from|of|a|an)\\b", "")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    /**
     * 提取英文单词
     */
    private static String extractEnglishWords(String name) {
        return name.replaceAll("[^a-zA-Z\\s]", "")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    /**
     * 计算字符串相似度
     */
    private static double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        return (maxLen - levenshteinDistance(s1, s2)) / (double) maxLen;
    }

    /**
     * 计算编辑距离
     */
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        return dp[s1.length()][s2.length()];
    }
    
    /**
     * 获取预定义的图标URL
     */
    private static String getPredefinedIconUrl(String packageName, String appName) {
        // 常见应用的高质量图标映射
        Map<String, String> iconMapping = new HashMap<>();

        // 搜索引擎
        iconMapping.put("百度", "https://www.baidu.com/favicon.ico");
        iconMapping.put("DeepSeek", "https://chat.deepseek.com/favicon.ico");
        iconMapping.put("智谱", "https://chatglm.cn/favicon.ico");

        // 社交应用
        iconMapping.put("微信", "https://res.wx.qq.com/a/wx_fed/assets/res/OTE0YTAw.png");
        iconMapping.put("QQ", "https://qzonestyle.gtimg.cn/qzone/qzact/act/external/qq-logo.png");
        iconMapping.put("微博", "https://weibo.com/favicon.ico");

        // 购物应用
        iconMapping.put("淘宝", "https://www.taobao.com/favicon.ico");
        iconMapping.put("京东", "https://www.jd.com/favicon.ico");
        iconMapping.put("拼多多", "https://mobile.yangkeduo.com/favicon.ico");

        // 视频应用
        iconMapping.put("抖音", "https://lf1-cdn-tos.bytegoofy.com/obj/iconpark/svg_20337_14.5e6831d1b8c9b4a5b8b2b4b5b8b2b4b5.svg");
        iconMapping.put("快手", "https://static.kuaishou.com/kos/nlav11092/static/image/favicon.ico");
        iconMapping.put("B站", "https://www.bilibili.com/favicon.ico");
        iconMapping.put("哔哩哔哩", "https://www.bilibili.com/favicon.ico");

        // 音乐应用
        iconMapping.put("网易云音乐", "https://s1.music.126.net/style/favicon.ico");
        iconMapping.put("QQ音乐", "https://y.qq.com/favicon.ico");

        // 工具应用
        iconMapping.put("支付宝", "https://t.alipayobjects.com/images/T1HHFgXXVeXXXXXXXX.png");
        iconMapping.put("美团", "https://www.meituan.com/favicon.ico");
        iconMapping.put("滴滴", "https://www.didiglobal.com/favicon.ico");

        // 检查应用名称匹配
        for (Map.Entry<String, String> entry : iconMapping.entrySet()) {
            if (appName.contains(entry.getKey()) || entry.getKey().contains(appName)) {
                return entry.getValue();
            }
        }

        // 检查包名匹配
        if (packageName.contains("baidu")) return iconMapping.get("百度");
        if (packageName.contains("tencent") && appName.contains("微信")) return iconMapping.get("微信");
        if (packageName.contains("tencent") && appName.contains("QQ")) return iconMapping.get("QQ");
        if (packageName.contains("taobao")) return iconMapping.get("淘宝");
        if (packageName.contains("jd") || packageName.contains("jingdong")) return iconMapping.get("京东");
        if (packageName.contains("pdd") || packageName.contains("pinduoduo")) return iconMapping.get("拼多多");
        if (packageName.contains("douyin")) return iconMapping.get("抖音");
        if (packageName.contains("kuaishou")) return iconMapping.get("快手");
        if (packageName.contains("bilibili")) return iconMapping.get("B站");
        if (packageName.contains("netease") && appName.contains("音乐")) return iconMapping.get("网易云音乐");
        if (packageName.contains("alipay")) return iconMapping.get("支付宝");
        if (packageName.contains("meituan")) return iconMapping.get("美团");
        if (packageName.contains("didi")) return iconMapping.get("滴滴");

        return null;
    }

    /**
     * 获取多个备用图标URL
     */
    private static List<String> getFallbackIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // 1. Google Play Store (官方)
        urls.add("https://play-lh.googleusercontent.com/apps/" + packageName + "/icon");

        // 2. APKMirror (高质量Android图标)
        urls.add("https://www.apkmirror.com/wp-content/themes/APKMirror/ap_resize/ap_resize.php?src=https://www.apkmirror.com/wp-content/uploads/icons/" + packageName + ".png&w=96&h=96&q=100");

        // 3. 华为应用市场
        urls.add("https://appimg.dbankcdn.com/application/icon144/" + packageName + ".png");

        // 4. 小米应用商店
        urls.add("https://file.market.xiaomi.com/thumbnail/PNG/l114/" + packageName);

        // 5. 应用宝
        urls.add("https://android-artworks.25pp.com/fs08/2021/11/12/" + packageName + ".png");

        // 6. F-Droid (开源应用)
        urls.add("https://f-droid.org/repo/icons-640/" + packageName + ".png");

        // 7. App Store 适中分辨率图标源 (优化版)
        try {
            String encodedName = URLEncoder.encode(appName, "UTF-8");
            urls.add("https://itunes.apple.com/search?term=" + encodedName + "&media=software&entity=software&limit=1&country=us");
            urls.add("https://itunes.apple.com/search?term=" + encodedName + "&media=software&entity=software&limit=1&country=cn");

            // 适中分辨率App Store图标模板 (256x256 和 512x512)
            urls.add("https://is1-ssl.mzstatic.com/image/thumb/Purple126/v4/*/AppIcon-0-0-1x_U007emarketing-0-0-0-7-0-0-sRGB-0-0-0-GLES2_U002c0-512MB-85-220-0-0.png/512x512bb.png");
            urls.add("https://is2-ssl.mzstatic.com/image/thumb/Purple116/v4/*/AppIcon-0-0-1x_U007emarketing-0-0-0-7-0-0-sRGB-0-0-0-GLES2_U002c0-512MB-85-220-0-0.png/256x256bb.png");
        } catch (Exception e) {
            Log.w(TAG, "编码应用名称失败: " + appName);
        }

        // 8. 基于域名的Logo API
        String domain = extractDomainFromPackage(packageName);
        if (domain != null) {
            urls.add("https://logo.clearbit.com/" + domain);
            urls.add("https://www.google.com/s2/favicons?domain=" + domain + "&sz=64");
        }

        return urls;
    }

    /**
     * 从包名提取可能的域名
     */
    private static String extractDomainFromPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;

        // 常见的包名到域名映射
        if (packageName.contains("google")) return "google.com";
        if (packageName.contains("microsoft")) return "microsoft.com";
        if (packageName.contains("facebook")) return "facebook.com";
        if (packageName.contains("twitter")) return "twitter.com";
        if (packageName.contains("instagram")) return "instagram.com";
        if (packageName.contains("youtube")) return "youtube.com";
        if (packageName.contains("netflix")) return "netflix.com";
        if (packageName.contains("spotify")) return "spotify.com";
        if (packageName.contains("amazon")) return "amazon.com";
        if (packageName.contains("uber")) return "uber.com";
        if (packageName.contains("airbnb")) return "airbnb.com";

        // 尝试从包名反向构造域名
        String[] parts = packageName.split("\\.");
        if (parts.length >= 2) {
            // 假设格式为 com.company.app 或 company.app
            String company = parts[parts.length - 2];
            return company + ".com";
        }

        return null;
    }
    
    /**
     * 下载文本内容
     */
    private static String downloadText(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            reader.close();
            connection.disconnect();
            
            return response.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "下载文本失败: " + urlString, e);
            return null;
        }
    }
    
    /**
     * 下载图标（优化版）
     */
    private static Bitmap downloadIcon(String iconUrl) {
        if (iconUrl == null || iconUrl.isEmpty()) {
            return null;
        }

        // 检查失败缓存
        if (failedUrls.contains(iconUrl)) {
            Log.d(TAG, "跳过已知失败的URL: " + iconUrl);
            return null;
        }

        try {
            URL url = new URL(iconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoInput(true);

            // 设置User-Agent，避免某些服务器拒绝请求
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0)");

            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP错误: " + responseCode + " for " + iconUrl);
                failedUrls.add(iconUrl);
                connection.disconnect();
                return null;
            }

            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            connection.disconnect();

            if (bitmap == null) {
                Log.w(TAG, "无法解码图标: " + iconUrl);
                failedUrls.add(iconUrl);
                return null;
            }

            // 验证图标质量
            if (bitmap.getWidth() < 16 || bitmap.getHeight() < 16) {
                Log.w(TAG, "图标尺寸太小: " + bitmap.getWidth() + "x" + bitmap.getHeight() + " for " + iconUrl);
                failedUrls.add(iconUrl);
                return null;
            }

            Log.d(TAG, "成功下载图标: " + iconUrl + " (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "下载图标失败: " + iconUrl, e);
            failedUrls.add(iconUrl);
            return null;
        }
    }
    
    /**
     * 更新小组件
     */
    private static void updateWidget(Context context, RemoteViews views) {
        // 这里需要根据具体的小组件更新逻辑来实现
        // 由于我们在CustomizableWidgetProvider中调用，这个方法可能不需要做任何事情
        // 因为RemoteViews的更改会在方法返回后自动应用
    }

    /**
     * 应用Material Design风格的图标处理 (优化版)
     * 根据实际显示需求调整尺寸
     */
    private static Bitmap applyMaterialDesignStyle(Bitmap originalIcon) {
        if (originalIcon == null) {
            return null;
        }

        try {
            // 根据小组件实际需求调整目标尺寸
            int targetSize = 96; // 32dp * 3 (适合小组件显示)

            // 创建缩放后的图标
            Bitmap scaledIcon = Bitmap.createScaledBitmap(originalIcon, targetSize, targetSize, true);

            // 创建输出bitmap
            Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            // 设置抗锯齿
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            // 绘制圆角背景
            float cornerRadius = targetSize * 0.22f; // Material Design推荐的圆角比例
            RectF rect = new RectF(0, 0, targetSize, targetSize);

            // 绘制白色背景（用于透明图标）
            paint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

            // 设置混合模式以绘制图标
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));

            // 绘制图标，稍微缩小以留出边距
            float margin = targetSize * 0.08f; // 减少边距以适应小尺寸
            RectF iconRect = new RectF(margin, margin, targetSize - margin, targetSize - margin);
            canvas.drawBitmap(scaledIcon, null, iconRect, paint);

            // 添加轻微的边框
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.5f); // 减少边框宽度
            paint.setColor(0x1A000000); // 10% 黑色透明度
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

            return output;

        } catch (Exception e) {
            Log.e(TAG, "处理图标样式失败", e);
            return originalIcon; // 返回原始图标作为备用
        }
    }
}
