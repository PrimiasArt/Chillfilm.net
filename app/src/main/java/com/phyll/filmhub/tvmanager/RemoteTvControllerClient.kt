package com.phyll.filmhub.tvmanager

import com.phyll.filmhub.model.ManagedTvDevice
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.RemoteTvLiveState
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleStyleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Client running inside the standalone Android app to discover Smart TVs / TV Boxes on the LAN,
 * sync the phone's stored Gemini API key & subtitle styles to any selected TV, push movies/streams,
 * adjust subtitles, and stream live screenshots + touch/D-Pad events to/from the TV (`:8899`).
 */
class RemoteTvControllerClient(
    private val adbClient: AdbTcpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2_000, TimeUnit.MILLISECONDS)
        .readTimeout(6_000, TimeUnit.MILLISECONDS)
        .writeTimeout(6_000, TimeUnit.MILLISECONDS)
        .build()

    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(1_200, TimeUnit.MILLISECONDS)
        .readTimeout(2_500, TimeUnit.MILLISECONDS)
        .build()

    fun getLocalIpv4Addresses(): Set<String> {
        val result = LinkedHashSet<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172.")) {
                            result.add(host)
                        }
                    }
                }
            }
        }
        return result
    }

    suspend fun discoverLanTvs(
        existingDevices: List<ManagedTvDevice> = emptyList()
    ): List<ManagedTvDevice> = withContext(Dispatchers.IO) {
        val ownIps = getLocalIpv4Addresses()
        val prefixes = LinkedHashSet<String>()
        ownIps.forEach { ip ->
            val dot = ip.lastIndexOf('.')
            if (dot > 0) prefixes.add(ip.substring(0, dot))
        }
        existingDevices.forEach { dev ->
            val dot = dev.ip.lastIndexOf('.')
            if (dot > 0) prefixes.add(dev.ip.substring(0, dot))
        }

        val candidates = LinkedHashSet<String>()
        existingDevices.forEach { if (!ownIps.contains(it.ip)) candidates.add(it.ip) }
        for (prefix in prefixes) {
            for (hostPart in 1..254) {
                val ip = "$prefix.$hostPart"
                if (!ownIps.contains(ip)) {
                    candidates.add(ip)
                }
            }
        }

        val sem = Semaphore(48)
        val found = coroutineScope {
            candidates.map { ip ->
                async {
                    sem.withPermit {
                        val adbOpen = isTcpPortOpen(ip, AdbTcpClient.DEFAULT_ADB_PORT, 260)
                        val compOpen = isTcpPortOpen(ip, DEFAULT_COMPANION_PORT, 260)
                        if (adbOpen || compOpen) {
                            probeTvDetails(ip, adbOpen = adbOpen, companionOpen = compOpen)
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        found.sortedWith(
            compareByDescending<ManagedTvDevice> { it.companionOnline }
                .thenByDescending { it.adbOnline }
                .thenBy { it.ip }
        )
    }

    suspend fun probeSingleTv(ip: String): ManagedTvDevice = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim()
        val adbOpen = isTcpPortOpen(cleanIp, AdbTcpClient.DEFAULT_ADB_PORT, 800)
        val compOpen = isTcpPortOpen(cleanIp, DEFAULT_COMPANION_PORT, 800)
        probeTvDetails(cleanIp, adbOpen = adbOpen, companionOpen = compOpen)
    }

    private fun probeTvDetails(
        ip: String,
        adbOpen: Boolean,
        companionOpen: Boolean
    ): ManagedTvDevice {
        val adbInfo = if (adbOpen) {
            runCatching { adbClient.testConnectionAndProbe(ip, AdbTcpClient.DEFAULT_ADB_PORT, timeoutMs = 4_000) }
                .getOrNull()
        } else null

        val state = if (companionOpen) {
            fetchLiveStateSync(ip, DEFAULT_COMPANION_PORT)
        } else null

        val infoJson = if (companionOpen) {
            getJsonSync("http://$ip:$DEFAULT_COMPANION_PORT/api/info")
        } else null

        val hwModel = adbInfo?.model?.takeIf { it.isNotBlank() }
            ?: infoJson?.optString("hardwareModel").orEmpty()
        val friendlyName = hwModel.ifBlank {
            infoJson?.optString("deviceName").orEmpty()
        }.ifBlank { "Android TV ($ip)" }

        val version = adbInfo?.appVersion?.takeIf { it.isNotBlank() }
            ?: infoJson?.optString("version").orEmpty()
        val installed = (adbInfo?.packageInstalled == true) || companionOpen

        return ManagedTvDevice(
            ip = ip,
            name = friendlyName,
            model = hwModel,
            adbPort = AdbTcpClient.DEFAULT_ADB_PORT,
            companionPort = DEFAULT_COMPANION_PORT,
            adbOnline = adbOpen,
            adbNeedsAuth = adbInfo?.needsAuthorization == true,
            companionOnline = companionOpen && state?.online == true,
            appInstalled = installed,
            appVersion = version,
            activeScreen = state?.screen.orEmpty(),
            nowPlayingTitle = if (state?.playerActive == true) state.movieTitle else "",
            hasGeminiKeyOnTv = state?.hasGeminiKey == true,
            lastSeenMs = System.currentTimeMillis()
        )
    }

    suspend fun fetchLiveState(tvIp: String, port: Int = DEFAULT_COMPANION_PORT): RemoteTvLiveState? =
        withContext(Dispatchers.IO) {
            fetchLiveStateSync(tvIp, port)
        }

    private fun fetchLiveStateSync(tvIp: String, port: Int): RemoteTvLiveState? {
        val obj = getJsonSync("http://$tvIp:$port/api/state") ?: return null
        if (!obj.optBoolean("ok", false)) return null
        val subObj = obj.optJSONObject("subtitleStyle")
        val playerObj = obj.optJSONObject("player")

        val baseStyle = SubtitleStyleConfig()
        val parsedStyle = if (subObj != null) {
            val priColor = SubtitleColorChoice.entries.firstOrNull {
                it.name.equals(subObj.optString("primaryColor"), ignoreCase = true)
            } ?: baseStyle.primaryColor
            val secColor = SubtitleColorChoice.entries.firstOrNull {
                it.name.equals(subObj.optString("secondaryColor"), ignoreCase = true)
            } ?: baseStyle.secondaryColor
            val edge = SubtitleEdgeStyle.entries.firstOrNull {
                it.name.equals(subObj.optString("edgeStyle"), ignoreCase = true)
            } ?: baseStyle.edgeStyle

            baseStyle.copy(
                fontSizeSp = subObj.optDouble("fontSizeSp", baseStyle.fontSizeSp.toDouble()).toFloat(),
                secondaryFontSizeSp = subObj.optDouble("secondaryFontSizeSp", baseStyle.secondaryFontSizeSp.toDouble()).toFloat(),
                primaryColor = priColor,
                secondaryColor = secColor,
                linkPrimarySecondaryStyle = subObj.optBoolean("linkPrimarySecondaryStyle", false),
                edgeStyle = edge,
                boxOpacity = subObj.optDouble("boxOpacity", baseStyle.boxOpacity.toDouble()).toFloat(),
                bottomOffsetDp = subObj.optDouble("bottomOffsetDp", baseStyle.bottomOffsetDp.toDouble()).toFloat(),
                hardsubMaskEnabled = subObj.optBoolean("hardsubMaskEnabled", false),
                dualSubEnabled = subObj.optBoolean("dualSubEnabled", false),
                geminiModelId = obj.optString("geminiModel", baseStyle.geminiModelId)
            )
        } else {
            baseStyle
        }

        return RemoteTvLiveState(
            tvIp = tvIp,
            online = true,
            screen = obj.optString("screen", "HOME"),
            selectedCountry = obj.optString("selectedCountry", "all"),
            deviceUiMode = obj.optString("deviceUiMode", "MOBILE"),
            hasGeminiKey = obj.optBoolean("hasGeminiKey", false),
            geminiModel = obj.optString("geminiModel", baseStyle.geminiModelId),
            banner = obj.optString("banner", ""),
            isPlaying = playerObj?.optBoolean("isPlaying", false) ?: false,
            isBuffering = playerObj?.optBoolean("isBuffering", false) ?: false,
            playerActive = playerObj?.optBoolean("isActive", false) ?: false,
            movieId = playerObj?.optString("movieId", "") ?: "",
            movieTitle = playerObj?.optString("movieTitle", "") ?: "",
            episodeId = playerObj?.optString("episodeId", "") ?: "",
            episodeName = playerObj?.optString("episodeName", "") ?: "",
            positionMs = playerObj?.optLong("positionMs", 0L) ?: 0L,
            durationMs = playerObj?.optLong("durationMs", 0L) ?: 0L,
            syncOffsetMs = playerObj?.optLong("syncOffsetMs", 0L) ?: 0L,
            subSpeedScale = playerObj?.optDouble("subSpeedScale", 1.0)?.toFloat() ?: 1.0f,
            primaryThaiText = playerObj?.optString("primaryThaiText", "") ?: "",
            secondaryText = playerObj?.optString("secondaryText", "") ?: "",
            statusBadge = playerObj?.optString("statusBadge", "") ?: "",
            subtitleStyle = parsedStyle
        )
    }

    suspend fun fetchScreenFrame(
        tvIp: String,
        port: Int = DEFAULT_COMPANION_PORT,
        width: Int = 960,
        quality: Int = 70
    ): ByteArray? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("http://$tvIp:$port/api/screen.jpg?w=$width&q=$quality&t=${System.currentTimeMillis()}")
            .get()
            .build()
        runCatching {
            streamClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        }.getOrNull()
    }

    suspend fun syncCredentialsAndConfigToTv(
        tvIp: String,
        apiKey: String,
        styleConfig: SubtitleStyleConfig,
        silent: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        var allOk = true
        if (apiKey.isNotBlank() || styleConfig.geminiModelId.isNotBlank()) {
            val geminiPayload = JSONObject()
                .put("type", "gemini")
                .put("apiKey", apiKey)
                .put("modelId", styleConfig.geminiModelId)
                .put("silent", silent)
            allOk = postActionSync(tvIp, geminiPayload) && allOk
        }

        val stylePayload = JSONObject()
            .put("type", "sub_style")
            .put("fontSizeSp", styleConfig.fontSizeSp.toDouble())
            .put("secondaryFontSizeSp", styleConfig.secondaryFontSizeSp.toDouble())
            .put("primaryColor", styleConfig.primaryColor.name)
            .put("secondaryColor", styleConfig.secondaryColor.name)
            .put("linkPrimarySecondaryStyle", styleConfig.linkPrimarySecondaryStyle)
            .put("edgeStyle", styleConfig.edgeStyle.name)
            .put("boxOpacity", styleConfig.boxOpacity.toDouble())
            .put("bottomOffsetDp", styleConfig.bottomOffsetDp.toDouble())
            .put("hardsubMaskEnabled", styleConfig.hardsubMaskEnabled)
            .put("dualSubEnabled", styleConfig.dualSubEnabled)
        allOk = postActionSync(tvIp, stylePayload) && allOk
        allOk
    }

    suspend fun sendAction(tvIp: String, payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        postActionSync(tvIp, payload)
    }

    suspend fun sendTouch(tvIp: String, payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        postJsonSync("http://$tvIp:$DEFAULT_COMPANION_PORT/api/touch", payload)
    }

    suspend fun sendTouchKey(tvIp: String, key: String): Boolean =
        sendTouch(tvIp, JSONObject().put("type", "key").put("key", key))

    suspend fun sendTap(tvIp: String, nx: Float, ny: Float): Boolean =
        sendTouch(
            tvIp,
            JSONObject()
                .put("type", "tap")
                .put("x", nx.toDouble())
                .put("y", ny.toDouble())
        )

    suspend fun sendSwipe(tvIp: String, x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        sendTouch(
            tvIp,
            JSONObject()
                .put("type", "swipe")
                .put("x1", x1.toDouble())
                .put("y1", y1.toDouble())
                .put("x2", x2.toDouble())
                .put("y2", y2.toDouble())
        )

    suspend fun sendPlayerCommand(
        tvIp: String,
        cmd: String,
        value: Long = 0L,
        second: Long = 0L
    ): Boolean = sendAction(
        tvIp,
        JSONObject()
            .put("type", "player_cmd")
            .put("cmd", cmd)
            .put("value", value)
            .put("second", second)
    )

    suspend fun pushDirectStream(tvIp: String, title: String, streamUrl: String): Boolean =
        sendAction(
            tvIp,
            JSONObject()
                .put("type", "stream")
                .put("title", title)
                .put("url", streamUrl)
        )

    suspend fun pushMovieAndPlayOnTv(
        tvIp: String,
        movie: MovieItem,
        episode: Episode,
        startPositionMs: Long = 0L
    ): Boolean {
        val subsArr = JSONArray()
        episode.subtitles.forEach { s ->
            subsArr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("label", s.label)
                    .put("languageCode", s.languageCode)
                    .put("urlOrContent", s.urlOrContent)
                    .put("isRawContent", s.isRawContent)
                    .put("sourcePlatform", s.sourcePlatform)
                    .put("isAutoGenerated", s.isAutoGenerated)
            )
        }
        val payload = JSONObject()
            .put("type", "play_movie_payload")
            .put("movieId", movie.id)
            .put("movieTitle", movie.title)
            .put("originalTitle", movie.originalTitle)
            .put("year", movie.year)
            .put("countryCode", movie.country.code)
            .put("genre", movie.genre)
            .put("posterUrl", movie.posterUrl)
            .put("backdropUrl", movie.backdropUrl)
            .put("description", movie.description)
            .put("providerName", movie.providerName)
            .put("episodeId", episode.id)
            .put("episodeName", episode.name)
            .put("streamUrl", episode.streamUrl)
            .put("audioStreamUrl", episode.audioStreamUrl)
            .put("fallbackStreamUrl", episode.fallbackStreamUrl)
            .put("startPositionMs", startPositionMs)
            .put("subtitles", subsArr)
        return sendAction(tvIp, payload)
    }

    suspend fun pushSubtitleFileBytes(
        tvIp: String,
        fileName: String,
        rawBytes: ByteArray,
        lang: String = "th"
    ): Boolean {
        val b64 = encodeBase64(rawBytes)
        return sendAction(
            tvIp,
            JSONObject()
                .put("type", "subtitle")
                .put("fileName", fileName)
                .put("lang", lang)
                .put("base64", b64)
        )
    }

    private fun postActionSync(tvIp: String, payload: JSONObject): Boolean =
        postJsonSync("http://$tvIp:$DEFAULT_COMPANION_PORT/api/action", payload)

    private fun postJsonSync(url: String, payload: JSONObject): Boolean {
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optBoolean("ok", true)
            }
        }.getOrDefault(false)
    }

    private fun getJsonSync(url: String): JSONObject? {
        val req = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) null else JSONObject(body)
            }
        }.getOrNull()
    }

    private fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun encodeBase64(input: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val out = StringBuilder(((input.size + 2) / 3) * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i++].toInt() and 0xFF
            val hasB1 = i < input.size
            val b1 = if (hasB1) input[i++].toInt() and 0xFF else 0
            val hasB2 = i < input.size
            val b2 = if (hasB2) input[i++].toInt() and 0xFF else 0

            out.append(table[b0 ushr 2])
            out.append(table[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            out.append(if (hasB1) table[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            out.append(if (hasB2) table[b2 and 0x3F] else '=')
        }
        return out.toString()
    }

    companion object {
        const val DEFAULT_COMPANION_PORT = 8899
    }
}
