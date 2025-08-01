package com.example.aifloatingball.service

import com.example.aifloatingball.model.Content
import com.example.aifloatingball.model.ContentPlatform
import com.example.aifloatingball.model.Creator

/**
 * 通用内容服务接口
 * 各平台需要实现此接口来提供统一的数据访问
 */
interface ContentService {
    
    /**
     * 获取平台信息
     */
    fun getPlatform(): ContentPlatform
    
    /**
     * 搜索创作者
     * @param keyword 搜索关键词
     * @param page 页码（从1开始）
     * @param pageSize 每页数量
     * @return 创作者列表
     */
    suspend fun searchCreators(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<Creator>>
    
    /**
     * 根据ID获取创作者信息
     * @param uid 创作者唯一ID
     * @return 创作者信息
     */
    suspend fun getCreatorInfo(uid: String): Result<Creator>
    
    /**
     * 获取创作者的内容列表
     * @param uid 创作者唯一ID
     * @param page 页码（从1开始）
     * @param pageSize 每页数量
     * @return 内容列表
     */
    suspend fun getCreatorContents(
        uid: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<Content>>
    
    /**
     * 获取内容详情
     * @param contentId 内容ID
     * @return 内容详情
     */
    suspend fun getContentDetail(contentId: String): Result<Content>
    
    /**
     * 获取热门内容
     * @param page 页码
     * @param pageSize 每页数量
     * @return 热门内容列表
     */
    suspend fun getHotContents(
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<Content>>
    
    /**
     * 验证创作者ID是否有效
     * @param uid 创作者ID
     * @return 是否有效
     */
    suspend fun validateCreatorId(uid: String): Result<Boolean>
    
    /**
     * 获取平台特定的URL格式
     * @param uid 创作者ID
     * @return 创作者主页URL
     */
    fun getCreatorUrl(uid: String): String
    
    /**
     * 获取内容URL
     * @param contentId 内容ID
     * @return 内容链接
     */
    fun getContentUrl(contentId: String): String
}

/**
 * 内容服务工厂
 */
object ContentServiceFactory {
    
    private val services = mutableMapOf<ContentPlatform, ContentService>()
    
    /**
     * 注册内容服务
     */
    fun registerService(platform: ContentPlatform, service: ContentService) {
        services[platform] = service
    }
    
    /**
     * 获取内容服务
     */
    fun getService(platform: ContentPlatform): ContentService? {
        return services[platform]
    }
    
    /**
     * 获取所有已注册的服务
     */
    fun getAllServices(): Map<ContentPlatform, ContentService> {
        return services.toMap()
    }
    
    /**
     * 获取支持的平台列表
     */
    fun getSupportedPlatforms(): List<ContentPlatform> {
        return services.keys.toList()
    }
}

/**
 * 内容服务异常
 */
sealed class ContentServiceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    class NetworkException(message: String, cause: Throwable? = null) : ContentServiceException(message, cause)
    
    class AuthenticationException(message: String) : ContentServiceException(message)
    
    class RateLimitException(message: String) : ContentServiceException(message)
    
    class CreatorNotFoundException(uid: String) : ContentServiceException("Creator not found: $uid")
    
    class ContentNotFoundException(contentId: String) : ContentServiceException("Content not found: $contentId")
    
    class InvalidParameterException(parameter: String) : ContentServiceException("Invalid parameter: $parameter")
    
    class PlatformUnavailableException(platform: ContentPlatform) : ContentServiceException("Platform unavailable: ${platform.displayName}")
    
    class ParseException(message: String, cause: Throwable? = null) : ContentServiceException(message, cause)
}
