package com.example.aifloatingball.service

import android.content.Context
import android.util.Log
import com.example.aifloatingball.model.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * B站内容服务实现
 */
class BilibiliContentService(private val context: Context) : ContentService {
    
    companion object {
        private const val TAG = "BilibiliContentService"
        private const val BASE_URL = "https://api.bilibili.com"
        
        @Volatile
        private var INSTANCE: BilibiliContentService? = null
        
        fun getInstance(context: Context): BilibiliContentService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BilibiliContentService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    override fun getPlatform(): ContentPlatform = ContentPlatform.BILIBILI
    
    override suspend fun searchCreators(keyword: String, page: Int, pageSize: Int): Result<List<Creator>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/x/web-interface/search/type?search_type=bili_user&keyword=$keyword&page=$page&page_size=$pageSize"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(ContentServiceException.NetworkException("HTTP ${response.code}"))
            }
            
            // 解析搜索结果
            val searchResult = gson.fromJson<BilibiliSearchResponse>(responseBody, object : TypeToken<BilibiliSearchResponse>() {}.type)
            
            if (searchResult.code != 0) {
                return@withContext Result.failure(ContentServiceException.ParseException("API Error: ${searchResult.message}"))
            }
            
            val creators = searchResult.data?.result?.map { userInfo ->
                Creator(
                    uid = userInfo.mid.toString(),
                    platform = ContentPlatform.BILIBILI,
                    name = userInfo.uname,
                    avatar = userInfo.upic,
                    description = userInfo.usign,
                    followerCount = userInfo.fans,
                    isVerified = userInfo.official_verify?.type == 0,
                    verifyInfo = userInfo.official_verify?.desc ?: "",
                    profileUrl = "https://space.bilibili.com/${userInfo.mid}"
                )
            } ?: emptyList()
            
