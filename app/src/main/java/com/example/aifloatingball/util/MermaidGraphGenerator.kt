package com.example.aifloatingball.util

import com.example.aifloatingball.model.CollectionRelationEntity
import com.example.aifloatingball.model.RelationType
import com.example.aifloatingball.model.UnifiedCollectionItem

/**
 * Mermaid图表生成器
 * 用于将收藏项及其关联关系转换为Mermaid图表代码
 * 
 * 支持的图表类型：
 * - 流程图（Flowchart）
 * - 思维导图（Mindmap）
 * - 关系图（Entity Relationship Diagram）
 * - 甘特图（Gantt）
 */
class MermaidGraphGenerator {
    
    /**
     * 生成Mermaid流程图代码
     * 
     * @param collections 收藏项列表
     * @param relations 关联关系列表
     * @param rootId 根节点ID（可选，用于指定起始节点）
     * @return Mermaid流程图代码
     */
    fun generateFlowchart(
        collections: List<UnifiedCollectionItem>,
        relations: List<CollectionRelationEntity>,
        rootId: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("```mermaid\n")
        sb.append("graph TD\n")
        
        // 如果没有指定根节点，使用第一个收藏项作为根节点
        val root = rootId ?: collections.firstOrNull()?.id
        
        // 生成节点定义
        collections.forEach { item ->
            val nodeId = sanitizeId(item.id)
            val nodeLabel = sanitizeLabel(item.title.take(50))  // 限制标题长度
            val nodeType = getNodeShape(item.collectionType.name)
            val nodeColor = getNodeColor(item.collectionType.color)
            
            sb.append("    $nodeId$nodeType[\"$nodeLabel\"]\n")
            
            // 如果是根节点，添加样式
            if (item.id == root) {
                sb.append("    style $nodeId fill:#ff6b6b,stroke:#c92a2a,stroke-width:3px\n")
            } else {
                sb.append("    style $nodeId fill:#${String.format("%06X", nodeColor and 0xFFFFFF)},stroke:#333,stroke-width:2px\n")
            }
        }
        
        // 生成边（关联关系）
        relations.forEach { relation ->
            val sourceId = sanitizeId(relation.sourceId)
            val targetId = sanitizeId(relation.targetId)
            val label = "${relation.relationType.icon} ${relation.relationType.displayName}"
            val edgeStyle = getEdgeStyle(relation.relationType, relation.weight)
            
            sb.append("    $sourceId -->|$label| $targetId\n")
            
            // 添加边的样式
            if (edgeStyle.isNotEmpty()) {
                sb.append("    $sourceId -.->|$label| $targetId\n")
            }
        }
        
        sb.append("```\n")
        return sb.toString()
    }
    
    /**
     * 生成Mermaid思维导图代码
     * 
     * @param rootId 根节点ID
     * @param collections 收藏项列表
     * @param relations 关联关系列表
     * @return Mermaid思维导图代码
     */
    fun generateMindMap(
        rootId: String,
        collections: List<UnifiedCollectionItem>,
        relations: List<CollectionRelationEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("```mermaid\n")
        sb.append("mindmap\n")
        
        val rootItem = collections.find { it.id == rootId } ?: return ""
        val rootLabel = sanitizeLabel(rootItem.title)
        
        sb.append("  root(($rootLabel))\n")
        
        // 构建树结构（使用BFS）
        val visited = mutableSetOf<String>()
        val queue = mutableListOf<Pair<String, Int>>(Pair(rootId, 0))
        val maxDepth = 3  // 限制深度避免过于复杂
        
        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.removeAt(0)
            
            if (currentId in visited || depth >= maxDepth) {
                continue
            }
            
            visited.add(currentId)
            
            // 查找所有以当前节点为源的关联
            val children = relations.filter { it.sourceId == currentId }
            
            children.forEach { relation ->
                val childItem = collections.find { it.id == relation.targetId } ?: return@forEach
                
                if (relation.targetId !in visited) {
                    val indent = "    ".repeat(depth + 1)
                    val childLabel = sanitizeLabel(childItem.title)
                    sb.append("$indent$childLabel\n")
                    
                    queue.add(Pair(relation.targetId, depth + 1))
                }
            }
        }
        
