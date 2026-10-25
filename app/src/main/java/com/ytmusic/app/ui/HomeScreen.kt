package com.ytmusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytmusic.app.data.MusicRepository
import com.ytmusic.app.data.Playlist
import com.ytmusic.app.data.Track
import com.ytmusic.app.player.PlayerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** 心情/场景分类（官方首页顶部那排），点 chip 切对应内容 */
private val HOME_CHIPS = listOf("全部", "播客", "焕活能量", "派对", "轻松愉悦", "休闲放松", "健身")

/**
 * 首页：打开就能看到内容，不用先搜索。
 * 全部数据走 NewPipeExtractor 公开接口，匿名可用；点任何一首都能播。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedChip by remember { mutableStateOf("全部") }
    var loading by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf<String?>(null) }

    // 首页三区
    var quickPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var likedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var hotTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    // 分类模式
    var categoryLoading by remember { mutableStateOf(false) }
    var categoryTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var categoryError by remember { mutableStateOf<String?>(null) }

    // 歌单详情模式
    var detailPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var detailError by remember { mutableStateOf<String?>(null) }

    fun loadHome() {
        scope.launch {
            loading = true
            homeError = null
            val trendingDeferred = async {
                MusicRepository.getTrending().getOrElse {
                    // kiosk 拿不到时回退到热门搜索，保证首页不断
                    MusicRepository.searchSongs("热门音乐").getOrElse { emptyList() }
                }
            }
            val playlistsDeferred = async {
                MusicRepository.searchPlaylists("热门歌单").getOrElse { emptyList() }
            }
            val likedDeferred = async {
                MusicRepository.searchSongs("流行音乐").getOrElse { emptyList() }
            }
            try {
                hotTracks = trendingDeferred.await()
                quickPlaylists = playlistsDeferred.await()
                likedTracks = likedDeferred.await()
                if (hotTracks.isEmpty() && likedTracks.isEmpty()) {
                    homeError = "首页加载失败，请检查网络后重试"
                }
            } catch (e: Exception) {
                homeError = "首页加载失败：${e.message}"
            }
            loading = false
        }
    }

    fun loadCategory(chip: String) {
        scope.launch {
            categoryLoading = true
            categoryError = null
            MusicRepository.searchSongs(chip)
                .onSuccess { categoryTracks = it }
                .onFailure { categoryError = "加载失败：${it.message}" }
            categoryLoading = false
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

    LaunchedEffect(Unit) { loadHome() }

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
            // 顶部分类 chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HOME_CHIPS.forEach { chip ->
                    FilterChip(
                        selected = selectedChip == chip,
                        onClick = {
                            selectedChip = chip
                            detailPlaylist = null
                            if (chip == "全部") {
                                categoryTracks = emptyList()
                            } else {
                                loadCategory(chip)
                            }
                        },
                        label = { Text(chip) }
                    )
                }
            }

            when {
                detailPlaylist != null -> {
                    PlaylistDetail(
                        playlist = detailPlaylist!!,
                        tracks = detailTracks,
                        loading = detailLoading,
                        error = detailError,
                        onBack = { detailPlaylist = null },
                        onPlayAll = { PlayerManager.playQueue(context, detailTracks, 0) },
                        onTrackClick = { idx ->
                            PlayerManager.playQueue(context, detailTracks, idx)
                        }
                    )
                }
                selectedChip != "全部" -> {
                    CategoryContent(
                        chip = selectedChip,
                        tracks = categoryTracks,
                        loading = categoryLoading,
                        error = categoryError,
                        onRetry = { loadCategory(selectedChip) },
                        onTrackClick = { idx ->
                            PlayerManager.playQueue(context, categoryTracks, idx)
                        },
                        onPlayAll = { PlayerManager.playQueue(context, categoryTracks, 0) }
                    )
                }
                else -> {
                    HomeContent(
                        loading = loading,
                        error = homeError,
                        onRetry = { loadHome() },
                        quickPlaylists = quickPlaylists,
                        likedTracks = likedTracks,
                        hotTracks = hotTracks,
                        onPlaylistClick = { openPlaylist(it) },
                        onLikedClick = { idx ->
                            PlayerManager.playQueue(context, likedTracks, idx)
                        },
                        onHotClick = { idx ->
                            PlayerManager.playQueue(context, hotTracks, idx)
                        },
                        onPlayAllLiked = { PlayerManager.playQueue(context, likedTracks, 0) },
                        onPlayAllHot = { PlayerManager.playQueue(context, hotTracks, 0) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    quickPlaylists: List<Playlist>,
    likedTracks: List<Track>,
    hotTracks: List<Track>,
    onPlaylistClick: (Playlist) -> Unit,
    onLikedClick: (Int) -> Unit,
    onHotClick: (Int) -> Unit,
    onPlayAllLiked: () -> Unit,
    onPlayAllHot: () -> Unit
) {
    if (loading) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (error != null && hotTracks.isEmpty() && likedTracks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(error)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
    ) {
        // 快速点歌 / 一键点歌：横向歌单卡片
        if (quickPlaylists.isNotEmpty()) {
            item {
                SectionHeader(title = "快速点歌")
            }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(quickPlaylists) { playlist ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onPlaylistClick(playlist) }
                        ) {
                            if (playlist.thumbnailUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = playlist.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(140.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                playlist.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // 猜你喜欢
        if (likedTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "猜你喜欢",
                    onPlayAll = onPlayAllLiked
                )
            }
            items(likedTracks.size) { idx ->
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TrackRow(
                        track = likedTracks[idx],
                        onClick = { onLikedClick(idx) }
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // 当下最热门音乐
        if (hotTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "当下最热门音乐",
                    onPlayAll = onPlayAllHot
                )
            }
            items(hotTracks.size) { idx ->
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TrackRow(
                        track = hotTracks[idx],
                        onClick = { onHotClick(idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onPlayAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge
        )
        if (onPlayAll != null) {
            TextButton(onClick = onPlayAll) { Text("全部播放") }
        }
    }
}

@Composable
private fun CategoryContent(
    chip: String,
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onPlayAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                chip,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
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
                horizontalArrangement = Arrangement.Center
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
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
            return
        }
        if (tracks.isEmpty()) {
            Text(
                "暂无内容",
                modifier = Modifier.padding(32.dp)
            )
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
