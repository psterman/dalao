package com.example.dalao.widget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfig {
    public WidgetSize size = WidgetSize.SEARCH_SINGLE_ROW;
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
        
        config.size = WidgetSize.valueOf(json.optString("size", "SEARCH_SINGLE_ROW"));
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
    SEARCH_SINGLE_ROW("搜索+1排图标", 3, 2),
    SEARCH_DOUBLE_ROW("搜索+2排图标", 3, 3),
    ICONS_SINGLE_ROW("1排图标", 3, 1),
    ICONS_DOUBLE_ROW("2排图标", 3, 2),
    SINGLE_ICON("单个图标", 1, 1),
    QUAD_ICONS("四个图标", 2, 2),
    SEARCH_ONLY("搜索图标", 1, 1);

    private final String displayName;
    private final int width;
    private final int height;

    WidgetSize(String displayName, int width, int height) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean hasSearchBox() {
        return this == SEARCH_SINGLE_ROW || this == SEARCH_DOUBLE_ROW;
    }

    public boolean isSearchOnly() {
        return this == SEARCH_ONLY;
    }

    public int getMaxIcons() {
        switch (this) {
            case SEARCH_SINGLE_ROW:
            case ICONS_SINGLE_ROW:
            case QUAD_ICONS:
                return 4;
            case SEARCH_DOUBLE_ROW:
            case ICONS_DOUBLE_ROW:
                return 8;
            case SINGLE_ICON:
                return 1;
            case SEARCH_ONLY:
                return 0;
            default:
                return 4;
        }
    }
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
