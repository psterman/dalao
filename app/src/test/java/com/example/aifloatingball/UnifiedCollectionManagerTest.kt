package com.example.aifloatingball

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aifloatingball.manager.UnifiedCollectionManager
import com.example.aifloatingball.model.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * 统一收藏管理器测试
 */
class UnifiedCollectionManagerTest {
    
    private lateinit var context: Context
    private lateinit var manager: UnifiedCollectionManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = UnifiedCollectionManager.getInstance(context)
    }
    
    @Test
    fun testAddCollection() {
        val item = createTestCollectionItem()
        val result = manager.addCollection(item)
        
        assertTrue("添加收藏项应该成功", result)
        
        val retrieved = manager.getCollectionById(item.id)
        assertNotNull("应该能获取到添加的收藏项", retrieved)
        assertEquals("标题应该匹配", item.title, retrieved?.title)
    }
    
    @Test
    fun testUpdateCollection() {
        val item = createTestCollectionItem()
        manager.addCollection(item)
        
        val updated = item.copy(
            title = "更新后的标题",
            priority = Priority.HIGH
        )
        val result = manager.updateCollection(updated)
        
        assertTrue("更新应该成功", result)
        
        val retrieved = manager.getCollectionById(item.id)
        assertEquals("标题应该已更新", "更新后的标题", retrieved?.title)
        assertEquals("优先级应该已更新", Priority.HIGH, retrieved?.priority)
    }
    
    @Test
    fun testDeleteCollection() {
        val item = createTestCollectionItem()
        manager.addCollection(item)
        
        val result = manager.deleteCollection(item.id)
        assertTrue("删除应该成功", result)
        
        val retrieved = manager.getCollectionById(item.id)
        assertNull("删除后应该获取不到", retrieved)
    }
    
    @Test
    fun testSearchByText() {
        val item1 = createTestCollectionItem(title = "Android开发指南")
        val item2 = createTestCollectionItem(title = "iOS开发教程")
        val item3 = createTestCollectionItem(title = "Web前端开发")
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        manager.addCollection(item3)
        
        val results = manager.searchCollections(query = "Android")
        assertEquals("应该找到1个结果", 1, results.size)
        assertEquals("结果标题应该匹配", "Android开发指南", results[0].title)
    }
    
    @Test
    fun testSearchByType() {
        val item1 = createTestCollectionItem(collectionType = CollectionType.AI_REPLY)
        val item2 = createTestCollectionItem(collectionType = CollectionType.WEB_BOOKMARK)
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        
        val results = manager.searchCollections(type = CollectionType.AI_REPLY)
        assertEquals("应该找到1个AI回复收藏", 1, results.size)
        assertEquals("类型应该匹配", CollectionType.AI_REPLY, results[0].collectionType)
    }
    
    @Test
    fun testSearchByTags() {
        val item1 = createTestCollectionItem(customTags = listOf("Android", "开发"))
        val item2 = createTestCollectionItem(customTags = listOf("iOS", "开发"))
        val item3 = createTestCollectionItem(customTags = listOf("Web"))
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        manager.addCollection(item3)
        
        val results = manager.searchCollections(tags = listOf("Android"))
        assertEquals("应该找到1个带Android标签的", 1, results.size)
        
        val results2 = manager.searchCollections(tags = listOf("开发"))
        assertEquals("应该找到2个带开发标签的", 2, results2.size)
    }
    
    @Test
    fun testSortByCollectedTime() {
        val item1 = createTestCollectionItem(collectedTime = System.currentTimeMillis() - 10000)
        val item2 = createTestCollectionItem(collectedTime = System.currentTimeMillis() - 5000)
        val item3 = createTestCollectionItem(collectedTime = System.currentTimeMillis())
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        manager.addCollection(item3)
        
        val all = manager.getAllCollections()
        val sorted = manager.sortCollections(all, SortDimension.COLLECTED_TIME, SortDirection.DESC)
        
        assertTrue("应该按时间降序排列", sorted[0].collectedTime >= sorted[1].collectedTime)
        assertTrue("最新的应该在前面", sorted[0].collectedTime >= sorted[2].collectedTime)
    }
    
    @Test
    fun testSortByPriority() {
        val item1 = createTestCollectionItem(priority = Priority.LOW)
        val item2 = createTestCollectionItem(priority = Priority.HIGH)
        val item3 = createTestCollectionItem(priority = Priority.NORMAL)
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        manager.addCollection(item3)
        
        val all = manager.getAllCollections()
        val sorted = manager.sortCollections(all, SortDimension.PRIORITY, SortDirection.DESC)
        
        assertEquals("优先级最高的应该在前面", Priority.HIGH, sorted[0].priority)
        assertEquals("优先级最低的应该在后面", Priority.LOW, sorted[sorted.size - 1].priority)
    }
    
    @Test
    fun testMoveCollection() {
        val item = createTestCollectionItem(collectionType = CollectionType.AI_REPLY)
        manager.addCollection(item)
        
        val result = manager.moveCollectionToType(item.id, CollectionType.WEB_BOOKMARK)
        assertTrue("移动应该成功", result)
        
        val retrieved = manager.getCollectionById(item.id)
        assertEquals("类型应该已更改", CollectionType.WEB_BOOKMARK, retrieved?.collectionType)
    }
    
    @Test
    fun testBatchDelete() {
        val item1 = createTestCollectionItem()
        val item2 = createTestCollectionItem()
        val item3 = createTestCollectionItem()
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        manager.addCollection(item3)
        
        val deletedCount = manager.deleteCollections(listOf(item1.id, item2.id))
        assertEquals("应该删除2个", 2, deletedCount)
        
        assertNull("item1应该已删除", manager.getCollectionById(item1.id))
        assertNull("item2应该已删除", manager.getCollectionById(item2.id))
        assertNotNull("item3应该还在", manager.getCollectionById(item3.id))
    }
    
    @Test
    fun testBatchMove() {
        val item1 = createTestCollectionItem(collectionType = CollectionType.AI_REPLY)
        val item2 = createTestCollectionItem(collectionType = CollectionType.AI_REPLY)
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        
        val movedCount = manager.moveCollectionsToType(
            listOf(item1.id, item2.id),
            CollectionType.WEB_BOOKMARK
        )
        assertEquals("应该移动2个", 2, movedCount)
        
        val retrieved1 = manager.getCollectionById(item1.id)
        val retrieved2 = manager.getCollectionById(item2.id)
        assertEquals("item1类型应该已更改", CollectionType.WEB_BOOKMARK, retrieved1?.collectionType)
        assertEquals("item2类型应该已更改", CollectionType.WEB_BOOKMARK, retrieved2?.collectionType)
    }
    
    @Test
    fun testExportImportJson() {
        val item1 = createTestCollectionItem(title = "测试1")
        val item2 = createTestCollectionItem(title = "测试2")
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        
        // 导出
        val json = manager.exportToJson()
        assertNotNull("JSON应该不为空", json)
        assertTrue("JSON应该包含数据", json.length > 10)
        
        // 清空数据
        manager.getAllCollections().forEach { manager.deleteCollection(it.id) }
        assertEquals("清空后应该没有数据", 0, manager.getAllCollections().size)
        
        // 导入
        val importedCount = manager.importFromJson(json, merge = false)
        assertEquals("应该导入2个", 2, importedCount)
        
        val all = manager.getAllCollections()
        assertEquals("导入后应该有2个", 2, all.size)
    }
    
    @Test
    fun testExportCsv() {
        val item = createTestCollectionItem(
            title = "测试标题",
            content = "测试内容",
            customTags = listOf("标签1", "标签2")
        )
        manager.addCollection(item)
        
        val csv = manager.exportToCsv()
        assertNotNull("CSV应该不为空", csv)
        assertTrue("CSV应该包含标题行", csv.contains("ID,标题"))
        assertTrue("CSV应该包含数据", csv.contains("测试标题"))
    }
    
    @Test
    fun testGetCollectionsByType() {
        val item1 = createTestCollectionItem(collectionType = CollectionType.AI_REPLY)
        val item2 = createTestCollectionItem(collectionType = CollectionType.AI_REPLY)
        val item3 = createTestCollectionItem(collectionType = CollectionType.WEB_BOOKMARK)
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        manager.addCollection(item3)
        
        val aiReplies = manager.getCollectionsByType(CollectionType.AI_REPLY)
        assertEquals("应该找到2个AI回复", 2, aiReplies.size)
        
        val webBookmarks = manager.getCollectionsByType(CollectionType.WEB_BOOKMARK)
        assertEquals("应该找到1个网页收藏", 1, webBookmarks.size)
    }
    
    @Test
    fun testGetAllCustomTags() {
        val item1 = createTestCollectionItem(customTags = listOf("Android", "开发"))
        val item2 = createTestCollectionItem(customTags = listOf("iOS", "开发"))
        val item3 = createTestCollectionItem(customTags = listOf("Web"))
        
        manager.addCollection(item1)
        manager.addCollection(item2)
        manager.addCollection(item3)
        
        val allTags = manager.getAllCustomTags()
        assertEquals("应该找到4个唯一标签", 4, allTags.size)
        assertTrue("应该包含Android", allTags.contains("Android"))
        assertTrue("应该包含开发", allTags.contains("开发"))
    }
    
    /**
     * 创建测试收藏项
     */
    private fun createTestCollectionItem(
        title: String = "测试标题",
        content: String = "测试内容",
        collectionType: CollectionType = CollectionType.AI_REPLY,
        sourceLocation: String = "测试来源",
        sourceDetail: String? = null,
        collectedTime: Long = System.currentTimeMillis(),
        customTags: List<String> = emptyList(),
        priority: Priority = Priority.NORMAL,
        completionStatus: CompletionStatus = CompletionStatus.NOT_STARTED,
        likeLevel: Int = 0,
        emotionTag: EmotionTag = EmotionTag.NEUTRAL,
        isEncrypted: Boolean = false,
        reminderTime: Long? = null
    ): UnifiedCollectionItem {
        return UnifiedCollectionItem(
            title = title,
            content = content,
            preview = content.take(200),
            collectionType = collectionType,
            sourceLocation = sourceLocation,
            sourceDetail = sourceDetail,
            collectedTime = collectedTime,
            customTags = customTags,
            priority = priority,
            completionStatus = completionStatus,
            likeLevel = likeLevel,
            emotionTag = emotionTag,
            isEncrypted = isEncrypted,
            reminderTime = reminderTime
        )
    }
}







