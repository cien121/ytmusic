package com.ytmusic.app.data

import android.content.Context
import com.ytmusic.app.data.AudioQualitySelector
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 离线下载（官方 Premium 收费功能，这里默认免费开放，不设付费墙）。
 * 下载最高音质音频到 App 私有目录，无需存储权限；媒体库「已下载」里可离线播放。
 * 下载记录存在普通 SharedPreferences（videoId -> 标题/歌手/封面/文件名）。
 */
object DownloadManager {

    data class DownloadedTrack(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String,
        val file: File
    )

    /** streamUrl -> 下载进度 0..100 */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    private val _downloaded = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val downloaded: StateFlow<List<DownloadedTrack>> = _downloaded.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val PREFS = "ytmusic_downloads"

    fun videoIdOf(track: Track): String {
        val url = track.streamUrl
        val vIndex = url.indexOf("v=")
        if (vIndex >= 0) {
            val rest = url.substring(vIndex + 2)
            val end = rest.indexOf('&').takeIf { it >= 0 } ?: rest.length
            return rest.substring(0, end)
        }
        return "track${track.streamUrl.hashCode()}"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 刷新已下载列表（启动、下载完成、删除后调用） */
    fun refresh(context: Context) {
        val dir = downloadDir(context)
        val list = mutableListOf<DownloadedTrack>()
        for ((videoId, value) in prefs(context).all) {
            val parts = (value as? String)?.split("\t") ?: continue
            if (parts.size < 4) continue
            val file = File(dir, parts[3])
            if (file.exists()) {
                list.add(
                    DownloadedTrack(
                        videoId = videoId,
                        title = parts[0],
                        artist = parts[1],
                        thumbnailUrl = parts[2],
                        file = file
                    )
                )
            }
        }
        _downloaded.value = list.sortedBy { it.title }
    }

    fun isDownloaded(context: Context, track: Track): Boolean {
        val id = videoIdOf(track)
        return _downloaded.value.any { it.videoId == id } ||
            prefs(context).contains(id)
    }

    /** 下载：取最高音质流 → 写文件 → 记索引 */
    fun download(context: Context, track: Track) {
        val appContext = context.applicationContext
        val key = track.streamUrl
        scope.launch {
            try {
                _progress.value = _progress.value + (key to 0)
                val result = MusicRepository.getAudioQualities(track.streamUrl)
                val qualities = result.getOrNull()
                if (qualities.isNullOrEmpty()) {
                    _progress.value = _progress.value - key
                    return@launch
                }
                val best = AudioQualitySelector.selectDefault(qualities) ?: qualities[0]
                val ext = when {
                    best.formatName.contains("opus", ignoreCase = true) -> "opus"
                    best.formatName.contains("webm", ignoreCase = true) -> "webm"
                    else -> "m4a"
                }
                val videoId = videoIdOf(track)
                val dir = downloadDir(appContext)
                val file = File(dir, "$videoId.$ext")
                val conn = (URL(best.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                    )
                }
                try {
                    val total = conn.contentLengthLong
                    var done = 0L
                    conn.inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = (done * 100 / total).toInt().coerceIn(0, 100)
                                    _progress.value = _progress.value + (key to p)
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                prefs(appContext).edit()
                    .putString(
                        videoId,
                        listOf(
                            track.title.replace("\t", " "),
                            track.artist.replace("\t", " "),
                            track.thumbnailUrl,
                            file.name
                        ).joinToString("\t")
                    )
                    .apply()
                _progress.value = _progress.value - key
                refresh(appContext)
            } catch (e: Exception) {
                _progress.value = _progress.value - key
            }
        }
    }

    fun delete(context: Context, downloaded: DownloadedTrack) {
        try {
            downloaded.file.delete()
        } catch (e: Exception) {
            // 忽略
        }
        prefs(context).edit().remove(downloaded.videoId).apply()
        refresh(context)
    }

    private fun downloadDir(context: Context): File {
        return File(context.filesDir, "downloads").apply { mkdirs() }
    }
}
