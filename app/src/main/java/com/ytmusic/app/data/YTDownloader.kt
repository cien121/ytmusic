package com.ytmusic.app.data

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * NewPipeExtractor 的 HTTP 实现（HttpURLConnection，无额外依赖）。
 * YouTube 会拦截默认 Java UA，必须用浏览器 UA。
 */
class YTDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val conn = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            request.headers().forEach { (key, values) ->
                values.forEach { addRequestProperty(key, it) }
            }
            // 浏览器 UA，否则 YouTube 直接 403
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            val data = request.dataToSend()
            if (data != null) {
                doOutput = true
                outputStream.use { it.write(data) }
            }
        }

        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val body = stream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        // headerFields 带一个 null key（状态行），过滤掉
        val headers = conn.headerFields.filterKeys { it != null }
            .mapValues { it.value ?: emptyList() }
        return Response(code, conn.responseMessage, headers, body, conn.url.toString())
    }
}
