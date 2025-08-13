package com.example.dalao.widget;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

/**
 * 剪贴板辅助工具类
 * 用于安全地读取和验证剪贴板内容
 */
public class ClipboardHelper {
    
    private static final String TAG = "ClipboardHelper";
    private static final int MAX_CLIPBOARD_LENGTH = 1000; // 最大剪贴板内容长度
    
    /**
     * 获取剪贴板中的文本内容
     * @param context 上下文
     * @return 剪贴板文本内容，如果为空或无效则返回null
     */
    public static String getClipboardText(Context context) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            
            if (clipboardManager == null) {
                Log.w(TAG, "ClipboardManager is null");
                return null;
            }
            
            if (!clipboardManager.hasPrimaryClip()) {
                Log.d(TAG, "No primary clip available");
                return null;
            }
            
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) {
                Log.d(TAG, "ClipData is null or empty");
                return null;
            }
            
            ClipData.Item item = clipData.getItemAt(0);
            if (item == null) {
                Log.d(TAG, "ClipData item is null");
                return null;
            }
            
            CharSequence text = item.getText();
            if (text == null) {
                Log.d(TAG, "ClipData text is null");
                return null;
            }
            
            String clipboardText = text.toString().trim();
            
            // 验证剪贴板内容
            if (clipboardText.isEmpty()) {
                Log.d(TAG, "Clipboard text is empty");
                return null;
            }
            
            // 限制长度，避免过长的内容
            if (clipboardText.length() > MAX_CLIPBOARD_LENGTH) {
                clipboardText = clipboardText.substring(0, MAX_CLIPBOARD_LENGTH) + "...";
                Log.d(TAG, "Clipboard text truncated to " + MAX_CLIPBOARD_LENGTH + " characters");
            }
            
            Log.d(TAG, "Retrieved clipboard text: " + clipboardText.substring(0, Math.min(50, clipboardText.length())) + "...");
            return clipboardText;
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading clipboard", e);
            return null;
        }
    }
    
    /**
     * 检查剪贴板是否有有效的文本内容
     * @param context 上下文
     * @return true如果有有效内容，false否则
     */
    public static boolean hasValidClipboardText(Context context) {
        String text = getClipboardText(context);
        return text != null && !text.trim().isEmpty();
    }
    
    /**
     * 验证文本是否适合作为搜索查询
     * @param text 要验证的文本
     * @return true如果适合作为搜索查询，false否则
     */
    public static boolean isValidSearchQuery(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        // 过滤掉一些不适合搜索的内容
        String trimmed = text.trim();
        
        // 太短的内容（少于2个字符）
        if (trimmed.length() < 2) {
            return false;
        }
        
        // 只包含数字和特殊字符的内容
        if (trimmed.matches("^[0-9\\s\\p{Punct}]+$")) {
            return false;
        }
        
        // 看起来像密码或敏感信息的内容
        if (trimmed.matches(".*[a-zA-Z0-9]{20,}.*") && !trimmed.contains(" ")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 清理文本内容，使其更适合作为搜索查询
     * @param text 原始文本
     * @return 清理后的文本
     */
    public static String cleanTextForSearch(String text) {
        if (text == null) {
            return null;
        }
        
        String cleaned = text.trim();
        
        // 移除多余的空白字符
        cleaned = cleaned.replaceAll("\\s+", " ");
        
        // 如果是URL，提取域名或关键部分
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            try {
                // 简单的URL处理，提取域名
                String[] parts = cleaned.split("/");
                if (parts.length > 2) {
                    cleaned = parts[2].replace("www.", "");
                }
            } catch (Exception e) {
                // 如果处理失败，保持原文本
            }
        }
        
        // 限制长度
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100).trim();
        }
        
        return cleaned;
    }
}
