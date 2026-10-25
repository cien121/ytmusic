package com.ytmusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytmusic.app.data.AudioQualitySelector
import com.ytmusic.app.data.Track
import com.ytmusic.app.player.PlayerManager

/**
 * 底部迷你播放条（首页/搜索/媒体库共用）：点整条进完整播放页。
 * 音质弹窗也放这里，全局只有一个入口。
 */
@Composable
fun MiniPlayerBar(onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
    val currentTrack by PlayerManager.currentTrack.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val isLoading by PlayerManager.isLoading.collectAsState()
    val qualities by PlayerManager.qualities.collectAsState()
    val selectedQuality by PlayerManager.selectedQuality.collectAsState()
    val track = currentTrack ?: return

    var showQualityDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPlayer() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${track.artist} · ${selectedQuality?.label ?: ""}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = { showQualityDialog = true }) { Text("音质") }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            IconButton(onClick = { PlayerManager.togglePlayPause() }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }
        }
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

@Composable
fun QualityDialog(
    qualities: List<com.ytmusic.app.data.AudioQuality>,
    selected: com.ytmusic.app.data.AudioQuality?,
    onSelect: (com.ytmusic.app.data.AudioQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音质（全部免费）") },
        text = {
            Column {
                AudioQualitySelector.sortedForDisplay(qualities).forEach { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(q)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            q.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (q == selected) Text("✓")
                    }
                }
                if (qualities.isEmpty()) Text("暂无可用音质")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 通用歌曲行：封面 + 歌名 + 歌手/播放量，点开即播 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            val second = listOf(
                track.artist,
                track.durationText,
                track.viewCountText
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(
                second,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
        trailing?.invoke()
    }
}
