package com.ytmusic.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTube Music 数据层（NewPipeExtractor，公开数据，无需登录）。
 * 搜索优先用 music_songs 过滤（只返回歌曲）；过滤串不被识别或结果为空时
 * 自动回退到无过滤搜索，保证核心功能不断。
 * 首页榜单走 YouTube Trending kiosk（默认 kiosk），匿名可用。
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
            .map { it.toTrack() }
    }

    /** 热门榜单：用热门音乐搜索实现（kiosk API 各版本签名不稳定，搜索更可靠）。匿名可用。 */
    suspend fun getTrending(): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            try {
                val items = searchWithFilter("热门音乐", listOf("music_songs"))
                    .ifEmpty { searchWithFilter("热门音乐", null) }
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 歌单搜索（music_playlists 过滤），用于首页横向一键点歌卡片 */
    suspend fun searchPlaylists(query: String): Result<List<Playlist>> =
        withContext(Dispatchers.IO) {
            try {
                val extractor =
                    ServiceList.YouTube.getSearchExtractor(query, listOf("music_playlists"), "")
                extractor.fetchPage()
                val info = SearchInfo.getInfo(extractor)
                val items = info.relatedItems
                    .filterIsInstance<PlaylistInfoItem>()
                    .map { item ->
                        Playlist(
                            title = item.name,
                            thumbnailUrl = pickThumbnailUrl(item.thumbnails),
                            playlistUrl = item.url
                        )
                    }
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 取一个歌单里的歌（点歌单卡片后进详情页播） */
    suspend fun getPlaylistTracks(playlistUrl: String): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            try {
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl)
                val items = info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toTrack() }
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 专辑搜索结果：歌单项（专辑）与单曲项混排，调用方分开展示 */
    data class AlbumSearchResult(
        val playlists: List<Playlist>,
        val tracks: List<Track>
    )

    /** 专辑搜索（music_albums 过滤）：专辑多为歌单项，少数为单曲项 */
    suspend fun searchAlbums(query: String): Result<AlbumSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val extractor =
                    ServiceList.YouTube.getSearchExtractor(query, listOf("music_albums"), "")
                extractor.fetchPage()
                val info = SearchInfo.getInfo(extractor)
                val playlists = mutableListOf<Playlist>()
                val tracks = mutableListOf<Track>()
                for (item in info.relatedItems) {
                    when (item) {
                        is PlaylistInfoItem -> playlists.add(
                            Playlist(
                                title = item.name,
                                thumbnailUrl = pickThumbnailUrl(item.thumbnails),
                                playlistUrl = item.url
                            )
                        )
                        is StreamInfoItem -> tracks.add(item.toTrack())
                    }
                }
                Result.success(AlbumSearchResult(playlists, tracks))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun StreamInfoItem.toTrack(): Track {
        return Track(
            title = name,
            artist = uploaderName ?: "",
            durationSec = duration,
            thumbnailUrl = pickThumbnailUrl(thumbnails),
            streamUrl = url,
            viewCount = viewCount
        )
    }

    private fun pickThumbnail(item: StreamInfoItem): String =
        pickThumbnailUrl(item.thumbnails)

    private fun pickThumbnailUrl(
        thumbs: List<org.schabi.newpipe.extractor.Image>?
    ): String {
        if (thumbs.isNullOrEmpty()) return ""
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
