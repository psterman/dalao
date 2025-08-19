package com.example.aifloatingball

import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import org.junit.Test
import org.junit.Assert.*

/**
 * AI联系人列表测试
 */
class AIContactListTest {

    @Test
    fun testAIContactCreation() {
        // 测试AI联系人创建
        val contact = ChatContact(
            id = "ai_chatgpt",
            name = "ChatGPT",
            type = ContactType.AI,
            description = null, // 不显示描述
            isOnline = true,
            lastMessage = "API已配置，可以开始对话",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            isPinned = true,
            customData = mapOf(
                "api_url" to "https://api.openai.com/v1/chat/completions",
                "api_key" to "test_key",
                "model" to "gpt-3.5-turbo",
                "is_configured" to "true"
            )
        )

        assertEquals("ChatGPT", contact.name)
        assertEquals(ContactType.AI, contact.type)
        assertNull(contact.description) // 确保描述为空
        assertTrue(contact.isOnline)
        assertEquals("true", contact.customData["is_configured"])
    }

    @Test
    fun testAIGroupTagMapping() {
        // 测试AI分组标签映射
        val testCases = mapOf(
            "ChatGPT" to "编程助手",
            "Claude" to "编程助手", 
            "DeepSeek" to "编程助手",
            "文心一言" to "写作助手",
            "智谱AI" to "写作助手",
            "通义千问" to "翻译助手",
            "讯飞星火" to "翻译助手",
            "Gemini" to "AI助手",
            "Kimi" to "AI助手"
        )

        testCases.forEach { (aiName, expectedTag) ->
            val actualTag = getAIGroupTag(aiName)
            assertEquals("AI $aiName 的分组标签应该是 $expectedTag", expectedTag, actualTag)
        }
    }

    @Test
    fun testConfiguredAIFiltering() {
        // 测试已配置API的AI过滤
        val allContacts = listOf(
            createTestContact("ChatGPT", true),
            createTestContact("Claude", false),
            createTestContact("DeepSeek", true),
            createTestContact("Gemini", false)
        )

        val configuredOnly = allContacts.filter { contact ->
            contact.customData["is_configured"] == "true"
        }

        assertEquals(2, configuredOnly.size)
        assertTrue(configuredOnly.any { it.name == "ChatGPT" })
        assertTrue(configuredOnly.any { it.name == "DeepSeek" })
        assertFalse(configuredOnly.any { it.name == "Claude" })
        assertFalse(configuredOnly.any { it.name == "Gemini" })
    }

    private fun createTestContact(name: String, isConfigured: Boolean): ChatContact {
        return ChatContact(
            id = "ai_${name.lowercase()}",
            name = name,
            type = ContactType.AI,
            description = null,
            isOnline = isConfigured,
            lastMessage = if (isConfigured) "API已配置" else "未配置API",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            isPinned = isConfigured,
            customData = mapOf(
                "is_configured" to isConfigured.toString()
            )
        )
    }

    private fun getAIGroupTag(aiName: String): String? {
        return when (aiName.lowercase()) {
            "chatgpt", "claude", "deepseek" -> "编程助手"
            "文心一言", "智谱ai" -> "写作助手"
            "通义千问", "讯飞星火" -> "翻译助手"
            "gemini", "kimi" -> "AI助手"
            else -> null
        }
    }
}
