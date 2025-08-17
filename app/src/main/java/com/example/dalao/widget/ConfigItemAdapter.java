package com.example.dalao.widget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aifloatingball.BuildConfig;
import com.example.aifloatingball.R;
import com.example.aifloatingball.manager.PreciseIconManager;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigItemAdapter extends RecyclerView.Adapter<ConfigItemAdapter.ViewHolder> {

    private static final String TAG = "ConfigItemAdapter";
    private Context context;
    private List<AppItem> availableItems;
    private Set<String> selectedPackageNames;

    // 图标缓存
    private static final Map<String, Bitmap> iconCache = new HashMap<>();
    // 品牌检测缓存
    private static String deviceBrand = null;
    private static String deviceModel = null;
    // 精准图标管理器
    private PreciseIconManager preciseIconManager;
    
    public ConfigItemAdapter(Context context, List<AppItem> selectedItems, List<AppItem> availableItems) {
        this.context = context;
        this.availableItems = availableItems;
        this.selectedPackageNames = new HashSet<>();

        // 初始化选中状态
        for (AppItem item : selectedItems) {
            selectedPackageNames.add(item.packageName);
        }

        // 初始化设备信息
        initDeviceInfo();

        // 初始化精准图标管理器
        this.preciseIconManager = new PreciseIconManager(context);

        // 启动图标测试（调试模式）
        if (BuildConfig.DEBUG) {
            testIconLoading();
        }
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_config_app, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppItem item = availableItems.get(position);
        
        holder.nameTextView.setText(item.name);
        holder.packageTextView.setText(item.packageName);
        
        // 设置图标 - 使用统一的精准图标加载系统
        loadPreciseIconForConfig(holder.iconImageView, item.name, item.packageName, item.iconName);
        
        // 设置选中状态
        boolean isSelected = selectedPackageNames.contains(item.packageName);
        holder.checkBox.setChecked(isSelected);
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            boolean newState = !holder.checkBox.isChecked();
            holder.checkBox.setChecked(newState);

            Log.d("ConfigItemAdapter", "项目点击: " + item.name + " (" + item.packageName + ") -> " + newState);
            if (newState) {
                selectedPackageNames.add(item.packageName);
                Log.d("ConfigItemAdapter", "添加到选中列表: " + item.packageName + ", 当前选中数量: " + selectedPackageNames.size());
            } else {
                selectedPackageNames.remove(item.packageName);
                Log.d("ConfigItemAdapter", "从选中列表移除: " + item.packageName + ", 当前选中数量: " + selectedPackageNames.size());
            }
        });

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d("ConfigItemAdapter", "复选框状态变化: " + item.name + " (" + item.packageName + ") -> " + isChecked);
            if (isChecked) {
                selectedPackageNames.add(item.packageName);
                Log.d("ConfigItemAdapter", "复选框添加到选中列表: " + item.packageName + ", 当前选中数量: " + selectedPackageNames.size());
            } else {
                selectedPackageNames.remove(item.packageName);
                Log.d("ConfigItemAdapter", "复选框从选中列表移除: " + item.packageName + ", 当前选中数量: " + selectedPackageNames.size());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return availableItems.size();
    }
    
    public List<AppItem> getSelectedItems() {
        List<AppItem> selectedItems = new ArrayList<>();
        Log.d("ConfigItemAdapter", "getSelectedItems() - 可用项目数量: " + availableItems.size());
        Log.d("ConfigItemAdapter", "getSelectedItems() - 选中的包名数量: " + selectedPackageNames.size());

        for (String packageName : selectedPackageNames) {
            Log.d("ConfigItemAdapter", "选中的包名: " + packageName);
        }

        for (AppItem item : availableItems) {
            if (selectedPackageNames.contains(item.packageName)) {
                selectedItems.add(item);
                Log.d("ConfigItemAdapter", "添加选中项目: " + item.name + " (" + item.packageName + ")");
            }
        }

        Log.d("ConfigItemAdapter", "getSelectedItems() - 返回的选中项目数量: " + selectedItems.size());
        return selectedItems;
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImageView;
        TextView nameTextView;
        TextView packageTextView;
        CheckBox checkBox;
        
        ViewHolder(View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.icon_image_view);
            nameTextView = itemView.findViewById(R.id.name_text_view);
            packageTextView = itemView.findViewById(R.id.package_text_view);
            checkBox = itemView.findViewById(R.id.checkbox);
        }
    }

    /**
     * 初始化设备信息
     */
    private void initDeviceInfo() {
        if (deviceBrand == null) {
            deviceBrand = Build.BRAND.toLowerCase();
            deviceModel = Build.MODEL.toLowerCase();
            Log.d(TAG, "设备品牌: " + deviceBrand + ", 型号: " + deviceModel);
        }
    }

    /**
     * 使用智能图标加载策略 - 配置页面专用
     * 根据应用类型采用不同的优先级策略
     */
    private void loadPreciseIconForConfig(ImageView iconView, String appName, String packageName, String iconName) {
        // 判断应用类型
        AppType appType = determineAppType(appName, packageName);

        switch (appType) {
            case AI_APP:
            case SEARCH_ENGINE:
                // AI应用和搜索引擎：优先使用精准在线图标
                loadOnlineFirstStrategy(iconView, appName, packageName, iconName, appType);
                break;
            case REGULAR_APP:
            default:
                // 常规应用：优先使用设备已安装图标
                loadDeviceFirstStrategy(iconView, appName, packageName, iconName);
                break;
        }
    }

    /**
     * 在线优先策略 - 适用于AI应用和搜索引擎
     */
    private void loadOnlineFirstStrategy(ImageView iconView, String appName, String packageName, String iconName, AppType appType) {
        // 1. 先设置预设资源图标（如果有）
        if (!iconName.isEmpty()) {
            int iconRes = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
            if (iconRes != 0) {
                iconView.setImageResource(iconRes);
                Log.d(TAG, "✅ 使用预设资源图标: " + appName + " (类型: " + appType + ")");
                // 继续异步加载更精准的在线图标
            }
        }

        // 2. 如果没有预设图标，设置默认图标
        if (iconView.getDrawable() == null) {
            int defaultIcon = (appType == AppType.AI_APP) ? R.drawable.ic_ai : R.drawable.ic_search;
            iconView.setImageResource(defaultIcon);
            Log.d(TAG, "⏳ 设置默认图标，开始异步加载: " + appName + " (类型: " + appType + ")");
        }

        // 3. 异步加载精准在线图标
        new AsyncTask<Void, Void, Drawable>() {
            @Override
            protected Drawable doInBackground(Void... voids) {
                try {
                    return getPreciseIconSync(packageName, appName);
                } catch (Exception e) {
                    Log.e(TAG, "获取在线图标失败: " + appName, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                if (drawable != null && iconView != null) {
                    iconView.setImageDrawable(drawable);
                    Log.d(TAG, "✅ 在线精准图标加载成功: " + appName + " (类型: " + appType + ")");
                } else {
                    Log.d(TAG, "❌ 在线图标加载失败，保持当前图标: " + appName);
                }
            }
        }.execute();
    }

    /**
     * 设备优先策略 - 适用于常规应用
     */
    private void loadDeviceFirstStrategy(ImageView iconView, String appName, String packageName, String iconName) {
        // 1. 优先尝试获取设备中已安装应用的真实图标
        Drawable installedAppIcon = getInstalledAppIcon(packageName);
        if (installedAppIcon != null) {
            iconView.setImageDrawable(installedAppIcon);
            Log.d(TAG, "✅ 使用设备已安装应用图标: " + appName);
            return;
        }

        // 2. 尝试使用预设的资源图标
        if (!iconName.isEmpty()) {
            int iconRes = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
            if (iconRes != 0) {
                iconView.setImageResource(iconRes);
                Log.d(TAG, "✅ 使用预设资源图标: " + appName);
                return;
            }
        }

        // 3. 设置默认图标，然后异步加载在线图标
        iconView.setImageResource(R.drawable.ic_apps);
        Log.d(TAG, "⏳ 设置默认图标，开始异步加载: " + appName);

        // 4. 异步加载在线精准图标
        new AsyncTask<Void, Void, Drawable>() {
            @Override
            protected Drawable doInBackground(Void... voids) {
                try {
                    return getPreciseIconSync(packageName, appName);
                } catch (Exception e) {
                    Log.e(TAG, "获取在线图标失败: " + appName, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                if (drawable != null && iconView != null) {
                    iconView.setImageDrawable(drawable);
                    Log.d(TAG, "✅ 在线图标加载成功: " + appName);
                } else {
                    Log.d(TAG, "❌ 在线图标加载失败，保持默认图标: " + appName);
                }
            }
        }.execute();
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
    private AppType determineAppType(String appName, String packageName) {
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
    private boolean isAIApp(String appName, String packageName) {
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
    private boolean isSearchEngine(String appName, String packageName) {
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
     * 获取设备中已安装应用的图标
     */
    private Drawable getInstalledAppIcon(String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "应用未安装: " + packageName);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "获取应用图标失败: " + packageName, e);
            return null;
        }
    }

    /**
     * 同步获取精准图标 (简化版本，避免协程复杂性)
     */
    private Drawable getPreciseIconSync(String packageName, String appName) {
        // 直接尝试从预定义的URL获取图标
        String iconUrl = getSimplePredefinedIconUrl(appName, packageName);
        if (iconUrl != null) {
            return downloadIconFromUrl(iconUrl);
        }

        return null;
    }

    /**
     * 获取简化的预定义图标URL
     */
    private String getSimplePredefinedIconUrl(String appName, String packageName) {
        // AI应用
        if (appName.contains("DeepSeek") || packageName.contains("deepseek")) {
            return "https://chat.deepseek.com/favicon.ico";
        }
        if (appName.contains("Kimi") || packageName.contains("moonshot")) {
            return "https://kimi.moonshot.cn/favicon.ico";
        }
        if (appName.contains("智谱") || packageName.contains("zhipu")) {
            return "https://chatglm.cn/favicon.ico";
        }
        if (appName.contains("Claude") || packageName.contains("claude")) {
            return "https://claude.ai/favicon.ico";
        }
        if (appName.contains("ChatGPT") || packageName.contains("openai")) {
            return "https://chat.openai.com/favicon.ico";
        }
        if (appName.contains("Gemini") || (packageName.contains("google") && appName.contains("Gemini"))) {
            return "https://gemini.google.com/favicon.ico";
        }

        // 搜索引擎
        if (appName.contains("百度") || packageName.contains("baidu")) {
            return "https://www.baidu.com/favicon.ico";
        }
        if (appName.contains("Google") || packageName.contains("google")) {
            return "https://www.google.com/favicon.ico";
        }
        if (appName.contains("Bing") || packageName.contains("bing")) {
            return "https://www.bing.com/favicon.ico";
        }
        if (appName.contains("搜狗") || packageName.contains("sogou")) {
            return "https://www.sogou.com/favicon.ico";
        }
        if (appName.contains("360") || packageName.contains("360")) {
            return "https://www.so.com/favicon.ico";
        }
        if (appName.contains("DuckDuckGo") || packageName.contains("duckduckgo")) {
            return "https://duckduckgo.com/favicon.ico";
        }

        // 常规应用
        if (appName.contains("小红书") || packageName.contains("xingin")) {
            return "https://www.xiaohongshu.com/favicon.ico";
        }
        if (appName.contains("知乎") || packageName.contains("zhihu")) {
            return "https://static.zhihu.com/heifetz/favicon.ico";
        }
        if (appName.contains("抖音") || packageName.contains("aweme")) {
            return "https://www.douyin.com/favicon.ico";
        }
        if (appName.contains("美团") || packageName.contains("meituan")) {
            return "https://www.meituan.com/favicon.ico";
        }
        if (appName.contains("微博") || packageName.contains("weibo")) {
            return "https://weibo.com/favicon.ico";
        }
        if (appName.contains("快手") || packageName.contains("kuaishou")) {
            return "https://www.kuaishou.com/favicon.ico";
        }

        return null;
    }

    /**
     * 从URL下载图标
     */
    private Drawable downloadIconFromUrl(String iconUrl) {
        try {
            URL url = new URL(iconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");

            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                connection.disconnect();

                if (bitmap != null) {
                    return new BitmapDrawable(context.getResources(), bitmap);
                }
            }

            connection.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "下载图标失败: " + iconUrl, e);
        }

        return null;
    }

    /**
     * 多品牌兼容的图标加载系统 (保留作为备用)
     */
    private void loadAppIconForAllBrands(ImageView iconView, String appName, String packageName, String iconName) {
        String cacheKey = packageName + "_" + appName;

        // 1. 检查缓存
        if (iconCache.containsKey(cacheKey)) {
            Bitmap cachedIcon = iconCache.get(cacheKey);
            if (cachedIcon != null) {
                iconView.setImageBitmap(cachedIcon);
                iconView.setVisibility(View.VISIBLE);
                Log.d(TAG, "使用缓存图标: " + appName);
                return;
            }
        }

        // 2. 尝试获取系统安装的应用图标
        try {
            Drawable appIcon = context.getPackageManager().getApplicationIcon(packageName);
            if (appIcon != null) {
                iconView.setImageDrawable(appIcon);
                iconView.setVisibility(View.VISIBLE);
                Log.d(TAG, "使用系统应用图标: " + appName);
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "应用未安装，尝试其他方式: " + appName);
        }

        // 3. 尝试资源图标
        if (!iconName.isEmpty()) {
            int iconRes = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
            if (iconRes != 0) {
                iconView.setImageResource(iconRes);
                iconView.setVisibility(View.VISIBLE);
                Log.d(TAG, "使用资源图标: " + appName);
                return;
            }
        }

        // 4. 设置默认图标
        iconView.setImageResource(R.drawable.ic_ai);
        iconView.setVisibility(View.VISIBLE);

        // 5. 根据品牌特性异步加载在线图标
        if (shouldLoadOnlineIcon()) {
            new BrandCompatibleIconTask(iconView, appName, packageName, cacheKey).execute();
        } else {
            Log.d(TAG, "当前品牌不支持在线图标加载或网络受限: " + deviceBrand);
        }
    }

    /**
     * 判断是否应该加载在线图标
     */
    private boolean shouldLoadOnlineIcon() {
        // 根据不同品牌的特性决定是否加载在线图标
        switch (deviceBrand) {
            case "xiaomi":
            case "redmi":
                return isMiuiNetworkAllowed();
            case "oppo":
                return isOppoNetworkAllowed();
            case "vivo":
                return isVivoNetworkAllowed();
            case "huawei":
            case "honor":
                return isHuaweiNetworkAllowed();
            case "oneplus":
                return isOnePlusNetworkAllowed();
            case "realme":
                return isRealmeNetworkAllowed();
            case "samsung":
                return isSamsungNetworkAllowed();
            default:
                return true; // 其他品牌默认允许
        }
    }

    /**
     * 小米/红米网络权限检测
     */
    private boolean isMiuiNetworkAllowed() {
        // MIUI对后台网络访问有严格限制
        // 检查是否在白名单中或有网络权限
        try {
            return context.checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * OPPO网络权限检测
     */
    private boolean isOppoNetworkAllowed() {
        // ColorOS对应用联网有管控
        // 检查网络状态和权限
        try {
            return context.checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED &&
                   context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * vivo网络权限检测
     */
    private boolean isVivoNetworkAllowed() {
        // Funtouch OS/Origin OS对网络访问有限制
        try {
            return context.checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 华为/荣耀网络权限检测
     */
    private boolean isHuaweiNetworkAllowed() {
        // EMUI/HarmonyOS对网络访问管控较严
        try {
            return context.checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED &&
                   !isHuaweiPowerSavingMode();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 一加网络权限检测
     */
    private boolean isOnePlusNetworkAllowed() {
        // OxygenOS相对宽松
        try {
            return context.checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * realme网络权限检测
     */
    private boolean isRealmeNetworkAllowed() {
        // Realme UI基于ColorOS，类似OPPO
        return isOppoNetworkAllowed();
    }

    /**
     * 三星网络权限检测
     */
    private boolean isSamsungNetworkAllowed() {
        // One UI相对宽松
        try {
            return context.checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测华为是否处于省电模式
     */
    private boolean isHuaweiPowerSavingMode() {
        try {
            // 简单检测，实际可能需要更复杂的逻辑
            return false;
        } catch (Exception e) {
            return true; // 出错时假设处于省电模式
        }
    }

    /**
     * 品牌兼容的图标加载任务
     */
    private class BrandCompatibleIconTask extends AsyncTask<Void, Void, Bitmap> {
        private ImageView iconView;
        private String appName;
        private String packageName;
        private String cacheKey;

        BrandCompatibleIconTask(ImageView iconView, String appName, String packageName, String cacheKey) {
            this.iconView = iconView;
            this.appName = appName;
            this.packageName = packageName;
            this.cacheKey = cacheKey;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            try {
                // 根据品牌选择最适合的图标源
                List<String> iconUrls = getBrandSpecificIconUrls(packageName, appName);

                for (String iconUrl : iconUrls) {
                    Bitmap icon = downloadIconWithBrandOptimization(iconUrl);
                    if (icon != null) {
                        Log.d(TAG, "成功下载图标: " + appName + " from " + iconUrl);
                        return icon;
                    }
                }

                return null;

            } catch (Exception e) {
                Log.e(TAG, "品牌兼容图标加载失败: " + appName, e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap icon) {
            if (icon != null && iconView != null) {
                // 缓存图标
                iconCache.put(cacheKey, icon);

                // 设置图标
                iconView.setImageBitmap(icon);
                iconView.setVisibility(View.VISIBLE);

                Log.d(TAG, "成功设置品牌兼容图标: " + appName);
            } else {
                Log.d(TAG, "品牌兼容图标加载失败，保持默认图标: " + appName);
            }
        }
    }

    /**
     * 根据品牌获取特定的图标URL列表
     */
    private List<String> getBrandSpecificIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // 预定义的高质量图标映射
        String predefinedUrl = getPredefinedIconUrl(packageName, appName);
        if (predefinedUrl != null) {
            urls.add(predefinedUrl);
        }

        // 根据品牌特性选择图标源
        switch (deviceBrand) {
            case "xiaomi":
            case "redmi":
                urls.addAll(getMiuiCompatibleIconUrls(packageName, appName));
                break;
            case "oppo":
                urls.addAll(getOppoCompatibleIconUrls(packageName, appName));
                break;
            case "vivo":
                urls.addAll(getVivoCompatibleIconUrls(packageName, appName));
                break;
            case "huawei":
            case "honor":
                urls.addAll(getHuaweiCompatibleIconUrls(packageName, appName));
                break;
            case "oneplus":
                urls.addAll(getOnePlusCompatibleIconUrls(packageName, appName));
                break;
            case "realme":
                urls.addAll(getRealmeCompatibleIconUrls(packageName, appName));
                break;
            case "samsung":
                urls.addAll(getSamsungCompatibleIconUrls(packageName, appName));
                break;
            default:
                urls.addAll(getGenericIconUrls(packageName, appName));
                break;
        }

        return urls;
    }

    /**
     * 获取预定义图标URL
     */
    private String getPredefinedIconUrl(String packageName, String appName) {
        Map<String, String> iconMapping = new HashMap<>();

        // AI搜索引擎
        iconMapping.put("百度", "https://www.baidu.com/favicon.ico");
        iconMapping.put("DeepSeek", "https://chat.deepseek.com/favicon.ico");
        iconMapping.put("智谱", "https://chatglm.cn/favicon.ico");
        iconMapping.put("文心一言", "https://yiyan.baidu.com/favicon.ico");

        // 社交应用
        iconMapping.put("微信", "https://res.wx.qq.com/a/wx_fed/assets/res/OTE0YTAw.png");
        iconMapping.put("QQ", "https://qzonestyle.gtimg.cn/qzone/qzact/act/external/qq-logo.png");

        // 购物应用
        iconMapping.put("淘宝", "https://www.taobao.com/favicon.ico");
        iconMapping.put("京东", "https://www.jd.com/favicon.ico");

        // 工具应用
        iconMapping.put("支付宝", "https://t.alipayobjects.com/images/T1HHFgXXVeXXXXXXXX.png");
        iconMapping.put("美团", "https://www.meituan.com/favicon.ico");

        // 检查应用名称匹配
        for (Map.Entry<String, String> entry : iconMapping.entrySet()) {
            if (appName.contains(entry.getKey()) || entry.getKey().contains(appName)) {
                return entry.getValue();
            }
        }

        // 检查包名匹配
        if (packageName.contains("baidu")) {
            if (appName.contains("文心")) return iconMapping.get("文心一言");
            return iconMapping.get("百度");
        }
        if (packageName.contains("deepseek")) return iconMapping.get("DeepSeek");
        if (packageName.contains("zhipu")) return iconMapping.get("智谱");
        if (packageName.contains("tencent") && appName.contains("微信")) return iconMapping.get("微信");
        if (packageName.contains("tencent") && appName.contains("QQ")) return iconMapping.get("QQ");
        if (packageName.contains("taobao")) return iconMapping.get("淘宝");
        if (packageName.contains("jd") || packageName.contains("jingdong")) return iconMapping.get("京东");
        if (packageName.contains("alipay")) return iconMapping.get("支付宝");
        if (packageName.contains("meituan")) return iconMapping.get("美团");

        return null;
    }

    /**
     * 小米/红米兼容的图标URL
     */
    private List<String> getMiuiCompatibleIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // 小米应用商店
        urls.add("https://file.market.xiaomi.com/thumbnail/PNG/l114/" + packageName);

        // 小米CDN优化的图标源
        urls.add("https://cdn.cnbj1.fds.api.mi-img.com/mi-mall/app-icon/" + packageName + ".png");

        // 通用源
        urls.addAll(getGenericIconUrls(packageName, appName));

        return urls;
    }

    /**
     * OPPO兼容的图标URL
     */
    private List<String> getOppoCompatibleIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // OPPO软件商店
        urls.add("https://storedl2.nearme.com.cn/assets/common/pkg/" + packageName + "/icon.png");

        // 通用源
        urls.addAll(getGenericIconUrls(packageName, appName));

        return urls;
    }

    /**
     * vivo兼容的图标URL
     */
    private List<String> getVivoCompatibleIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // vivo应用商店
        urls.add("https://appstore-1252774496.file.myqcloud.com/appicon/" + packageName + ".png");

        // 通用源
        urls.addAll(getGenericIconUrls(packageName, appName));

        return urls;
    }

    /**
     * 华为/荣耀兼容的图标URL
     */
    private List<String> getHuaweiCompatibleIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // 华为应用市场
        urls.add("https://appimg.dbankcdn.com/application/icon144/" + packageName + ".png");

        // 荣耀应用市场
        urls.add("https://appgallery.cloud.huawei.com/appdl/" + packageName + "/icon");

        // 通用源（华为网络环境下可能需要特殊处理）
        urls.addAll(getGenericIconUrls(packageName, appName));

        return urls;
    }

    /**
     * 一加兼容的图标URL
     */
    private List<String> getOnePlusCompatibleIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // 一加相对开放，使用通用源
        urls.addAll(getGenericIconUrls(packageName, appName));

        return urls;
    }

    /**
     * realme兼容的图标URL
     */
    private List<String> getRealmeCompatibleIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // realme基于ColorOS，类似OPPO
        urls.addAll(getOppoCompatibleIconUrls(packageName, appName));

        return urls;
    }

    /**
     * 三星兼容的图标URL
     */
    private List<String> getSamsungCompatibleIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // 三星Galaxy Store
        urls.add("https://img.samsungapps.com/productNew/000/" + packageName + "/icon/icon.png");

        // 通用源
        urls.addAll(getGenericIconUrls(packageName, appName));

        return urls;
    }

    /**
     * 通用图标URL
     */
    private List<String> getGenericIconUrls(String packageName, String appName) {
        List<String> urls = new ArrayList<>();

        // Google Play Store (官方)
        urls.add("https://play-lh.googleusercontent.com/apps/" + packageName + "/icon");

        // APKMirror (高质量Android图标)
        urls.add("https://www.apkmirror.com/wp-content/themes/APKMirror/ap_resize/ap_resize.php?src=https://www.apkmirror.com/wp-content/uploads/icons/" + packageName + ".png&w=96&h=96&q=100");

        // 基于域名的Logo API
        String domain = extractDomainFromPackage(packageName);
        if (domain != null) {
            urls.add("https://logo.clearbit.com/" + domain);
            urls.add("https://www.google.com/s2/favicons?domain=" + domain + "&sz=64");
        }

        return urls;
    }

    /**
     * 从包名提取域名
     */
    private String extractDomainFromPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;

        // 常见的包名到域名映射
        Map<String, String> domainMapping = new HashMap<>();
        domainMapping.put("baidu", "baidu.com");
        domainMapping.put("tencent", "qq.com");
        domainMapping.put("taobao", "taobao.com");
        domainMapping.put("jd", "jd.com");
        domainMapping.put("jingdong", "jd.com");
        domainMapping.put("alipay", "alipay.com");
        domainMapping.put("meituan", "meituan.com");
        domainMapping.put("deepseek", "deepseek.com");
        domainMapping.put("zhipu", "chatglm.cn");

        for (Map.Entry<String, String> entry : domainMapping.entrySet()) {
            if (packageName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 尝试从包名反向构造域名
        String[] parts = packageName.split("\\.");
        if (parts.length >= 2) {
            String company = parts[parts.length - 2];
            return company + ".com";
        }

        return null;
    }

    /**
     * 品牌优化的图标下载
     */
    private Bitmap downloadIconWithBrandOptimization(String iconUrl) {
        if (iconUrl == null || iconUrl.isEmpty()) {
            return null;
        }

        try {
            URL url = new URL(iconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 根据品牌设置不同的超时时间
            int timeout = getBrandSpecificTimeout();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            connection.setDoInput(true);

            // 设置品牌特定的User-Agent
            connection.setRequestProperty("User-Agent", getBrandSpecificUserAgent());

            // 华为设备可能需要特殊的请求头
            if (deviceBrand.contains("huawei") || deviceBrand.contains("honor")) {
                connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            }

            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP错误: " + responseCode + " for " + iconUrl + " on " + deviceBrand);
                connection.disconnect();
                return null;
            }

            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            connection.disconnect();

            if (bitmap == null) {
                Log.w(TAG, "无法解码图标: " + iconUrl + " on " + deviceBrand);
                return null;
            }

            // 验证图标质量
            if (bitmap.getWidth() < 16 || bitmap.getHeight() < 16) {
                Log.w(TAG, "图标尺寸太小: " + bitmap.getWidth() + "x" + bitmap.getHeight() + " for " + iconUrl);
                return null;
            }

            Log.d(TAG, "成功下载品牌优化图标: " + iconUrl + " (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ") on " + deviceBrand);
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "品牌优化图标下载失败: " + iconUrl + " on " + deviceBrand, e);
            return null;
        }
    }

    /**
     * 获取品牌特定的超时时间
     */
    private int getBrandSpecificTimeout() {
        switch (deviceBrand) {
            case "huawei":
            case "honor":
                return 3000; // 华为网络环境可能较慢
            case "xiaomi":
            case "redmi":
                return 4000; // MIUI网络管控较严
            case "oppo":
            case "realme":
                return 4000; // ColorOS网络管控
            case "vivo":
                return 4000; // Funtouch OS网络管控
            default:
                return 5000; // 默认超时
        }
    }

    /**
     * 获取品牌特定的User-Agent
     */
    private String getBrandSpecificUserAgent() {
        switch (deviceBrand) {
            case "xiaomi":
            case "redmi":
                return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + deviceModel + ") AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.120 Mobile Safari/537.36 XiaoMi/MiuiBrowser";
            case "huawei":
            case "honor":
                return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + deviceModel + ") AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.120 Mobile Safari/537.36 HuaweiBrowser";
            case "oppo":
                return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + deviceModel + ") AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.120 Mobile Safari/537.36 OppoBrowser";
            case "vivo":
                return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + deviceModel + ") AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.120 Mobile Safari/537.36 VivoBrowser";
            default:
                return "Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0";
        }
    }

    /**
     * 测试图标加载功能（调试模式）
     */
    private void testIconLoading() {
        Log.d(TAG, "🧪 开始测试配置页面图标加载功能...");

        // 测试一些常见的应用
        String[] testApps = {
            "DeepSeek", "com.deepseek.chat",
            "Kimi", "com.moonshot.kimi",
            "智谱", "com.zhipu.chatglm",
            "百度", "com.baidu.searchbox",
            "Google", "com.google.android.googlequicksearchbox",
            "小红书", "com.xingin.xhs",
            "知乎", "com.zhihu.android"
        };

        for (int i = 0; i < testApps.length; i += 2) {
            String appName = testApps[i];
            String packageName = testApps[i + 1];

            String iconUrl = getSimplePredefinedIconUrl(appName, packageName);
            if (iconUrl != null) {
                Log.d(TAG, "✅ 找到图标URL: " + appName + " -> " + iconUrl);
            } else {
                Log.w(TAG, "❌ 未找到图标URL: " + appName + " (" + packageName + ")");
            }
        }

        Log.d(TAG, "🏁 配置页面图标加载测试完成");
    }
}
