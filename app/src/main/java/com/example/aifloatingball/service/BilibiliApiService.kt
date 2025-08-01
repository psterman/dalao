package com.example.aifloatingball.service

import android.content.Context
import android.util.Log
import com.example.aifloatingball.model.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * B站API服务类
 */
class BilibiliApiService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "BilibiliApiService"
        private const val BASE_URL = "https://api.bilibili.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        
        @Volatile
        private var INSTANCE: BilibiliApiService? = null
        
        fun getInstance(context: Context): BilibiliApiService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BilibiliApiService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(uid: Long): Result<UserInfoData> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/x/web-interface/card?mid=$uid"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/")
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Empty response"))
            }
            
            val apiResponse = gson.fromJson<BilibiliApiResponse<UserInfoData>>(responseBody, object : com.google.gson.reflect.TypeToken<BilibiliApiResponse<UserInfoData>>() {}.type)
            
            if (apiResponse.code != 0) {
                return@withContext Result.failure(IOException("API Error: ${apiResponse.message}"))
            }
            
            val userInfo = apiResponse.data
            if (userInfo != null) {
                Result.success(userInfo)
            } else {
                Result.failure(IOException("User not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user info for uid: $uid", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取用户动态列表
     */
    suspend fun getUserDynamics(uid: Long, offset: String? = null): Result<List<LocalDynamic>> = withContext(Dispatchers.IO) {
        try {
            val url = if (offset.isNullOrEmpty()) {
                "$BASE_URL/x/polymer/web-dynamic/v1/feed/space?host_mid=$uid"
            } else {
                "$BASE_URL/x/polymer/web-dynamic/v1/feed/space?host_mid=$uid&offset=$offset"
            }
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://space.bilibili.com/$uid/dynamic")
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Empty response"))
            }
            
            val apiResponse = gson.fromJson<BilibiliApiResponse<DynamicListData>>(responseBody, object : com.google.gson.reflect.TypeToken<BilibiliApiResponse<DynamicListData>>() {}.type)
            
            if (apiResponse.code != 0) {
                return@withContext Result.failure(IOException("API Error: ${apiResponse.message}"))
            }
            
            val dynamics = apiResponse.data?.items ?: emptyList()
            val localDynamics = dynamics.mapNotNull { dynamic ->
                parseDynamicToLocal(dynamic, uid.toLong())
            }
            
            Result.success(localDynamics)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dynamics for uid: $uid", e)
            Result.failure(e)
        }
    }
    
    /**
     * 将B站动态转换为本地动态格式
     */
    private fun parseDynamicToLocal(dynamic: BilibiliDynamicItem, uid: Long): LocalDynamic? {
        try {
            val author = dynamic.modules?.module_author ?: return null
            val content = dynamic.modules?.module_dynamic
            
            // 简化处理，主要获取基本信息
            val parsedContent = content?.desc?.text ?: ""
            val type = when {
                content?.major?.archive != null -> "video"
                content?.major?.draw != null -> "image"
                else -> "text"
            }
            val title = content?.major?.archive?.title ?: ""
            val jumpUrl = content?.major?.archive?.jump_url ?: ""
            
            return LocalDynamic(
                id = dynamic.id_str ?: "",
                uid = uid,
                authorName = author.name ?: "",
                authorAvatar = author.face ?: "",
                content = parsedContent,
                type = type,
                title = title,
                jumpUrl = jumpUrl,
                timestamp = author.pub_ts?.times(1000) ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dynamic: ${dynamic.id_str}", e)
            return null
        }
    }
    
    /**
     * 搜索用户
     */
    suspend fun searchUsers(keyword: String): Result<List<BilibiliUser>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/x/web-interface/search/type?search_type=bili_user&keyword=$keyword"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://search.bilibili.com/")
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Empty response"))
            }
            
            // 这里需要根据实际的搜索API响应格式来解析
            // 由于B站搜索API比较复杂，这里先返回空列表
            Result.success(emptyList<BilibiliUser>())
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users with keyword: $keyword", e)
            Result.failure(e)
        }
    }
}

/**
 * 辅助数据类，用于返回多个值
 */
private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
