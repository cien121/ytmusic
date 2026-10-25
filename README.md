# YTMusic

YouTube Music 客户端（只做音乐，不做视频）。

- 数据：NewPipeExtractor（JitPack），公开搜索播放，无需登录、无广告
- 播放：Media3 ExoPlayer + MediaSessionService，后台/锁屏继续播
- 音质：默认最高码率（Opus 160kbps+ / AAC 优先），播放器里可手动切换全量音频流
- 登录：MicroG 方案（ReVanced 同款）——调标准 Google Sign-In API，由 MicroG 响应；
  未装 MicroG 时提示安装，匿名模式全部功能照常用。登录后歌单/喜欢的音乐走
  GoogleAuthUtil 拿 YouTube token（第二步接）。
- 只交付 arm64-v8a

## 构建

本机无 JDK/Android SDK，走 CI：推到 GitHub 仓库后 Actions 自动构建，
APK 在 `YTMusic-APK` artifact 里（`app/build/outputs/apk/release/*.apk`）。

## 注意事项

- 首次 CI 构建未经本地验证（本机无 JDK/SDK），JitPack 首次拉取
  NewPipeExtractor 可能较慢；若构建报错先看日志。
- YouTube 偶尔会要求 poToken（完整性校验），极少数歌曲可能 403；
  属于 extractor 层面的已知限制，后续可再接 WebView poToken。
- debug 包未固定 debug.keystore（本机无 keytool 生成），跨 CI 构建
  签名可能变化，覆盖安装失败时先卸载旧包。