        sb.append("```\n")
        return sb.toString()
    }
    
    /**
     * 生成Mermaid关系图代码
     * 
     * @param collections 收藏项列表
     * @param relations 关联关系列表
     * @return Mermaid关系图代码
     */
    fun generateEntityRelationshipDiagram(
        collections: List<UnifiedCollectionItem>,
        relations: List<CollectionRelationEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("```mermaid\n")
        sb.append("erDiagram\n")
        
        // 生成实体定义
        collections.forEach { item ->
            val entityName = sanitizeLabel(item.title.take(20))
            val typeName = item.collectionType.displayName
            
            sb.append("    ${entityName} {\n")
            sb.append("        string id \"${item.id.take(8)}\"\n")
            sb.append("        string title \"${item.title.take(30)}\"\n")
            sb.append("        string type \"$typeName\"\n")
            sb.append("    }\n")
        }
        
        // 生成关系
        relations.forEach { relation ->
            val sourceItem = collections.find { it.id == relation.sourceId } ?: return@forEach
            val targetItem = collections.find { it.id == relation.targetId } ?: return@forEach
            
            val sourceName = sanitizeLabel(sourceItem.title.take(20))
            val targetName = sanitizeLabel(targetItem.title.take(20))
            val relationLabel = "${relation.relationType.icon} ${relation.relationType.displayName}"
            
            // 根据关联类型选择不同的关系符号
            val relationSymbol = when (relation.relationType) {
                RelationType.CONTAINS -> "||--o{"
                RelationType.DEPENDENCY -> "}o--||"
                RelationType.PARENT, RelationType.CHILD -> "||--||"
                else -> "}o--o{"
            }
            
            sb.append("    $sourceName $relationSymbol $targetName : \"$relationLabel\"\n")
        }
        
        sb.append("```\n")
        return sb.toString()
    }
    
    /**
     * 生成Mermaid甘特图代码（用于展示收藏项的时间线）
     * 
     * @param collections 收藏项列表（按时间排序）
     * @return Mermaid甘特图代码
     */
    fun generateGanttChart(
        collections: List<UnifiedCollectionItem>
    ): String {
        val sb = StringBuilder()
        sb.append("```mermaid\n")
        sb.append("gantt\n")
        sb.append("    title 收藏项时间线\n")
        sb.append("    dateFormat YYYY-MM-DD\n")
        sb.append("    section 收藏项\n")
        
        collections.sortedBy { it.collectedTime }.forEach { item ->
            val title = sanitizeLabel(item.title.take(30))
            val startDate = formatDate(item.collectedTime)
            val endDate = formatDate(item.modifiedTime)
            
            sb.append("    $title :$startDate, $endDate\n")
        }
        
        sb.append("```\n")
        return sb.toString()
    }
    
    /**
     * 生成完整的HTML页面（包含Mermaid.js）
     * 
     * @param mermaidCode Mermaid代码
     * @param title 页面标题
     * @return 完整的HTML代码
     */
    fun generateHtmlPage(mermaidCode: String, title: String = "收藏项关联图"): String {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>
                <style>
                    body {
                        margin: 0;
                        padding: 20px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: #f5f5f5;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        background: white;
                        padding: 20px;
                        border-radius: 8px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                    }
                    h1 {
                        margin-top: 0;
                        color: #333;
                    }
                    .mermaid {
                        text-align: center;
                        background: white;
                        padding: 20px;
                        border-radius: 4px;
                    }
                    .controls {
                        margin-bottom: 20px;
                        text-align: center;
                    }
                    button {
                        padding: 10px 20px;
                        margin: 5px;
                        background: #2196F3;
                        color: white;
                        border: none;
                        border-radius: 4px;
                        cursor: pointer;
                    }
                    button:hover {
                        background: #1976D2;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>$title</h1>
                    <div class="mermaid">
                        $mermaidCode
                    </div>
                </div>
                <script>
                    mermaid.initialize({
                        startOnLoad: true,
                        theme: 'default',
                        flowchart: {
                            useMaxWidth: true,
                            htmlLabels: true,
                            curve: 'basis'
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 清理ID，使其符合Mermaid语法要求
     */
    private fun sanitizeId(id: String): String {
        return id.replace(Regex("[^a-zA-Z0-9_]"), "_").take(50)
    }
    
    /**
     * 清理标签文本，转义特殊字符
     */
    private fun sanitizeLabel(label: String): String {
        return label
            .replace("\"", "'")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\\", "\\\\")
            .trim()
    }
    
    /**
     * 获取节点形状（根据收藏类型）
     */
    private fun getNodeShape(typeName: String): String {
        return when (typeName) {
            "AI_REPLY" -> "((("  // 圆形
            "WEB_BOOKMARK" -> "{{{"  // 六边形
            "VIDEO_COLLECTION" -> ">"  // 菱形
            else -> "["  // 矩形
        }
    }
    
    /**
     * 获取节点颜色
     */
    private fun getNodeColor(color: Int): Int {
        return color
    }
    
    /**
     * 获取边的样式
     */
    private fun getEdgeStyle(type: RelationType, weight: Float): String {
        val strokeWidth = (2 + weight * 3).toInt()
        val color = when (type) {
            RelationType.DEPENDENCY -> "#ff6b6b"
            RelationType.REFERENCE -> "#51cf66"
            RelationType.CONTAINS -> "#4dabf7"
            RelationType.SIMILAR -> "#ffd43b"
            else -> "#868e96"
        }
        return "stroke:$color,stroke-width:${strokeWidth}px"
    }
    
    /**
     * 格式化日期
     */
    private fun formatDate(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return format.format(date)
    }
}

