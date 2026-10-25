package com.ytmusic.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ytmusic.app.data.AudioQuality
import com.ytmusic.app.data.AudioQualitySelector
import com.ytmusic.app.data.MusicRepository
import com.ytmusic.app.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放控制中心（单例，UI 层只跟它打交道）。
 * 通过 MediaController 连 MusicService 的 ExoPlayer，App 在后台也能播。
 * 音质切换：不断当前播放进度，换流地址重播。
 */
object PlayerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var connecting = false

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _qualities = MutableStateFlow<List<AudioQuality>>(emptyList())
    val qualities: StateFlow<List<AudioQuality>> = _qualities.asStateFlow()

    private val _selectedQuality = MutableStateFlow<AudioQuality?>(null)
    val selectedQuality: StateFlow<AudioQuality?> = _selectedQuality.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                })
                _isPlaying.value = controller?.isPlaying == true
            } catch (e: Exception) {
                _error.value = "播放器连接失败"
            } finally {
                connecting = false
            }
        }, MoreExecutors.directExecutor())
    }

    private suspend fun ensureController(context: Context): MediaController? {
        if (controller != null) return controller
        connect(context)
        // 等服务绑定（搜索本身耗时，通常点播放时已连好）
        repeat(20) {
            if (controller != null) return controller
            delay(100)
        }
        return controller
    }

    /** 点一首歌：解析全量音频流 → 默认最高音质 → 播放 */
    fun play(context: Context, track: Track) {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            val result = MusicRepository.getAudioQualities(track.streamUrl)
            result.onSuccess { qs ->
                if (qs.isEmpty()) {
                    _error.value = "这首歌没有可用音频流"
                    _isLoading.value = false
                    return@launch
                }
                _qualities.value = qs
                val def = AudioQualitySelector.selectDefault(qs)
                _selectedQuality.value = def
                _currentTrack.value = track
                val c = ensureController(context)
                if (c == null || def == null) {
                    _error.value = "播放器未就绪，重试一次"
                    _isLoading.value = false
                    return@launch
                }
                playUrl(c, track, def.url)
                _isLoading.value = false
            }.onFailure {
                _error.value = "加载失败：${it.message}"
                _isLoading.value = false
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    /** 手动切换音质：不断进度，换地址重播 */
    fun selectQuality(context: Context, quality: AudioQuality) {
        val track = _currentTrack.value ?: return
        val c = controller ?: return
        val pos = c.currentPosition
        _selectedQuality.value = quality
        scope.launch {
            playUrl(c, track, quality.url)
            c.seekTo(pos)
        }
    }

    private fun playUrl(c: MediaController, track: Track, url: String) {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(android.net.Uri.parse(track.thumbnailUrl))
                    .build()
            )
            .build()
        // 不设任何码率/轨道限制，音质直通
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun clearError() {
        _error.value = null
    }
}
