package com.ytmusic.app.data

/** 一首歌（搜索结果项） */
data class Track(
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String,
    /** YouTube / YouTube Music 的 watch 链接 */
    val streamUrl: String
) {
    val durationText: String
        get() {
            if (durationSec <= 0) return ""
            return "%d:%02d".format(durationSec / 60, durationSec % 60)
        }
}

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
