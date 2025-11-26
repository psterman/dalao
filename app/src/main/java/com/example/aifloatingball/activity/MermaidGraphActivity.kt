package com.example.aifloatingball.activity

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.UnifiedCollectionManager
import com.example.aifloatingball.model.CollectionRelationEntity
import com.example.aifloatingball.util.MermaidGraphGenerator

/**
 * Mermaid图表展示Activity
 * 用于可视化展示收藏项之间的关联关系
 * 
 * 支持的图表类型：
 * - 流程图（Flowchart）
 * - 思维导图（Mindmap）
 * - 关系图（Entity Relationship Diagram）
 */
class MermaidGraphActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_ROOT_ID = "root_id"
        const val EXTRA_GRAPH_TYPE = "graph_type"
        
        const val TYPE_FLOWCHART = "flowchart"
        const val TYPE_MINDMAP = "mindmap"
        const val TYPE_ER_DIAGRAM = "er_diagram"
    }
    
    private lateinit var webView: WebView
    private lateinit var collectionManager: UnifiedCollectionManager
    private lateinit var graphGenerator: MermaidGraphGenerator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        collectionManager = UnifiedCollectionManager.getInstance(this)
        graphGenerator = MermaidGraphGenerator()
        
        webView = WebView(this)
        setContentView(webView)
        
        setupWebView()
        loadGraph()
    }
    
    /**
     * 设置WebView
     */
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }
    }
    
    /**
     * 加载图表
     */
    private fun loadGraph() {
        val rootId = intent.getStringExtra(EXTRA_ROOT_ID)
        val graphType = intent.getStringExtra(EXTRA_GRAPH_TYPE) ?: TYPE_FLOWCHART
        
        val collections = collectionManager.getAllCollections()
        
        // 从收藏项中提取关联关系
        val relations = mutableListOf<CollectionRelationEntity>()
        collections.forEach { item ->
            item.relations.forEach { relation ->
                relations.add(
                    CollectionRelationEntity(
                        sourceId = item.id,
                        targetId = relation.targetId,
                        relationType = relation.relationType,
                        weight = relation.weight,
                        note = relation.note
                    )
                )
            }
        }
        
        val mermaidCode = when (graphType) {
            TYPE_MINDMAP -> {
                val root = rootId ?: collections.firstOrNull()?.id ?: return
                graphGenerator.generateMindMap(root, collections, relations)
            }
            TYPE_ER_DIAGRAM -> {
                graphGenerator.generateEntityRelationshipDiagram(collections, relations)
            }
            else -> {
                graphGenerator.generateFlowchart(collections, relations, rootId)
            }
        }
        
        val html = graphGenerator.generateHtmlPage(mermaidCode, "收藏项关联图")
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

