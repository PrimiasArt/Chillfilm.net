package com.phyll.filmhub.companion

import android.view.KeyEvent
import com.phyll.filmhub.data.PreferencesManager
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.SnapCueCandidate
import com.phyll.filmhub.subtitle.GeminiAudioSubtitleGenerator
import com.phyll.filmhub.subtitle.SubtitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Base64

data class CompanionPlayerTelemetry(
    val isActive: Boolean = false,
    val movieId: String = "",
    val movieTitle: String = "",
    val episodeId: String = "",
    val episodeName: String = "",
    val streamUrl: String = "",
    val posterUrl: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPreFlightBlocking: Boolean = false,
    val preFlightStep: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val syncOffsetMs: Long = 0L,
    val subSpeedScale: Float = 1.0f,
    val dualSubEnabled: Boolean = false,
    val hardsubMaskEnabled: Boolean = true,
    val primaryThaiText: String = "",
    val secondaryText: String = "",
    val statusBadge: String = "",
    val isAutoSyncing: Boolean = false,
    val snapCandidates: List<SnapCueCandidate> = emptyList()
) {
    fun toJson(): JSONObject {
        val snapArr = JSONArray()
        snapCandidates.forEach { c ->
            snapArr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("startMs", c.startMs)
                    .put("endMs", c.endMs)
                    .put("thaiText", c.thaiText)
                    .put("referenceText", c.referenceText)
                    .put("isNearest", c.isNearestToAnchor)
            )
        }
        return JSONObject()
            .put("isActive", isActive)
            .put("movieId", movieId)
            .put("movieTitle", movieTitle)
            .put("episodeId", episodeId)
            .put("episodeName", episodeName)
            .put("streamUrl", streamUrl)
            .put("posterUrl", posterUrl)
            .put("isPlaying", isPlaying)
            .put("isBuffering", isBuffering)
            .put("isPreFlightBlocking", isPreFlightBlocking)
            .put("preFlightStep", preFlightStep)
            .put("positionMs", positionMs)
            .put("durationMs", durationMs)
            .put("syncOffsetMs", syncOffsetMs)
            .put("subSpeedScale", subSpeedScale.toDouble())
            .put("dualSubEnabled", dualSubEnabled)
            .put("hardsubMaskEnabled", hardsubMaskEnabled)
            .put("primaryThaiText", primaryThaiText)
            .put("secondaryText", secondaryText)
            .put("statusBadge", statusBadge)
            .put("isAutoSyncing", isAutoSyncing)
            .put("snapCandidates", snapArr)
    }
}

data class PlayerRemoteCommand(
    val id: Long = System.nanoTime(),
    val action: String,
    val valueLong: Long = 0L,
    val secondLong: Long = 0L
)

sealed class CompanionEvent {
    data class RemoteSearch(val query: String) : CompanionEvent()
    data class SelectCountry(val countryCode: String) : CompanionEvent()
    data class OpenMovieOnTv(val movieId: String, val quickPlay: Boolean) : CompanionEvent()
    data class PlayEpisodeOnTv(val movieId: String, val episodeId: String) : CompanionEvent()
    data class PlayerCommand(val command: PlayerRemoteCommand) : CompanionEvent()
    data class NavigateAction(val target: String) : CompanionEvent()
    data class PushDirectStream(val title: String, val streamUrl: String) : CompanionEvent()
    data class PushSubtitleContent(
        val fileName: String,
        val languageCode: String,
        val decodedContent: String
    ) : CompanionEvent()
    data class AddVodProvider(
        val name: String,
        val url: String,
        val providerType: String
    ) : CompanionEvent()
    data class SetGeminiKey(
        val apiKey: String,
        val modelId: String = "",
        val silent: Boolean = false
    ) : CompanionEvent()
    data class PlayMoviePayload(
        val movieId: String,
        val movieTitle: String,
        val originalTitle: String,
        val year: String,
        val countryCode: String,
        val genre: String,
        val posterUrl: String,
        val backdropUrl: String,
        val description: String,
        val providerName: String,
        val episodeId: String,
        val episodeName: String,
        val streamUrl: String,
        val audioStreamUrl: String = "",
        val fallbackStreamUrl: String = "",
        val startPositionMs: Long = 0L,
        val subtitles: List<ExternalSubtitleRef> = emptyList()
    ) : CompanionEvent()
    data object WipeGeminiKey : CompanionEvent()
    data class UpdateSubtitleStyle(
        val target: String = "PRIMARY",
        val fontSizeSp: Float? = null,
        val secondaryFontSizeSp: Float? = null,
        val sizeDeltaSp: Float? = null,
        val colorName: String? = null,
        val primaryColorName: String? = null,
        val secondaryColorName: String? = null,
        val linkPrimarySecondary: Boolean? = null,
        val edgeStyleName: String? = null,
        val boxOpacity: Float? = null,
        val bottomOffsetDp: Float? = null,
        val hardsubMaskEnabled: Boolean? = null,
        val dualSubEnabled: Boolean? = null,
        val resetDefault: Boolean = false
    ) : CompanionEvent()
}

/**
 * Embedded LAN HTTP & Live TV-to-Phone Streaming Server running on port 8899.
 * Provides:
 * 1. Live TV App Screen Stream (`GET /api/screen.jpg`) + Direct Touch/Swipe/Key Injection (`POST /api/touch`)
 * 2. Native Phone-Style Web App Mirror (`GET /api/state`, `GET /api/catalog`, `GET /api/movie`, `POST /api/action`)
 * 3. Ephemeral RAM-only Gemini Key Loader & Wireless .SRT Subtitle / Stream Uploader
 */
