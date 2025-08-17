package com.example.dalao.widget;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.aifloatingball.R;

/**
 * 图标测试活动 - 用于测试真实图标加载效果
 */
public class IconTestActivity extends Activity {
    private static final String TAG = "IconTestActivity";
    private LinearLayout iconContainer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 创建简单的测试界面
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);
        
        TextView title = new TextView(this);
        title.setText("🧪 真实图标测试");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 32);
        mainLayout.addView(title);
        
        iconContainer = new LinearLayout(this);
        iconContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(iconContainer);
        
        setContentView(mainLayout);
        
        // 开始测试
        testRealIcons();
    }
    
    private void testRealIcons() {
        String[] testApps = {
            "ChatGPT", "Claude", "DeepSeek", "智谱清言", "文心一言", 
            "通义千问", "Gemini", "Kimi", "豆包",
            "百度", "Google", "必应", "搜狗", "360搜索", "夸克", "DuckDuckGo"
        };
        
        String[] testPackages = {
            "com.openai.chatgpt", "com.anthropic.claude", "com.deepseek.chat", 
            "cn.zhipuai.chatglm", "com.baidu.yiyan", "com.alibaba.tongyi",
            "com.google.android.apps.bard", "com.moonshot.kimi", "com.bytedance.doubao",
            "com.baidu.searchbox", "com.google.android.googlequicksearchbox", 
            "com.microsoft.bing", "com.sogou.sogousearch", "com.qihoo.browser", 
            "com.quark.browser", "com.duckduckgo.mobile.android"
        };
        
        RealIconProvider iconProvider = RealIconProvider.getInstance(this);
        
        for (int i = 0; i < testApps.length && i < testPackages.length; i++) {
            createTestIconView(testApps[i], testPackages[i], iconProvider);
        }
    }
    
    private void createTestIconView(String appName, String packageName, RealIconProvider iconProvider) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 16, 0, 16);
        
        // 创建图标视图
        ImageView iconView = new ImageView(this);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(128, 128));
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iconView.setImageResource(R.drawable.ic_apps); // 默认图标
        
        // 创建文本视图
        TextView textView = new TextView(this);
        textView.setText(appName + "\n" + packageName);
        textView.setPadding(32, 0, 0, 0);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        itemLayout.addView(iconView);
        itemLayout.addView(textView);
        iconContainer.addView(itemLayout);
        
        // 测试加载真实图标
        Log.d(TAG, "🧪 测试加载图标: " + appName + " (" + packageName + ")");
        
        // 模拟小组件的RemoteViews行为
        new Thread(() -> {
            try {
                // 直接测试下载图标
                String iconUrl = getTestIconUrl(appName, packageName);
                if (iconUrl != null) {
                    Log.d(TAG, "📥 测试下载图标URL: " + iconUrl);
                    
                    java.net.URL url = new java.net.URL(iconUrl);
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
                    
                    java.io.InputStream inputStream = connection.getInputStream();
                    Bitmap iconBitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                    connection.disconnect();
                    
                    if (iconBitmap != null) {
                        runOnUiThread(() -> {
                            iconView.setImageBitmap(iconBitmap);
                            textView.setText(appName + "\n✅ 图标加载成功");
                            Log.d(TAG, "✅ 图标加载成功: " + appName);
                        });
                    } else {
                        runOnUiThread(() -> {
                            textView.setText(appName + "\n❌ 图标解码失败");
                            Log.w(TAG, "❌ 图标解码失败: " + appName);
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        textView.setText(appName + "\n⚠️ 无图标URL");
                        Log.w(TAG, "⚠️ 无图标URL: " + appName);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    textView.setText(appName + "\n❌ 加载失败: " + e.getMessage());
                    Log.e(TAG, "❌ 图标加载失败: " + appName, e);
                });
            }
        }).start();
    }
    
    private String getTestIconUrl(String appName, String packageName) {
        // 使用Google S2 API
        String googleS2 = "https://www.google.com/s2/favicons?domain=%s&sz=64";
        
        // 根据应用名称和包名映射到域名
        if (appName.contains("ChatGPT") || packageName.contains("chatgpt")) {
            return String.format(googleS2, "chat.openai.com");
        }
        if (appName.contains("Claude") || packageName.contains("claude")) {
            return String.format(googleS2, "claude.ai");
        }
        if (appName.contains("DeepSeek") || packageName.contains("deepseek")) {
            return String.format(googleS2, "chat.deepseek.com");
        }
        if (appName.contains("智谱") || packageName.contains("zhipu")) {
            return String.format(googleS2, "chatglm.cn");
        }
        if (appName.contains("文心") || packageName.contains("yiyan")) {
            return String.format(googleS2, "yiyan.baidu.com");
        }
        if (appName.contains("通义") || packageName.contains("tongyi")) {
            return String.format(googleS2, "tongyi.aliyun.com");
        }
        if (appName.contains("Gemini") || packageName.contains("gemini")) {
            return String.format(googleS2, "gemini.google.com");
        }
        if (appName.contains("Kimi") || packageName.contains("kimi")) {
            return String.format(googleS2, "kimi.moonshot.cn");
        }
        if (appName.contains("豆包") || packageName.contains("doubao")) {
            return String.format(googleS2, "www.doubao.com");
        }
        if (appName.contains("百度") || packageName.contains("baidu")) {
            return String.format(googleS2, "www.baidu.com");
        }
        if (appName.contains("Google") || packageName.contains("google")) {
            return String.format(googleS2, "www.google.com");
        }
        if (appName.contains("必应") || packageName.contains("bing")) {
            return String.format(googleS2, "www.bing.com");
        }
        if (appName.contains("搜狗") || packageName.contains("sogou")) {
            return String.format(googleS2, "www.sogou.com");
        }
        if (appName.contains("360") || packageName.contains("360")) {
            return String.format(googleS2, "www.so.com");
        }
        if (appName.contains("夸克") || packageName.contains("quark")) {
            return String.format(googleS2, "quark.sm.cn");
        }
        if (appName.contains("DuckDuckGo") || packageName.contains("duckduckgo")) {
            return String.format(googleS2, "duckduckgo.com");
        }
        
        return null;
    }
}
