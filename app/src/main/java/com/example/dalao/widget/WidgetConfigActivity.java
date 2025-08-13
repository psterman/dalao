package com.example.dalao.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aifloatingball.R;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfigActivity extends AppCompatActivity {
    
    private static final String TAG = "WidgetConfig";
    private static final String PREFS_NAME = "widget_config";
    
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private WidgetConfig config;
    
    private RadioGroup sizeRadioGroup;
    private CheckBox showSearchBoxCheckBox;
    private RecyclerView aiEnginesRecyclerView;
    private RecyclerView appSearchRecyclerView;
    private RecyclerView searchEnginesRecyclerView;
    
    private ConfigItemAdapter aiEnginesAdapter;
    private ConfigItemAdapter appSearchAdapter;
    private ConfigItemAdapter searchEnginesAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_config);
        
        // 获取小组件ID
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, 
                AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        
        // 设置结果为取消，如果用户没有完成配置就退出
        setResult(RESULT_CANCELED);
        
        initViews();
        loadConfig();
        setupAdapters();
    }
    
    private void initViews() {
        sizeRadioGroup = findViewById(R.id.size_radio_group);
        showSearchBoxCheckBox = findViewById(R.id.show_search_box_checkbox);
        aiEnginesRecyclerView = findViewById(R.id.ai_engines_recycler_view);
        appSearchRecyclerView = findViewById(R.id.app_search_recycler_view);
        searchEnginesRecyclerView = findViewById(R.id.search_engines_recycler_view);
        
        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(v -> saveConfig());
        
        Button cancelButton = findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(v -> finish());
    }
    
    private void loadConfig() {
        config = WidgetUtils.getWidgetConfig(this, appWidgetId);
        Log.d(TAG, "加载配置 - 小组件ID: " + appWidgetId + ", 尺寸: " + config.size + ", 显示搜索框: " + config.showSearchBox);
        Log.d(TAG, "AI引擎数量: " + config.aiEngines.size() + ", 应用数量: " + config.appSearchItems.size() + ", 搜索引擎数量: " + config.searchEngines.size());

        // 设置UI状态
        setRadioButtonForSize(config.size);
        showSearchBoxCheckBox.setChecked(config.showSearchBox);
    }
    

    
    private void setRadioButtonForSize(WidgetSize size) {
        switch (size) {
            case SMALL:
                ((RadioButton) findViewById(R.id.size_small)).setChecked(true);
                break;
            case LARGE:
                ((RadioButton) findViewById(R.id.size_large)).setChecked(true);
                break;
            case MEDIUM:
            default:
                ((RadioButton) findViewById(R.id.size_medium)).setChecked(true);
                break;
        }
    }
    
    private void setupAdapters() {
        // AI引擎适配器
        aiEnginesAdapter = new ConfigItemAdapter(this, config.aiEngines, WidgetUtils.getAvailableAIEngines());
        aiEnginesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        aiEnginesRecyclerView.setAdapter(aiEnginesAdapter);

        // 应用搜索适配器
        appSearchAdapter = new ConfigItemAdapter(this, config.appSearchItems, WidgetUtils.getAvailableApps());
        appSearchRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        appSearchRecyclerView.setAdapter(appSearchAdapter);

        // 搜索引擎适配器
        searchEnginesAdapter = new ConfigItemAdapter(this, config.searchEngines, WidgetUtils.getAvailableSearchEngines());
        searchEnginesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchEnginesRecyclerView.setAdapter(searchEnginesAdapter);
    }
    

    
    private void saveConfig() {
        try {
            Log.d(TAG, "开始保存配置...");

            // 获取尺寸设置
            int checkedSizeId = sizeRadioGroup.getCheckedRadioButtonId();
            if (checkedSizeId == R.id.size_small) {
                config.size = WidgetSize.SMALL;
            } else if (checkedSizeId == R.id.size_large) {
                config.size = WidgetSize.LARGE;
            } else {
                config.size = WidgetSize.MEDIUM;
            }

            // 获取搜索框设置
            config.showSearchBox = showSearchBoxCheckBox.isChecked();

            // 获取选中的项目
            List<AppItem> selectedAI = aiEnginesAdapter.getSelectedItems();
            List<AppItem> selectedApps = appSearchAdapter.getSelectedItems();
            List<AppItem> selectedEngines = searchEnginesAdapter.getSelectedItems();

            Log.d(TAG, "适配器返回的选中项目:");
            Log.d(TAG, "AI引擎: " + selectedAI.size() + " 项");
            for (AppItem item : selectedAI) {
                Log.d(TAG, "  - " + item.name + " (" + item.packageName + ")");
            }
            Log.d(TAG, "应用: " + selectedApps.size() + " 项");
            for (AppItem item : selectedApps) {
                Log.d(TAG, "  - " + item.name + " (" + item.packageName + ")");
            }
            Log.d(TAG, "搜索引擎: " + selectedEngines.size() + " 项");
            for (AppItem item : selectedEngines) {
                Log.d(TAG, "  - " + item.name + " (" + item.packageName + ")");
            }

            config.aiEngines = selectedAI;
            config.appSearchItems = selectedApps;
            config.searchEngines = selectedEngines;

            Log.d(TAG, "保存配置 - 小组件ID: " + appWidgetId + ", 尺寸: " + config.size + ", 显示搜索框: " + config.showSearchBox);
            Log.d(TAG, "最终配置 - AI引擎数量: " + config.aiEngines.size() + ", 应用数量: " + config.appSearchItems.size() + ", 搜索引擎数量: " + config.searchEngines.size());

            // 保存配置
            WidgetUtils.saveWidgetConfig(this, appWidgetId, config);
            
            // 更新小组件
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            CustomizableWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId);
            
            // 设置结果并关闭
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            
            Toast.makeText(this, "小组件配置已保存", Toast.LENGTH_SHORT).show();
            finish();
            
        } catch (Exception e) {
            Log.e(TAG, "保存配置失败", e);
            Toast.makeText(this, "保存配置失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
