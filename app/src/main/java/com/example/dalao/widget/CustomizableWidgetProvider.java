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

        // 获取小组件配置
        WidgetConfig config = getWidgetConfig(context, appWidgetId);

        Log.d(TAG, "小组件配置 - 尺寸: " + config.size + ", 显示搜索框: " + config.showSearchBox);
        Log.d(TAG, "小组件配置 - AI引擎数量: " + config.aiEngines.size() + ", 应用数量: " + config.appSearchItems.size() + ", 搜索引擎数量: " + config.searchEngines.size());

        // 打印具体的配置内容
        Log.d(TAG, "AI引擎列表:");
        for (AppItem item : config.aiEngines) {
            Log.d(TAG, "  - " + item.name + " (" + item.packageName + ")");
        }
        Log.d(TAG, "应用列表:");
        for (AppItem item : config.appSearchItems) {
            Log.d(TAG, "  - " + item.name + " (" + item.packageName + ")");
        }
        Log.d(TAG, "搜索引擎列表:");
        for (AppItem item : config.searchEngines) {
            Log.d(TAG, "  - " + item.name + " (" + item.packageName + ")");
        }

        // 根据尺寸选择布局
        int layoutId = getLayoutForSize(config.size);
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        // 设置搜索框
        setupSearchBox(context, views, config, appWidgetId);

        // 设置应用图标
        setupAppIcons(context, views, config, appWidgetId);

        // 更新小组件
        appWidgetManager.updateAppWidget(appWidgetId, views);
        Log.d(TAG, "小组件更新完成: " + appWidgetId);
    }
    
    private static WidgetConfig getWidgetConfig(Context context, int appWidgetId) {
        return WidgetUtils.getWidgetConfig(context, appWidgetId);
    }
    
    private static int getLayoutForSize(WidgetSize size) {
        switch (size) {
            case SMALL:
                return R.layout.customizable_widget_small;
            case LARGE:
                return R.layout.customizable_widget_large;
            case MEDIUM:
            default:
                return R.layout.customizable_widget_medium;
        }
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

                    // 设置图标 - 使用iTunes服务获取高质量图标
                    try {
                        // 首先尝试使用iTunes服务获取图标
                        WidgetIconLoader.loadIconFromiTunes(context, views, iconViewId, item.name, item.packageName, R.drawable.ic_ai);
                    } catch (Exception e) {
                        // 如果iTunes服务失败，回退到原有逻辑
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

                // 设置图标 - 使用iTunes服务获取高质量图标
                try {
                    // 首先尝试使用iTunes服务获取图标
                    WidgetIconLoader.loadIconFromiTunes(context, views, iconViewId, item.name, item.packageName, android.R.drawable.sym_def_app_icon);
                } catch (Exception e) {
                    // 如果iTunes服务失败，回退到原有逻辑
                    int iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
                    if (iconRes != 0) {
                        views.setImageViewResource(iconViewId, iconRes);
                    } else {
                        // 如果找不到图标，使用默认应用图标
                        views.setImageViewResource(iconViewId, android.R.drawable.sym_def_app_icon);
                    }
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

                // 设置图标 - 使用iTunes服务获取高质量图标
                try {
                    // 首先尝试使用iTunes服务获取图标
                    WidgetIconLoader.loadIconFromiTunes(context, views, iconViewId, item.name, item.packageName, R.drawable.ic_search);
                } catch (Exception e) {
                    // 如果iTunes服务失败，回退到原有逻辑
                    int iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
                    if (iconRes != 0) {
                        views.setImageViewResource(iconViewId, iconRes);
                    } else {
                        // 如果找不到图标，使用默认搜索图标
                        views.setImageViewResource(iconViewId, R.drawable.ic_search);
                    }
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

        // 根据尺寸返回支持的最大AI图标数量
        switch (config.size) {
            case SMALL:
                return 2; // 小尺寸支持2个AI图标
            case MEDIUM:
                return 2; // 中尺寸支持2个AI图标
            case LARGE:
                return 4; // 大尺寸支持4个AI图标
            default:
                return 2;
        }
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
}