class CompanionHttpServer(
    private val port: Int = 8899,
    private val screenStreamController: TvScreenStreamController = TvScreenStreamController(),
    private val stateSnapshotProvider: () -> JSONObject = { JSONObject().put("ok", true) },
    private val catalogJsonProvider: (countryCode: String?, searchQuery: String?) -> JSONObject = { _, _ -> JSONObject().put("ok", true) },
    private val movieDetailProvider: suspend (movieId: String) -> JSONObject? = { null },
    private val deviceInfoProvider: () -> JSONObject = { JSONObject().put("ok", true).put("app", "PhyllFilmHub") },
    private val apkSourcePathProvider: () -> String? = { null },
    private val onEvent: (CompanionEvent) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelInspector = GeminiAudioSubtitleGenerator()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun start(): String? {
        if (serverSocket?.isClosed == false) {
            return getLanUrl()
        }
        return try {
            val socket = ServerSocket(port)
            serverSocket = socket
            serverJob = scope.launch {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            }
            getLanUrl()
        } catch (_: Exception) {
            null
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
    }

    fun getLanUrl(): String? {
        val ip = findLanIpv4() ?: return null
        return "http://$ip:$port"
    }

    private fun findLanIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQueryParams(rawPath: String): Map<String, String> {
        val qIdx = rawPath.indexOf('?')
        if (qIdx < 0 || qIdx + 1 >= rawPath.length) return emptyMap()
        val query = rawPath.substring(qIdx + 1)
        val map = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isBlank()) continue
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val k = runCatching { URLDecoder.decode(pair.substring(0, eq), "UTF-8") }.getOrDefault(pair.substring(0, eq))
                val v = runCatching { URLDecoder.decode(pair.substring(eq + 1), "UTF-8") }.getOrDefault(pair.substring(eq + 1))
                map[k] = v
            }
        }
        return map
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val headerBytes = ByteArrayOutputStream()
            var prev3 = 0
            var prev2 = 0
            var prev1 = 0
            while (true) {
                val b = input.read()
                if (b == -1) break
                headerBytes.write(b)
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
                if (headerBytes.size() > 16_384) break
            }

            val headerText = headerBytes.toString("UTF-8")
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val cleanPath = rawPath.substringBefore('?')
            val queryParams = parseQueryParams(rawPath)

            var contentLength = 0
            for (h in lines) {
                if (h.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = h.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }

            val bodyBytes = if (contentLength in 1..(4 * 1024 * 1024)) {
                val buf = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = input.read(buf, readTotal, contentLength - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                buf
            } else {
                ByteArray(0)
            }

            when {
                method == "GET" && cleanPath == "/" -> {
                    writeHttpResponse(client, 200, "text/html; charset=utf-8", MOBILE_WEB_PORTAL_HTML)
                }
                method == "GET" && cleanPath == "/api/screen.jpg" -> {
                    val w = queryParams["w"]?.toIntOrNull()?.coerceIn(480, 1280) ?: 960
                    val q = queryParams["q"]?.toIntOrNull()?.coerceIn(40, 85) ?: 68
                    val jpeg = screenStreamController.captureScreenJpeg(maxWidth = w, quality = q)
                    if (jpeg != null && jpeg.isNotEmpty()) {
                        writeBinaryHttpResponse(client, 200, "image/jpeg", jpeg)
                    } else {
                        writeHttpResponse(client, 404, "text/plain; charset=utf-8", "Frame not ready")
                    }
                }
                method == "GET" && cleanPath == "/api/state" -> {
                    val stateObj = runCatching { stateSnapshotProvider() }.getOrElse { JSONObject().put("ok", false) }
                    writeHttpResponse(client, 200, "application/json; charset=utf-8", stateObj.toString())
                }
                method == "GET" && cleanPath == "/api/info" -> {
                    val infoObj = runCatching { deviceInfoProvider() }.getOrElse { JSONObject().put("ok", false) }
                    writeHttpResponse(client, 200, "application/json; charset=utf-8", infoObj.toString())
                }
                method == "GET" && (cleanPath == "/download.apk" || cleanPath == "/phyll-film-hub.apk") -> {
                    val srcPath = apkSourcePathProvider()?.trim().orEmpty()
                    val apkFile = if (srcPath.isNotEmpty()) File(srcPath) else null
                    if (apkFile != null && apkFile.exists() && apkFile.isFile) {
                        writeApkStreamResponse(client, apkFile)
                    } else {
                        writeHttpResponse(client, 404, "text/plain; charset=utf-8", "APK not available")
                    }
                }
                method == "GET" && cleanPath == "/api/catalog" -> {
                    val country = queryParams["country"]
                    val q = queryParams["q"]
                    val catObj = runCatching { catalogJsonProvider(country, q) }.getOrElse { JSONObject().put("sections", JSONArray()) }
                    writeHttpResponse(client, 200, "application/json; charset=utf-8", catObj.toString())
                }
                method == "GET" && cleanPath == "/api/movie" -> {
                    val id = queryParams["id"].orEmpty().trim()
                    val detailObj = if (id.isNotEmpty()) movieDetailProvider(id) else null
                    if (detailObj != null) {
                        writeHttpResponse(client, 200, "application/json; charset=utf-8", detailObj.toString())
                    } else {
                        writeHttpResponse(client, 404, "application/json; charset=utf-8", """{"ok":false,"msg":"Không tìm thấy phim"}""")
                    }
                }
                method == "POST" && cleanPath == "/api/touch" -> {
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val ok = processTouchJson(bodyStr)
                    writeHttpResponse(
                        client,
                        200,
                        "application/json; charset=utf-8",
                        if (ok) """{"ok":true}""" else """{"ok":false}"""
                    )
                }
                method == "POST" && cleanPath == "/api/models" -> {
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val key = runCatching { JSONObject(bodyStr).optString("apiKey").trim() }.getOrDefault("")
                    val liveModels = if (key.isNotEmpty()) {
                        modelInspector.fetchAvailableGeminiModels(key)
                    } else {
                        emptyList()
                    }
                    val merged = LinkedHashSet<String>()
                    liveModels.forEach { merged.add(it) }
                    PreferencesManager.PRESET_GEMINI_MODELS.forEach { merged.add(it) }
                    val respObj = JSONObject()
                        .put("ok", liveModels.isNotEmpty())
                        .put("count", liveModels.size)
                        .put("models", JSONArray(merged.toList()))
                    writeHttpResponse(client, 200, "application/json; charset=utf-8", respObj.toString())
                }
                method == "POST" && cleanPath == "/api/action" -> {
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val ok = processActionJson(bodyStr)
                    val jsonResp = if (ok) """{"ok":true,"msg":"Đã thực thi trên TV!"}"""
                    else """{"ok":false,"msg":"Dữ liệu không hợp lệ"}"""
                    writeHttpResponse(client, 200, "application/json; charset=utf-8", jsonResp)
                }
                else -> {
                    writeHttpResponse(client, 404, "text/plain; charset=utf-8", "Not Found")
                }
            }
        }
    }

    private fun processTouchJson(jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            when (obj.optString("type")) {
                "tap" -> {
                    val nx = obj.optDouble("x", 0.5).toFloat()
                    val ny = obj.optDouble("y", 0.5).toFloat()
                    screenStreamController.injectTap(nx, ny)
                    true
                }
                "swipe" -> {
                    val x1 = obj.optDouble("x1", 0.5).toFloat()
                    val y1 = obj.optDouble("y1", 0.7).toFloat()
                    val x2 = obj.optDouble("x2", 0.5).toFloat()
                    val y2 = obj.optDouble("y2", 0.3).toFloat()
                    screenStreamController.injectSwipe(x1, y1, x2, y2)
                    true
                }
                "key" -> {
                    when (obj.optString("key").uppercase()) {
                        "UP" -> screenStreamController.injectKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
                        "DOWN" -> screenStreamController.injectKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
                        "LEFT" -> screenStreamController.injectKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
                        "RIGHT" -> screenStreamController.injectKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
                        "OK", "CENTER", "ENTER" -> screenStreamController.injectKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
                        "BACK" -> onEvent(CompanionEvent.NavigateAction("back"))
                        "HOME" -> onEvent(CompanionEvent.NavigateAction("home"))
                        "SETTINGS" -> onEvent(CompanionEvent.NavigateAction("settings"))
                        "TOGGLE_UI" -> onEvent(CompanionEvent.NavigateAction("toggle_ui_mode"))
                        "CLOSE_QR" -> onEvent(CompanionEvent.NavigateAction("close_qr"))
                        "PLAY_PAUSE" -> onEvent(CompanionEvent.PlayerCommand(PlayerRemoteCommand(action = "toggle_play")))
                        "VOL_UP" -> screenStreamController.injectKeyEvent(KeyEvent.KEYCODE_VOLUME_UP)
                        "VOL_DOWN" -> screenStreamController.injectKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN)
                        else -> return false
                    }
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun processActionJson(jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            when (obj.optString("type")) {
                "search" -> {
                    val q = obj.optString("query").trim()
                    if (q.isEmpty()) return false
                    onEvent(CompanionEvent.RemoteSearch(q))
                    true
                }
                "select_country" -> {
                    val code = obj.optString("country").trim()
                    if (code.isEmpty()) return false
                    onEvent(CompanionEvent.SelectCountry(code))
                    true
                }
                "open_movie" -> {
                    val movieId = obj.optString("movieId").trim()
                    val quickPlay = obj.optBoolean("quickPlay", false)
                    if (movieId.isEmpty()) return false
                    onEvent(CompanionEvent.OpenMovieOnTv(movieId, quickPlay))
                    true
                }
                "play_episode" -> {
                    val movieId = obj.optString("movieId").trim()
                    val episodeId = obj.optString("episodeId").trim()
                    if (movieId.isEmpty()) return false
                    onEvent(CompanionEvent.PlayEpisodeOnTv(movieId, episodeId))
                    true
                }
                "player_cmd" -> {
                    val cmd = obj.optString("cmd").trim()
                    if (cmd.isEmpty()) return false
                    val val1 = obj.optLong("value", 0L)
                    val val2 = obj.optLong("second", 0L)
                    onEvent(
                        CompanionEvent.PlayerCommand(
                            PlayerRemoteCommand(
                                action = cmd,
                                valueLong = val1,
                                secondLong = val2
                            )
                        )
                    )
                    true
                }
                "nav" -> {
                    val target = obj.optString("target").trim()
                    if (target.isEmpty()) return false
                    onEvent(CompanionEvent.NavigateAction(target))
                    true
                }
                "stream" -> {
                    val url = obj.optString("url").trim()
                    if (url.isEmpty()) return false
                    val title = obj.optString("title").trim().ifBlank { "Phim từ Điện thoại" }
                    onEvent(CompanionEvent.PushDirectStream(title, url))
                    true
                }
                "subtitle" -> {
                    val fileName = obj.optString("fileName", "phone_sub.srt")
                    val lang = obj.optString("lang", "th")
                    val base64 = obj.optString("base64").trim()
                    if (base64.isEmpty()) return false
                    val rawBytes = Base64.getDecoder().decode(base64)
                    val decoded = SubtitleParser.decodeBytesSmart(rawBytes)
                    if (decoded.isBlank()) return false
                    onEvent(CompanionEvent.PushSubtitleContent(fileName, lang, decoded))
                    true
                }
                "provider" -> {
                    val name = obj.optString("name").trim().ifBlank { "Nguồn phim mới" }
                    val url = obj.optString("url").trim()
                    val pType = obj.optString("providerType", "STANDARD_CMS_JSON")
                    if (url.isEmpty()) return false
                    onEvent(CompanionEvent.AddVodProvider(name, url, pType))
                    true
                }
                "gemini" -> {
                    val key = obj.optString("apiKey").trim()
                    val modelId = obj.optString("modelId").trim()
                    val silent = obj.optBoolean("silent", false)
                    if (key.isEmpty() && modelId.isEmpty()) return false
                    onEvent(CompanionEvent.SetGeminiKey(key, modelId, silent))
                    true
                }
                "play_movie_payload" -> {
                    val streamUrl = obj.optString("streamUrl").trim()
                    if (streamUrl.isEmpty()) return false
                    val subsArr = obj.optJSONArray("subtitles")
                    val subRefs = ArrayList<ExternalSubtitleRef>()
                    if (subsArr != null) {
                        for (i in 0 until subsArr.length()) {
                            val s = subsArr.optJSONObject(i) ?: continue
                            val u = s.optString("urlOrContent").trim()
                            if (u.isNotEmpty()) {
                                subRefs.add(
                                    ExternalSubtitleRef(
                                        id = s.optString("id", "rem_sub_$i"),
                                        label = s.optString("label", "Phụ đề $i"),
                                        languageCode = s.optString("languageCode", "und"),
                                        urlOrContent = u,
                                        isRawContent = s.optBoolean("isRawContent", false),
                                        sourcePlatform = s.optString("sourcePlatform", "Phone App"),
                                        isAutoGenerated = s.optBoolean("isAutoGenerated", false)
                                    )
                                )
                            }
                        }
                    }
                    onEvent(
                        CompanionEvent.PlayMoviePayload(
                            movieId = obj.optString("movieId").ifBlank { "rem_${System.currentTimeMillis()}" },
                            movieTitle = obj.optString("movieTitle").ifBlank { "Phim từ App Android" },
                            originalTitle = obj.optString("originalTitle", ""),
                            year = obj.optString("year", ""),
                            countryCode = obj.optString("countryCode", "global"),
                            genre = obj.optString("genre", "Điện ảnh"),
                            posterUrl = obj.optString("posterUrl", ""),
                            backdropUrl = obj.optString("backdropUrl", ""),
                            description = obj.optString("description", ""),
                            providerName = obj.optString("providerName", "Android Controller"),
                            episodeId = obj.optString("episodeId", "ep_1"),
                            episodeName = obj.optString("episodeName", "Tập 1"),
                            streamUrl = streamUrl,
                            audioStreamUrl = obj.optString("audioStreamUrl", ""),
                            fallbackStreamUrl = obj.optString("fallbackStreamUrl", ""),
                            startPositionMs = obj.optLong("startPositionMs", 0L),
                            subtitles = subRefs
                        )
                    )
                    true
                }
                "wipe_gemini" -> {
                    onEvent(CompanionEvent.WipeGeminiKey)
                    true
                }
                "sub_style" -> {
                    val target = obj.optString("target", "PRIMARY").trim().ifBlank { "PRIMARY" }
                    val fontSize = if (obj.has("fontSizeSp")) obj.optDouble("fontSizeSp", 20.0).toFloat() else null
                    val secSize = if (obj.has("secondaryFontSizeSp")) obj.optDouble("secondaryFontSizeSp", 16.0).toFloat() else null
                    val sizeDelta = if (obj.has("sizeDeltaSp")) obj.optDouble("sizeDeltaSp", 0.0).toFloat() else null
                    val colorName = obj.optString("color").trim().takeIf { it.isNotEmpty() }
                    val priColor = obj.optString("primaryColor").trim().takeIf { it.isNotEmpty() }
                    val secColor = obj.optString("secondaryColor").trim().takeIf { it.isNotEmpty() }
                    val linkMode = if (obj.has("linkPrimarySecondaryStyle")) obj.optBoolean("linkPrimarySecondaryStyle") else null
                    val edgeStyle = obj.optString("edgeStyle").trim().takeIf { it.isNotEmpty() }
                    val boxOp = if (obj.has("boxOpacity")) obj.optDouble("boxOpacity", 0.78).toFloat() else null
                    val bottomOff = if (obj.has("bottomOffsetDp")) obj.optDouble("bottomOffsetDp", 18.0).toFloat() else null
                    val maskEn = if (obj.has("hardsubMaskEnabled")) obj.optBoolean("hardsubMaskEnabled") else null
                    val dualEn = if (obj.has("dualSubEnabled")) obj.optBoolean("dualSubEnabled") else null
                    val resetDef = obj.optBoolean("resetDefault", false)
                    onEvent(
                        CompanionEvent.UpdateSubtitleStyle(
                            target = target,
                            fontSizeSp = fontSize,
                            secondaryFontSizeSp = secSize,
                            sizeDeltaSp = sizeDelta,
                            colorName = colorName,
                            primaryColorName = priColor,
                            secondaryColorName = secColor,
                            linkPrimarySecondary = linkMode,
                            edgeStyleName = edgeStyle,
                            boxOpacity = boxOp,
                            bottomOffsetDp = bottomOff,
                            hardsubMaskEnabled = maskEn,
                            dualSubEnabled = dualEn,
                            resetDefault = resetDef
                        )
                    )
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun writeHttpResponse(
        client: Socket,
        statusCode: Int,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeBinaryHttpResponse(client, statusCode, contentType, bytes)
    }

    private fun writeBinaryHttpResponse(
        client: Socket,
        statusCode: Int,
        contentType: String,
        bytes: ByteArray
    ) {
        val statusText = if (statusCode == 200) "OK" else "Not Found"
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        val out = client.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun writeApkStreamResponse(client: Socket, apkFile: File) {
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/vnd.android.package-archive\r\n" +
            "Content-Disposition: attachment; filename=\"ChillFilm-latest.apk\"\r\n" +
            "Content-Length: ${apkFile.length()}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        val out = client.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        val buf = ByteArray(64 * 1024)
        FileInputStream(apkFile).use { fis ->
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                out.write(buf, 0, read)
            }
        }
        out.flush()
    }

    companion object {
        private val MOBILE_WEB_PORTAL_HTML = """
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
<title>ChillFilm 2026 — Remote, Stream & Subtitle Studio</title>
<script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
<style>
  :root {
    --bg: #06070D;
    --surface: #0F1220;
    --elevated: #171B30;
    --glass: rgba(15, 18, 32, 0.84);
    --border: rgba(255, 255, 255, 0.13);
    --gold: #10B981;
    --cyan: #A855F7;
    --text: #F8FAFC;
    --muted: #A1A7B8;
    --green: #34D399;
    --red: #EF4444;
  }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    background-color: var(--bg);
    background-image:
      radial-gradient(at 12% 10%, rgba(16, 185, 129, 0.13) 0px, transparent 42%),
      radial-gradient(at 88% 85%, rgba(139, 92, 246, 0.15) 0px, transparent 48%);
    background-attachment: fixed;
    color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Inter', 'Segoe UI', Roboto, sans-serif;
    margin: 0;
    padding: 0 0 82px 0;
    overflow-x: hidden;
  }
  /* 2026 Spatial Glass Top Header */
  .top-bar {
    position: sticky;
    top: 0;
    z-index: 90;
    background: rgba(6, 7, 13, 0.82);
    backdrop-filter: blur(22px) saturate(180%);
    -webkit-backdrop-filter: blur(22px) saturate(180%);
    border-bottom: 1px solid var(--border);
    padding: 11px 15px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .brand { display: flex; align-items: center; gap: 9px; }
  .brand-logo {
    width: 26px; height: 26px; border-radius: 8px;
    background: linear-gradient(135deg, #10B981, #8B5CF6);
    color: #FFFFFF; font-weight: 900; font-size: 13px;
    display: flex; align-items: center; justify-content: center;
  }
  .brand h1 { color: var(--text); font-size: 14px; font-weight: 900; margin: 0; letter-spacing: 1px; }
  .brand h1 span { color: var(--gold); font-weight: 400; }
  .status-pill {
    font-size: 10px;
    font-weight: 700;
    padding: 4px 9px;
    border-radius: 99px;
    background: var(--elevated);
    color: var(--cyan);
    border: 1px solid var(--border);
    letter-spacing: 0.2px;
  }
  .status-pill.ok { background: #063E2E; color: #6EE7B7; border-color: rgba(16,185,129,0.45); }

  .container { padding: 12px 14px; max-width: 660px; margin: 0 auto; }
  .tab-pane { display: none; }
  .tab-pane.active { display: block; }

  /* Luxury Cards */
  .card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 14px;
    margin-bottom: 14px;
    box-shadow: 0 10px 28px rgba(0,0,0,0.42);
  }
  .card.highlight {
    border: 1px solid rgba(255, 184, 0, 0.45);
    background: linear-gradient(180deg, #131B2B 0%, #0C1018 100%);
  }
  .card h2 {
    font-size: 14px; color: var(--text); font-weight: 800;
    margin: 0 0 8px; display: flex; align-items: center; justify-content: space-between;
    letter-spacing: 0.2px;
  }
  .card.highlight h2 { color: var(--gold); }
  .subtext { font-size: 12px; color: var(--muted); margin: 0 0 10px; line-height: 1.45; }

  /* Live TV Screen Stream Box + TRUE LANDSCAPE FULLSCREEN ROTATION */
  .stream-wrap {
    position: relative;
    width: 100%;
    aspect-ratio: 16 / 9;
    background: #040609;
    border-radius: 14px;
    overflow: hidden;
    border: 1.5px solid rgba(255,255,255,0.14);
    touch-action: none;
    user-select: none;
  }
  .stream-wrap img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
    display: block;
    pointer-events: none;
  }
  /* When Phone is in Portrait (or rotation-locked), rotate stage 90deg to fill 100% screen in Landscape! */
  .stage-landscape-rotated {
    position: fixed !important;
    top: 0 !important;
    left: 0 !important;
    width: 100vh !important;
    height: 100vw !important;
    max-height: none !important;
    aspect-ratio: auto !important;
    transform-origin: top left !important;
    transform: rotate(90deg) translateY(-100%) !important;
    z-index: 9999 !important;
    border-radius: 0 !important;
    border: none !important;
    background: #000 !important;
  }
  /* When Phone is already physically in Landscape */
  .stage-landscape-native {
    position: fixed !important;
    inset: 0 !important;
    width: 100vw !important;
    height: 100vh !important;
    max-height: none !important;
    aspect-ratio: auto !important;
    z-index: 9999 !important;
    border-radius: 0 !important;
    border: none !important;
    background: #000 !important;
  }

  /* Floating Glass HUD inside Fullscreen Landscape */
  .fs-hud {
    position: absolute;
    left: 12px;
    right: 12px;
    bottom: 10px;
    display: none;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    flex-wrap: wrap;
    background: rgba(7, 10, 15, 0.78);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid rgba(255,255,255,0.16);
    border-radius: 14px;
    padding: 7px 10px;
    z-index: 30;
  }
  .stage-landscape-rotated .fs-hud,
  .stage-landscape-native .fs-hud,
  .stream-wrap:fullscreen .fs-hud,
  .phone-video-stage:fullscreen .fs-hud {
    display: flex;
  }
  .fs-hud-btn {
    padding: 6px 10px;
    border-radius: 9px;
    border: 1px solid rgba(255,255,255,0.18);
    background: rgba(22, 30, 46, 0.9);
    color: #FFF;
    font-size: 11px;
    font-weight: 700;
    cursor: pointer;
  }
  .fs-hud-btn.gold { background: var(--gold); color: #070A0F; border-color: var(--gold); font-weight: 800; }
  .fs-hud-btn.danger { background: rgba(185, 28, 28, 0.9); color: #FFF; border-color: #EF4444; }

  .touch-ripple {
    position: absolute;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    border: 2.5px solid var(--gold);
    background: rgba(255, 184, 0, 0.32);
    transform: translate(-50%, -50%) scale(0.4);
    opacity: 0;
    pointer-events: none;
    transition: transform 0.25s ease-out, opacity 0.25s ease-out;
    z-index: 20;
  }
  .touch-ripple.show {
    transform: translate(-50%, -50%) scale(1.25);
    opacity: 1;
  }
  .stream-badge {
    position: absolute;
    top: 8px;
    left: 8px;
    background: rgba(7, 10, 15, 0.80);
    color: var(--gold);
    font-size: 10px;
    font-weight: 700;
    padding: 4px 8px;
    border-radius: 7px;
    border: 1px solid rgba(255,184,0,0.35);
    pointer-events: none;
    z-index: 15;
  }
  .stream-fs-trigger {
    position: absolute;
    top: 8px;
    right: 8px;
    background: rgba(0, 229, 255, 0.18);
    color: var(--cyan);
    border: 1px solid rgba(0, 229, 255, 0.55);
    border-radius: 8px;
    padding: 5px 10px;
    font-size: 11px;
    font-weight: 800;
    cursor: pointer;
    z-index: 25;
    backdrop-filter: blur(8px);
  }

  /* Buttons & Grids */
  .remote-bar {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 7px;
    margin-top: 9px;
  }
  .dpad-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    max-width: 260px;
    margin: 10px auto 4px;
  }
  .btn {
    width: 100%;
    padding: 9px 10px;
    border: 1px solid var(--border);
    border-radius: 11px;
    background: var(--elevated);
    color: var(--text);
    font-weight: 700;
    font-size: 12px;
    cursor: pointer;
    text-align: center;
    transition: transform 0.08s, background 0.15s;
  }
  .btn:active { transform: scale(0.96); background: #24314A; }
  .btn.gold { background: var(--gold); color: #070A0F; border-color: var(--gold); font-weight: 800; }
  .btn.cyan { background: rgba(0,229,255,0.16); color: var(--cyan); border-color: rgba(0,229,255,0.45); font-weight: 800; }
  .btn.danger { background: rgba(127,29,29,0.75); color: #FCA5A5; border-color: rgba(239,68,68,0.4); }
  .btn.pill { border-radius: 99px; padding: 6px 12px; width: auto; }
  .btn.active-mode {
    background: var(--gold);
    color: #070A0F;
    border-color: var(--gold);
    font-weight: 900;
  }

  /* Color Swatches for Subtitle Styling */
  .swatch-row {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    margin: 8px 0;
  }
  .color-swatch {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    border-radius: 99px;
    background: var(--elevated);
    border: 1.5px solid var(--border);
    color: var(--text);
    font-size: 11px;
    font-weight: 700;
    cursor: pointer;
  }
  .color-swatch.selected {
    border-color: var(--gold);
    background: rgba(255, 184, 0, 0.16);
    color: var(--gold);
  }
  .swatch-dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 1px solid rgba(255,255,255,0.4);
  }

  /* Inputs */
  input, select {
    width: 100%;
    padding: 10px 13px;
    border-radius: 11px;
    border: 1px solid var(--border);
    background: #070A0F;
    color: #FFF;
    font-size: 13px;
    margin-bottom: 9px;
  }
  .row { display: flex; gap: 8px; align-items: center; margin-bottom: 9px; }
  .row input { margin-bottom: 0; flex: 1; }
  .row .btn { width: auto; white-space: nowrap; }

  /* Country Filter Pills */
  .chips-scroll {
    display: flex;
    gap: 7px;
    overflow-x: auto;
    padding: 2px 0 12px;
    scrollbar-width: none;
  }
  .chips-scroll::-webkit-scrollbar { display: none; }
  .chip {
    flex: 0 0 auto;
    padding: 7px 14px;
    border-radius: 99px;
    background: var(--surface);
    border: 1px solid var(--border);
    color: #D1D5DB;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
  }
  .chip.active {
    background: var(--gold);
    color: #070A0F;
    border-color: var(--gold);
    font-weight: 800;
  }

  /* Luxury Phone Movie Grid */
  .section-title {
    font-size: 15px;
    font-weight: 800;
    color: var(--text);
    margin: 14px 0 8px;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .section-title::before {
    content: '';
    display: inline-block;
    width: 3px;
    height: 14px;
    background: var(--gold);
    border-radius: 2px;
  }
  .movie-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  .movie-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 13px;
    overflow: hidden;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    transition: transform 0.12s;
  }
  .movie-card:active { transform: scale(0.97); }
  .poster-box {
    position: relative;
    width: 100%;
    aspect-ratio: 2 / 2.85;
    background: #0A0E17;
    overflow: hidden;
  }
  .poster-box img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
  .poster-badge {
    position: absolute;
    top: 6px;
    right: 6px;
    background: var(--gold);
    color: #070A0F;
    font-size: 9px;
    font-weight: 900;
    padding: 2px 6px;
    border-radius: 5px;
    letter-spacing: 0.3px;
  }
  .poster-country {
    position: absolute;
    top: 6px;
    left: 6px;
    background: rgba(7,10,15,0.78);
    border: 1px solid rgba(255,255,255,0.14);
    color: #E2E8F0;
    font-size: 9px;
    font-weight: 600;
    padding: 2px 6px;
    border-radius: 5px;
  }
  .movie-info { padding: 8px 10px; }
  .movie-title {
    font-size: 12px;
    font-weight: 700;
    color: var(--text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .movie-meta {
    font-size: 10px;
    color: var(--muted);
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* Compact Hug-to-Text Subtitle Preview Box */
  .sub-live-box {
    background: #05070B;
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 10px;
    margin: 6px 0 10px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 52px;
  }
  .sub-pill-hug {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    background: rgba(8, 11, 17, 0.78);
    border: 0.5px solid rgba(255,255,255,0.14);
    border-radius: 8px;
    padding: 4px 12px;
    max-width: 96%;
    text-align: center;
  }
  .sub-thai { color: #FFD600; font-size: 16px; font-weight: 700; line-height: 1.32; }
  .sub-sec { color: #FFFFFF; font-size: 13px; margin-top: 2px; line-height: 1.3; }

  .snap-list {
    max-height: 240px;
    overflow-y: auto;
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 6px;
    background: #070A0F;
    margin-top: 8px;
  }
  .snap-item {
    padding: 8px 10px;
    border-radius: 9px;
    border: 1px solid #1E293B;
    margin-bottom: 6px;
    background: #111723;
    cursor: pointer;
  }
  .snap-item.nearest { border-color: var(--gold); background: #1D2218; }

  /* Modal Sheet & Phone Video Stage with Fullscreen Landscape */
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.84);
    z-index: 200;
    display: none;
    align-items: flex-end;
    justify-content: center;
  }
  .modal-backdrop.open { display: flex; }
  .modal-sheet {
    width: 100%;
    max-width: 660px;
    max-height: 90vh;
    background: var(--surface);
    border-top-left-radius: 22px;
    border-top-right-radius: 22px;
    border-top: 1.5px solid rgba(255,184,0,0.5);
    padding: 16px;
    overflow-y: auto;
  }
  .ep-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    margin-top: 10px;
  }
  .phone-video-stage {
    position: relative;
    width: 100%;
    aspect-ratio: 16 / 9;
    background: #000;
    border-radius: 12px;
    overflow: hidden;
    border: 1px solid var(--border);
    margin-top: 6px;
  }
  video.phone-video {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
    display: block;
  }
  .phone-video-sub-overlay {
    position: absolute;
    left: 12px;
    right: 12px;
    bottom: 42px;
    display: flex;
    justify-content: center;
    pointer-events: none;
    z-index: 18;
  }

  /* Floating Glass Bottom Dock */
  .bottom-nav-wrap {
    position: fixed;
    bottom: 10px;
    left: 14px;
    right: 14px;
    z-index: 100;
    display: flex;
    justify-content: center;
    pointer-events: none;
  }
  .bottom-nav {
    pointer-events: auto;
    width: 100%;
    max-width: 520px;
    height: 56px;
    background: rgba(12, 16, 24, 0.92);
    backdrop-filter: blur(18px);
    -webkit-backdrop-filter: blur(18px);
    border: 1px solid rgba(255,255,255,0.10);
    border-radius: 20px;
    display: flex;
    justify-content: space-around;
    align-items: center;
    padding: 0 6px;
    box-shadow: 0 10px 30px rgba(0,0,0,0.6);
  }
  .nav-item {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    color: var(--muted);
    font-size: 11px;
    font-weight: 600;
    padding: 8px 6px;
    border-radius: 13px;
    cursor: pointer;
  }
  .nav-item.active {
    color: var(--gold);
    font-weight: 800;
    background: rgba(255,184,0,0.14);
  }

  #toast {
    position: fixed;
    bottom: 76px;
    left: 16px;
    right: 16px;
    background: var(--green);
    color: #052E16;
    font-weight: 800;
    font-size: 12px;
    padding: 10px 14px;
    border-radius: 12px;
    text-align: center;
    display: none;
    z-index: 10000;
    box-shadow: 0 8px 24px rgba(0,0,0,0.5);
  }
</style>
</head>
<body>

  <!-- Luxury Glass Header -->
  <div class="top-bar">
    <div class="brand">
      <div class="brand-logo">▶</div>
      <h1>PHYLL <span>CINEMA</span></h1>
      <span id="tvScreenBadge" class="status-pill">TV: Đang kết nối...</span>
    </div>
    <div style="display:flex;gap:6px;">
      <span class="status-pill ok" onclick="switchTab('tab-sub')" style="cursor:pointer">💬 Chỉnh Sub</span>
      <span id="aiKeyBadge" class="status-pill" onclick="switchTab('tab-ai')" style="cursor:pointer">AI & Key</span>
    </div>
  </div>

  <div class="container">

    <!-- LIVE NOW-PLAYING BAR & SUBTITLE STYLE STUDIO (Visible whenever TV is playing) -->
    <div id="nowPlayingCard" class="card highlight" style="display:none;">
      <h2>
        <span id="npTitle">Đang phát trên TV</span>
        <span id="npTime" style="font-size:11px;color:var(--cyan);">00:00 / 00:00</span>
      </h2>
      <div id="npSubStatus" class="subtext" style="margin-bottom:4px;color:#6EE7B7;font-weight:600;"></div>

      <!-- Compact Hug-to-Text Live Subtitle Monitor -->
      <div class="sub-live-box">
        <div id="npSubPill" class="sub-pill-hug">
          <div id="npThaiLine" class="sub-thai">กำลังรอซับไทย...</div>
          <div id="npSecLine" class="sub-sec" style="display:none;"></div>
        </div>
      </div>

      <!-- Timeline Scrubber -->
      <input type="range" id="npSeekSlider" min="0" max="1000" value="0"
        style="margin-bottom:8px;accent-color:#FFB800;padding:0;height:6px;"
        onchange="seekTvToSlider(this.value)">

      <!-- Playback Controls -->
      <div class="remote-bar" style="grid-template-columns:repeat(5,1fr);">
        <button class="btn" onclick="sendPlayerCmd('seek_rel', -10000)">⏪ -10s</button>
        <button class="btn gold" id="npPlayBtn" onclick="sendPlayerCmd('toggle_play', 0)">⏯ Phát</button>
        <button class="btn" onclick="sendPlayerCmd('seek_rel', 10000)">⏩ +10s</button>
        <button class="btn" onclick="sendPlayerCmd('next_episode', 0)">⏭ Tập kế</button>
        <button class="btn cyan" onclick="watchActiveOnPhone()">📲 Xem ĐT</button>
      </div>

      <!-- Quick Subtitle Style & Sync Bar -->
      <div class="remote-bar" style="grid-template-columns:repeat(4,1fr);margin-top:7px;">
        <button class="btn gold" onclick="toggleQuickStylePanel()">🎨 Cỡ & Màu Sub</button>
        <button class="btn cyan" onclick="sendPlayerCmd('auto_sync', 0)">🎯 AI Tự Khớp</button>
        <button class="btn" onclick="togglePhoneSnapList()">⚡ Chọn Câu</button>
        <button class="btn danger" onclick="sendPlayerCmd('exit_player', 0)">⏹ Thoát</button>
      </div>

      <!-- Simultaneous Direct Controls for ALL / PRIMARY / SECONDARY right inside Now Playing -->
      <div id="npQuickStylePanel" style="display:none;margin-top:10px;padding-top:10px;border-top:1px solid var(--border);">
        <!-- 1. Chỉnh Tất Cả -->
        <div style="background:#0B101B;border:1px solid rgba(16,185,129,0.4);border-radius:12px;padding:10px;margin-bottom:8px;">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
            <span style="font-size:12px;font-weight:800;color:#6EE7B7;">🔗 1. Chỉnh Tất Cả (Chính + Phụ cùng lúc)</span>
            <span class="lblAllSize" style="font-size:11px;color:var(--gold);font-weight:800;">20sp / 15sp</span>
          </div>
          <div class="remote-bar" style="grid-template-columns:repeat(3,1fr);margin-top:0;">
            <button class="btn" onclick="nudgeTargetSubSize('ALL', -2)">A- Tất Cả (-2sp)</button>
            <button class="btn gold" onclick="nudgeTargetSubSize('ALL', 2)">A+ Tất Cả (+2sp)</button>
            <button class="btn" onclick="resetSubStyleDefault()">↺ Mặc Định</button>
          </div>
          <div class="swatch-row" style="margin-bottom:0;">
            <div class="color-swatch swatch-all" data-color="CINEMA_YELLOW" onclick="pickTargetColor('ALL','CINEMA_YELLOW')"><span class="swatch-dot" style="background:#FFD600"></span>Vàng</div>
            <div class="color-swatch swatch-all" data-color="WHITE" onclick="pickTargetColor('ALL','WHITE')"><span class="swatch-dot" style="background:#FFFFFF"></span>Trắng</div>
            <div class="color-swatch swatch-all" data-color="CYAN" onclick="pickTargetColor('ALL','CYAN')"><span class="swatch-dot" style="background:#00E5FF"></span>Cyan</div>
            <div class="color-swatch swatch-all" data-color="MINT_GREEN" onclick="pickTargetColor('ALL','MINT_GREEN')"><span class="swatch-dot" style="background:#69F0AE"></span>Ngọc</div>
            <div class="color-swatch swatch-all" data-color="SAKURA_PINK" onclick="pickTargetColor('ALL','SAKURA_PINK')"><span class="swatch-dot" style="background:#FF80AB"></span>Hồng</div>
            <div class="color-swatch swatch-all" data-color="AMBER_GOLD" onclick="pickTargetColor('ALL','AMBER_GOLD')"><span class="swatch-dot" style="background:#FFAB40"></span>Cam</div>
          </div>
        </div>

        <!-- 2. Sub Chính (Tiếng Thái) -->
        <div style="background:#0B101B;border:1px solid rgba(255,184,0,0.4);border-radius:12px;padding:10px;margin-bottom:8px;">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
            <span style="font-size:12px;font-weight:800;color:var(--gold);">🇹🇭 2. Chỉnh Riêng Sub Chính (ไทย)</span>
            <span class="lblPriSize" style="font-size:12px;color:var(--gold);font-weight:900;">20sp</span>
          </div>
          <div class="row" style="margin-bottom:6px;">
            <button class="btn" onclick="nudgeTargetSubSize('PRIMARY', -2)">-2sp</button>
            <input type="range" class="priSizeSlider" min="12" max="48" step="1" value="20"
              style="accent-color:#FFB800;padding:0;height:6px;margin:0;"
              oninput="previewTargetSizeSlider('PRIMARY', this.value)"
              onchange="commitTargetSizeSlider('PRIMARY', this.value)">
            <button class="btn gold" onclick="nudgeTargetSubSize('PRIMARY', 2)">+2sp</button>
          </div>
          <div class="swatch-row" style="margin-bottom:0;">
            <div class="color-swatch swatch-pri" data-color="CINEMA_YELLOW" onclick="pickTargetColor('PRIMARY','CINEMA_YELLOW')"><span class="swatch-dot" style="background:#FFD600"></span>Vàng</div>
            <div class="color-swatch swatch-pri" data-color="WHITE" onclick="pickTargetColor('PRIMARY','WHITE')"><span class="swatch-dot" style="background:#FFFFFF"></span>Trắng</div>
            <div class="color-swatch swatch-pri" data-color="CYAN" onclick="pickTargetColor('PRIMARY','CYAN')"><span class="swatch-dot" style="background:#00E5FF"></span>Cyan</div>
            <div class="color-swatch swatch-pri" data-color="MINT_GREEN" onclick="pickTargetColor('PRIMARY','MINT_GREEN')"><span class="swatch-dot" style="background:#69F0AE"></span>Ngọc</div>
            <div class="color-swatch swatch-pri" data-color="SAKURA_PINK" onclick="pickTargetColor('PRIMARY','SAKURA_PINK')"><span class="swatch-dot" style="background:#FF80AB"></span>Hồng</div>
            <div class="color-swatch swatch-pri" data-color="AMBER_GOLD" onclick="pickTargetColor('PRIMARY','AMBER_GOLD')"><span class="swatch-dot" style="background:#FFAB40"></span>Cam</div>
          </div>
        </div>

        <!-- 3. Sub Phụ (Song Ngữ Việt/Anh) -->
        <div style="background:#0B101B;border:1px solid rgba(0,229,255,0.4);border-radius:12px;padding:10px;margin-bottom:8px;">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
            <span style="font-size:12px;font-weight:800;color:var(--cyan);">🇻🇳 3. Chỉnh Riêng Sub Phụ (Việt/Anh)</span>
            <button class="btn pill cyan btnDualSubToggle" onclick="toggleDualSubDirect()" style="padding:3px 10px;font-size:11px;">🈯 Sub Phụ: BẬT</button>
          </div>
          <div class="row" style="margin-bottom:6px;">
            <button class="btn" onclick="nudgeTargetSubSize('SECONDARY', -2)">-2sp</button>
            <input type="range" class="secSizeSlider" min="10" max="44" step="1" value="15"
              style="accent-color:#00E5FF;padding:0;height:6px;margin:0;"
              oninput="previewTargetSizeSlider('SECONDARY', this.value)"
              onchange="commitTargetSizeSlider('SECONDARY', this.value)">
            <button class="btn cyan" onclick="nudgeTargetSubSize('SECONDARY', 2)">+2sp</button>
            <span class="lblSecSize" style="font-size:12px;color:var(--cyan);font-weight:900;min-width:36px;text-align:right;">15sp</span>
          </div>
          <div class="swatch-row" style="margin-bottom:0;">
            <div class="color-swatch swatch-sec" data-color="WHITE" onclick="pickTargetColor('SECONDARY','WHITE')"><span class="swatch-dot" style="background:#FFFFFF"></span>Trắng</div>
            <div class="color-swatch swatch-sec" data-color="CINEMA_YELLOW" onclick="pickTargetColor('SECONDARY','CINEMA_YELLOW')"><span class="swatch-dot" style="background:#FFD600"></span>Vàng</div>
            <div class="color-swatch swatch-sec" data-color="CYAN" onclick="pickTargetColor('SECONDARY','CYAN')"><span class="swatch-dot" style="background:#00E5FF"></span>Cyan</div>
            <div class="color-swatch swatch-sec" data-color="MINT_GREEN" onclick="pickTargetColor('SECONDARY','MINT_GREEN')"><span class="swatch-dot" style="background:#69F0AE"></span>Ngọc</div>
            <div class="color-swatch swatch-sec" data-color="SAKURA_PINK" onclick="pickTargetColor('SECONDARY','SAKURA_PINK')"><span class="swatch-dot" style="background:#FF80AB"></span>Hồng</div>
            <div class="color-swatch swatch-sec" data-color="AMBER_GOLD" onclick="pickTargetColor('SECONDARY','AMBER_GOLD')"><span class="swatch-dot" style="background:#FFAB40"></span>Cam</div>
          </div>
        </div>

        <div class="remote-bar" style="grid-template-columns:repeat(4,1fr);margin-top:6px;">
          <button class="btn" onclick="sendPlayerCmd('nudge_sub', -500)">⏪ Sub -0.5s</button>
          <button class="btn" onclick="sendPlayerCmd('nudge_sub', 500)">Sub +0.5s ⏩</button>
          <button class="btn" onclick="updateSubFrameEdge('BACKGROUND_BOX')">Khung Gọn</button>
          <button class="btn" onclick="updateSubFrameEdge('OUTLINE')">Viền Chữ</button>
        </div>
      </div>

      <!-- 1-Tap Bilingual Cue Snapper -->
      <div id="phoneSnapWrap" style="display:none;margin-top:10px;">
        <div class="subtext" style="color:var(--gold);font-weight:700;">
          Chạm vào đúng câu nhân vật vừa nói để khớp Sub ngay (tự lưu cho cả bộ phim):
        </div>
        <div id="phoneSnapList" class="snap-list"></div>
      </div>
    </div>

    <!-- TAB 1: LUXURY PHONE CATALOG -->
    <div id="tab-app" class="tab-pane active">
      <div class="row" style="margin-bottom:8px;">
        <input id="qInput" placeholder="Tìm phim Thái, Hàn, Âu Mỹ hoặc dán link YouTube..."
          onkeydown="if(event.key==='Enter') doPhoneSearch()">
        <button class="btn gold" onclick="doPhoneSearch()">Tìm</button>
        <button class="btn" onclick="pushSearchToTv()" title="Hiện lên TV">Lên TV</button>
      </div>

      <div class="chips-scroll" id="countryChips">
        <div class="chip active" data-code="all" onclick="selectCountryChip('all', this)">Khám phá</div>
        <div class="chip" data-code="th" onclick="selectCountryChip('th', this)">Phim Thái</div>
        <div class="chip" data-code="yt" onclick="selectCountryChip('yt', this)">YouTube</div>
        <div class="chip" data-code="kr" onclick="selectCountryChip('kr', this)">Hàn Quốc</div>
        <div class="chip" data-code="cn" onclick="selectCountryChip('cn', this)">Trung Quốc</div>
        <div class="chip" data-code="us" onclick="selectCountryChip('us', this)">Âu Mỹ</div>
        <div class="chip" data-code="jp" onclick="selectCountryChip('jp', this)">Nhật Bản</div>
        <div class="chip" data-code="live" onclick="selectCountryChip('live', this)">Phim Bộ</div>
      </div>

      <div id="catalogContainer">
        <div class="subtext" style="text-align:center;padding:24px 0;">Đang đồng bộ danh mục phim...</div>
      </div>
    </div>

    <!-- TAB SUB: DEDICATED SIMULTANEOUS SUBTITLE STUDIO (PRIMARY / SECONDARY / ALL / TIMING) -->
    <div id="tab-sub" class="tab-pane">
      <div class="card highlight">
        <h2>
          <span>💬 Bảng Điều Khiển Phụ Đề Trực Quan</span>
          <button class="btn pill" onclick="sendPlayerCmd('toggle_sub_drawer', 0)" style="font-size:11px;">📺 Bảng Sub TV</button>
        </h2>
        <p class="subtext">
          Chỉnh trực tiếp <b>Tất Cả (Chính + Phụ)</b> hoặc chỉnh riêng từng dòng <b>Sub Chính (ไทย)</b> và <b>Sub Phụ (Việt/Anh)</b> ngay bên dưới — áp dụng tức thì lên TV!
        </p>

        <!-- Live Dual-Subtitle Preview Box -->
        <div class="sub-live-box">
          <div id="studioSubPill" class="sub-pill-hug">
            <div id="studioThaiLine" class="sub-thai">🇹🇭 ตัวอย่างซับไทย (20sp)</div>
            <div id="studioSecLine" class="sub-sec">🇻🇳 Phụ đề phụ song ngữ (15sp)</div>
          </div>
        </div>

        <!-- Block 1: Chỉnh Tất Cả (Chính + Phụ cùng lúc) -->
        <div style="background:#0B101B;border:1.5px solid rgba(16,185,129,0.5);border-radius:14px;padding:12px;margin-bottom:10px;">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
            <strong style="font-size:13px;color:#6EE7B7;">🔗 1. Chỉnh Tất Cả (Sub Chính + Sub Phụ cùng lúc)</strong>
            <span class="lblAllSize" style="font-size:12px;color:var(--gold);font-weight:800;">20sp / 15sp</span>
          </div>
          <div class="remote-bar" style="grid-template-columns:repeat(3,1fr);margin-top:0;margin-bottom:8px;">
            <button class="btn" onclick="nudgeTargetSubSize('ALL', -2)">🔽 A- Tất Cả (-2sp)</button>
            <button class="btn gold" onclick="nudgeTargetSubSize('ALL', 2)">🔼 A+ Tất Cả (+2sp)</button>
            <button class="btn danger" onclick="resetSubStyleDefault()">↺ Mặc Định</button>
          </div>
          <div style="font-size:11px;color:var(--muted);margin-bottom:4px;">Đổi màu đồng bộ cho cả 2 dòng:</div>
          <div class="swatch-row" style="margin-bottom:0;">
            <div class="color-swatch swatch-all" data-color="CINEMA_YELLOW" onclick="pickTargetColor('ALL','CINEMA_YELLOW')"><span class="swatch-dot" style="background:#FFD600"></span>Vàng Rạp</div>
            <div class="color-swatch swatch-all" data-color="WHITE" onclick="pickTargetColor('ALL','WHITE')"><span class="swatch-dot" style="background:#FFFFFF"></span>Trắng</div>
            <div class="color-swatch swatch-all" data-color="CYAN" onclick="pickTargetColor('ALL','CYAN')"><span class="swatch-dot" style="background:#00E5FF"></span>Cyan</div>
            <div class="color-swatch swatch-all" data-color="MINT_GREEN" onclick="pickTargetColor('ALL','MINT_GREEN')"><span class="swatch-dot" style="background:#69F0AE"></span>Xanh Ngọc</div>
            <div class="color-swatch swatch-all" data-color="SAKURA_PINK" onclick="pickTargetColor('ALL','SAKURA_PINK')"><span class="swatch-dot" style="background:#FF80AB"></span>Hồng</div>
            <div class="color-swatch swatch-all" data-color="AMBER_GOLD" onclick="pickTargetColor('ALL','AMBER_GOLD')"><span class="swatch-dot" style="background:#FFAB40"></span>Hổ Phách</div>
          </div>
        </div>

        <!-- Block 2: Chỉnh Riêng Sub Chính (Tiếng Thái) -->
        <div style="background:#0B101B;border:1.5px solid rgba(255,184,0,0.5);border-radius:14px;padding:12px;margin-bottom:10px;">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
            <strong style="font-size:13px;color:var(--gold);">🇹🇭 2. Chỉnh Riêng Sub Chính (Dòng 1 - Tiếng Thái)</strong>
            <span class="lblPriSize" style="font-size:13px;color:var(--gold);font-weight:900;">20sp</span>
          </div>
          <div class="row" style="margin-bottom:8px;">
            <button class="btn" onclick="nudgeTargetSubSize('PRIMARY', -2)">A- (-2sp)</button>
            <input type="range" class="priSizeSlider" min="12" max="48" step="1" value="20"
              style="accent-color:#FFB800;padding:0;height:6px;margin:0;"
              oninput="previewTargetSizeSlider('PRIMARY', this.value)"
              onchange="commitTargetSizeSlider('PRIMARY', this.value)">
            <button class="btn gold" onclick="nudgeTargetSubSize('PRIMARY', 2)">A+ (+2sp)</button>
          </div>
          <div style="font-size:11px;color:var(--muted);margin-bottom:4px;">Màu chữ riêng của Sub Chính (ไทย):</div>
          <div class="swatch-row" style="margin-bottom:0;">
            <div class="color-swatch swatch-pri" data-color="CINEMA_YELLOW" onclick="pickTargetColor('PRIMARY','CINEMA_YELLOW')"><span class="swatch-dot" style="background:#FFD600"></span>Vàng Rạp</div>
            <div class="color-swatch swatch-pri" data-color="WHITE" onclick="pickTargetColor('PRIMARY','WHITE')"><span class="swatch-dot" style="background:#FFFFFF"></span>Trắng</div>
            <div class="color-swatch swatch-pri" data-color="CYAN" onclick="pickTargetColor('PRIMARY','CYAN')"><span class="swatch-dot" style="background:#00E5FF"></span>Cyan</div>
            <div class="color-swatch swatch-pri" data-color="MINT_GREEN" onclick="pickTargetColor('PRIMARY','MINT_GREEN')"><span class="swatch-dot" style="background:#69F0AE"></span>Xanh Ngọc</div>
            <div class="color-swatch swatch-pri" data-color="SAKURA_PINK" onclick="pickTargetColor('PRIMARY','SAKURA_PINK')"><span class="swatch-dot" style="background:#FF80AB"></span>Hồng</div>
            <div class="color-swatch swatch-pri" data-color="AMBER_GOLD" onclick="pickTargetColor('PRIMARY','AMBER_GOLD')"><span class="swatch-dot" style="background:#FFAB40"></span>Hổ Phách</div>
          </div>
        </div>

        <!-- Block 3: Chỉnh Riêng Sub Phụ (Song Ngữ Việt/Anh) -->
        <div style="background:#0B101B;border:1.5px solid rgba(0,229,255,0.5);border-radius:14px;padding:12px;margin-bottom:10px;">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
            <strong style="font-size:13px;color:var(--cyan);">🇻🇳 3. Chỉnh Riêng Sub Phụ (Dòng 2 - Việt / Anh)</strong>
            <button class="btn pill cyan btnDualSubToggle" onclick="toggleDualSubDirect()" style="padding:4px 12px;font-size:11px;">🈯 Sub Phụ: BẬT</button>
          </div>
          <div class="row" style="margin-bottom:8px;">
            <button class="btn" onclick="nudgeTargetSubSize('SECONDARY', -2)">A- (-2sp)</button>
            <input type="range" class="secSizeSlider" min="10" max="44" step="1" value="15"
              style="accent-color:#00E5FF;padding:0;height:6px;margin:0;"
              oninput="previewTargetSizeSlider('SECONDARY', this.value)"
              onchange="commitTargetSizeSlider('SECONDARY', this.value)">
            <button class="btn cyan" onclick="nudgeTargetSubSize('SECONDARY', 2)">A+ (+2sp)</button>
            <span class="lblSecSize" style="font-size:13px;color:var(--cyan);font-weight:900;min-width:38px;text-align:right;">15sp</span>
          </div>
          <div style="font-size:11px;color:var(--muted);margin-bottom:4px;">Màu chữ riêng của Sub Phụ (Việt/Anh):</div>
          <div class="swatch-row" style="margin-bottom:0;">
            <div class="color-swatch swatch-sec" data-color="WHITE" onclick="pickTargetColor('SECONDARY','WHITE')"><span class="swatch-dot" style="background:#FFFFFF"></span>Trắng</div>
            <div class="color-swatch swatch-sec" data-color="CINEMA_YELLOW" onclick="pickTargetColor('SECONDARY','CINEMA_YELLOW')"><span class="swatch-dot" style="background:#FFD600"></span>Vàng Rạp</div>
            <div class="color-swatch swatch-sec" data-color="CYAN" onclick="pickTargetColor('SECONDARY','CYAN')"><span class="swatch-dot" style="background:#00E5FF"></span>Cyan</div>
            <div class="color-swatch swatch-sec" data-color="MINT_GREEN" onclick="pickTargetColor('SECONDARY','MINT_GREEN')"><span class="swatch-dot" style="background:#69F0AE"></span>Xanh Ngọc</div>
            <div class="color-swatch swatch-sec" data-color="SAKURA_PINK" onclick="pickTargetColor('SECONDARY','SAKURA_PINK')"><span class="swatch-dot" style="background:#FF80AB"></span>Hồng</div>
            <div class="color-swatch swatch-sec" data-color="AMBER_GOLD" onclick="pickTargetColor('SECONDARY','AMBER_GOLD')"><span class="swatch-dot" style="background:#FFAB40"></span>Hổ Phách</div>
          </div>
        </div>

        <!-- Block 4: Khớp Giờ Sub, Vị Trí & Khung Nền -->
        <div style="background:#0B101B;border:1px solid var(--border);border-radius:14px;padding:12px;">
          <strong style="font-size:13px;color:var(--text);display:block;margin-bottom:8px;">⏱ 4. Khớp Giờ Phụ Đề, Vị Trí & Khung Nền</strong>
          <div class="remote-bar" style="grid-template-columns:repeat(2,1fr);margin-top:0;margin-bottom:7px;">
            <button class="btn cyan" onclick="sendPlayerCmd('auto_sync', 0)">🎯 AI Tự Nghe & Khớp Sub</button>
            <button class="btn gold" onclick="switchTab('tab-app'); togglePhoneSnapList();">⚡ Chọn Câu Khớp 1 Chạm</button>
          </div>
          <div class="remote-bar" style="grid-template-columns:repeat(5,1fr);margin-bottom:7px;">
            <button class="btn" onclick="sendPlayerCmd('nudge_sub', -2000)">⏪ -2s</button>
            <button class="btn" onclick="sendPlayerCmd('nudge_sub', -500)">⏪ -0.5s</button>
            <button class="btn" onclick="sendPlayerCmd('reset_sync', 0)">0.0s</button>
            <button class="btn" onclick="sendPlayerCmd('nudge_sub', 500)">+0.5s ⏩</button>
            <button class="btn" onclick="sendPlayerCmd('nudge_sub', 2000)">+2s ⏩</button>
          </div>
          <div class="remote-bar" style="grid-template-columns:repeat(4,1fr);margin-bottom:7px;">
            <button class="btn" onclick="updateSubFrameEdge('BACKGROUND_BOX')">Khung Mờ Gọn</button>
            <button class="btn" onclick="updateSubFrameEdge('OUTLINE')">Viền Sắc Nét</button>
            <button class="btn" onclick="updateSubFrameEdge('DROP_SHADOW')">Đổ Bóng</button>
            <button class="btn" onclick="updateSubFrameEdge('NONE')">Trong Suốt</button>
          </div>
          <div class="remote-bar" style="grid-template-columns:repeat(3,1fr);">
            <button class="btn" onclick="nudgeSubBottomOffset(-6)">⬇ Hạ Thấp Sub</button>
            <button class="btn" onclick="nudgeSubBottomOffset(6)">⬆ Nâng Cao Sub</button>
            <button class="btn" onclick="sendPlayerCmd('toggle_mask', 0)">🛡 Che Hardsub</button>
          </div>
        </div>
      </div>
    </div>

    <!-- TAB 2: LIVE TV SCREEN STREAM & LANDSCAPE FULLSCREEN TOUCH -->
    <div id="tab-stream" class="tab-pane">
      <div class="card">
        <h2>
          <span>Stream Màn Hình TV & Chạm Trực Tiếp</span>
          <div style="display:flex;gap:6px;">
            <button class="btn pill cyan" onclick="enterLandscapeFullscreen('streamTouchBox')">⛶ Xoay Ngang Full</button>
            <button class="btn pill" id="toggleStreamBtn" onclick="toggleLiveStream()" style="font-size:11px;">⏸ Dừng</button>
          </div>
        </h2>
        <p class="subtext">
          Chạm hoặc vuốt trực tiếp trên khung hình TV. Bấm <b>⛶ Xoay Ngang Full</b> để phóng toàn màn hình ngang trên điện thoại (tự xoay ngang kể cả khi khóa xoay dọc)!
        </p>

        <div class="stream-wrap" id="streamTouchBox">
          <img id="tvLiveImg" src="/api/screen.jpg?t=0" alt="Màn hình TV trực tiếp">
          <div id="touchRipple" class="touch-ripple"></div>
          <div id="streamFpsBadge" class="stream-badge">LIVE TOUCH STREAM</div>
          <button type="button" class="stream-fs-trigger" id="streamFsTopBtn" onclick="toggleLandscapeFullscreen('streamTouchBox')">
            ⛶ Xoay Ngang Full
          </button>

          <!-- Floating Glass Controls inside Landscape Fullscreen -->
          <div class="fs-hud" id="streamFsHud" onclick="event.stopPropagation()">
            <button type="button" class="fs-hud-btn" onclick="sendTouchKey('BACK')">⬅ Back</button>
            <button type="button" class="fs-hud-btn" onclick="sendTouchKey('HOME')">🏠 Home</button>
            <button type="button" class="fs-hud-btn" onclick="sendPlayerCmd('seek_rel', -10000)">⏪ -10s</button>
            <button type="button" class="fs-hud-btn gold" onclick="sendTouchKey('PLAY_PAUSE')">⏯ Phát/Dừng</button>
            <button type="button" class="fs-hud-btn" onclick="sendPlayerCmd('seek_rel', 10000)">⏩ +10s</button>
            <button type="button" class="fs-hud-btn" onclick="nudgeSubSize(-2)">Sub A-</button>
            <button type="button" class="fs-hud-btn" onclick="nudgeSubSize(2)">Sub A+</button>
            <button type="button" class="fs-hud-btn" onclick="cycleSubColorQuick()">🎨 Màu Sub</button>
            <button type="button" class="fs-hud-btn danger" onclick="exitLandscapeFullscreen()">✖ Thoát Full</button>
          </div>
        </div>

        <!-- Quick Navigation & UI Mode Bar -->
        <div class="remote-bar">
          <button class="btn" onclick="sendTouchKey('BACK')">⬅ Quay lại</button>
          <button class="btn" onclick="sendTouchKey('HOME')">🏠 Trang chủ</button>
          <button class="btn cyan" onclick="sendTouchKey('TOGGLE_UI')">Đổi Mode TV/ĐT</button>
          <button class="btn" onclick="sendTouchKey('CLOSE_QR')">Đóng QR</button>
        </div>

        <!-- Precision D-Pad for TV -->
        <div class="dpad-grid">
          <div></div>
          <button class="btn" onclick="sendTouchKey('UP')">▲ Lên</button>
          <div></div>
          <button class="btn" onclick="sendTouchKey('LEFT')">◄ Trái</button>
          <button class="btn gold" onclick="sendTouchKey('OK')">OK</button>
          <button class="btn" onclick="sendTouchKey('RIGHT')">Phải ►</button>
          <button class="btn" onclick="sendTouchKey('VOL_DOWN')">Vol -</button>
          <button class="btn" onclick="sendTouchKey('DOWN')">▼ Xuống</button>
          <button class="btn" onclick="sendTouchKey('VOL_UP')">Vol +</button>
        </div>
      </div>
    </div>

    <!-- TAB 3: SUBTITLE STYLE STUDIO, GEMINI AI KEY (RAM-ONLY) & SUB UPLOAD -->
    <div id="tab-ai" class="tab-pane">
      <!-- Dedicated Subtitle Style Studio Card (Always Accessible) -->
      <div class="card highlight">
        <h2>🎨 1. Tùy Chỉnh Cỡ Chữ, Màu Chữ & Khung Sub (Chính / Phụ / Tất Cả)</h2>
        <p class="subtext">
          Chỉnh riêng <b>Sub Chính (ไทย)</b>, <b>Sub Phụ (Việt/Anh)</b> hoặc chọn <b>🔗 Chỉnh Tất Cả</b> để đồng bộ cả 2 dòng ngay lập tức trên TV & Điện thoại:
        </p>
        <div class="remote-bar" style="grid-template-columns:repeat(3,1fr);margin-bottom:8px;">
          <button class="btn active-mode" id="tabTargetBtnPrimary" onclick="setSubEditTarget('PRIMARY')">🇹🇭 Sub Chính</button>
          <button class="btn" id="tabTargetBtnSecondary" onclick="setSubEditTarget('SECONDARY')">🇻🇳 Sub Phụ</button>
          <button class="btn" id="tabTargetBtnAll" onclick="setSubEditTarget('ALL')">🔗 Chỉnh Tất Cả</button>
        </div>

        <div class="row">
          <span id="tabSubSizeLabel" style="font-size:12px;font-weight:800;min-width:120px;">Cỡ chữ: 20sp</span>
          <button class="btn" onclick="nudgeSubSize(-2)">A-</button>
          <input type="range" id="tabSubSizeSlider" min="12" max="48" step="1" value="20"
            style="accent-color:#FFB800;padding:0;height:6px;margin:0;"
            oninput="previewSubSizeSlider(this.value)"
            onchange="commitSubSizeSlider(this.value)">
          <button class="btn" onclick="nudgeSubSize(2)">A+</button>
        </div>

        <div class="swatch-row" id="tabColorSwatches">
          <div class="color-swatch selected" data-color="CINEMA_YELLOW" onclick="pickSubColor('CINEMA_YELLOW')">
            <span class="swatch-dot" style="background:#FFD600"></span>Vàng Rạp
          </div>
          <div class="color-swatch" data-color="WHITE" onclick="pickSubColor('WHITE')">
            <span class="swatch-dot" style="background:#FFFFFF"></span>Trắng
          </div>
          <div class="color-swatch" data-color="CYAN" onclick="pickSubColor('CYAN')">
            <span class="swatch-dot" style="background:#00E5FF"></span>Cyan
          </div>
          <div class="color-swatch" data-color="MINT_GREEN" onclick="pickSubColor('MINT_GREEN')">
            <span class="swatch-dot" style="background:#69F0AE"></span>Xanh Ngọc
          </div>
          <div class="color-swatch" data-color="SAKURA_PINK" onclick="pickSubColor('SAKURA_PINK')">
            <span class="swatch-dot" style="background:#FF80AB"></span>Hồng
          </div>
          <div class="color-swatch" data-color="AMBER_GOLD" onclick="pickSubColor('AMBER_GOLD')">
            <span class="swatch-dot" style="background:#FFAB40"></span>Hổ Phách
          </div>
        </div>

        <div class="remote-bar" style="grid-template-columns:repeat(4,1fr);margin-top:8px;">
          <button class="btn" onclick="updateSubFrameEdge('BACKGROUND_BOX')">Khung Mờ Gọn</button>
          <button class="btn" onclick="updateSubFrameEdge('OUTLINE')">Viền Sắc Nét</button>
          <button class="btn" onclick="updateSubFrameEdge('DROP_SHADOW')">Đổ Bóng</button>
          <button class="btn" onclick="updateSubFrameEdge('NONE')">Trong Suốt</button>
        </div>
        <div class="remote-bar" style="grid-template-columns:repeat(4,1fr);margin-top:6px;">
          <button class="btn" onclick="nudgeSubBottomOffset(-6)">Hạ Thấp Sub</button>
          <button class="btn" onclick="nudgeSubBottomOffset(6)">Nâng Cao Sub</button>
          <button class="btn" onclick="sendPlayerCmd('toggle_dual_sub', 0)">Bật/Tắt Sub Phụ</button>
          <button class="btn danger" onclick="resetSubStyleDefault()">Khôi Phục Chuẩn</button>
        </div>
      </div>

      <div class="card" style="border-color:rgba(0,229,255,0.45);background:linear-gradient(135deg,#0B1624,#131A26);">
        <h2 style="color:var(--cyan);">📲 Tải App Android Độc Lập (.APK) Về Điện Thoại</h2>
        <p class="subtext">
          Cài trực tiếp app trên điện thoại để lưu cố định API Key, tự quét LAN &amp; cài/gỡ App lên TV, hoặc xem phim trực tiếp trên máy Android!
        </p>
        <a href="/download.apk" download="ChillFilm-latest.apk" class="btn cyan" style="display:block;text-align:center;text-decoration:none;padding:12px;">
          ⬇ Tải Xuống ChillFilm-latest.apk
        </a>
      </div>

      <div class="card">
        <h2>🔐 2. Nạp Gemini Key Tạm (Chỉ Lưu RAM TV) & Chọn Model</h2>
        <p class="subtext" style="color:#6EE7B7;font-weight:700;">
          KHÔNG LƯU Ổ CỨNG (0 BYTE) • THOÁT APP TRÊN TV TỰ ĐỘNG XÓA SẠCH KEY
        </p>
        <div class="row">
          <input type="password" id="geminiKey" autocomplete="off" spellcheck="false" placeholder="Dán Gemini API Key (AIza...)">
          <button type="button" class="btn" onclick="toggleKeyVis()">Hiện</button>
        </div>
        <button type="button" class="btn" style="margin-bottom:9px;" onclick="loadLiveModels()">
          Tải Danh Sách Model Thực Tế Từ Key Này
        </button>
        <select id="geminiModel">
          <option value="gemini-3-flash-preview">gemini-3-flash-preview (Khuyên dùng • Siêu tốc)</option>
          <option value="gemini-3.1-flash-lite-preview">gemini-3.1-flash-lite-preview (Flash Lite • Nhanh nhất)</option>
          <option value="gemini-3.1-pro-preview">gemini-3.1-pro-preview (Gemini 3.1 Pro • Chuẩn nhất)</option>
          <option value="gemini-2.5-flash">gemini-2.5-flash (Gemini 2.5 Flash • Ổn định)</option>
          <option value="gemini-2.5-pro">gemini-2.5-pro (Gemini 2.5 Pro)</option>
        </select>
        <input id="customModel" autocomplete="off" placeholder="Hoặc tự gõ mã Model bất kỳ...">
        <button class="btn gold" onclick="sendGemini()">Nạp Key Vào RAM TV & Kích Hoạt AI</button>
        <button class="btn danger" style="margin-top:7px;" onclick="wipeGemini()">Xóa Ngay API Key Khỏi RAM TV</button>
      </div>

      <div class="card">
        <h2>💬 3. Bắn File Phụ Đề (.SRT / .VTT / .ASS) Lên TV</h2>
        <select id="subLang">
          <option value="th">ภาษาไทย (Sub Thái gốc - UTF-8 & Windows-874)</option>
          <option value="vi">Tiếng Việt (Tự động AI dịch sang Thái + Sub Kép)</option>
          <option value="en">English (Tự động AI dịch sang Thái + Sub Kép)</option>
        </select>
        <input type="file" id="subFile" accept=".srt,.vtt,.ass,.ssa,.txt">
        <button class="btn cyan" onclick="sendSub()">Nạp Sub Vào Phim Đang Xem</button>
      </div>

      <div class="card">
        <h2>▶ 4. Phát Link Phim / YouTube Trực Tiếp (.M3U8 / .MP4 / YT)</h2>
        <input id="streamTitle" placeholder="Tên phim hiển thị (Tùy chọn)">
        <input id="streamUrl" placeholder="https://youtu.be/... hoặc https://.../index.m3u8">
        <button class="btn gold" onclick="sendStream()">Phát Ngay Trên TV</button>
      </div>

      <div class="card">
        <h2>🌐 5. Thêm Nguồn API Phim Mở (JSON CMS / M3U)</h2>
        <input id="provName" placeholder="Tên nguồn (VD: Kho Phim Châu Á)">
        <input id="provUrl" placeholder="URL API hoặc link .m3u / .json">
        <select id="provType">
          <option value="STANDARD_CMS_JSON">Chuẩn JSON VOD CMS (OPhim / PhimAPI)</option>
          <option value="M3U_PLAYLIST">Danh sách phát M3U / M3U8</option>
          <option value="CUSTOM_JSON">Custom JSON Movie Catalog</option>
        </select>
        <button class="btn cyan" onclick="sendProvider()">Thêm Nguồn Phim</button>
      </div>
    </div>

  </div>

  <!-- Movie Detail & Direct Phone Player Modal (with Fullscreen Landscape Rotation!) -->
  <div id="movieModal" class="modal-backdrop" onclick="if(event.target===this) closeMovieModal()">
    <div class="modal-sheet">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
        <strong id="mdTitle" style="font-size:16px;color:var(--gold);">Chi tiết phim</strong>
        <button class="btn pill" onclick="closeMovieModal()">✖ Đóng</button>
      </div>
      <div id="mdMeta" class="subtext"></div>

      <!-- Inline Phone Video Player Stage (supports True Landscape Fullscreen Rotation!) -->
      <div id="phoneVideoWrap" style="display:none;margin-bottom:12px;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
          <span style="font-size:12px;color:var(--cyan);font-weight:700;">
            Đang phát trên ĐT: <span id="phoneVideoEpName"></span>
          </span>
          <button type="button" class="btn pill cyan" onclick="enterLandscapeFullscreen('phoneVideoStage')" style="padding:4px 10px;font-size:11px;">
            ⛶ Xoay Ngang Full
          </button>
        </div>
        <div class="phone-video-stage" id="phoneVideoStage">
          <video id="phoneVideoEl" class="phone-video" controls playsinline></video>
          <div class="phone-video-sub-overlay">
            <div class="sub-pill-hug" id="phoneVideoSubPill">
              <div id="phoneVideoSubPrimary" class="sub-thai"></div>
              <div id="phoneVideoSubSec" class="sub-sec" style="display:none;"></div>
            </div>
          </div>
          <div class="fs-hud" onclick="event.stopPropagation()">
            <button type="button" class="fs-hud-btn" onclick="seekPhoneVideoRel(-10)">⏪ -10s</button>
            <button type="button" class="fs-hud-btn gold" onclick="togglePhoneVideoPlay()">⏯ Phát/Dừng</button>
            <button type="button" class="fs-hud-btn" onclick="seekPhoneVideoRel(10)">⏩ +10s</button>
            <button type="button" class="fs-hud-btn" onclick="nudgeSubSize(-2)">Sub A-</button>
            <button type="button" class="fs-hud-btn" onclick="nudgeSubSize(2)">Sub A+</button>
            <button type="button" class="fs-hud-btn" onclick="cycleSubColorQuick()">🎨 Màu Sub</button>
            <button type="button" class="fs-hud-btn danger" onclick="exitLandscapeFullscreen()">✖ Thoát Full</button>
          </div>
        </div>
      </div>

      <div class="row" style="margin-top:8px;">
        <button class="btn gold" id="mdPlayTvBtn">📺 Phát Ngay Trên TV</button>
        <button class="btn cyan" id="mdWatchPhoneBtn">📲 Xem Trên ĐT (Xoay Ngang)</button>
      </div>

      <div style="font-size:12px;font-weight:700;color:var(--text);margin-top:10px;">
        Danh sách Tập phim (Chạm để phát lên TV):
      </div>
      <div id="mdEpisodes" class="ep-grid"></div>
    </div>
  </div>

  <div id="toast"></div>

  <!-- Floating Glass Bottom Dock (4 Dedicated Tabs) -->
  <div class="bottom-nav-wrap">
    <div class="bottom-nav">
      <div class="nav-item active" id="nav-app" onclick="switchTab('tab-app')">
        <span>🎬 Kho Phim</span>
      </div>
      <div class="nav-item" id="nav-sub" onclick="switchTab('tab-sub')">
        <span>💬 Chỉnh Sub</span>
      </div>
      <div class="nav-item" id="nav-stream" onclick="switchTab('tab-stream')">
        <span>📺 Stream TV</span>
      </div>
      <div class="nav-item" id="nav-ai" onclick="switchTab('tab-ai')">
        <span>🤖 AI & Nguồn</span>
      </div>
    </div>
  </div>

<script>
let activeTab = 'tab-app';
let streamEnabled = true;
let streamTimer = null;
let frameInFlight = false;
let currentCountry = 'all';
let latestTvState = null;
let currentModalMovie = null;
let hlsInstance = null;
let activeFullscreenStageId = null;

// Subtitle style state synced with TV + 2.5s edit lock so polling never overwrites active phone edits!
let subEditTarget = 'PRIMARY';
let lastPhoneStyleEditMs = 0;
let currentSubStyle = {
  fontSizeSp: 20,
  secondaryFontSizeSp: 15,
  primaryColor: 'CINEMA_YELLOW',
  primaryColorHex: '#FFD600',
  secondaryColor: 'WHITE',
  secondaryColorHex: '#FFFFFF',
  linkPrimarySecondaryStyle: false,
  edgeStyle: 'BACKGROUND_BOX',
  boxOpacity: 0.78,
  bottomOffsetDp: 18,
  hardsubMaskEnabled: false,
  dualSubEnabled: false
};
const COLOR_ORDER = ['CINEMA_YELLOW', 'WHITE', 'CYAN', 'MINT_GREEN', 'SAKURA_PINK', 'AMBER_GOLD'];
const COLOR_HEX_MAP = {
  'CINEMA_YELLOW': '#FFD600',
  'WHITE': '#FFFFFF',
  'CYAN': '#00E5FF',
  'MINT_GREEN': '#69F0AE',
  'SAKURA_PINK': '#FF80AB',
  'AMBER_GOLD': '#FFAB40'
};

function showToast(msg, err) {
  const t = document.getElementById('toast');
  t.style.background = err ? '#EF4444' : '#10B981';
  t.style.color = err ? '#FFF' : '#052E16';
  t.innerText = msg;
  t.style.display = 'block';
  clearTimeout(window._toastTimer);
  window._toastTimer = setTimeout(() => { t.style.display = 'none'; }, 2400);
}

function switchTab(tabId) {
  activeTab = tabId;
  document.querySelectorAll('.tab-pane').forEach(el => el.classList.remove('active'));
  const targetPane = document.getElementById(tabId);
  if (targetPane) targetPane.classList.add('active');
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  if (tabId === 'tab-app') document.getElementById('nav-app').classList.add('active');
  if (tabId === 'tab-sub') document.getElementById('nav-sub').classList.add('active');
  if (tabId === 'tab-stream') document.getElementById('nav-stream').classList.add('active');
  if (tabId === 'tab-ai') document.getElementById('nav-ai').classList.add('active');

  if (tabId === 'tab-stream' && streamEnabled) {
    requestNextFrame();
  }
}

/* ---------------- TRUE LANDSCAPE FULLSCREEN ROTATION (TV STREAM & PHONE VIDEO) ---------------- */
function applyFullscreenOrientationClasses() {
  if (!activeFullscreenStageId) return;
  const el = document.getElementById(activeFullscreenStageId);
  if (!el) return;
  const isPortraitViewport = window.innerWidth < window.innerHeight;
  if (isPortraitViewport) {
    el.classList.add('stage-landscape-rotated');
    el.classList.remove('stage-landscape-native');
  } else {
    el.classList.add('stage-landscape-native');
    el.classList.remove('stage-landscape-rotated');
  }
}

window.addEventListener('resize', applyFullscreenOrientationClasses);
window.addEventListener('orientationchange', () => setTimeout(applyFullscreenOrientationClasses, 120));

async function enterLandscapeFullscreen(stageId) {
  const el = document.getElementById(stageId);
  if (!el) return;
  activeFullscreenStageId = stageId;

  // Try native Fullscreen + Orientation Lock first, and ALWAYS apply CSS landscape guarantee!
  try {
    if (el.requestFullscreen) {
      await el.requestFullscreen();
    } else if (el.webkitRequestFullscreen) {
      el.webkitRequestFullscreen();
    }
  } catch (e) {}

  try {
    if (screen.orientation && screen.orientation.lock) {
      await screen.orientation.lock('landscape');
    }
  } catch (e) {}

  applyFullscreenOrientationClasses();
  const topBtn = document.getElementById('streamFsTopBtn');
  if (topBtn) topBtn.innerText = '✖ Thoát Full';
  if (stageId === 'streamTouchBox') {
    requestNextFrame();
  }
}

async function exitLandscapeFullscreen() {
  ['streamTouchBox', 'phoneVideoStage'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.classList.remove('stage-landscape-rotated');
      el.classList.remove('stage-landscape-native');
    }
  });
  activeFullscreenStageId = null;
  const topBtn = document.getElementById('streamFsTopBtn');
  if (topBtn) topBtn.innerText = '⛶ Xoay Ngang Full';

  try {
    if (screen.orientation && screen.orientation.unlock) {
      screen.orientation.unlock();
    }
  } catch (e) {}
  try {
    if (document.fullscreenElement && document.exitFullscreen) {
      await document.exitFullscreen();
    }
  } catch (e) {}
}

function toggleLandscapeFullscreen(stageId) {
  if (activeFullscreenStageId === stageId) {
    exitLandscapeFullscreen();
  } else {
    enterLandscapeFullscreen(stageId);
  }
}

/* ---------------- SUBTITLE SIZE & COLOR STUDIO (SIMULTANEOUS PRIMARY / SECONDARY / ALL) ---------------- */
function toggleQuickStylePanel() {
  const p = document.getElementById('npQuickStylePanel');
  if (p) {
    p.style.display = (p.style.display === 'none') ? 'block' : 'none';
  }
}

function setSubEditTarget(mode) {
  lastPhoneStyleEditMs = Date.now();
  subEditTarget = mode;
  const isLinked = (mode === 'ALL');
  currentSubStyle.linkPrimarySecondaryStyle = isLinked;
  syncSubStyleUiControls();
  postAction({
    type: 'sub_style',
    target: mode,
    linkPrimarySecondaryStyle: isLinked
  }, true);
}

function syncSubStyleUiControls() {
  const priSize = Math.round(currentSubStyle.fontSizeSp || 20);
  const secSize = Math.round(currentSubStyle.secondaryFontSizeSp || 15);

  document.querySelectorAll('.lblAllSize').forEach(el => {
    el.innerText = 'Chính ' + priSize + 'sp • Phụ ' + secSize + 'sp';
  });
  document.querySelectorAll('.lblPriSize').forEach(el => {
    el.innerText = priSize + 'sp';
  });
  document.querySelectorAll('.lblSecSize').forEach(el => {
    el.innerText = secSize + 'sp';
  });

  document.querySelectorAll('.priSizeSlider').forEach(el => {
    if (document.activeElement !== el) el.value = priSize;
  });
  document.querySelectorAll('.secSizeSlider').forEach(el => {
    if (document.activeElement !== el) el.value = secSize;
  });

  document.querySelectorAll('.btnDualSubToggle').forEach(el => {
    el.innerText = currentSubStyle.dualSubEnabled ? '🈯 Sub Phụ: Đang BẬT' : '🈯 Sub Phụ: Đang TẮT';
    el.className = currentSubStyle.dualSubEnabled ? 'btn pill cyan btnDualSubToggle' : 'btn pill btnDualSubToggle';
  });

  document.querySelectorAll('.swatch-pri').forEach(sw => {
    if (sw.getAttribute('data-color') === currentSubStyle.primaryColor) sw.classList.add('selected');
    else sw.classList.remove('selected');
  });
  document.querySelectorAll('.swatch-sec').forEach(sw => {
    if (sw.getAttribute('data-color') === currentSubStyle.secondaryColor) sw.classList.add('selected');
    else sw.classList.remove('selected');
  });
  document.querySelectorAll('.swatch-all').forEach(sw => {
    if (sw.getAttribute('data-color') === currentSubStyle.primaryColor &&
        sw.getAttribute('data-color') === currentSubStyle.secondaryColor) {
      sw.classList.add('selected');
    } else {
      sw.classList.remove('selected');
    }
  });

  // Legacy single-target controls in tab-ai (if present)
  ['targetBtnPrimary', 'tabTargetBtnPrimary'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.className = (subEditTarget === 'PRIMARY') ? 'btn active-mode' : 'btn';
  });
  ['targetBtnSecondary', 'tabTargetBtnSecondary'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.className = (subEditTarget === 'SECONDARY') ? 'btn active-mode' : 'btn';
  });
  ['targetBtnAll', 'tabTargetBtnAll'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.className = (subEditTarget === 'ALL') ? 'btn active-mode' : 'btn';
  });
  const activeSize = (subEditTarget === 'SECONDARY') ? secSize : priSize;
  ['subSizeLabel', 'tabSubSizeLabel'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.innerText = 'Cỡ chữ: ' + activeSize + 'sp';
  });
  ['subSizeSlider', 'tabSubSizeSlider'].forEach(id => {
    const el = document.getElementById(id);
    if (el && document.activeElement !== el) el.value = activeSize;
  });

  // Apply live style to Phone preview boxes & Phone Video Overlay
  const priHex = COLOR_HEX_MAP[currentSubStyle.primaryColor] || currentSubStyle.primaryColorHex || '#FFD600';
  const secHex = COLOR_HEX_MAP[currentSubStyle.secondaryColor] || currentSubStyle.secondaryColorHex || '#FFFFFF';
  const priPx = Math.max(12, Math.min(26, Math.round(priSize * 0.78))) + 'px';
  const secPx = Math.max(11, Math.min(22, Math.round(secSize * 0.78))) + 'px';

  ['npThaiLine', 'phoneVideoSubPrimary', 'studioThaiLine'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.style.color = priHex;
      el.style.fontSize = priPx;
      if (id === 'studioThaiLine') {
        el.innerText = '🇹🇭 ตัวอย่างซับไทย (' + priSize + 'sp)';
      }
    }
  });
  ['npSecLine', 'phoneVideoSubSec', 'studioSecLine'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.style.color = secHex;
      el.style.fontSize = secPx;
      if (id === 'studioSecLine') {
        el.style.display = currentSubStyle.dualSubEnabled ? 'block' : 'none';
        el.innerText = '🇻🇳 Phụ đề phụ song ngữ (' + secSize + 'sp)';
      }
    }
  });
}

function previewTargetSizeSlider(target, val) {
  lastPhoneStyleEditMs = Date.now();
  const sz = parseFloat(val) || 20;
  if (target === 'SECONDARY') {
    currentSubStyle.secondaryFontSizeSp = sz;
    currentSubStyle.linkPrimarySecondaryStyle = false;
  } else if (target === 'ALL') {
    currentSubStyle.fontSizeSp = sz;
    currentSubStyle.secondaryFontSizeSp = Math.max(10, Math.round(sz * 0.8));
    currentSubStyle.linkPrimarySecondaryStyle = true;
  } else {
    currentSubStyle.fontSizeSp = sz;
    currentSubStyle.linkPrimarySecondaryStyle = false;
  }
  syncSubStyleUiControls();
}

async function commitTargetSizeSlider(target, val) {
  lastPhoneStyleEditMs = Date.now();
  const sz = parseFloat(val) || 20;
  previewTargetSizeSlider(target, sz);
  const payload = {
    type: 'sub_style',
    target: target,
    linkPrimarySecondaryStyle: (target === 'ALL')
  };
  if (target === 'SECONDARY') {
    payload.secondaryFontSizeSp = sz;
    payload.dualSubEnabled = true;
    currentSubStyle.dualSubEnabled = true;
  } else {
    payload.fontSizeSp = sz;
  }
  await postAction(payload, true);
  showToast((target === 'SECONDARY' ? 'Sub Phụ: ' : 'Sub Chính: ') + Math.round(sz) + 'sp', false);
}

async function nudgeTargetSubSize(target, delta) {
  lastPhoneStyleEditMs = Date.now();
  if (target === 'SECONDARY') {
    currentSubStyle.secondaryFontSizeSp = Math.max(10, Math.min(48, (currentSubStyle.secondaryFontSizeSp || 15) + delta));
    currentSubStyle.linkPrimarySecondaryStyle = false;
    currentSubStyle.dualSubEnabled = true;
  } else if (target === 'ALL') {
    currentSubStyle.fontSizeSp = Math.max(12, Math.min(52, (currentSubStyle.fontSizeSp || 20) + delta));
    currentSubStyle.secondaryFontSizeSp = Math.max(10, Math.min(48, (currentSubStyle.secondaryFontSizeSp || 15) + delta));
    currentSubStyle.linkPrimarySecondaryStyle = true;
  } else {
    currentSubStyle.fontSizeSp = Math.max(12, Math.min(52, (currentSubStyle.fontSizeSp || 20) + delta));
    currentSubStyle.linkPrimarySecondaryStyle = false;
  }
  syncSubStyleUiControls();
  await postAction({
    type: 'sub_style',
    target: target,
    sizeDeltaSp: delta,
    linkPrimarySecondaryStyle: (target === 'ALL'),
    dualSubEnabled: (target === 'SECONDARY') ? true : undefined
  }, true);
}

async function pickTargetColor(target, colorName) {
  lastPhoneStyleEditMs = Date.now();
  const hex = COLOR_HEX_MAP[colorName] || '#FFD600';
  if (target === 'SECONDARY') {
    currentSubStyle.secondaryColor = colorName;
    currentSubStyle.secondaryColorHex = hex;
    currentSubStyle.linkPrimarySecondaryStyle = false;
    currentSubStyle.dualSubEnabled = true;
  } else if (target === 'ALL') {
    currentSubStyle.primaryColor = colorName;
    currentSubStyle.primaryColorHex = hex;
    currentSubStyle.secondaryColor = colorName;
    currentSubStyle.secondaryColorHex = hex;
    currentSubStyle.linkPrimarySecondaryStyle = true;
  } else {
    currentSubStyle.primaryColor = colorName;
    currentSubStyle.primaryColorHex = hex;
    currentSubStyle.linkPrimarySecondaryStyle = false;
  }
  syncSubStyleUiControls();
  await postAction({
    type: 'sub_style',
    target: target,
    color: colorName,
    linkPrimarySecondaryStyle: (target === 'ALL'),
    dualSubEnabled: (target === 'SECONDARY') ? true : undefined
  }, true);
  showToast('Đã đổi màu ' + (target === 'ALL' ? 'Tất Cả Sub' : (target === 'SECONDARY' ? 'Sub Phụ' : 'Sub Chính')) + '!', false);
}

async function toggleDualSubDirect() {
  lastPhoneStyleEditMs = Date.now();
  currentSubStyle.dualSubEnabled = !currentSubStyle.dualSubEnabled;
  syncSubStyleUiControls();
  await postAction({
    type: 'sub_style',
    target: 'SECONDARY',
    dualSubEnabled: currentSubStyle.dualSubEnabled
  }, true);
  showToast(currentSubStyle.dualSubEnabled ? 'Đã BẬT Sub Phụ Song Ngữ!' : 'Đã TẮT Sub Phụ!', false);
}

function previewSubSizeSlider(val) {
  previewTargetSizeSlider(subEditTarget, val);
}

async function commitSubSizeSlider(val) {
  await commitTargetSizeSlider(subEditTarget, val);
}

async function nudgeSubSize(delta) {
  await nudgeTargetSubSize('ALL', delta);
}

async function pickSubColor(colorName) {
  await pickTargetColor(subEditTarget, colorName);
}

async function cycleSubColorQuick() {
  const cur = currentSubStyle.primaryColor || 'CINEMA_YELLOW';
  const idx = (COLOR_ORDER.indexOf(cur) + 1) % COLOR_ORDER.length;
  await pickTargetColor('ALL', COLOR_ORDER[idx]);
}

async function updateSubFrameEdge(edgeName) {
  lastPhoneStyleEditMs = Date.now();
  currentSubStyle.edgeStyle = edgeName;
  await postAction({
    type: 'sub_style',
    target: subEditTarget,
    edgeStyle: edgeName
  }, true);
  showToast('Đã cập nhật khung nền phụ đề!', false);
}

async function nudgeSubBottomOffset(deltaDp) {
  lastPhoneStyleEditMs = Date.now();
  const nextOff = Math.max(4, Math.min(160, (currentSubStyle.bottomOffsetDp || 18) + deltaDp));
  currentSubStyle.bottomOffsetDp = nextOff;
  await postAction({
    type: 'sub_style',
    target: subEditTarget,
    bottomOffsetDp: nextOff
  }, true);
  showToast('Vị trí Sub: ' + Math.round(nextOff) + 'dp', false);
}

async function resetSubStyleDefault() {
  lastPhoneStyleEditMs = Date.now();
  currentSubStyle.fontSizeSp = 20;
  currentSubStyle.secondaryFontSizeSp = 15;
  currentSubStyle.primaryColor = 'CINEMA_YELLOW';
  currentSubStyle.primaryColorHex = '#FFD600';
  currentSubStyle.secondaryColor = 'WHITE';
  currentSubStyle.secondaryColorHex = '#FFFFFF';
  currentSubStyle.linkPrimarySecondaryStyle = false;
  syncSubStyleUiControls();
  await postAction({
    type: 'sub_style',
    resetDefault: true
  }, false);
}

/* ---------------- LIVE TV SCREEN STREAM + ROTATION-AWARE TOUCH & SWIPE ---------------- */
function toggleLiveStream() {
  streamEnabled = !streamEnabled;
  const btn = document.getElementById('toggleStreamBtn');
  const badge = document.getElementById('streamFpsBadge');
  if (streamEnabled) {
    btn.innerText = '⏸ Dừng';
    badge.innerText = 'LIVE TOUCH STREAM';
    requestNextFrame();
  } else {
    btn.innerText = '▶ Tiếp tục';
    badge.innerText = 'ĐÃ TẠM DỪNG';
  }
}

function requestNextFrame() {
  const isFullscreenStream = (activeFullscreenStageId === 'streamTouchBox');
  if (!streamEnabled || (activeTab !== 'tab-stream' && !isFullscreenStream) || frameInFlight) return;
  frameInFlight = true;
  const img = document.getElementById('tvLiveImg');
  const nextImg = new Image();
  const w = isFullscreenStream ? 1120 : 960;
  nextImg.onload = function() {
    img.src = nextImg.src;
    frameInFlight = false;
    if (streamEnabled && (activeTab === 'tab-stream' || activeFullscreenStageId === 'streamTouchBox')) {
      clearTimeout(streamTimer);
      streamTimer = setTimeout(requestNextFrame, 130);
    }
  };
  nextImg.onerror = function() {
    frameInFlight = false;
    if (streamEnabled && (activeTab === 'tab-stream' || activeFullscreenStageId === 'streamTouchBox')) {
      clearTimeout(streamTimer);
      streamTimer = setTimeout(requestNextFrame, 450);
    }
  };
  nextImg.src = '/api/screen.jpg?w=' + w + '&q=70&t=' + Date.now();
}

(function initTouchStreamSurface() {
  const box = document.getElementById('streamTouchBox');
  const ripple = document.getElementById('touchRipple');
  let startX = 0, startY = 0, isDown = false;

  function getNormCoords(clientX, clientY) {
    const rect = box.getBoundingClientRect();
    const isRotated90 = box.classList.contains('stage-landscape-rotated');
    let nx, ny, px, py;
    if (isRotated90) {
      // When rotated +90deg clockwise, screen Y maps to unrotated X, and inverted screen X maps to unrotated Y!
      nx = Math.max(0, Math.min(1, (clientY - rect.top) / Math.max(1, rect.height)));
      ny = Math.max(0, Math.min(1, 1.0 - ((clientX - rect.left) / Math.max(1, rect.width))));
      px = nx * box.clientWidth;
      py = ny * box.clientHeight;
    } else {
      nx = Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
      ny = Math.max(0, Math.min(1, (clientY - rect.top) / Math.max(1, rect.height)));
      px = nx * box.clientWidth;
      py = ny * box.clientHeight;
    }
    return { nx, ny, px, py };
  }

  function showRippleAt(px, py) {
    ripple.style.left = px + 'px';
    ripple.style.top = py + 'px';
    ripple.classList.add('show');
    setTimeout(() => ripple.classList.remove('show'), 260);
  }

  box.addEventListener('pointerdown', function(e) {
    if (e.target.closest('.fs-hud') || e.target.closest('.stream-fs-trigger')) return;
    isDown = true;
    const c = getNormCoords(e.clientX, e.clientY);
    startX = c.nx;
    startY = c.ny;
    showRippleAt(c.px, c.py);
  });

  box.addEventListener('pointerup', async function(e) {
    if (!isDown) return;
    isDown = false;
    const c = getNormCoords(e.clientX, e.clientY);
    const dx = c.nx - startX;
    const dy = c.ny - startY;
    const dist = Math.hypot(dx, dy);

    if (dist > 0.045) {
      await fetch('/api/touch', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({type: 'swipe', x1: startX, y1: startY, x2: c.nx, y2: c.ny})
      });
    } else {
      await fetch('/api/touch', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({type: 'tap', x: c.nx, y: c.ny})
      });
    }
    setTimeout(requestNextFrame, 80);
  });
})();

async function sendTouchKey(keyName) {
  try {
    await fetch('/api/touch', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({type: 'key', key: keyName})
    });
    setTimeout(requestNextFrame, 90);
    setTimeout(pollTvState, 140);
  } catch (e) {}
}

/* ---------------- LIVE TV STATE & NOW-PLAYING REMOTE ---------------- */
function formatTimeMs(ms) {
  if (!ms || ms < 0) return '00:00';
  const totalSec = Math.floor(ms / 1000);
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
}

async function pollTvState() {
  try {
    const r = await fetch('/api/state?t=' + Date.now());
    const st = await r.json();
    latestTvState = st;

    const screenBadge = document.getElementById('tvScreenBadge');
    const uiLabel = (st.deviceUiMode === 'MOBILE') ? 'Mode ĐT' : 'Mode TV';
    screenBadge.innerText = 'TV: ' + (st.screen || 'HOME') + ' • ' + uiLabel;

    const aiBadge = document.getElementById('aiKeyBadge');
    if (st.hasGeminiKey) {
      aiBadge.className = 'status-pill ok';
      aiBadge.innerText = 'AI: ' + (st.geminiModel || 'Ready').slice(0, 14);
    } else {
      aiBadge.className = 'status-pill';
      aiBadge.innerText = 'Sub & AI';
    }

    if (st.subtitleStyle && (Date.now() - lastPhoneStyleEditMs > 2500)) {
      const ss = st.subtitleStyle;
      currentSubStyle = Object.assign(currentSubStyle, ss);
      syncSubStyleUiControls();
    }

    const npCard = document.getElementById('nowPlayingCard');
    const p = st.player;
    if (p && p.isActive) {
      npCard.style.display = 'block';
      document.getElementById('npTitle').innerText = p.movieTitle + ' (' + p.episodeName + ')';
      document.getElementById('npTime').innerText = formatTimeMs(p.positionMs) + ' / ' + formatTimeMs(p.durationMs);
      document.getElementById('npSubStatus').innerText = p.isPreFlightBlocking
        ? ('Đang chuẩn bị: ' + (p.preFlightStep || 'Phụ đề...'))
        : (p.statusBadge || 'Sub: ไทย');

      const thaiTxt = p.primaryThaiText || (p.isPlaying ? '♪ ...' : '⏸ Đang tạm dừng');
      const secTxt = p.secondaryText || '';
      document.getElementById('npThaiLine').innerText = thaiTxt;
      const npSec = document.getElementById('npSecLine');
      if (secTxt) {
        npSec.style.display = 'block';
        npSec.innerText = secTxt;
      } else {
        npSec.style.display = 'none';
      }

      // Mirror live subtitle onto Phone Video Player overlay too
      const pvPri = document.getElementById('phoneVideoSubPrimary');
      const pvSec = document.getElementById('phoneVideoSubSec');
      const pvPill = document.getElementById('phoneVideoSubPill');
      if (pvPri && pvPill) {
        if (p.primaryThaiText || p.secondaryText) {
          pvPill.style.display = 'inline-flex';
          pvPri.innerText = p.primaryThaiText || '';
          if (p.secondaryText) {
            pvSec.style.display = 'block';
            pvSec.innerText = p.secondaryText;
          } else {
            pvSec.style.display = 'none';
          }
        } else {
          pvPill.style.display = 'none';
        }
      }

      document.getElementById('npPlayBtn').innerText = p.isPlaying ? '⏸ Dừng' : '▶ Phát';

      const slider = document.getElementById('npSeekSlider');
      if (p.durationMs > 0 && document.activeElement !== slider) {
        slider.max = p.durationMs;
        slider.value = p.positionMs;
      }

      renderPhoneSnapCandidates(p.snapCandidates || [], p.positionMs);
    } else {
      npCard.style.display = 'none';
    }
  } catch (e) {}
}

function renderPhoneSnapCandidates(cues, anchorMs) {
  const wrap = document.getElementById('phoneSnapWrap');
  if (wrap.style.display === 'none') return;
  const list = document.getElementById('phoneSnapList');
  if (!cues || cues.length === 0) {
    list.innerHTML = '<div class="subtext" style="padding:8px;">Chưa có danh sách câu thoại để khớp.</div>';
    return;
  }
  list.innerHTML = cues.map(c => {
    const cls = c.isNearest ? 'snap-item nearest' : 'snap-item';
    const refHtml = c.referenceText ? ('<div style="font-size:12px;color:#00E5FF;margin-top:2px;">' + escapeHtml(c.referenceText) + '</div>') : '';
    return '<div class="' + cls + '" onclick="snapSubtitleCueOnTv(' + c.startMs + ',' + anchorMs + ')">' +
      '<div style="font-size:11px;color:#9CA3AF;">' + formatTimeMs(c.startMs) + (c.isNearest ? ' • Gần nhất' : '') + '</div>' +
      '<div style="font-size:14px;font-weight:700;color:#FFD600;">' + escapeHtml(c.thaiText) + '</div>' +
      refHtml +
      '</div>';
  }).join('');
}

function togglePhoneSnapList() {
  const wrap = document.getElementById('phoneSnapWrap');
  wrap.style.display = (wrap.style.display === 'none') ? 'block' : 'none';
  if (wrap.style.display === 'block' && latestTvState && latestTvState.player) {
    renderPhoneSnapCandidates(latestTvState.player.snapCandidates || [], latestTvState.player.positionMs || 0);
  }
}

async function snapSubtitleCueOnTv(cueStartMs, anchorMs) {
  await postAction({
    type: 'player_cmd',
    cmd: 'snap_cue',
    value: cueStartMs,
    second: anchorMs || (latestTvState && latestTvState.player ? latestTvState.player.positionMs : 0)
  });
  document.getElementById('phoneSnapWrap').style.display = 'none';
  setTimeout(pollTvState, 120);
}

async function sendPlayerCmd(cmd, val) {
  await postAction({type: 'player_cmd', cmd: cmd, value: val || 0}, true);
  setTimeout(pollTvState, 120);
}

async function seekTvToSlider(val) {
  await postAction({type: 'player_cmd', cmd: 'seek_to', value: parseInt(val, 10) || 0}, true);
  setTimeout(pollTvState, 120);
}

/* ---------------- PHONE CATALOG & MOVIE DETAIL ---------------- */
function escapeHtml(s) {
  return String(s || '').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
}

async function loadPhoneCatalog(countryCode, searchQuery) {
  const container = document.getElementById('catalogContainer');
  try {
    let url = '/api/catalog?country=' + encodeURIComponent(countryCode || 'all');
    if (searchQuery) url += '&q=' + encodeURIComponent(searchQuery);
    const r = await fetch(url);
    const data = await r.json();

    if (searchQuery && data.searchResults) {
      if (data.searchResults.length === 0) {
        container.innerHTML = '<div class="subtext" style="text-align:center;padding:24px;">Không tìm thấy phim phù hợp cho "' + escapeHtml(searchQuery) + '".</div>';
      } else {
        container.innerHTML = '<div class="section-title"><span>Kết quả tìm kiếm (' + data.searchResults.length + ')</span></div>' +
          renderMovieGridHtml(data.searchResults);
      }
      return;
    }

    const sections = data.sections || [];
    if (sections.length === 0) {
      container.innerHTML = '<div class="subtext" style="text-align:center;padding:24px;">TV đang tải danh mục phim, vui lòng đợi...</div>';
      setTimeout(() => loadPhoneCatalog(countryCode, searchQuery), 1800);
      return;
    }

    container.innerHTML = sections.map(sec => {
      return '<div class="section-title"><span>' + escapeHtml(sec.title) + '</span></div>' +
        renderMovieGridHtml(sec.movies || []);
    }).join('');
  } catch (e) {
    container.innerHTML = '<div class="subtext" style="text-align:center;padding:24px;">Không thể kết nối tới TV.</div>';
  }
}

function renderMovieGridHtml(movies) {
  return '<div class="movie-grid">' + movies.map(m => {
    const poster = m.posterUrl ? ('<img src="' + escapeHtml(m.posterUrl) + '" loading="lazy" alt="">') : '';
    const subBadge = m.hasNativeThaiSub ? 'SUB ไทย' : 'AI ไทย';
    return '<div class="movie-card" onclick="openMovieDetailOnPhone(\'' + escapeHtml(m.id) + '\')">' +
      '<div class="poster-box">' +
        poster +
        '<div class="poster-country">' + escapeHtml(m.countryName) + '</div>' +
        '<div class="poster-badge">' + subBadge + '</div>' +
      '</div>' +
      '<div class="movie-info">' +
        '<div class="movie-title">' + escapeHtml(m.title) + '</div>' +
        '<div class="movie-meta">' + escapeHtml([m.year, m.quality, m.providerName].filter(Boolean).join(' • ')) + '</div>' +
      '</div>' +
    '</div>';
  }).join('') + '</div>';
}

function selectCountryChip(code, chipEl) {
  currentCountry = code;
  document.getElementById('qInput').value = '';
  document.querySelectorAll('#countryChips .chip').forEach(c => c.classList.remove('active'));
  if (chipEl) chipEl.classList.add('active');
  postAction({type: 'select_country', country: code}, true);
  setTimeout(() => loadPhoneCatalog(code, ''), 350);
}

function doPhoneSearch() {
  const q = document.getElementById('qInput').value.trim();
  if (!q) {
    loadPhoneCatalog(currentCountry, '');
    return;
  }
  postAction({type: 'search', query: q}, true);
  setTimeout(() => loadPhoneCatalog(currentCountry, q), 650);
}

function pushSearchToTv() {
  const q = document.getElementById('qInput').value.trim();
  if (!q) return showToast('Hãy nhập từ khóa tìm kiếm trước', true);
  postAction({type: 'search', query: q});
}

async function openMovieDetailOnPhone(movieId) {
  const modal = document.getElementById('movieModal');
  document.getElementById('mdTitle').innerText = 'Đang tải thông tin & tập phim...';
  document.getElementById('mdMeta').innerText = '';
  document.getElementById('mdEpisodes').innerHTML = '';
  document.getElementById('phoneVideoWrap').style.display = 'none';
  stopPhoneVideo();
  modal.classList.add('open');

  try {
    const r = await fetch('/api/movie?id=' + encodeURIComponent(movieId));
    const m = await r.json();
    currentModalMovie = m;
    document.getElementById('mdTitle').innerText = m.title || 'Chi tiết phim';
    document.getElementById('mdMeta').innerText =
      [m.countryName, m.year, m.quality, m.genre].filter(Boolean).join(' • ') +
      (m.description ? (' — ' + m.description.slice(0, 140) + '...') : '');

    const eps = m.episodes || [];
    const firstEp = eps[0] || null;

    document.getElementById('mdPlayTvBtn').onclick = function() {
      postAction({type: 'open_movie', movieId: m.id, quickPlay: true});
      closeMovieModal();
    };
    document.getElementById('mdWatchPhoneBtn').onclick = function() {
      if (firstEp && firstEp.streamUrl) {
        playStreamOnPhoneVideo(firstEp.name, firstEp.streamUrl, true);
      } else {
        showToast('Phim này cần phát trực tiếp trên TV', true);
      }
    };

    if (eps.length === 0) {
      document.getElementById('mdEpisodes').innerHTML = '<div class="subtext">Bấm "Phát Ngay Trên TV" để mở phim.</div>';
    } else {
      document.getElementById('mdEpisodes').innerHTML = eps.map(ep => {
        return '<button class="btn" onclick="playEpisodeFromPhone(\'' + escapeHtml(m.id) + '\',\'' + escapeHtml(ep.id) + '\')">' +
          escapeHtml(ep.name) +
        '</button>';
      }).join('');
    }
  } catch (e) {
    document.getElementById('mdTitle').innerText = 'Không tải được chi tiết phim';
  }
}

function playEpisodeFromPhone(movieId, episodeId) {
  postAction({type: 'play_episode', movieId: movieId, episodeId: episodeId});
  closeMovieModal();
}

function playStreamOnPhoneVideo(epName, streamUrl, autoLandscape) {
  const wrap = document.getElementById('phoneVideoWrap');
  const video = document.getElementById('phoneVideoEl');
  document.getElementById('phoneVideoEpName').innerText = epName || '';
  wrap.style.display = 'block';
  stopPhoneVideo();

  if (window.Hls && Hls.isSupported() && streamUrl.indexOf('.m3u8') >= 0) {
    hlsInstance = new Hls();
    hlsInstance.loadSource(streamUrl);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, function() {
      video.play().catch(()=>{});
    });
  } else {
    video.src = streamUrl;
    video.play().catch(()=>{});
  }
  if (autoLandscape) {
    enterLandscapeFullscreen('phoneVideoStage');
  }
}

function togglePhoneVideoPlay() {
  const v = document.getElementById('phoneVideoEl');
  if (!v) return;
  if (v.paused) v.play().catch(()=>{}); else v.pause();
}

function seekPhoneVideoRel(sec) {
  const v = document.getElementById('phoneVideoEl');
  if (!v) return;
  v.currentTime = Math.max(0, (v.currentTime || 0) + sec);
}

function watchActiveOnPhone() {
  if (!latestTvState || !latestTvState.player || !latestTvState.player.streamUrl) {
    return showToast('TV hiện chưa phát luồng video nào', true);
  }
  const p = latestTvState.player;
  const modal = document.getElementById('movieModal');
  document.getElementById('mdTitle').innerText = p.movieTitle || 'Đang phát';
  document.getElementById('mdMeta').innerText = 'Đang xem song song với TV • ' + (p.episodeName || '');
  document.getElementById('mdEpisodes').innerHTML = '';
  modal.classList.add('open');
  playStreamOnPhoneVideo(p.episodeName, p.streamUrl, true);
}

function stopPhoneVideo() {
  const video = document.getElementById('phoneVideoEl');
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }
  if (video) {
    video.pause();
    video.removeAttribute('src');
    video.load();
  }
}

function closeMovieModal() {
  exitLandscapeFullscreen();
  stopPhoneVideo();
  document.getElementById('movieModal').classList.remove('open');
}

/* ---------------- GEMINI KEY & UPLOAD ACTIONS ---------------- */
function toggleKeyVis() {
  const el = document.getElementById('geminiKey');
  el.type = (el.type === 'password') ? 'text' : 'password';
}

async function loadLiveModels() {
  const apiKey = document.getElementById('geminiKey').value.trim();
  if (!apiKey) return showToast('Hãy dán Gemini API Key trước!', true);
  showToast('Đang truy vấn danh sách Model Gemini...', false);
  try {
    const r = await fetch('/api/models', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({apiKey: apiKey})
    });
    const d = await r.json();
    if (d.models && d.models.length > 0) {
      const sel = document.getElementById('geminiModel');
      const prev = sel.value;
      sel.innerHTML = '';
      d.models.forEach(function(m) {
        const opt = document.createElement('option');
        opt.value = m;
        opt.textContent = m;
        sel.appendChild(opt);
      });
      if (d.models.indexOf(prev) >= 0) sel.value = prev;
      showToast(d.ok ? ('Đã tải ' + d.count + ' Model Gemini!') : 'Kiểm tra lại API Key', !d.ok);
    }
  } catch (e) {
    showToast('Lỗi kết nối tới TV', true);
  }
}

async function postAction(payload, silent) {
  try {
    const r = await fetch('/api/action', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload)
    });
    const d = await r.json();
    if (!silent) showToast(d.msg || 'Thành công!', !d.ok);
    return d.ok;
  } catch (e) {
    if (!silent) showToast('Lỗi kết nối tới TV', true);
    return false;
  }
}

async function sendGemini() {
  const apiKey = document.getElementById('geminiKey').value.trim();
  const customM = document.getElementById('customModel').value.trim();
  const selectedM = document.getElementById('geminiModel').value;
  const modelId = customM || selectedM;
  if (!apiKey) return showToast('Vui lòng dán Gemini API Key!', true);
  const ok = await postAction({type:'gemini', apiKey:apiKey, modelId:modelId});
  if (ok) {
    document.getElementById('geminiKey').value = '';
    showToast('Đã nạp vào RAM TV (' + modelId + ')!', false);
    setTimeout(pollTvState, 200);
  }
}

function wipeGemini() {
  document.getElementById('geminiKey').value = '';
  postAction({type:'wipe_gemini'});
  setTimeout(pollTvState, 200);
}

function sendStream() {
  const url = document.getElementById('streamUrl').value.trim();
  const title = document.getElementById('streamTitle').value.trim();
  if (!url) return showToast('Vui lòng nhập link phim / YouTube', true);
  postAction({type:'stream', title:title, url:url});
}

function sendProvider() {
  const name = document.getElementById('provName').value.trim();
  const url = document.getElementById('provUrl').value.trim();
  const providerType = document.getElementById('provType').value;
  if (!url) return showToast('Vui lòng nhập URL nguồn phim', true);
  postAction({type:'provider', name:name, url:url, providerType:providerType});
}

function sendSub() {
  const input = document.getElementById('subFile');
  if (!input.files || !input.files[0]) return showToast('Hãy chọn file .srt/.vtt/.ass', true);
  const file = input.files[0];
  const lang = document.getElementById('subLang').value;
  const reader = new FileReader();
  reader.onload = function() {
    const base64 = reader.result.split(',')[1];
    postAction({type:'subtitle', fileName:file.name, lang:lang, base64:base64});
  };
  reader.readAsDataURL(file);
}

// Start live polling & catalog sync
pollTvState();
loadPhoneCatalog('all', '');
setInterval(pollTvState, 1100);
</script>
</body>
</html>
        """.trimIndent()
    }
}