            Result.success(creators)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching creators", e)
            Result.failure(ContentServiceException.NetworkException("Search failed", e))
        }
    }
    
    override suspend fun getCreatorInfo(uid: String): Result<Creator> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/x/space/acc/info?mid=$uid"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(ContentServiceException.NetworkException("HTTP ${response.code}"))
            }
            
            val apiResponse = gson.fromJson<BilibiliApiResponse<UserInfoData>>(responseBody, object : TypeToken<BilibiliApiResponse<UserInfoData>>() {}.type)
            
            if (apiResponse.code != 0) {
                return@withContext Result.failure(ContentServiceException.CreatorNotFoundException(uid))
            }
            
            val userInfo = apiResponse.data ?: return@withContext Result.failure(ContentServiceException.CreatorNotFoundException(uid))
            
            val creator = Creator(
                uid = userInfo.mid.toString(),
                platform = ContentPlatform.BILIBILI,
                name = userInfo.name,
                avatar = userInfo.face,
                description = userInfo.sign,
                followerCount = userInfo.follower,
                isVerified = userInfo.official?.type == 0,
                verifyInfo = userInfo.official?.title ?: "",
                profileUrl = "https://space.bilibili.com/${userInfo.mid}"
            )
            
            Result.success(creator)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting creator info", e)
            Result.failure(ContentServiceException.NetworkException("Get creator info failed", e))
        }
    }
    
    override suspend fun getCreatorContents(uid: String, page: Int, pageSize: Int): Result<List<Content>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/x/polymer/web-dynamic/v1/feed/space?host_mid=$uid&page=$page&page_size=$pageSize"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(ContentServiceException.NetworkException("HTTP ${response.code}"))
            }
            
            val apiResponse = gson.fromJson<BilibiliApiResponse<DynamicListData>>(responseBody, object : TypeToken<BilibiliApiResponse<DynamicListData>>() {}.type)
            
            if (apiResponse.code != 0) {
                return@withContext Result.failure(ContentServiceException.ParseException("API Error: ${apiResponse.message}"))
            }
            
            val contents = apiResponse.data?.items?.mapNotNull { item ->
                convertToContent(item, uid)
            } ?: emptyList()
            
            Result.success(contents)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting creator contents", e)
            Result.failure(ContentServiceException.NetworkException("Get contents failed", e))
        }
    }
    
    override suspend fun getContentDetail(contentId: String): Result<Content> {
        // B站动态详情API实现
        return Result.failure(ContentServiceException.PlatformUnavailableException(ContentPlatform.BILIBILI))
    }
    
    override suspend fun getHotContents(page: Int, pageSize: Int): Result<List<Content>> {
        // B站热门内容API实现
        return Result.failure(ContentServiceException.PlatformUnavailableException(ContentPlatform.BILIBILI))
    }
    
    override suspend fun validateCreatorId(uid: String): Result<Boolean> {
        return try {
            val result = getCreatorInfo(uid)
            Result.success(result.isSuccess)
        } catch (e: Exception) {
            Result.success(false)
        }
    }
    
    override fun getCreatorUrl(uid: String): String {
        return "https://space.bilibili.com/$uid"
    }
    
    override fun getContentUrl(contentId: String): String {
        return "https://www.bilibili.com/video/$contentId"
    }
    
    /**
     * 将B站动态转换为通用内容格式
     */
    private fun convertToContent(item: BilibiliDynamicItem, creatorUid: String): Content? {
        return try {
            val author = item.modules?.module_author
            val dynamic = item.modules?.module_dynamic
            
            Content(
                id = item.id_str ?: return null,
                platform = ContentPlatform.BILIBILI,
                creatorUid = creatorUid,
                creatorName = author?.name ?: "",
                creatorAvatar = author?.face ?: "",
                title = dynamic?.major?.archive?.title ?: dynamic?.desc?.text ?: "",
                description = dynamic?.desc?.text ?: "",
                contentType =  determineContentType(item),
                coverUrl = dynamic?.major?.archive?.cover ?: "",
                contentUrl = dynamic?.major?.archive?.jump_url ?: "",
                publishTime = item.modules?.module_author?.pub_ts?.times(1000) ?: 0,
                viewCount = dynamic?.major?.archive?.stat?.play ?: 0,
                likeCount = item.modules?.module_stat?.like?.count ?: 0,
                commentCount = item.modules?.module_stat?.comment?.count ?: 0,
                shareCount = item.modules?.module_stat?.forward?.count ?: 0,
                duration = dynamic?.major?.archive?.duration_text?.let { parseDuration(it) } ?: 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert dynamic item", e)
            null
        }
    }
    
    /**
     * 确定内容类型
     */
    private fun determineContentType(item: BilibiliDynamicItem): ContentType {
        val major = item.modules?.module_dynamic?.major
        return when (major?.type) {
            "MAJOR_TYPE_ARCHIVE" -> ContentType.VIDEO
            "MAJOR_TYPE_DRAW" -> ContentType.IMAGE
            "MAJOR_TYPE_ARTICLE" -> ContentType.ARTICLE
            "MAJOR_TYPE_LIVE" -> ContentType.LIVE
            else -> ContentType.TEXT
        }
    }
    
    /**
     * 解析时长字符串
     */
    private fun parseDuration(durationText: String): Long {
        return try {
            val parts = durationText.split(":")
            when (parts.size) {
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
}

// B站API响应数据类
data class BilibiliApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class BilibiliSearchResponse(
    val code: Int,
    val message: String,
    val data: SearchData?
)

data class SearchData(
    val result: List<SearchUserInfo>?
)

data class SearchUserInfo(
    val mid: Long,
    val uname: String,
    val upic: String,
    val usign: String,
    val fans: Long,
    val official_verify: OfficialVerify?
)

data class OfficialVerify(
    val type: Int,
    val desc: String
)

// 用户信息数据类
data class UserInfoData(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val follower: Long,
    val official: Official?
)

data class Official(
    val type: Int,
    val title: String
)

// 动态列表数据类
data class DynamicListData(
    val items: List<BilibiliDynamicItem>?
)

data class BilibiliDynamicItem(
    val id_str: String?,
    val modules: DynamicModules?
)

data class DynamicModules(
    val module_author: ModuleAuthor?,
    val module_dynamic: ModuleDynamic?,
    val module_stat: ModuleStat?
)

data class ModuleAuthor(
    val name: String?,
    val face: String?,
    val pub_ts: Long?
)

data class ModuleDynamic(
    val desc: DynamicDesc?,
    val major: DynamicMajor?
)

data class DynamicDesc(
    val text: String?
)

data class DynamicMajor(
    val type: String?,
    val archive: DynamicArchive?,
    val draw: DynamicDraw?
)

data class DynamicArchive(
    val title: String?,
    val cover: String?,
    val jump_url: String?,
    val duration_text: String?,
    val stat: ArchiveStat?
)

data class ArchiveStat(
    val play: Long?
)

data class DynamicDraw(
    val items: List<DrawItem>?
)

data class DrawItem(
    val src: String?
)

data class ModuleStat(
    val like: StatCount?,
    val comment: StatCount?,
    val forward: StatCount?
)

data class StatCount(
    val count: Long?
)
