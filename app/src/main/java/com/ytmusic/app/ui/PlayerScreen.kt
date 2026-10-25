package com.ytmusic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytmusic.app.data.DownloadManager
import com.ytmusic.app.player.PlayerManager

/**
 * 完整播放页（官方播放体验对标）：
 * 大封面、歌名、歌手、进度条、上一首/下一首、播放暂停、音质选择、离线下载。
 * 全部免费：无广告、后台播放、最高音质、离线下载，不设付费墙。
 */
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val track by PlayerManager.currentTrack.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val isLoading by PlayerManager.isLoading.collectAsState()
    val position by PlayerManager.position.collectAsState()
    val duration by PlayerManager.duration.collectAsState()
    val qualities by PlayerManager.qualities.collectAsState()
    val selectedQuality by PlayerManager.selectedQuality.collectAsState()
    val queue by PlayerManager.queue.collectAsState()
    val queueIndex by PlayerManager.queueIndex.collectAsState()
    val downloadProgress by DownloadManager.progress.collectAsState()
    val downloadedList by DownloadManager.downloaded.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val current = track
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "正在播放",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(24.dp))

        if (current == null) {
            Text("还没有在播的歌，去首页或搜索点一首吧")
            return
        }

        // 大封面
        if (current.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = current.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            current.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(4.dp))
        Text(
            current.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(16.dp))

        // 进度条
        val sliderMax = if (duration > 0) duration.toFloat() else 1f
        val sliderValue = if (dragging) dragValue else position.toFloat().coerceIn(0f, sliderMax)
        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                PlayerManager.seekTo(dragValue.toLong())
            },
            valueRange = 0f..sliderMax,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                formatMs(if (dragging) dragValue.toLong() else position),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatMs(duration),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(8.dp))

        // 播放控制：上一首 / 播放暂停 / 下一首
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { PlayerManager.previous(context) },
                enabled = queueIndex > 0
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
            }
            Spacer(Modifier.size(16.dp))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
            } else {
                IconButton(
                    onClick = { PlayerManager.togglePlayPause() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            IconButton(
                onClick = { PlayerManager.next(context) },
                enabled = queueIndex + 1 < queue.size
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
            }
        }

        Spacer(Modifier.height(16.dp))

        // 音质 + 下载
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showQualityDialog = true }) {
                Text("音质：${selectedQuality?.label ?: "自动"}")
            }
            val prog = downloadProgress[current.streamUrl]
            val isDownloaded = downloadedList.any {
                it.videoId == DownloadManager.videoIdOf(current)
            }
            when {
                prog != null -> Text("下载中 $prog%")
                isDownloaded -> {
                    IconButton(onClick = {
                        val dl = downloadedList.first {
                            it.videoId == DownloadManager.videoIdOf(current)
                        }
                        PlayerManager.playFile(context, dl)
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "已下载，点播本地")
                    }
                    Text("已下载")
                }
                else -> {
                    TextButton(onClick = { DownloadManager.download(context, current) }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("下载")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "无广告 · 后台播放 · 最高音质 · 全部免费",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showQualityDialog) {
        QualityDialog(
            qualities = qualities,
            selected = selectedQuality,
            onSelect = {
                PlayerManager.selectQuality(context, it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
