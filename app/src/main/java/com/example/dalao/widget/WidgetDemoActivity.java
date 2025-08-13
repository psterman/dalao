package com.example.dalao.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aifloatingball.R;

/**
 * 小组件演示Activity
 * 用于测试和演示可定制小组件功能
 */
public class WidgetDemoActivity extends AppCompatActivity {
    
    private static final String TAG = "WidgetDemo";
    private TextView statusTextView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_demo);
        
        initViews();
        updateStatus();
    }
    
    private void initViews() {
        statusTextView = findViewById(R.id.status_text_view);
        
        Button addWidgetButton = findViewById(R.id.add_widget_button);
        addWidgetButton.setOnClickListener(v -> requestAddWidget());
        
        Button refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> {
            updateAllWidgets();
            updateStatus();
        });
        
        Button configTestButton = findViewById(R.id.config_test_button);
        configTestButton.setOnClickListener(v -> testConfiguration());
    }
    
    private void updateStatus() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, CustomizableWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(provider);
        
        StringBuilder status = new StringBuilder();
        status.append("可定制小组件状态:\n\n");
        status.append("已添加的小组件数量: ").append(widgetIds.length).append("\n\n");
        
        if (widgetIds.length > 0) {
            status.append("小组件ID列表:\n");
            for (int i = 0; i < widgetIds.length; i++) {
                int widgetId = widgetIds[i];
                status.append("- ID: ").append(widgetId);
                
                // 检查是否已配置
                if (WidgetUtils.isWidgetConfigured(this, widgetId)) {
                    status.append(" (已配置)");
                    
                    // 显示配置信息
                    WidgetConfig config = WidgetUtils.getWidgetConfig(this, widgetId);
                    status.append("\n  尺寸: ").append(config.size);
                    status.append("\n  搜索框: ").append(config.showSearchBox ? "显示" : "隐藏");
                    status.append("\n  AI引擎: ").append(config.aiEngines.size()).append("个");
                    status.append("\n  应用: ").append(config.appSearchItems.size()).append("个");
                    status.append("\n  搜索引擎: ").append(config.searchEngines.size()).append("个");
                } else {
                    status.append(" (未配置)");
                }
                
                if (i < widgetIds.length - 1) {
                    status.append("\n\n");
                }
            }
        } else {
            status.append("请点击下方按钮添加小组件到桌面");
        }
        
        statusTextView.setText(status.toString());
    }
    
    private void requestAddWidget() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, CustomizableWidgetProvider.class);
        
        if (appWidgetManager.isRequestPinAppWidgetSupported()) {
            Intent intent = new Intent(this, WidgetConfigActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            
            boolean success = appWidgetManager.requestPinAppWidget(provider, null, null);
            
            if (success) {
                Toast.makeText(this, "请在桌面上放置小组件", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "当前启动器不支持添加小组件", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "当前系统不支持动态添加小组件", Toast.LENGTH_LONG).show();
            
            // 提供手动添加的指导
            Toast.makeText(this, "请长按桌面空白处，选择小组件，找到智能定制小组件", Toast.LENGTH_LONG).show();
        }
    }
    
    private void updateAllWidgets() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, CustomizableWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(provider);
        
        if (widgetIds.length > 0) {
            Intent updateIntent = new Intent(this, CustomizableWidgetProvider.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
            sendBroadcast(updateIntent);
            
            Toast.makeText(this, "已刷新 " + widgetIds.length + " 个小组件", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "更新了 " + widgetIds.length + " 个小组件");
        } else {
            Toast.makeText(this, "没有找到小组件", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void testConfiguration() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, CustomizableWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(provider);
        
        if (widgetIds.length > 0) {
            // 打开第一个小组件的配置页面
            Intent configIntent = new Intent(this, WidgetConfigActivity.class);
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetIds[0]);
            startActivityForResult(configIntent, 1001);
        } else {
            Toast.makeText(this, "请先添加小组件", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "小组件配置已保存", Toast.LENGTH_SHORT).show();
                updateAllWidgets();
                updateStatus();
            } else {
                Toast.makeText(this, "配置已取消", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
