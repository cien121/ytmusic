package com.ytmusic.app

import android.app.Application
import com.ytmusic.app.data.YTDownloader
import org.schabi.newpipe.extractor.NewPipe

class YTMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化 NewPipeExtractor（必须在任何 extractor 调用之前）
        NewPipe.init(YTDownloader())
    }
}
