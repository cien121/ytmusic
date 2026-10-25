package com.ytmusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytmusic.app.auth.MicroGAccountManager
import com.ytmusic.app.data.DownloadManager
import com.ytmusic.app.data.Playlist
import com.ytmusic.app.data.Track
import com.ytmusic.app.data.YouTubeApiClient
import com.ytmusic.app.player.PlayerManager
import kotlinx.coroutines.launch

/**
 * 媒体库：登录后同步歌单、喜欢的音乐、订阅；未登录提示登录。
 * 已下载（离线下载，免费功能）不依赖登录，一直可用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenPlayer: () -> Unit,
    onSearchChannel: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf(MicroGAccountManager.loadAccount(context)) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var syncedPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var likedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var subscriptions by remember {
        mutableStateOf<List<YouTubeApiClient.Subscription>>(emptyList())
    }

    val downloadedList by DownloadManager.downloaded.collectAsState()

    // 歌单详情模式
    var detailPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var detailError by remember { mutableStateOf<String?>(null) }

    fun loadSync() {
        val email = account?.email ?: return
        scope.launch {
            syncing = true
            syncError = null
            YouTubeApiClient.getSyncedLibrary(context, email)
                .onSuccess {
                    syncedPlaylists = it.playlists
                    likedTracks = it.likedTracks
                    subscriptions = it.subscriptions
                }
                .onFailure { syncError = "同步失败：${it.message}" }
            syncing = false
        }
    }

    fun openSyncedPlaylist(playlist: Playlist) {
        detailPlaylist = playlist
        detailTracks = emptyList()
        detailError = null
        scope.launch {
            detailLoading = true
            // Data API 歌单优先用 playlistItems 接口，拿不到 id 时回退 extractor
            val listId = playlist.playlistUrl
                .substringAfter("list=", "")
                .substringBefore("&")
                .takeIf { it.isNotEmpty() }
            val result = if (listId != null && account != null) {
                YouTubeApiClient.getApiPlaylistTracks(context, account!!.email, listId)
            } else {
                null
            }
            if (result != null && result.isSuccess) {
                detailTracks = result.getOrDefault(emptyList())
            } else {
                com.ytmusic.app.data.MusicRepository.getPlaylistTracks(playlist.playlistUrl)
                    .onSuccess { detailTracks = it }
                    .onFailure { detailError = "歌单加载失败：${it.message}" }
            }
            detailLoading = false
        }
    }

    LaunchedEffect(Unit) {
        account = MicroGAccountManager.loadAccount(context)
        DownloadManager.refresh(context)
        if (account != null) loadSync()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YTMusic") },
                actions = { AccountIconButton() }
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                onOpenPlayer = onOpenPlayer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (detailPlaylist != null) {
                PlaylistDetail(
                    playlist = detailPlaylist!!,
                    tracks = detailTracks,
                    loading = detailLoading,
                    error = detailError,
                    onBack = { detailPlaylist = null },
                    onPlayAll = { PlayerManager.playQueue(context, detailTracks, 0) },
                    onTrackClick = { idx ->
                        PlayerManager.playQueue(context, detailTracks, idx)
                    },
                    onRetry = { openSyncedPlaylist(detailPlaylist!!) }
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                if (account == null) {
                    item {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "登录后同步你的歌单、喜欢的音乐和订阅",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "点右上角账号图标登录（需先在 MicroG 里添加谷歌账号）。" +
                                    "不登录也能用首页、搜索和播放，全部免费。"
                            )
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已登录：${account!!.email}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        syncError?.let {
                            Text(it, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }

                    if (syncedPlaylists.isNotEmpty()) {
                        item {
                            Text(
                                "我的歌单",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        items(syncedPlaylists.size) { idx ->
                            val playlist = syncedPlaylists[idx]
                            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                                TrackRow(
                                    track = Track(
                                        title = playlist.title,
                                        artist = "歌单",
                                        durationSec = 0,
                                        thumbnailUrl = playlist.thumbnailUrl,
                                        streamUrl = playlist.playlistUrl
                                    ),
                                    onClick = { openSyncedPlaylist(playlist) }
                                )
                            }
                        }
                    }

                    if (likedTracks.isNotEmpty()) {
                        item {
                            Text(
                                "喜欢的音乐",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        items(likedTracks.size) { idx ->
                            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                                TrackRow(
                                    track = likedTracks[idx],
                                    onClick = {
                                        PlayerManager.playQueue(context, likedTracks, idx)
                                    }
                                )
                            }
                        }
                    }

                    if (subscriptions.isNotEmpty()) {
                        item {
                            Text(
                                "我的订阅",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        items(subscriptions.size) { idx ->
                            val sub = subscriptions[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSearchChannel(sub.channelName) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sub.thumbnailUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = sub.thumbnailUrl,
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
                                Text(
                                    sub.channelName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // 已下载（不依赖登录，离线免费功能）
                item {
                    Text(
                        "已下载（离线播放，免费）",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (downloadedList.isEmpty()) {
                    item {
                        Text(
                            "还没有下载的歌。在歌曲行点下载图标即可离线保存。",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(downloadedList.size) { idx ->
                        val dl = downloadedList[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dl.thumbnailUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = dl.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    dl.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    dl.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(onClick = { PlayerManager.playFile(context, dl) }) {
                                Text("播放")
                            }
                            IconButton(onClick = {
                                DownloadManager.delete(context, dl)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }
    }
}
