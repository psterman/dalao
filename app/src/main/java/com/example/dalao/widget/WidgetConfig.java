package com.example.dalao.widget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfig {
    public WidgetSize size = WidgetSize.MEDIUM;
    public boolean showSearchBox = true;
    public List<AppItem> aiEngines = new ArrayList<>();
    public List<AppItem> appSearchItems = new ArrayList<>();
    public List<AppItem> searchEngines = new ArrayList<>();
    
    public String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("size", size.name());
        json.put("showSearchBox", showSearchBox);
        
        // AI引擎
        JSONArray aiArray = new JSONArray();
        for (AppItem item : aiEngines) {
            aiArray.put(item.toJson());
        }
        json.put("aiEngines", aiArray);
        
        // 应用搜索项
        JSONArray appArray = new JSONArray();
        for (AppItem item : appSearchItems) {
            appArray.put(item.toJson());
        }
        json.put("appSearchItems", appArray);
        
        // 搜索引擎
        JSONArray searchArray = new JSONArray();
        for (AppItem item : searchEngines) {
            searchArray.put(item.toJson());
        }
        json.put("searchEngines", searchArray);
        
        return json.toString();
    }
    
    public static WidgetConfig fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        WidgetConfig config = new WidgetConfig();
        
        config.size = WidgetSize.valueOf(json.optString("size", "MEDIUM"));
        config.showSearchBox = json.optBoolean("showSearchBox", true);
        
        // AI引擎
        JSONArray aiArray = json.optJSONArray("aiEngines");
        if (aiArray != null) {
            for (int i = 0; i < aiArray.length(); i++) {
                config.aiEngines.add(AppItem.fromJson(aiArray.getJSONObject(i)));
            }
        }
        
        // 应用搜索项
        JSONArray appArray = json.optJSONArray("appSearchItems");
        if (appArray != null) {
            for (int i = 0; i < appArray.length(); i++) {
                config.appSearchItems.add(AppItem.fromJson(appArray.getJSONObject(i)));
            }
        }
        
        // 搜索引擎
        JSONArray searchArray = json.optJSONArray("searchEngines");
        if (searchArray != null) {
            for (int i = 0; i < searchArray.length(); i++) {
                config.searchEngines.add(AppItem.fromJson(searchArray.getJSONObject(i)));
            }
        }
        
        return config;
    }
}

enum WidgetSize {
    SMALL,   // 2x1
    MEDIUM,  // 4x2
    LARGE    // 4x3
}

class AppItem {
    public String name;
    public String packageName;
    public String iconName;
    
    public AppItem() {}
    
    public AppItem(String name, String packageName, String iconName) {
        this.name = name;
        this.packageName = packageName;
        this.iconName = iconName;
    }
    
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("packageName", packageName);
        json.put("iconName", iconName);
        return json;
    }
    
    public static AppItem fromJson(JSONObject json) throws JSONException {
        AppItem item = new AppItem();
        item.name = json.optString("name", "");
        item.packageName = json.optString("packageName", "");
        item.iconName = json.optString("iconName", "");
        return item;
    }
}
