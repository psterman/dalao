package com.example.aifloatingball

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebView
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 文本选择辅助类，处理WebView中的文本选择操作
 */
class TextSelectionHelper {
    companion object {
        private const val TAG = "TextSelectionHelper"
        
        /**
         * 处理文本选择时的JSON解析错误
         */
        fun handleTextSelectionResult(result: String): JSONObject? {
            return try {
                // 首先尝试直接解析JSON字符串
                JSONObject(result.trim('"').replace("\\\\\"", "\""))
            } catch (e: Exception) {
                Log.e(TAG, "JSON解析失败，尝试其他方式: ${e.message}")
                
                try {
                    // 尝试清理JSON字符串中的转义字符后再解析
                    val cleanedResult = result.trim('"')
                        .replace("\\\\", "\\")
                        .replace("\\\"", "\"")
                        .replace("\\\\\\\"", "\"")
                    
                    JSONObject(cleanedResult)
                } catch (e2: Exception) {
                    Log.e(TAG, "第二次JSON解析也失败: ${e2.message}")
                    
                    // 如果仍然失败，尝试构建一个空的JSONObject
                    try {
                        JSONObject("""{"text":"","left":{"x":0,"y":0},"right":{"x":0,"y":0}}""")
                    } catch (e3: Exception) {
                        Log.e(TAG, "构建默认JSON也失败: ${e3.message}")
                        null
                    }
                }
            }
        }

        /**
         * 激活文本选择功能
         * @param webView WebView实例
         * @param event 触摸事件
         * @param isMenuShowing 菜单是否显示中的标志
         * @param isMenuAnimating 菜单是否正在动画中的标志
         * @param hideTextSelectionMenu 隐藏菜单的回调
         * @param showTextSelectionMenuSafely 显示菜单的回调
         * @param textSelectionManager 文本选择管理器
         * @param onSelectedText 选中文本的回调
         */
        fun activateSelection(
            webView: WebView, 
            event: MotionEvent,
            isMenuShowing: AtomicBoolean,
            isMenuAnimating: AtomicBoolean,
            hideTextSelectionMenu: () -> Unit,
            showTextSelectionMenuSafely: (WebView, Int, Int) -> Unit,
            textSelectionManager: Any?,
            onSelectedText: (String) -> Unit
        ) {
            if (isMenuShowing.get() || isMenuAnimating.get()) {
                hideTextSelectionMenu()
            }
            
            val script = """
                (function() {
                    try {
                        var x = ${event.x};
                        var y = ${event.y};
                        
                        var elem = document.elementFromPoint(x, y);
                        if (!elem) return null;
                        
                        var isText = elem.nodeType === 3 || 
                                    (elem.nodeType === 1 && 
                                     (elem.tagName === 'P' || 
                                      elem.tagName === 'SPAN' || 
                                      elem.tagName === 'DIV' || 
                                      /H[1-6]/.test(elem.tagName)));
                        
                        if (isText) {
                            var range = document.caretRangeFromPoint(x, y);
                            if (!range) return null;
                            
                            var selection = window.getSelection();
                            selection.removeAllRanges();
                            selection.addRange(range);
                            
                            var rect = range.getBoundingClientRect();
                            var selectedText = selection.toString()
                                .replace(/[\n\r]/g, ' ')  // 替换换行为空格
                                .replace(/[\\"]/g, '\\\\"');  // 转义引号
                            
                            return JSON.stringify({
                                text: selectedText,
                                left: {
                                    x: Math.round(rect.left + window.scrollX),
                                    y: Math.round(rect.bottom + window.scrollY)
                                },
                                right: {
                                    x: Math.round(rect.right + window.scrollX),
                                    y: Math.round(rect.bottom + window.scrollY)
                                }
                            });
                        }
                        return null;
                    } catch(e) {
                        console.error('Selection error:', e);
                        return null;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(script) { result ->
                try {
                    if (result != "null") {
                        // 使用修复的方法解析JSON
                        val json = handleTextSelectionResult(result)
                        
                        if (json != null) {
                            val selectedText = json.optString("text", "")
                            if (selectedText.isNotEmpty()) {
                                onSelectedText(selectedText)
                                
                                Handler(Looper.getMainLooper()).post {
                                    try {
                                        val leftPos = json.getJSONObject("left")
                                        val rightPos = json.getJSONObject("right")
                                        
                                        if (textSelectionManager != null && textSelectionManager is com.example.aifloatingball.manager.TextSelectionManager) {
                                            textSelectionManager.showSelectionHandles(
                                                leftPos.getInt("x"),
                                                leftPos.getInt("y"),
                                                rightPos.getInt("x"),
                                                rightPos.getInt("y")
                                            )
                                        }
                                        
                                        showTextSelectionMenuSafely(webView, 
                                            event.rawX.toInt(), 
                                            event.rawY.toInt()
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "处理选择位置信息失败", e)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理文本选择结果失败", e)
                }
            }
        }
    }
} 