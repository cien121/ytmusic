package com.ytmusic.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTube Music 数据层（NewPipeExtractor，公开数据，无需登录）。
 * 搜索优先用 music_songs 过滤（只返回歌曲）；过滤串不被识别或结果为空时
 * 自动回退到无过滤搜索，保证核心功能不断。
 */
object MusicRepository {

    suspend fun searchSongs(query: String): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            try {
                val items = searchWithFilter(query, listOf("music_songs"))
                    .ifEmpty { searchWithFilter(query, null) }
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun searchWithFilter(query: String, filters: List<String>?): List<Track> {
        val extractor = if (filters == null) {
            ServiceList.YouTube.getSearchExtractor(query)
        } else {
            ServiceList.YouTube.getSearchExtractor(query, filters, "")
        }
        extractor.fetchPage()
        val info = SearchInfo.getInfo(extractor)
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                Track(
                    title = item.name,
                    artist = item.uploaderName ?: "",
                    durationSec = item.duration,
                    thumbnailUrl = pickThumbnail(item),
                    streamUrl = item.url
                )
            }
    }

    private fun pickThumbnail(item: StreamInfoItem): String {
        val thumbs = item.thumbnails ?: return ""
        return thumbs.maxByOrNull { it.height }?.url
            ?: thumbs.firstOrNull()?.url
            ?: ""
    }

    /**
     * 取全量音频流（不只默认档），供音质选择用。
     * 注意：getAverageBitrate 返回的是 bps，>1000 时换算成 kbps。
     */
    suspend fun getAudioQualities(streamUrl: String): Result<List<AudioQuality>> =
        withContext(Dispatchers.IO) {
            try {
                val info = StreamInfo.getInfo(ServiceList.YouTube, streamUrl)
                val qualities = info.audioStreams.map { it.toQuality() }
                Result.success(qualities)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun AudioStream.toQuality(): AudioQuality {
        val raw = averageBitrate
        val kbps = if (raw > 1000) raw / 1000 else raw
        return AudioQuality(
            formatName = format?.name ?: "未知",
            bitrateKbps = kbps,
            url = content ?: "",
            itag = itag
        )
    }
}
