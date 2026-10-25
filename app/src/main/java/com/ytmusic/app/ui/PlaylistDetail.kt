package com.ytmusic.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ytmusic.app.data.Playlist
import com.ytmusic.app.data.Track

/** 歌单详情页（首页快速点歌、搜索歌单/专辑共用）：返回 + 全部播放 + 歌曲列表 */
@Composable
fun PlaylistDetail(
    playlist: Playlist,
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                playlist.title,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            if (tracks.isNotEmpty()) {
                TextButton(onClick = onPlayAll) { Text("全部播放") }
            }
        }
        if (loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }
        if (error != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error)
                if (onRetry != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("重试") }
                }
            }
            return
        }
        if (tracks.isEmpty()) {
            Text("这个歌单里没有可播放的歌曲", modifier = Modifier.padding(32.dp))
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks.size) { idx ->
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TrackRow(
                        track = tracks[idx],
                        onClick = { onTrackClick(idx) }
                    )
                }
            }
        }
    }
}
