package com.ytmusic.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ytmusic.app.data.DownloadManager
import com.ytmusic.app.player.PlayerManager
import com.ytmusic.app.ui.HomeScreen
import com.ytmusic.app.ui.LibraryScreen
import com.ytmusic.app.ui.MainTab
import com.ytmusic.app.ui.PlayerScreen
import com.ytmusic.app.ui.SearchScreen
import com.ytmusic.app.ui.YTMusicTheme

/**
 * 主入口：底部三 tab（首页/搜索/媒体库）+ 完整播放页覆盖层。
 * 点迷你播放条进完整播放页，返回键回 tab 页。
 * 全部功能免费：无广告、后台播放、最高音质、离线下载，不设付费墙。
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限被拒也不影响播放，只是通知栏不显示 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+：通知栏播放控制需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        PlayerManager.connect(applicationContext)
        DownloadManager.refresh(applicationContext)

        setContent {
            YTMusicTheme {
                var tab by remember { mutableStateOf(MainTab.HOME) }
                var showPlayer by remember { mutableStateOf(false) }
                // 媒体库订阅频道点过来时，搜索页自动搜该频道名
                var searchRequest by remember { mutableStateOf<String?>(null) }

                val openPlayer = { showPlayer = true }

                if (showPlayer) {
                    PlayerScreen(onBack = { showPlayer = false })
                } else {
                    when (tab) {
                        MainTab.HOME -> HomeScreen(
                            selectedTab = tab,
                            onTabSelected = { tab = it },
                            onOpenPlayer = openPlayer
                        )
                        MainTab.SEARCH -> SearchScreen(
                            selectedTab = tab,
                            onTabSelected = { tab = it },
                            onOpenPlayer = openPlayer,
                            initialQuery = searchRequest,
                            onInitialQueryHandled = { searchRequest = null }
                        )
                        MainTab.LIBRARY -> LibraryScreen(
                            selectedTab = tab,
                            onTabSelected = { tab = it },
                            onOpenPlayer = openPlayer,
                            onSearchChannel = { channelName ->
                                searchRequest = channelName
                                tab = MainTab.SEARCH
                            }
                        )
                    }
                }
            }
        }
    }
}
