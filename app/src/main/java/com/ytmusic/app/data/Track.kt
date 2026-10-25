package com.ytmusic.app.data

/** 一首歌（搜索/榜单结果项） */
data class Track(
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String,
    /** YouTube / YouTube Music 的 watch 链接 */
    val streamUrl: String,
    /** 播放量，-1 表示未知 */
    val viewCount: Long = -1
) {
    val durationText: String
        get() {
            if (durationSec <= 0) return ""
            return "%d:%02d".format(durationSec / 60, durationSec % 60)
        }

    /** 官方 App 风格：1.6亿 / 293万 / 516万 */
    val viewCountText: String
        get() {
            if (viewCount < 0) return ""
            return when {
                viewCount >= 100_000_000 -> {
                    val v = viewCount / 100_000_000.0
                    "播放次数：${if (v == v.toLong().toDouble()) "${v.toLong()}亿" else "%.1f亿".format(v)}"
                }
                viewCount >= 10_000 -> "播放次数：${viewCount / 10_000}万"
                viewCount > 0 -> "播放次数：$viewCount"
                else -> ""
            }
        }
}

/** 一个歌单（搜索 music_playlists 的结果项，点开可播里面的歌） */
data class Playlist(
    val title: String,
    val thumbnailUrl: String,
    val playlistUrl: String
)

/** 一档可用音质（一条音频流） */
data class AudioQuality(
    val formatName: String,
    val bitrateKbps: Int,
    val url: String,
    val itag: Int
) {
    val label: String
        get() = "$formatName $bitrateKbps kbps"
}

object AudioQualitySelector {
    /**
     * 默认选最高音质：
     * 1. Opus >= 160kbps 中最高的一档
     * 2. 否则 AAC/M4A 中最高的一档
     * 3. 否则全部中最高码率的一档
     */
    fun selectDefault(qualities: List<AudioQuality>): AudioQuality? {
        if (qualities.isEmpty()) return null
        val opus = qualities.filter {
            it.formatName.contains("opus", ignoreCase = true) && it.bitrateKbps >= 160
        }
        if (opus.isNotEmpty()) return opus.maxByOrNull { it.bitrateKbps }
        val aac = qualities.filter {
            it.formatName.contains("m4a", ignoreCase = true) ||
                it.formatName.contains("aac", ignoreCase = true)
        }
        if (aac.isNotEmpty()) return aac.maxByOrNull { it.bitrateKbps }
        return qualities.maxByOrNull { it.bitrateKbps }
    }

    /** 展示用：按码率降序 */
    fun sortedForDisplay(qualities: List<AudioQuality>): List<AudioQuality> =
        qualities.sortedByDescending { it.bitrateKbps }
}
