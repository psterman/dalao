package com.example.dalao.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

/**
 * 图标准确性测试工具
 * 用于验证图标获取的准确性
 */
public class IconAccuracyTester {
    
    private static final String TAG = "IconAccuracyTester";
    
    /**
     * 测试常见应用的图标获取
     */
    public static void testCommonApps(Context context) {
        Log.d(TAG, "开始测试常见应用图标获取准确性...");
        
        // 测试应用列表
        List<TestApp> testApps = Arrays.asList(
            new TestApp("百度", "com.baidu.searchbox", "百度搜索"),
            new TestApp("DeepSeek", "com.deepseek.chat", "DeepSeek AI"),
            new TestApp("智谱", "com.zhipu.chatglm", "智谱清言"),
            new TestApp("微信", "com.tencent.mm", "WeChat"),
            new TestApp("QQ", "com.tencent.mobileqq", "QQ"),
            new TestApp("淘宝", "com.taobao.taobao", "淘宝"),
            new TestApp("京东", "com.jingdong.app.mall", "京东"),
            new TestApp("抖音", "com.ss.android.ugc.aweme", "抖音短视频"),
            new TestApp("B站", "tv.danmaku.bili", "哔哩哔哩"),
            new TestApp("网易云音乐", "com.netease.cloudmusic", "网易云音乐"),
            new TestApp("支付宝", "com.eg.android.AlipayGphone", "支付宝"),
            new TestApp("美团", "com.sankuai.meituan", "美团")
        );
        
        int successCount = 0;
        int totalCount = testApps.size();
        
        for (TestApp app : testApps) {
            boolean success = testSingleApp(app);
            if (success) {
                successCount++;
            }
            
            // 添加延迟，避免请求过于频繁
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        double accuracy = (double) successCount / totalCount * 100;
        Log.i(TAG, String.format("图标获取测试完成: %d/%d 成功, 准确率: %.1f%%", 
                                 successCount, totalCount, accuracy));
    }
    
    /**
     * 测试单个应用的图标获取
     */
    private static boolean testSingleApp(TestApp app) {
        Log.d(TAG, "测试应用: " + app.displayName + " (" + app.packageName + ")");
        
        try {
            // 使用改进的图标加载器进行测试
            TestIconLoader loader = new TestIconLoader();
            Bitmap icon = loader.loadIconSync(app.displayName, app.packageName);
            
            if (icon != null) {
                Log.i(TAG, "✓ 成功获取图标: " + app.displayName + 
                          " [" + icon.getWidth() + "x" + icon.getHeight() + "]");
                return true;
            } else {
                Log.w(TAG, "✗ 未能获取图标: " + app.displayName);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "✗ 测试失败: " + app.displayName, e);
            return false;
        }
    }
    
    /**
     * 测试应用数据类
     */
    private static class TestApp {
        final String displayName;
        final String packageName;
        final String alternativeName;
        
        TestApp(String displayName, String packageName, String alternativeName) {
            this.displayName = displayName;
            this.packageName = packageName;
            this.alternativeName = alternativeName;
        }
    }
    
    /**
     * 同步图标加载器（用于测试）
     */
    private static class TestIconLoader {
        
        /**
         * 同步加载图标（仅用于测试）
         */
        Bitmap loadIconSync(String appName, String packageName) {
            try {
                // 1. 检查预定义图标映射
                String predefinedUrl = getPredefinedIconUrl(packageName, appName);
                if (predefinedUrl != null) {
                    Bitmap icon = downloadIcon(predefinedUrl);
                    if (icon != null) {
                        Log.d(TAG, "使用预定义图标: " + appName);
                        return icon;
                    }
                }
                
                // 2. 尝试iTunes搜索
                List<String> iconUrls = searchiTunesForIcons(appName);
                for (String iconUrl : iconUrls) {
                    Bitmap icon = downloadIcon(iconUrl);
                    if (icon != null) {
                        Log.d(TAG, "使用iTunes图标: " + appName);
                        return icon;
                    }
                }
                
                // 3. 尝试备用源
                List<String> fallbackUrls = getFallbackIconUrls(packageName, appName);
                for (String fallbackUrl : fallbackUrls) {
                    Bitmap icon = downloadIcon(fallbackUrl);
                    if (icon != null) {
                        Log.d(TAG, "使用备用图标源: " + appName);
                        return icon;
                    }
                }
                
                return null;
                
            } catch (Exception e) {
                Log.e(TAG, "同步加载图标失败: " + appName, e);
                return null;
            }
        }
        
        // 这里需要复制WidgetIconLoader中的相关方法
        // 为了简化，我们直接调用WidgetIconLoader的静态方法
        private String getPredefinedIconUrl(String packageName, String appName) {
            // 简化版本，实际应该调用WidgetIconLoader的方法
            return null;
        }
        
        private List<String> searchiTunesForIcons(String appName) {
            // 简化版本，实际应该调用WidgetIconLoader的方法
            return Arrays.asList();
        }
        
        private List<String> getFallbackIconUrls(String packageName, String appName) {
            // 简化版本，实际应该调用WidgetIconLoader的方法
            return Arrays.asList();
        }
        
        private Bitmap downloadIcon(String iconUrl) {
            // 简化版本，实际应该调用WidgetIconLoader的方法
            return null;
        }
    }
}
