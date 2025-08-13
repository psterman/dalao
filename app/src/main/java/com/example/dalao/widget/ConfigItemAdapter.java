package com.example.dalao.widget;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aifloatingball.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigItemAdapter extends RecyclerView.Adapter<ConfigItemAdapter.ViewHolder> {
    
    private Context context;
    private List<AppItem> availableItems;
    private Set<String> selectedPackageNames;
    
    public ConfigItemAdapter(Context context, List<AppItem> selectedItems, List<AppItem> availableItems) {
        this.context = context;
        this.availableItems = availableItems;
        this.selectedPackageNames = new HashSet<>();
        
        // 初始化选中状态
        for (AppItem item : selectedItems) {
            selectedPackageNames.add(item.packageName);
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
        
        // 设置图标
        if (!item.iconName.isEmpty()) {
            int iconRes = context.getResources().getIdentifier(item.iconName, "drawable", context.getPackageName());
            if (iconRes != 0) {
                holder.iconImageView.setImageResource(iconRes);
                holder.iconImageView.setVisibility(View.VISIBLE);
            } else {
                holder.iconImageView.setVisibility(View.GONE);
            }
        } else {
            holder.iconImageView.setVisibility(View.GONE);
        }
        
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
}
