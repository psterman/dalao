package com.example.dalao.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.util.Log;

import com.example.aifloatingball.R;
import com.example.aifloatingball.SimpleModeActivity;
import com.example.aifloatingball.service.DualFloatingWebViewService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CustomizableWidgetProvider extends AppWidgetProvider {
    
    private static final String TAG = "CustomizableWidget";
    private static final String PREFS_NAME = "widget_config";
    private static final String ACTION_WIDGET_CLICK = "com.example.aifloatingball.WIDGET_CLICK";
    private static final String ACTION_SEARCH_CLICK = "com.example.aifloatingball.WIDGET_SEARCH_CLICK";
    
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
    
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "更新小组件: " + appWidgetId);

        // 测试图标资源
        testIconResources(context);

        // 获取小组件配置
        WidgetConfig config = getWidgetConfig(context, appWidgetId);

        Log.d(TAG, "小组件配置 - 尺寸: " + config.size + ", 显示搜索框: " + config.showSearchBox);
        Log.d(TAG, "小组件配置 - AI引擎数量: " + config.aiEngines.size() + ", 应用数量: " + config.appSearchItems.size() + ", 搜索引擎数量: " + config.searchEngines.size());

        // 打印具体的配置内容
        Log.d(TAG, "AI引擎列表:");
        for (AppItem item : config.aiEngines) {
            Log.d(TAG, "  - " + item.name + " (" + item.packageName + ") iconName: " + item.iconName);
        }
        Log.d(TAG, "应用列表:");
        for (AppItem item : config.appSearchItems) {
            Log.d(TAG, "  - " + item.name + " (" + item.packageName + ")");
        }
        Log.d(TAG, "搜索引擎列表:");
        for (AppItem item : config.searchEngines) {
            Log.d(TAG, "  - " + item.name + " (" + item.packageName + ") iconName: " + item.iconName);
        }

        // 根据尺寸选择布局
        int layoutId = getLayoutForSize(config.size);
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        // 根据模板类型设置内容
        setupWidgetContent(context, views, config, appWidgetId);

        // 更新小组件
        appWidgetManager.updateAppWidget(appWidgetId, views);
        Log.d(TAG, "小组件更新完成: " + appWidgetId);
    }
    
    private static WidgetConfig getWidgetConfig(Context context, int appWidgetId) {
        return WidgetUtils.getWidgetConfig(context, appWidgetId);
    }

    /**
     * 更新小组件的辅助方法（用于图标加载完成后的回调）
     */
    private static void updateWidget(Context context, RemoteViews views) {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            android.content.ComponentName componentName = new android.content.ComponentName(context, CustomizableWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

            // 更新所有小组件实例
            for (int appWidgetId : appWidgetIds) {
                appWidgetManager.updateAppWidget(appWidgetId, views);
            }

            Log.d(TAG, "小组件图标更新完成，更新了 " + appWidgetIds.length + " 个实例");
        } catch (Exception e) {
            Log.e(TAG, "更新小组件失败", e);
        }
    }
    
    private static int getLayoutForSize(WidgetSize size) {
        switch (size) {
            case SEARCH_SINGLE_ROW:
                return R.layout.widget_search_single_row;
            case SEARCH_DOUBLE_ROW:
                return R.layout.widget_search_double_row;
            case ICONS_SINGLE_ROW:
                return R.layout.widget_icons_single_row;
            case ICONS_DOUBLE_ROW:
                return R.layout.widget_icons_double_row;
            case SINGLE_ICON:
                return R.layout.widget_single_icon;
            case QUAD_ICONS:
                return R.layout.widget_quad_icons;
            case SEARCH_ONLY:
                return R.layout.widget_search_only;
            default:
                return R.layout.widget_search_single_row;
        }
    }

    private static void setupWidgetContent(Context context, RemoteViews views, WidgetConfig config, int appWidgetId) {
        if (config.size.isSearchOnly()) {
            // 搜索图标模板
            setupSearchOnlyWidget(context, views, config, appWidgetId);
        } else {
            // 其他模板
            if (config.size.hasSearchBox()) {
                setupSearchBox(context, views, config, appWidgetId);
            }
            setupUnifiedIcons(context, views, config, appWidgetId);
        }
    }

    private static void setupSearchOnlyWidget(Context context, RemoteViews views, WidgetConfig config, int appWidgetId) {
        // 设置搜索图标点击事件
        Intent searchIntent = new Intent(context, SimpleModeActivity.class);
        searchIntent.putExtra("widget_type", "search");
        searchIntent.putExtra("show_input_dialog", true);
        searchIntent.putExtra("source", "桌面小组件");
        searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent searchPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 50000,
            searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.search_icon, searchPendingIntent);
    }

    private static void setupUnifiedIcons(Context context, RemoteViews views, WidgetConfig config, int appWidgetId) {
        int maxIcons = config.size.getMaxIcons();
        Log.d(TAG, "设置统一图标，最大数量: " + maxIcons);

        // 收集所有图标项
        java.util.List<AppItem> allItems = new java.util.ArrayList<>();
        allItems.addAll(config.aiEngines);
        allItems.addAll(config.appSearchItems);
        allItems.addAll(config.searchEngines);

        // 设置图标
        for (int i = 1; i <= maxIcons; i++) {
            int iconId = getIconResourceId(i);
            int labelId = getLabelResourceId(i);

            if (i <= allItems.size()) {
                AppItem item = allItems.get(i - 1);
                setupSingleIcon(context, views, iconId, labelId, item, appWidgetId, i);
            } else {
                // 隐藏未使用的图标
                views.setViewVisibility(iconId, android.view.View.GONE);
                if (labelId != 0) {
                    views.setViewVisibility(labelId, android.view.View.GONE);
                }
            }
        }
    }

    private static int getIconResourceId(int position) {
        switch (position) {
            case 1: return R.id.icon_1;
            case 2: return R.id.icon_2;
            case 3: return R.id.icon_3;
            case 4: return R.id.icon_4;
            case 5: return R.id.icon_5;
            case 6: return R.id.icon_6;
            case 7: return R.id.icon_7;
            case 8: return R.id.icon_8;
            default: return 0;
        }
    }

    private static int getLabelResourceId(int position) {
        switch (position) {
            case 1: return R.id.label_1;
            case 2: return R.id.label_2;
            case 3: return R.id.label_3;
            case 4: return R.id.label_4;
            case 5: return R.id.label_5;
            case 6: return R.id.label_6;
            case 7: return R.id.label_7;
            case 8: return R.id.label_8;
            default: return 0;
        }
    }

    private static void setupSingleIcon(Context context, RemoteViews views, int iconId, int labelId, AppItem item, int appWidgetId, int position) {
        // 显示图标
        views.setViewVisibility(iconId, android.view.View.VISIBLE);

        // 使用优化的图标加载策略
        loadOptimizedIcon(context, views, iconId, item, R.drawable.ic_apps);

        // 设置标签
        if (labelId != 0) {
            views.setViewVisibility(labelId, android.view.View.VISIBLE);
            views.setTextViewText(labelId, item.name);
        }

        // 设置点击事件
        setupIconClickEvent(context, views, iconId, item, appWidgetId, position);
    }

    private static void setupIconClickEvent(Context context, RemoteViews views, int iconId, AppItem item, int appWidgetId, int position) {
        // 检查当前显示模式，根据模式选择不同的启动方式
        String displayMode = getCurrentDisplayMode(context);
        Log.d(TAG, "当前显示模式: " + displayMode + ", 设置图标点击事件: " + item.name);

        if ("simple_mode".equals(displayMode)) {
            // 简易模式：启动SimpleModeActivity
            setupSimpleModeClickEvent(context, views, iconId, item, appWidgetId, position);
        } else {
            // 悬浮球模式或灵动岛模式：使用DualFloatingWebViewService
            setupFloatingModeClickEvent(context, views, iconId, item, appWidgetId, position, displayMode);
        }
    }

    /**
     * 获取当前显示模式
     */
    private static String getCurrentDisplayMode(Context context) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            return prefs.getString("display_mode", "simple_mode");
        } catch (Exception e) {
            Log.e(TAG, "获取显示模式失败", e);
            return "simple_mode"; // 默认返回简易模式
        }
    }

    /**
     * 设置简易模式的点击事件
     */
    private static void setupSimpleModeClickEvent(Context context, RemoteViews views, int iconId, AppItem item, int appWidgetId, int position) {
        Intent clickIntent = new Intent(context, SimpleModeActivity.class);

        // 根据item类型设置不同的点击行为
        if (isAIEngine(item)) {
            clickIntent.putExtra("widget_type", "ai_chat");
            clickIntent.putExtra("ai_engine", item.packageName);
            clickIntent.putExtra("ai_name", item.name);
            clickIntent.putExtra("auto_start_ai_chat", true);
        } else if (isSearchEngine(item)) {
            clickIntent.putExtra("widget_type", "web_search");
            clickIntent.putExtra("search_engine", item.packageName);
            clickIntent.putExtra("search_engine_name", item.name);
            clickIntent.putExtra("auto_start_web_search", true);
        } else {
            clickIntent.putExtra("widget_type", "app_search");
            clickIntent.putExtra("app_package", item.packageName);
            clickIntent.putExtra("app_name", item.name);
            clickIntent.putExtra("auto_switch_to_app_search", true);
        }

        clickIntent.putExtra("source", "桌面小组件");
        clickIntent.putExtra("use_clipboard_if_no_search_box", true);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 60000 + position,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(iconId, pendingIntent);
        Log.d(TAG, "设置简易模式点击事件: " + item.name);
    }

    /**
     * 设置悬浮球/灵动岛模式的点击事件
     */
    private static void setupFloatingModeClickEvent(Context context, RemoteViews views, int iconId, AppItem item, int appWidgetId, int position, String displayMode) {
        // 获取剪贴板内容
        String clipboardText = ClipboardHelper.getClipboardText(context);
        String queryToUse = null;

        if (ClipboardHelper.isValidSearchQuery(clipboardText)) {
            queryToUse = ClipboardHelper.cleanTextForSearch(clipboardText);
            Log.d(TAG, "使用剪贴板内容: " + queryToUse);
        } else {
            Log.d(TAG, "剪贴板内容无效或为空");
        }

        Intent clickIntent = new Intent(context, DualFloatingWebViewService.class);

        // 根据item类型设置不同的点击行为
        if (isAIEngine(item)) {
            // AI应用：使用AI搜索引擎键值
            String engineKey = getAIEngineKey(item.packageName, item.name);
            if (queryToUse != null) {
                clickIntent.putExtra("search_query", queryToUse);
            }
            clickIntent.putExtra("engine_key", engineKey);
            Log.d(TAG, "设置AI搜索: " + item.name + " -> " + engineKey);
        } else if (isSearchEngine(item)) {
            // 搜索引擎：使用搜索引擎键值
            String engineKey = getSearchEngineKey(item.packageName, item.name);
            if (queryToUse != null) {
                clickIntent.putExtra("search_query", queryToUse);
            } else {
                // 没有搜索内容时，设置空查询以打开首页
                clickIntent.putExtra("search_query", "");
            }
            clickIntent.putExtra("engine_key", engineKey);
            Log.d(TAG, "设置网络搜索: " + item.name + " -> " + engineKey);
        } else {
            // 应用：对于应用搜索，暂时回退到SimpleModeActivity
            // 因为DualFloatingWebViewService主要用于网络搜索和AI对话
            Log.d(TAG, "应用搜索回退到SimpleModeActivity: " + item.name);
            setupSimpleModeClickEvent(context, views, iconId, item, appWidgetId, position);
            return;
        }

        clickIntent.putExtra("search_source", "桌面小组件");
        clickIntent.putExtra("window_count", 1); // 单窗口模式

        PendingIntent pendingIntent = PendingIntent.getService(
            context,
            appWidgetId + 70000 + position,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(iconId, pendingIntent);
        Log.d(TAG, "设置" + displayMode + "模式点击事件: " + item.name);
    }

    /**
     * 获取AI引擎的键值，用于DualFloatingWebViewService
     */
    private static String getAIEngineKey(String packageName, String name) {
        if (name == null) return "deepseek"; // 默认值

        String lowerName = name.toLowerCase();
        if (lowerName.contains("chatgpt") || lowerName.contains("gpt")) return "chatgpt";
        if (lowerName.contains("claude")) return "claude";
        if (lowerName.contains("deepseek")) return "deepseek";
        if (lowerName.contains("gemini")) return "gemini";
        if (lowerName.contains("kimi")) return "kimi";
        if (lowerName.contains("智谱") || lowerName.contains("zhipu")) return "zhipu";
        if (lowerName.contains("文心") || lowerName.contains("wenxin")) return "wenxin";
        if (lowerName.contains("通义") || lowerName.contains("qianwen")) return "qianwen";
        if (lowerName.contains("豆包") || lowerName.contains("doubao")) return "doubao";

        return "deepseek"; // 默认返回DeepSeek
    }

    /**
     * 获取搜索引擎的键值，用于DualFloatingWebViewService
     */
    private static String getSearchEngineKey(String packageName, String name) {
        if (name == null) return "google"; // 默认值

        String lowerName = name.toLowerCase();

        // 根据应用包名进行精确匹配
        if (packageName != null) {
            String lowerPackage = packageName.toLowerCase();
            if (lowerPackage.contains("baidu")) return "baidu";
            if (lowerPackage.contains("sogou")) return "sogou";
            if (lowerPackage.contains("bing")) return "bing_cn";
            if (lowerPackage.contains("360")) return "360";
            if (lowerPackage.contains("quark")) return "quark";
            if (lowerPackage.contains("google")) return "google";
        }

        // 根据应用名称进行匹配
        if (lowerName.contains("百度") || lowerName.contains("baidu")) return "baidu";
        if (lowerName.contains("搜狗") || lowerName.contains("sogou")) return "sogou";
        if (lowerName.contains("必应") || lowerName.contains("bing")) return "bing_cn";
        if (lowerName.contains("360搜索") || lowerName.contains("360")) return "360";
        if (lowerName.contains("夸克") || lowerName.contains("quark")) return "quark";
        if (lowerName.contains("google") || lowerName.contains("谷歌")) return "google";
        if (lowerName.contains("duckduckgo")) return "duckduckgo";

        return "google"; // 默认返回Google
    }

    private static boolean isAIEngine(AppItem item) {
        // 简单的AI引擎判断逻辑
        return item.packageName != null && (
            item.packageName.contains("zhipu") ||
            item.packageName.contains("deepseek") ||
            item.packageName.contains("wenxin") ||
            item.packageName.contains("tongyi") ||
            item.packageName.contains("chatgpt") ||
            item.packageName.contains("claude") ||
            item.packageName.contains("gemini")
        );
    }

    private static boolean isSearchEngine(AppItem item) {
        // 简单的搜索引擎判断逻辑
        return item.packageName != null && (
            item.packageName.equals("baidu") ||
            item.packageName.equals("google") ||
            item.packageName.equals("bing") ||
            item.packageName.equals("sogou") ||
            item.packageName.equals("so360") ||
            item.packageName.equals("duckduckgo")
        );
    }

    private static void setupSearchBox(Context context, RemoteViews views, WidgetConfig config, int appWidgetId) {
        if (config.showSearchBox) {
            // 显示搜索框
            views.setViewVisibility(R.id.widget_search_box, android.view.View.VISIBLE);

            // 设置搜索框点击事件
            Intent searchIntent = new Intent(context, CustomizableWidgetProvider.class);
            searchIntent.setAction(ACTION_SEARCH_CLICK);
            searchIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

            PendingIntent searchPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 10000,
                searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            views.setOnClickPendingIntent(R.id.widget_search_box, searchPendingIntent);
        } else {
            // 隐藏搜索框
            views.setViewVisibility(R.id.widget_search_box, android.view.View.GONE);
        }
    }
    
    private static void setupAppIcons(Context context, RemoteViews views, WidgetConfig config, int appWidgetId) {
        // 根据配置设置不同类型的应用图标
        setupAIEngines(context, views, config.aiEngines, config, appWidgetId);
        setupAppSearchItems(context, views, config.appSearchItems, config, appWidgetId);
        setupSearchEngines(context, views, config.searchEngines, config, appWidgetId);
    }
    
    private static void setupAIEngines(Context context, RemoteViews views, java.util.List<AppItem> aiEngines, WidgetConfig config, int appWidgetId) {
        Log.d(TAG, "setupAIEngines - 小组件ID: " + appWidgetId + ", AI引擎数量: " + aiEngines.size());

        // 获取当前布局支持的最大AI图标数量
        int maxAIIcons = getMaxAIIconsForCurrentLayout(context, appWidgetId);
        Log.d(TAG, "当前布局支持的最大AI图标数量: " + maxAIIcons);

        // 首先隐藏所有AI图标
        for (int i = 0; i < maxAIIcons; i++) {
            int iconViewId = getAIIconViewId(i);
            if (iconViewId != 0) {
                try {
                    views.setViewVisibility(iconViewId, android.view.View.GONE);
                    Log.d(TAG, "隐藏AI图标位置 " + i + " (ID: " + iconViewId + ")");
                } catch (Exception e) {
                    Log.w(TAG, "无法隐藏AI图标位置 " + i + ": " + e.getMessage());
                }
            }
        }

        // 设置AI引擎图标和点击事件
        int displayCount = Math.min(aiEngines.size(), maxAIIcons);
        Log.d(TAG, "将显示 " + displayCount + " 个AI引擎");

        for (int i = 0; i < displayCount; i++) {
            AppItem item = aiEngines.get(i);
            int iconViewId = getAIIconViewId(i);
            Log.d(TAG, "设置AI引擎 " + i + ": " + item.name + " (图标ID: " + iconViewId + ")");

            if (iconViewId != 0) {
                try {
                    // 显示图标
                    views.setViewVisibility(iconViewId, android.view.View.VISIBLE);
                    Log.d(TAG, "显示AI图标位置 " + i + " (ID: " + iconViewId + ")");

                    // 设置图标 - 优先使用本地资源图标，确保快速加载
                    int iconRes = 0;
                    if (item.iconName != null && !item.iconName.isEmpty()) {
                        iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
                    }

                    if (iconRes != 0) {
                        views.setImageViewResource(iconViewId, iconRes);
                        Log.d(TAG, "设置AI图标: " + item.iconName + " (资源ID: " + iconRes + ")");
                    } else {
                        // 如果找不到图标，使用默认AI图标
                        views.setImageViewResource(iconViewId, R.drawable.ic_ai);
                        Log.d(TAG, "使用默认AI图标 (找不到: " + item.iconName + ")");
                    }

                    // 使用新的精准图标加载器（AI应用专用）
                    try {
                        com.example.aifloatingball.widget.WidgetPreciseIconLoader iconLoader =
                            new com.example.aifloatingball.widget.WidgetPreciseIconLoader(context);
                        iconLoader.loadPreciseIconForWidget(
                            item.packageName,
                            item.name,
                            views,
                            iconViewId,
                            iconRes != 0 ? iconRes : R.drawable.ic_ai,
                            () -> {
                                updateWidget(context, views);
                                return null;
                            }
                        );
                    } catch (Exception e) {
                        Log.w(TAG, "AI应用精准图标加载失败: " + e.getMessage());
                    }

                    // 设置点击事件 - 智能启动AI对话
                    Intent clickIntent = new Intent(context, SimpleModeActivity.class);
                    clickIntent.putExtra("widget_type", "ai_chat");
                    clickIntent.putExtra("ai_engine", item.packageName);
                    clickIntent.putExtra("ai_name", item.name);
                    clickIntent.putExtra("source", "桌面小组件");
                    clickIntent.putExtra("auto_start_ai_chat", true);
                    clickIntent.putExtra("use_clipboard_if_no_search_box", true);
                    clickIntent.putExtra("show_search_box", config.showSearchBox);
                    clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId + 20000 + i,
                        clickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    views.setOnClickPendingIntent(iconViewId, pendingIntent);
                    Log.d(TAG, "设置AI图标点击事件: " + item.name);

                } catch (Exception e) {
                    Log.e(TAG, "设置AI图标失败 " + i + ": " + e.getMessage(), e);
                }
            } else {
                Log.w(TAG, "AI图标位置 " + i + " 的视图ID为0，跳过设置");
            }
        }
    }
    
    private static void setupAppSearchItems(Context context, RemoteViews views, java.util.List<AppItem> appItems, WidgetConfig config, int appWidgetId) {
        // 首先隐藏所有应用图标
        for (int i = 0; i < 4; i++) {
            int iconViewId = getAppIconViewId(i);
            if (iconViewId != 0) {
                views.setViewVisibility(iconViewId, android.view.View.GONE);
            }
        }

        // 设置应用搜索项
        for (int i = 0; i < Math.min(appItems.size(), 4); i++) {
            AppItem item = appItems.get(i);
            int iconViewId = getAppIconViewId(i);

            if (iconViewId != 0) {
                // 显示图标
                views.setViewVisibility(iconViewId, android.view.View.VISIBLE);

                // 设置图标 - 优先使用本地资源图标，确保快速加载
                int iconRes = 0;
                if (item.iconName != null && !item.iconName.isEmpty()) {
                    iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
                }

                if (iconRes != 0) {
                    views.setImageViewResource(iconViewId, iconRes);
                    Log.d(TAG, "设置应用图标: " + item.iconName + " (资源ID: " + iconRes + ")");
                } else {
                    // 如果找不到图标，使用默认应用图标
                    views.setImageViewResource(iconViewId, android.R.drawable.sym_def_app_icon);
                    Log.d(TAG, "使用默认应用图标 (找不到: " + item.iconName + ")");
                }

                // 使用新的精准图标加载器（常规应用专用）
                try {
                    com.example.aifloatingball.widget.WidgetPreciseIconLoader iconLoader =
                        new com.example.aifloatingball.widget.WidgetPreciseIconLoader(context);
                    iconLoader.loadPreciseIconForWidget(
                        item.packageName,
                        item.name,
                        views,
                        iconViewId,
                        iconRes != 0 ? iconRes : android.R.drawable.sym_def_app_icon,
                        () -> {
                            updateWidget(context, views);
                            return null;
                        }
                    );
                } catch (Exception e) {
                    Log.w(TAG, "常规应用精准图标加载失败: " + e.getMessage());
                }

                // 设置点击事件智能启动应用
                Intent clickIntent = new Intent(context, SimpleModeActivity.class);
                clickIntent.putExtra("widget_type", "app_search");
                clickIntent.putExtra("app_package", item.packageName);
                clickIntent.putExtra("app_name", item.name);
                clickIntent.putExtra("source", "桌面小组件");
                clickIntent.putExtra("auto_switch_to_app_search", true);
                clickIntent.putExtra("use_clipboard_if_no_search_box", true);
                clickIntent.putExtra("show_search_box", config.showSearchBox);
                clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 30000 + i,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                views.setOnClickPendingIntent(iconViewId, pendingIntent);
            }
        }
    }
    
    private static void setupSearchEngines(Context context, RemoteViews views, java.util.List<AppItem> searchEngines, WidgetConfig config, int appWidgetId) {
        // 首先隐藏所有搜索引擎图标
        for (int i = 0; i < 4; i++) {
            int iconViewId = getSearchEngineIconViewId(i);
            if (iconViewId != 0) {
                views.setViewVisibility(iconViewId, android.view.View.GONE);
            }
        }

        // 设置搜索引擎图标和点击事件
        for (int i = 0; i < Math.min(searchEngines.size(), 4); i++) {
            AppItem item = searchEngines.get(i);
            int iconViewId = getSearchEngineIconViewId(i);

            if (iconViewId != 0) {
                // 显示图标
                views.setViewVisibility(iconViewId, android.view.View.VISIBLE);

                // 设置图标 - 优先使用本地资源图标，确保快速加载
                int iconRes = 0;
                if (item.iconName != null && !item.iconName.isEmpty()) {
                    iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
                }

                if (iconRes != 0) {
                    views.setImageViewResource(iconViewId, iconRes);
                    Log.d(TAG, "设置搜索引擎图标: " + item.iconName + " (资源ID: " + iconRes + ")");
                } else {
                    // 如果找不到图标，使用默认搜索图标
                    views.setImageViewResource(iconViewId, R.drawable.ic_search);
                    Log.d(TAG, "使用默认搜索图标 (找不到: " + item.iconName + ")");
                }

                // 使用新的精准图标加载器（搜索引擎专用）
                try {
                    com.example.aifloatingball.widget.WidgetPreciseIconLoader iconLoader =
                        new com.example.aifloatingball.widget.WidgetPreciseIconLoader(context);
                    iconLoader.loadPreciseIconForWidget(
                        item.packageName,
                        item.name,
                        views,
                        iconViewId,
                        iconRes != 0 ? iconRes : R.drawable.ic_search,
                        () -> {
                            updateWidget(context, views);
                            return null;
                        }
                    );
                } catch (Exception e) {
                    Log.w(TAG, "搜索引擎精准图标加载失败: " + e.getMessage());
                }

                // 设置点击事件智能启动网络搜索
                Intent clickIntent = new Intent(context, SimpleModeActivity.class);
                clickIntent.putExtra("widget_type", "web_search");
                clickIntent.putExtra("search_engine", item.packageName);
                clickIntent.putExtra("search_engine_name", item.name);
                clickIntent.putExtra("source", "桌面小组件");
                clickIntent.putExtra("auto_start_web_search", true);
                clickIntent.putExtra("use_clipboard_if_no_search_box", true);
                clickIntent.putExtra("show_search_box", config.showSearchBox);
                clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 40000 + i,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                views.setOnClickPendingIntent(iconViewId, pendingIntent);
            }
        }
    }
    
    private static int getMaxAIIconsForCurrentLayout(Context context, int appWidgetId) {
        // 获取当前小组件的配置
        WidgetConfig config = getWidgetConfig(context, appWidgetId);

        // 新的统一图标系统，直接返回模板支持的最大图标数量
        return config.size.getMaxIcons();
    }

    private static int getAIIconViewId(int index) {
        switch (index) {
            case 0: return R.id.ai_icon_1;
            case 1: return R.id.ai_icon_2;
            case 2: return R.id.ai_icon_3;
            case 3: return R.id.ai_icon_4;
            default: return 0;
        }
    }
    
    private static int getAppIconViewId(int index) {
        switch (index) {
            case 0: return R.id.app_icon_1;
            case 1: return R.id.app_icon_2;
            default: return 0;
        }
    }

    private static int getSearchEngineIconViewId(int index) {
        switch (index) {
            case 0: return R.id.search_icon_1;
            case 1: return R.id.search_icon_2;
            default: return 0;
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        if (ACTION_SEARCH_CLICK.equals(intent.getAction())) {
            // 处理搜索框点击
            Intent searchIntent = new Intent(context, SimpleModeActivity.class);
            searchIntent.putExtra("widget_type", "search");
            searchIntent.putExtra("show_input_dialog", true);
            searchIntent.putExtra("source", "桌面小组件");
            searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(searchIntent);
        }
    }
    
    @Override
    public void onEnabled(Context context) {
        // 第一个小组件被添加时调用
    }
    
    @Override
    public void onDisabled(Context context) {
        // 最后一个小组件被移除时调用
    }
    
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // 清理小组件配置
        for (int appWidgetId : appWidgetIds) {
            WidgetUtils.deleteWidgetConfig(context, appWidgetId);
        }
    }

    /**
     * 智能图标加载策略
     * 根据应用类型采用不同的优先级策略
     */
    private static void loadOptimizedIcon(Context context, RemoteViews views, int iconId, AppItem item, int defaultIconRes) {
        // 判断应用类型
        AppType appType = determineAppType(item.name, item.packageName);

        switch (appType) {
            case AI_APP:
            case SEARCH_ENGINE:
                // AI应用和搜索引擎：优先使用预设资源图标
                loadResourceFirstStrategy(context, views, iconId, item, defaultIconRes, appType);
                break;
            case REGULAR_APP:
            default:
                // 常规应用：优先使用设备已安装图标
                loadDeviceFirstStrategy(context, views, iconId, item, defaultIconRes);
                break;
        }
    }

    /**
     * 资源优先策略 - 适用于AI应用和搜索引擎
     */
    private static void loadResourceFirstStrategy(Context context, RemoteViews views, int iconId, AppItem item, int defaultIconRes, AppType appType) {
        // 1. 优先使用预设的资源图标
        Log.d(TAG, "🔍 尝试加载图标: " + item.name + ", iconName: " + item.iconName + ", packageName: " + item.packageName);

        // 首先尝试使用配置中的iconName
        if (item.iconName != null && !item.iconName.isEmpty()) {
            int iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
            Log.d(TAG, "🔍 图标资源查找结果: " + item.iconName + " -> " + iconRes);
            if (iconRes != 0) {
                views.setImageViewResource(iconId, iconRes);
                Log.d(TAG, "✅ 使用预设资源图标: " + item.name + " (类型: " + appType + ", 资源ID: " + iconRes + ")");
                return;
            } else {
                Log.w(TAG, "⚠️ 未找到图标资源: " + item.iconName + " for " + item.name);
            }
        }

        // 2. 如果iconName为空或找不到资源，尝试智能映射
        String smartIconName = getSmartIconName(item.name, item.packageName);
        if (smartIconName != null) {
            int iconRes = context.getResources().getIdentifier(smartIconName, "drawable", context.getPackageName());
            Log.d(TAG, "🔍 智能映射图标资源: " + smartIconName + " -> " + iconRes);
            if (iconRes != 0) {
                views.setImageViewResource(iconId, iconRes);
                Log.d(TAG, "✅ 使用智能映射图标: " + item.name + " (类型: " + appType + ", 资源ID: " + iconRes + ")");
                return;
            }
        }

        // 2.5. 使用智能图标管理器获取真实图标（异步）
        tryLoadSmartIcon(context, views, iconId, item, appType);

        // 2.6. 尝试从官方网站获取真实图标（备用方案）
        tryLoadOfficialIcon(context, views, iconId, item, appType);

        // 3. 尝试获取设备中已安装应用的图标（作为备选）
        try {
            android.graphics.drawable.Drawable installedAppIcon = context.getPackageManager().getApplicationIcon(item.packageName);
            if (installedAppIcon != null) {
                android.graphics.Bitmap iconBitmap = drawableToBitmap(installedAppIcon);
                if (iconBitmap != null) {
                    views.setImageViewBitmap(iconId, iconBitmap);
                    Log.d(TAG, "✅ 使用设备已安装应用图标: " + item.name + " (类型: " + appType + ")");
                    return;
                }
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            Log.d(TAG, "应用未安装: " + item.packageName);
        } catch (Exception e) {
            Log.w(TAG, "获取应用图标失败: " + item.packageName, e);
        }

        // 4. 使用默认图标
        views.setImageViewResource(iconId, defaultIconRes);
        Log.d(TAG, "⚠️ 使用默认图标: " + item.name + " (类型: " + appType + ")");
    }

    /**
     * 智能图标名称映射
     */
    private static String getSmartIconName(String appName, String packageName) {
        // 搜索引擎映射
        if (appName.contains("百度") || packageName.contains("baidu")) return "ic_baidu";
        if (appName.contains("Google") || appName.contains("谷歌") || packageName.contains("google")) return "ic_google";
        if (appName.contains("必应") || appName.contains("Bing") || packageName.contains("bing")) return "ic_bing";
        if (appName.contains("搜狗") || appName.contains("Sogou") || packageName.contains("sogou")) return "ic_sogou";
        if (appName.contains("360") || packageName.contains("360")) return "ic_360";
        if (appName.contains("夸克") || appName.contains("Quark") || packageName.contains("quark")) return "ic_quark";
        if (appName.contains("DuckDuckGo") || packageName.contains("duckduckgo")) return "ic_duckduckgo";

        // AI应用映射
        if (appName.contains("DeepSeek") || packageName.contains("deepseek")) return "ic_deepseek";
        if (appName.contains("智谱") || appName.contains("ChatGLM") || packageName.contains("zhipu")) return "ic_zhipu";
        if (appName.contains("文心") || appName.contains("wenxin")) return "ic_wenxin";
        if (appName.contains("通义") || appName.contains("qianwen")) return "ic_qianwen";
        if (appName.contains("ChatGPT") || packageName.contains("chatgpt")) return "ic_chatgpt";
        if (appName.contains("Claude") || packageName.contains("claude")) return "ic_claude";
        if (appName.contains("Gemini") || packageName.contains("gemini")) return "ic_gemini";
        if (appName.contains("Kimi") || packageName.contains("kimi")) return "ic_kimi";
        if (appName.contains("豆包") || packageName.contains("doubao")) return "ic_doubao";

        return null;
    }

    /**
     * 获取官方网站URL用于下载真实图标
     */
    private static String getOfficialWebsiteUrl(String appName, String packageName) {
        // AI应用官方网站
        if (appName.contains("ChatGPT") || packageName.contains("chatgpt")) return "https://chat.openai.com";
        if (appName.contains("Claude") || packageName.contains("claude")) return "https://claude.ai";
        if (appName.contains("DeepSeek") || packageName.contains("deepseek")) return "https://chat.deepseek.com";
        if (appName.contains("智谱") || appName.contains("ChatGLM") || packageName.contains("zhipu")) return "https://chatglm.cn";
        if (appName.contains("文心") || appName.contains("wenxin")) return "https://yiyan.baidu.com";
        if (appName.contains("通义") || appName.contains("qianwen")) return "https://tongyi.aliyun.com";
        if (appName.contains("Gemini") || packageName.contains("gemini")) return "https://gemini.google.com";
        if (appName.contains("Kimi") || packageName.contains("kimi")) return "https://kimi.moonshot.cn";
        if (appName.contains("豆包") || packageName.contains("doubao")) return "https://www.doubao.com";

        // 搜索引擎官方网站
        if (appName.contains("百度") || packageName.contains("baidu")) return "https://www.baidu.com";
        if (appName.contains("Google") || appName.contains("谷歌") || packageName.contains("google")) return "https://www.google.com";
        if (appName.contains("必应") || appName.contains("Bing") || packageName.contains("bing")) return "https://www.bing.com";
        if (appName.contains("搜狗") || appName.contains("Sogou") || packageName.contains("sogou")) return "https://www.sogou.com";
        if (appName.contains("360") || packageName.contains("360")) return "https://www.so.com";
        if (appName.contains("夸克") || appName.contains("Quark") || packageName.contains("quark")) return "https://quark.sm.cn";
        if (appName.contains("DuckDuckGo") || packageName.contains("duckduckgo")) return "https://duckduckgo.com";

        return null;
    }

    /**
     * 使用智能图标管理器加载真实图标 - 最新优化方案
     */
    private static void tryLoadSmartIcon(Context context, RemoteViews views, int iconId, AppItem item, AppType appType) {
        Log.d(TAG, "🧠 开始智能图标加载: " + item.name + " (类型: " + appType + ")");

        SmartIconManager iconManager = SmartIconManager.getInstance(context);
        iconManager.loadSmartIcon(views, iconId, item.name, item.packageName, () -> {
            Log.d(TAG, "🔄 智能图标加载完成，更新小组件: " + item.name);
            updateWidget(context, views);
        });
    }

    /**
     * 尝试从官方网站加载真实图标 - 使用新的RealIconProvider
     */
    private static void tryLoadOfficialIcon(Context context, RemoteViews views, int iconId, AppItem item, AppType appType) {
        Log.d(TAG, "🌐 开始加载真实官方图标: " + item.name + " (类型: " + appType + ")");

        // 使用新的真实图标提供器
        RealIconProvider iconProvider = RealIconProvider.getInstance(context);
        iconProvider.setRealIcon(views, iconId, item.name, item.packageName, () -> {
            Log.d(TAG, "🔄 真实图标加载完成，更新小组件: " + item.name);
            // 更新小组件显示
            updateWidget(context, views);
        });

        // 同时保留原有的OfficialIconManager作为备用
        OfficialIconManager iconManager = OfficialIconManager.getInstance(context);
        iconManager.getOfficialIcon(item.name, item.packageName, new OfficialIconManager.IconCallback() {
            @Override
            public void onIconLoaded(android.graphics.Bitmap icon) {
                try {
                    views.setImageViewBitmap(iconId, icon);
                    Log.d(TAG, "✅ 使用备用官方下载图标: " + item.name + " (类型: " + appType + ")");
                    updateWidget(context, views);
                } catch (Exception e) {
                    Log.e(TAG, "设置备用官方图标失败: " + item.name, e);
                }
            }

            @Override
            public void onIconLoadFailed() {
                Log.w(TAG, "⚠️ 备用官方图标加载失败: " + item.name);
            }
        });
    }

    /**
     * 测试图标资源是否存在
     */
    private static void testIconResources(Context context) {
        String[] testIcons = {"ic_baidu", "ic_google", "ic_bing", "ic_deepseek", "ic_zhipu", "ic_chatgpt"};
        for (String iconName : testIcons) {
            int iconRes = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
            Log.d(TAG, "🧪 测试图标资源: " + iconName + " -> " + iconRes + " (存在: " + (iconRes != 0) + ")");
        }
    }

    /**
     * 设备优先策略 - 适用于常规应用
     */
    private static void loadDeviceFirstStrategy(Context context, RemoteViews views, int iconId, AppItem item, int defaultIconRes) {
        // 1. 优先尝试获取设备中已安装应用的真实图标
        try {
            android.graphics.drawable.Drawable installedAppIcon = context.getPackageManager().getApplicationIcon(item.packageName);
            if (installedAppIcon != null) {
                android.graphics.Bitmap iconBitmap = drawableToBitmap(installedAppIcon);
                if (iconBitmap != null) {
                    views.setImageViewBitmap(iconId, iconBitmap);
                    Log.d(TAG, "✅ 使用设备已安装应用图标: " + item.name);
                    return;
                }
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            Log.d(TAG, "应用未安装: " + item.packageName);
        } catch (Exception e) {
            Log.w(TAG, "获取应用图标失败: " + item.packageName, e);
        }

        // 2. 尝试使用预设的资源图标
        if (item.iconName != null && !item.iconName.isEmpty()) {
            int iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
            if (iconRes != 0) {
                views.setImageViewResource(iconId, iconRes);
                Log.d(TAG, "✅ 使用预设资源图标: " + item.name);
                return;
            }
        }

        // 3. 使用默认图标
        views.setImageViewResource(iconId, defaultIconRes);
        Log.d(TAG, "⚠️ 使用默认图标: " + item.name);
    }

    /**
     * 应用类型枚举
     */
    private enum AppType {
        AI_APP,         // AI应用
        SEARCH_ENGINE,  // 搜索引擎
        REGULAR_APP     // 常规应用
    }

    /**
     * 判断应用类型
     */
    private static AppType determineAppType(String appName, String packageName) {
        // AI应用判断
        if (isAIApp(appName, packageName)) {
            return AppType.AI_APP;
        }

        // 搜索引擎判断
        if (isSearchEngine(appName, packageName)) {
            return AppType.SEARCH_ENGINE;
        }

        // 默认为常规应用
        return AppType.REGULAR_APP;
    }

    /**
     * 判断是否为AI应用
     */
    private static boolean isAIApp(String appName, String packageName) {
        if (appName == null && packageName == null) return false;

        // 包名判断
        if (packageName != null) {
            String lowerPackage = packageName.toLowerCase();
            if (lowerPackage.contains("deepseek") || lowerPackage.contains("kimi") ||
                lowerPackage.contains("gemini") || lowerPackage.contains("chatglm") ||
                lowerPackage.contains("claude") || lowerPackage.contains("openai") ||
                lowerPackage.contains("gpt") || lowerPackage.contains("perplexity") ||
                lowerPackage.contains("doubao") || lowerPackage.contains("tongyi") ||
                lowerPackage.contains("wenxin") || lowerPackage.contains("xinghuo")) {
                return true;
            }
        }

        // 应用名称判断
        if (appName != null) {
            String lowerName = appName.toLowerCase();
            if (lowerName.contains("deepseek") || lowerName.contains("kimi") ||
                lowerName.contains("gemini") || lowerName.contains("chatglm") ||
                lowerName.contains("claude") || lowerName.contains("chatgpt") ||
                lowerName.contains("gpt") || lowerName.contains("perplexity") ||
                lowerName.contains("豆包") || lowerName.contains("通义") ||
                lowerName.contains("文心") || lowerName.contains("星火") ||
                lowerName.contains("深度求索") || lowerName.contains("月之暗面") ||
                lowerName.contains("智谱") || lowerName.contains("清言")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否为搜索引擎
     */
    private static boolean isSearchEngine(String appName, String packageName) {
        if (appName == null && packageName == null) return false;

        // 包名判断
        if (packageName != null) {
            String lowerPackage = packageName.toLowerCase();
            if (lowerPackage.equals("baidu") || lowerPackage.equals("google") ||
                lowerPackage.equals("bing") || lowerPackage.equals("sogou") ||
                lowerPackage.equals("so360") || lowerPackage.equals("duckduckgo") ||
                lowerPackage.equals("yahoo") || lowerPackage.equals("yandex")) {
                return true;
            }
        }

        // 应用名称判断
        if (appName != null) {
            String lowerName = appName.toLowerCase();
            if (lowerName.contains("百度") || lowerName.contains("google") ||
                lowerName.contains("bing") || lowerName.contains("搜狗") ||
                lowerName.contains("360") || lowerName.contains("duckduckgo") ||
                lowerName.contains("yahoo") || lowerName.contains("yandex") ||
                lowerName.equals("搜索")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 将Drawable转换为Bitmap
     */
    private static android.graphics.Bitmap drawableToBitmap(android.graphics.drawable.Drawable drawable) {
        try {
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            }

            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();

            // 确保尺寸有效
            if (width <= 0) width = 96;
            if (height <= 0) height = 96;

            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return bitmap;
        } catch (Exception e) {
            Log.w(TAG, "Drawable转Bitmap失败", e);
            return null;
        }
    }
}
