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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ytmusic.app.data.DownloadManager
import com.ytmusic.app.data.MusicRepository
import com.ytmusic.app.data.Playlist
import com.ytmusic.app.data.Track
import com.ytmusic.app.player.PlayerManager
import kotlinx.coroutines.launch

/** 搜索分类：歌曲 / 歌单 / 专辑，结果分类清晰 */
private enum class SearchCategory(val label: String) {
    SONGS("歌曲"),
    PLAYLISTS("歌单"),
    ALBUMS("专辑")
}

/**
 * 搜索页：搜歌名、歌手、专辑，结果按分类展示。
 * 歌曲点开即播（整列进队列可切歌）；歌单/专辑点开进详情页播。
 * 外部传入 initialQuery（如媒体库订阅频道名）时自动搜一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenPlayer: () -> Unit,
    initialQuery: String? = null,
    onInitialQueryHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchCategory.SONGS) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val playError by PlayerManager.error.collectAsState()
    val downloadProgress by DownloadManager.progress.collectAsState()
    val downloadedList by DownloadManager.downloaded.collectAsState()

    // 歌单/专辑详情模式
    var detailPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var detailError by remember { mutableStateOf<String?>(null) }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            searchError = null
            tracks = emptyList()
            playlists = emptyList()
            when (category) {
                SearchCategory.SONGS -> {
                    MusicRepository.searchSongs(q)
                        .onSuccess { tracks = it }
                        .onFailure { searchError = "搜索失败：${it.message}" }
                }
                SearchCategory.PLAYLISTS -> {
                    MusicRepository.searchPlaylists(q)
                        .onSuccess { playlists = it }
                        .onFailure { searchError = "搜索失败：${it.message}" }
                }
                SearchCategory.ALBUMS -> {
                    MusicRepository.searchAlbums(q)
                        .onSuccess {
                            playlists = it.playlists
                            tracks = it.tracks
                        }
                        .onFailure { searchError = "搜索失败：${it.message}" }
                }
            }
            searching = false
        }
    }

    fun openPlaylist(playlist: Playlist) {
        detailPlaylist = playlist
        detailTracks = emptyList()
        detailError = null
        scope.launch {
            detailLoading = true
            MusicRepository.getPlaylistTracks(playlist.playlistUrl)
                .onSuccess { detailTracks = it }
                .onFailure { detailError = "歌单加载失败：${it.message}" }
            detailLoading = false
        }
    }

    // 媒体库订阅频道点过来时自动搜
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            query = initialQuery
            category = SearchCategory.SONGS
            doSearch()
            onInitialQueryHandled()
        }
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
                    onRetry = { openPlaylist(detailPlaylist!!) }
                )
                return@Column
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜歌名、歌手、专辑…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch() })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { doSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Text("搜索")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchCategory.values().forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (query.isNotBlank()) doSearch()
                            },
                            label = { Text(cat.label) }
                        )
                    }
                }

                if (searching) {
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("搜索中…")
                    }
                }
                searchError?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                playError?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (category == SearchCategory.PLAYLISTS ||
                    category == SearchCategory.ALBUMS
                ) {
                    items(playlists.size) { idx ->
                        val playlist = playlists[idx]
                        TrackRow(
                            track = Track(
                                title = playlist.title,
                                artist = if (category == SearchCategory.ALBUMS) "专辑" else "歌单",
                                durationSec = 0,
                                thumbnailUrl = playlist.thumbnailUrl,
                                streamUrl = playlist.playlistUrl
                            ),
                            onClick = { openPlaylist(playlist) }
                        )
                    }
                }
                items(tracks.size) { idx ->
                    val track = tracks[idx]
                    TrackRow(
                        track = track,
                        onClick = { PlayerManager.playQueue(context, tracks, idx) },
                        trailing = {
                            val prog = downloadProgress[track.streamUrl]
                            val isDownloaded = downloadedList.any {
                                it.videoId == DownloadManager.videoIdOf(track)
                            }
                            when {
                                prog != null -> Text("$prog%", modifier = Modifier.padding(start = 8.dp))
                                isDownloaded -> Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "已下载",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                else -> IconButton(
                                    onClick = { DownloadManager.download(context, track) }
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = "下载")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
