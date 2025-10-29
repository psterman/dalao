package com.example.aifloatingball.utils

import android.graphics.Bitmap
import android.util.LruCache

/**
 * 缓存群聊头像与成员图标，减少重复下载与合成。
 */
object GroupAvatarCache {
    private val groupCache: LruCache<String, Bitmap>
    private val memberCache: LruCache<String, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // 将可用内存的一小部分用于缓存
        groupCache = object : LruCache<String, Bitmap>(maxMemory / 16) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
        memberCache = object : LruCache<String, Bitmap>(maxMemory / 24) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun makeGroupKey(members: List<String>, size: Int = 128): String {
        val normalized = members.map { it.trim().lowercase() }.sorted().joinToString("|")
        return "group:$normalized@$size"
    }

    fun getGroupAvatar(key: String): Bitmap? = groupCache.get(key)
    fun putGroupAvatar(key: String, bitmap: Bitmap) { groupCache.put(key, bitmap) }

    fun memberKey(name: String): String = name.trim().lowercase()
    fun getMemberIcon(name: String): Bitmap? = memberCache.get(memberKey(name))
    fun putMemberIcon(name: String, bitmap: Bitmap) { memberCache.put(memberKey(name), bitmap) }

    fun clear() {
        groupCache.evictAll(); memberCache.evictAll()
    }
}

