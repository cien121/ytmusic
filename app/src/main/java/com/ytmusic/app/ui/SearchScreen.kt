package com.ytmusic.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.ytmusic.app.auth.MicroGAccountManager
import com.ytmusic.app.data.AudioQualitySelector
import com.ytmusic.app.data.MusicRepository
import com.ytmusic.app.data.Track
import com.ytmusic.app.player.PlayerManager
import kotlinx.coroutines.launch

/**
 * MVP 单页：搜索 + 结果 + 底部迷你播放器 + 音质选择 + MicroG 账号入口。
 * 不打磨细节，先跑通。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val currentTrack by PlayerManager.currentTrack.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val isLoading by PlayerManager.isLoading.collectAsState()
    val qualities by PlayerManager.qualities.collectAsState()
    val selectedQuality by PlayerManager.selectedQuality.collectAsState()
    val playError by PlayerManager.error.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showMicroGMissingDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(MicroGAccountManager.loadAccount(context)) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val gmsAccount =
                    GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        .getResult(ApiException::class.java)
                MicroGAccountManager.saveAccount(context, gmsAccount)
                account = MicroGAccountManager.loadAccount(context)
            } catch (e: ApiException) {
                searchError = "登录失败：${e.statusCode}"
            }
        }
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            searchError = null
            MusicRepository.searchSongs(q)
                .onSuccess { results = it }
                .onFailure { searchError = "搜索失败：${it.message}" }
            searching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YTMusic") },
                actions = {
                    IconButton(onClick = {
                        if (account != null) {
                            showAccountDialog = true
                        } else if (!MicroGAccountManager.isMicroGInstalled(context)) {
                            showMicroGMissingDialog = true
                        } else {
                            signInLauncher.launch(
                                MicroGAccountManager.getSignInIntent(context)
                            )
                        }
                    }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "账号")
                    }
                }
            )
        },
        bottomBar = {
            if (currentTrack != null) {
                PlayerBar(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    qualityLabel = selectedQuality?.label ?: "",
                    onPlayPause = { PlayerManager.togglePlayPause() },
                    onQualityClick = { showQualityDialog = true }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜歌名、歌手…") },
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
            if (account != null) {
                Text(
                    "已登录：${account!!.email}",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "匿名模式（点右上角登录可同步歌单）",
                    style = MaterialTheme.typography.bodySmall
                )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, modifier = Modifier.weight(1f))
                    TextButton(onClick = { PlayerManager.clearError() }) { Text("知道了") }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { PlayerManager.play(context, track) }
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
                            Text(
                                "${track.artist} · ${track.durationText}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("音质") },
            text = {
                Column {
                    AudioQualitySelector.sortedForDisplay(qualities).forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PlayerManager.selectQuality(context, q)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                q.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (q == selectedQuality) Text("✓")
                        }
                    }
                    if (qualities.isEmpty()) Text("暂无可用音质")
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showMicroGMissingDialog) {
        AlertDialog(
            onDismissRequest = { showMicroGMissingDialog = false },
            title = { Text("未检测到 MicroG") },
            text = {
                Text(
                    "请先安装 MicroG（ReVanced MicroG 或 MicroG RE），" +
                        "安装后可登录谷歌账号同步歌单。当前可继续匿名使用。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showMicroGMissingDialog = false }) { Text("知道了") }
            }
        )
    }

    if (showAccountDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("账号") },
            text = { Text("${account!!.displayName}\n${account!!.email}") },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = {
                    MicroGAccountManager.signOut(context)
                    account = null
                    showAccountDialog = false
                }) { Text("退出登录") }
            }
        )
    }
}

@Composable
private fun PlayerBar(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean,
    qualityLabel: String,
    onPlayPause: () -> Unit,
    onQualityClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = track.thumbnailUrl,
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
                "${track.artist} · $qualityLabel",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onQualityClick) { Text("音质") }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }
        }
    }
}
