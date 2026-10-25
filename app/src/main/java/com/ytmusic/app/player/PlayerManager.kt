package com.ytmusic.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ytmusic.app.data.AudioQuality
import com.ytmusic.app.data.AudioQualitySelector
import com.ytmusic.app.data.DownloadManager
import com.ytmusic.app.data.MusicRepository
import com.ytmusic.app.data.Track
import java.io.File
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
 * 队列切歌、进度条、音质切换、本地已下载播放都在这里。
 * 全部免费：无广告、后台播放、最高音质、离线下载，不设付费墙。
 */
object PlayerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var connecting = false
    private var pollingStarted = false

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

    /** 播放队列（点列表里的歌时整列进队列，可上一首/下一首） */
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    /** 当前播放进度（毫秒），播放页进度条用 */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    /** 当前歌曲总时长（毫秒），未知为 0 */
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    val hasNext: Boolean
        get() = _queueIndex.value + 1 < _queue.value.size

    val hasPrevious: Boolean
        get() = _queueIndex.value > 0

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
                startPositionPolling()
            } catch (e: Exception) {
                _error.value = "播放器连接失败"
            } finally {
                connecting = false
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionPolling() {
        if (pollingStarted) return
        pollingStarted = true
        scope.launch {
            while (true) {
                delay(500)
                val c = controller
                if (c != null) {
                    _position.value = c.currentPosition.coerceAtLeast(0L)
                    val d = c.duration
                    _duration.value = if (d > 0) d else 0L
                }
            }
        }
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

    /** 点一首歌：单曲进队列 → 解析全量音频流 → 默认最高音质 → 播放 */
    fun play(context: Context, track: Track) {
        _queue.value = listOf(track)
        _queueIndex.value = 0
        playTrackInternal(context, track)
    }

    /** 点列表里的歌：整列进队列，从点中的位置开始播，可切歌 */
    fun playQueue(context: Context, tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val safeIndex = index.coerceIn(0, tracks.size - 1)
        _queue.value = tracks
        _queueIndex.value = safeIndex
        playTrackInternal(context, tracks[safeIndex])
    }

    fun next(context: Context) {
        val idx = _queueIndex.value + 1
        if (idx < _queue.value.size) {
            _queueIndex.value = idx
            playTrackInternal(context, _queue.value[idx])
        }
    }

    fun previous(context: Context) {
        val idx = _queueIndex.value - 1
        if (idx >= 0) {
            _queueIndex.value = idx
            playTrackInternal(context, _queue.value[idx])
        } else {
            controller?.seekTo(0)
        }
    }

    /** 播已下载的本地文件（不走网络取流） */
    fun playFile(context: Context, downloaded: DownloadManager.DownloadedTrack) {
        val track = Track(
            title = downloaded.title,
            artist = downloaded.artist,
            durationSec = 0,
            thumbnailUrl = downloaded.thumbnailUrl,
            streamUrl = downloaded.file.absolutePath
        )
        _queue.value = listOf(track)
        _queueIndex.value = 0
        _qualities.value = emptyList()
        _selectedQuality.value = null
        _currentTrack.value = track
        _error.value = null
        scope.launch {
            val c = ensureController(context)
            if (c == null) {
                _error.value = "播放器未就绪，重试一次"
                return@launch
            }
            val item = MediaItem.Builder()
                .setUri(Uri.fromFile(downloaded.file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .build()
                )
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    private fun playTrackInternal(context: Context, track: Track) {
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
                    .setArtworkUri(Uri.parse(track.thumbnailUrl))
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
