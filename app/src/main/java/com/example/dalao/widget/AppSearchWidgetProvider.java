package com.example.dalao.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.aifloatingball.R;
import com.example.aifloatingball.SimpleModeActivity;

public class AppSearchWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_search_widget_layout);

        // 创建点击意图
        Intent intent = new Intent(context, SimpleModeActivity.class);
        intent.putExtra("widget_type", "app_search");
        intent.putExtra("source", "桌面小组件");
        intent.putExtra("search_mode", "app_search");
        intent.putExtra("auto_switch_to_app_search", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            appWidgetId + 1000, // 不同的请求码
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 设置整个小组件的点击事件
        views.setOnClickPendingIntent(R.id.app_search_widget_layout, pendingIntent);

        // 更新小组件
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onEnabled(Context context) {
        // 第一个小组件被添加时调用
    }

    @Override
    public void onDisabled(Context context) {
        // 最后一个小组件被移除时调用
    }
}
