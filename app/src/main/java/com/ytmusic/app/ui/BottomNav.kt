package com.ytmusic.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ytmusic.app.player.PlayerManager

/** 底部导航三个 tab */
enum class MainTab(val label: String) {
    HOME("首页"),
    SEARCH("搜索"),
    LIBRARY("媒体库")
}

/**
 * 底部栏（首页/搜索/媒体库共用）：
 * 上面是迷你播放条（有歌在播才显示），下面是导航。
 */
@Composable
fun AppBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by PlayerManager.currentTrack.collectAsState()
    Column(modifier = modifier) {
        if (currentTrack != null) {
            MiniPlayerBar(onOpenPlayer = onOpenPlayer)
        }
        NavigationBar {
            NavigationBarItem(
                selected = selectedTab == MainTab.HOME,
                onClick = { onTabSelected(MainTab.HOME) },
                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                label = { Text("首页") }
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.SEARCH,
                onClick = { onTabSelected(MainTab.SEARCH) },
                icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("搜索") }
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.LIBRARY,
                onClick = { onTabSelected(MainTab.LIBRARY) },
                icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                label = { Text("媒体库") }
            )
        }
    }
}
