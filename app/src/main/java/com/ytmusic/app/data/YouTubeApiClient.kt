package com.ytmusic.app.data

import android.content.Context
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.ytmusic.app.auth.MicroGAccountManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 登录后同步：YouTube Data API v3（OAuth Bearer token，不需要 API key）。
 * 全部免费：歌单、喜欢的音乐、订阅，登录后直接拉取，不设任何付费墙。
 * 未登录 / token 失效时返回失败，调用方显示登录提示，不闪退。
 */
object YouTubeApiClient {

    data class Subscription(
        val channelId: String,
        val channelName: String,
        val thumbnailUrl: String
    )

    data class SyncedLibrary(
        val playlists: List<Playlist>,
        val likedTracks: List<Track>,
        val subscriptions: List<Subscription>
    )

    /** 用已登录账号的 token 一次拉齐歌单 + 喜欢 + 订阅 */
    suspend fun getSyncedLibrary(context: Context, email: String): Result<SyncedLibrary> =
        withContext(Dispatchers.IO) {
            try {
                val token = MicroGAccountManager.getYouTubeAuthToken(context, email)
                    ?: return@withContext Result.failure(Exception("拿不到登录 token，请重新登录"))
                val playlists = fetchPlaylists(token)
                val liked = fetchLikedTracks(token)
                val subs = fetchSubscriptions(token)
                Result.success(SyncedLibrary(playlists, liked, subs))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 取某歌单里的歌（Data API playlistItems） */
    suspend fun getApiPlaylistTracks(
        context: Context,
        email: String,
        playlistId: String
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val token = MicroGAccountManager.getYouTubeAuthToken(context, email)
                ?: return@withContext Result.failure(Exception("拿不到登录 token，请重新登录"))
            val url = "https://www.googleapis.com/youtube/v3/playlistItems" +
                "?part=snippet&maxResults=50&playlistId=${URLEncoder.encode(playlistId, "UTF-8")}"
            val root = getJson(url, token)
            val tracks = mutableListOf<Track>()
            for (item in optArray(root, "items")) {
                val snippet = optObject(item, "snippet") ?: continue
                val res = optObject(snippet, "resourceId") ?: continue
                val videoId = optString(res, "videoId") ?: continue
                tracks.add(
                    Track(
                        title = optString(snippet, "title") ?: "",
                        artist = optString(snippet, "videoOwnerChannelTitle") ?: "",
                        durationSec = 0,
                        thumbnailUrl = thumbUrl(snippet),
                        streamUrl = "https://www.youtube.com/watch?v=$videoId"
                    )
                )
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchPlaylists(token: String): List<Playlist> {
        val root = getJson(
            "https://www.googleapis.com/youtube/v3/playlists" +
                "?part=snippet,contentDetails&mine=true&maxResults=50",
            token
        )
        val out = mutableListOf<Playlist>()
        for (item in optArray(root, "items")) {
            val id = optString(item, "id") ?: continue
            val snippet = optObject(item, "snippet") ?: continue
            out.add(
                Playlist(
                    title = optString(snippet, "title") ?: "",
                    thumbnailUrl = thumbUrl(snippet),
                    // Data API 的歌单 id 转成标准 playlist 链接，后续可用 extractor 解析
                    playlistUrl = "https://www.youtube.com/playlist?list=$id"
                )
            )
        }
        return out
    }

    private fun fetchLikedTracks(token: String): List<Track> {
        val root = getJson(
            "https://www.googleapis.com/youtube/v3/videos" +
                "?part=snippet&myRating=like&maxResults=50",
            token
        )
        val out = mutableListOf<Track>()
        for (item in optArray(root, "items")) {
            val id = optString(item, "id") ?: continue
            val snippet = optObject(item, "snippet") ?: continue
            out.add(
                Track(
                    title = optString(snippet, "title") ?: "",
                    artist = optString(snippet, "channelTitle") ?: "",
                    durationSec = 0,
                    thumbnailUrl = thumbUrl(snippet),
                    streamUrl = "https://www.youtube.com/watch?v=$id"
                )
            )
        }
        return out
    }

    private fun fetchSubscriptions(token: String): List<Subscription> {
        val root = getJson(
            "https://www.googleapis.com/youtube/v3/subscriptions" +
                "?part=snippet&mine=true&maxResults=50",
            token
        )
        val out = mutableListOf<Subscription>()
        for (item in optArray(root, "items")) {
            val snippet = optObject(item, "snippet") ?: continue
            val res = optObject(snippet, "resourceId") ?: continue
            out.add(
                Subscription(
                    channelId = optString(res, "channelId") ?: "",
                    channelName = optString(snippet, "title") ?: "",
                    thumbnailUrl = thumbUrl(snippet)
                )
            )
        }
        return out
    }

    private fun getJson(url: String, token: String): JsonObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) {
                throw Exception("登录已过期，请重新登录")
            }
            if (code !in 200..299) {
                throw Exception("同步失败（HTTP $code）")
            }
            val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            return JsonParser.`object`().from(text)
        } finally {
            conn.disconnect()
        }
    }

    // ---- nanojson 防御性读取：字段缺失/类型不对时返回空，不抛异常 ----

    private fun optString(obj: JsonObject, key: String): String? {
        return try {
            obj.getString(key)
        } catch (e: Exception) {
            null
        }
    }

    private fun optObject(obj: JsonObject, key: String): JsonObject? {
        return try {
            obj.getObject(key)
        } catch (e: Exception) {
            null
        }
    }

    private fun optArray(root: JsonObject, key: String): List<JsonObject> {
        return try {
            val arr = root.getArray(key) ?: return emptyList()
            (0 until arr.size()).mapNotNull {
                try {
                    arr.getObject(it)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun thumbUrl(snippet: JsonObject): String {
        val thumbs = optObject(snippet, "thumbnails") ?: return ""
        for (key in listOf("high", "medium", "default")) {
            val t = optObject(thumbs, key)
            val url = t?.let { optString(it, "url") }
            if (!url.isNullOrEmpty()) return url
        }
        return ""
    }
}
