package com.example.dalao.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.List;
import java.util.Map;

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
                // 1. 尝试从iTunes获取图标URL
                List<String> iconUrls = searchiTunesForIcons(appName);
                
                // 2. 下载第一个可用的图标
                for (String iconUrl : iconUrls) {
                    Bitmap icon = downloadIcon(iconUrl);
                    if (icon != null) {
                        return icon;
                    }
                }
                
                // 3. 如果iTunes没有找到，尝试其他源
                String fallbackUrl = getFallbackIconUrl(packageName);
                if (fallbackUrl != null) {
                    return downloadIcon(fallbackUrl);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "加载图标失败: " + appName, e);
            }
            
            return null;
        }
        
        @Override
        protected void onPostExecute(Bitmap icon) {
            if (icon != null) {
                // 缓存图标
                iconCache.put(cacheKey, icon);
                
                // 设置图标到RemoteViews
                views.setImageViewBitmap(viewId, icon);
                Log.d(TAG, "成功加载图标: " + appName);
                
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
     * 从iTunes搜索图标URL
     */
    private static List<String> searchiTunesForIcons(String appName) {
        List<String> iconUrls = new ArrayList<>();
        
        try {
            String encodedName = URLEncoder.encode(appName, "UTF-8");
            String searchUrl = ITUNES_SEARCH_URL + "?term=" + encodedName + 
                              "&media=software&entity=software&limit=5";
            
            String response = downloadText(searchUrl);
            if (response != null) {
                iconUrls = parseiTunesResponse(response, appName);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "iTunes搜索失败: " + appName, e);
        }
        
        return iconUrls;
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
     * 简单的应用名称匹配
     */
    private static boolean isAppNameMatch(String trackName, String targetName) {
        if (trackName == null || targetName == null) return false;
        
        String track = trackName.toLowerCase().trim();
        String target = targetName.toLowerCase().trim();
        
        // 完全匹配
        if (track.equals(target)) return true;
        
        // 包含匹配
        if (track.contains(target) || target.contains(track)) return true;
        
        // 去除常见后缀后匹配
        String trackClean = track.replaceAll("\\s+(app|应用|软件)$", "");
        String targetClean = target.replaceAll("\\s+(app|应用|软件)$", "");
        
        return trackClean.equals(targetClean) || 
               trackClean.contains(targetClean) || 
               targetClean.contains(trackClean);
    }
    
    /**
     * 获取备用图标URL
     */
    private static String getFallbackIconUrl(String packageName) {
        // Google Play Store图标
        return "https://play-lh.googleusercontent.com/apps/" + packageName + "/icon";
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
     * 下载图标
     */
    private static Bitmap downloadIcon(String iconUrl) {
        try {
            URL url = new URL(iconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoInput(true);
            connection.connect();
            
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            connection.disconnect();
            
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "下载图标失败: " + iconUrl, e);
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
}
