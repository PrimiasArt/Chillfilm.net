package com.phyll.filmhub.offline

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phyll.filmhub.cloud.CloudSyncClient
import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup
import com.phyll.filmhub.model.SubtitleCue
import com.phyll.filmhub.subtitle.HlsPlaylistSanitizer
import com.phyll.filmhub.subtitle.ThaiSubtitleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class OfflineDownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class OfflineEpisodeRecord(
    val id: String,
    val movieId: String,
    val movieSlug: String,
    val movieTitle: String,
    val posterUrl: String,
    val year: String = "",
    val episodeId: String,
    val episodeName: String,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitleLangCode: String = "vi",
    val subtitleCueCount: Int = 0,
    val status: OfflineDownloadStatus = OfflineDownloadStatus.QUEUED,
    val progressPercent: Int = 0,
    val bytesDownloaded: Long = 0L,
    val addedAtMs: Long = System.currentTimeMillis()
) {
    fun toPlayablePair(): Pair<MovieItem, Episode> {
        val ep = Episode(
            id = episodeId,
            name = episodeName,
            streamUrl = streamUrl,
            headers = headers
        )
        val movie = MovieItem(
            id = movieId,
            title = movieTitle,
            year = year,
            country = CountryCategory.GLOBAL,
            posterUrl = posterUrl,
            backdropUrl = posterUrl,
            contentType = ContentType.MOVIE,
            providerId = "offline_download",
            providerName = "Đã tải Offline",
            servers = listOf(ServerGroup("Offline", listOf(ep))),
            detailLookupId = movieSlug
        )
        return movie to ep
    }
}

@OptIn(UnstableApi::class)
object OfflineDownloadManager {

    const val NOTIFICATION_CHANNEL_ID = "chillfilm_offline_downloads"

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initLock = Any()
    private val indexLock = Any()

    @Volatile
    private var simpleCache: SimpleCache? = null
    @Volatile
    private var downloadManager: DownloadManager? = null
    @Volatile
    private var notificationHelper: DownloadNotificationHelper? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var pollingStarted = false

    private val _downloads = MutableStateFlow<List<OfflineEpisodeRecord>>(emptyList())
    val downloads: StateFlow<List<OfflineEpisodeRecord>> = _downloads.asStateFlow()

    fun buildDownloadId(movie: MovieItem, episode: Episode): String {
        val slug = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
        val epKey = CloudSyncClient.normalizeEpisodeKey(episode.name.ifBlank { episode.id })
        return "${slug}__${epKey}"
    }

    fun ensureInitialized(context: Context) {
        val app = context.applicationContext
        if (downloadManager != null) return
        synchronized(initLock) {
            if (downloadManager != null) return
            appContext = app

            val dbProvider = StandaloneDatabaseProvider(app)
            val cacheDir = File(app.filesDir, "chillfilm_offline_media").apply { mkdirs() }
            val cache = SimpleCache(cacheDir, NoOpCacheEvictor(), dbProvider)
            simpleCache = cache

            val okHttp = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(HlsPlaylistSanitizer.createOkHttpInterceptor())
                .build()
            val httpFactory = OkHttpDataSource.Factory(okHttp)
                .setUserAgent("Mozilla/5.0 (Linux; Android 13; ChillFilm) AppleWebKit/537.36")

            val dm = DownloadManager(
                app,
                dbProvider,
                cache,
                httpFactory,
                Executors.newFixedThreadPool(4)
            ).apply {
                maxParallelDownloads = 2
                minRetryCount = 3
            }

            dm.addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    syncRecordsFromDownloadManager(app)
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    syncRecordsFromDownloadManager(app)
                }
            })

            downloadManager = dm
            notificationHelper = DownloadNotificationHelper(app, NOTIFICATION_CHANNEL_ID)
            loadIndexFromDisk(app)
            syncRecordsFromDownloadManager(app)
            startProgressPoller(app)
        }
    }

    fun getDownloadManager(context: Context): DownloadManager {
        ensureInitialized(context)
        return downloadManager!!
    }

    fun getNotificationHelper(context: Context): DownloadNotificationHelper {
        ensureInitialized(context)
        return notificationHelper!!
    }

    fun buildOfflineFirstDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        ensureInitialized(context)
        val cache = simpleCache ?: return upstreamFactory
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null) // Read-only cache hit for offline playback!
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun findDownload(movie: MovieItem, episode: Episode): OfflineEpisodeRecord? {
        val id = buildDownloadId(movie, episode)
        return _downloads.value.firstOrNull { it.id == id }
    }

    fun enqueueEpisodeDownload(
        context: Context,
        movie: MovieItem,
        episode: Episode,
        subtitleLangCode: String = "vi",
        subtitleManager: ThaiSubtitleManager? = null
    ): OfflineEpisodeRecord {
        ensureInitialized(context)
        val app = context.applicationContext
        val id = buildDownloadId(movie, episode)
        val slug = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
        val existing = _downloads.value.firstOrNull { it.id == id }
        val record = OfflineEpisodeRecord(
            id = id,
            movieId = movie.id,
            movieSlug = slug,
            movieTitle = movie.title,
            posterUrl = movie.posterUrl,
            year = movie.year,
            episodeId = episode.id,
            episodeName = episode.name.ifBlank { "Full" },
            streamUrl = episode.streamUrl,
            headers = episode.headers,
            subtitleLangCode = subtitleLangCode.trim().lowercase().ifBlank { "vi" },
            subtitleCueCount = existing?.subtitleCueCount ?: 0,
            status = OfflineDownloadStatus.QUEUED,
            progressPercent = existing?.progressPercent ?: 0,
            bytesDownloaded = existing?.bytesDownloaded ?: 0L,
            addedAtMs = existing?.addedAtMs ?: System.currentTimeMillis()
        )
        upsertIndexRecord(app, record)

        val uri = Uri.parse(episode.streamUrl)
        val reqBuilder = DownloadRequest.Builder(id, uri)
        if (episode.streamUrl.contains(".m3u8", ignoreCase = true)) {
            reqBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        val req = reqBuilder.build()
        runCatching {
            DownloadService.sendAddDownload(
                app,
                OfflineDownloadService::class.java,
                req,
                /* foreground = */ false
            )
        }.onFailure {
            // Fallback direct add if foreground service restricted in background
            downloadManager?.addDownload(req)
        }

        // Also archive AI Subtitles alongside this episode for 100% offline subtitle playback!
        scope.launch {
            archiveEpisodeSubtitleForOffline(app, id, movie, episode, record.subtitleLangCode, subtitleManager)
        }
        return record
    }

    fun pauseDownload(context: Context, id: String) {
        ensureInitialized(context)
        val app = context.applicationContext
        runCatching {
            DownloadService.sendSetStopReason(app, OfflineDownloadService::class.java, id, 1, false)
        }.onFailure {
            downloadManager?.setStopReason(id, 1)
        }
    }

    fun resumeDownload(context: Context, id: String) {
        ensureInitialized(context)
        val app = context.applicationContext
        runCatching {
            DownloadService.sendSetStopReason(app, OfflineDownloadService::class.java, id, Download.STOP_REASON_NONE, false)
        }.onFailure {
            downloadManager?.setStopReason(id, Download.STOP_REASON_NONE)
        }
    }

    fun removeDownload(context: Context, id: String) {
        ensureInitialized(context)
        val app = context.applicationContext
        runCatching {
            DownloadService.sendRemoveDownload(app, OfflineDownloadService::class.java, id, false)
        }.onFailure {
            downloadManager?.removeDownload(id)
        }
        synchronized(indexLock) {
            val next = _downloads.value.filterNot { it.id == id }
            _downloads.value = next
            persistIndexToDisk(app, next)
        }
        runCatching { offlineSubFile(app, id).delete() }
    }

    fun totalDownloadedBytes(): Long = _downloads.value.sumOf { it.bytesDownloaded.coerceAtLeast(0L) }

    suspend fun archiveEpisodeSubtitleForOffline(
        context: Context,
        downloadId: String,
        movie: MovieItem,
        episode: Episode,
        langCode: String,
        subtitleManager: ThaiSubtitleManager? = null
    ): Int {
        val app = context.applicationContext
        val mgr = subtitleManager ?: ThaiSubtitleManager(app.cacheDir, File(app.filesDir, "chillfilm_saved_subs_v1"))
        var cues: List<SubtitleCue> = mgr.loadEpisodeSubCache(movie, episode, langCode)?.primaryCues.orEmpty()
        if (cues.size < 3 && CloudSyncClient.isSignedIn()) {
            val cloud = CloudSyncClient.fetchCloudSubtitle(
                rawMovieSlug = movie.detailLookupId ?: movie.id,
                rawEpisodeNameOrId = episode.name.ifBlank { episode.id },
                langCode = langCode
            )
            if (cloud != null && cloud.primaryCues.size >= 3) {
                cues = cloud.primaryCues
            }
        }
        if (cues.size < 3) return 0
        saveOfflineSubtitleCues(app, downloadId, langCode, cues)
        synchronized(indexLock) {
            val updated = _downloads.value.map { rec ->
                if (rec.id == downloadId) rec.copy(subtitleCueCount = cues.size, subtitleLangCode = langCode) else rec
            }
            _downloads.value = updated
            persistIndexToDisk(app, updated)
        }
        return cues.size
    }

    fun saveOfflineSubtitleCues(context: Context, downloadId: String, langCode: String, cues: List<SubtitleCue>) {
        if (cues.isEmpty()) return
        runCatching {
            val file = offlineSubFile(context.applicationContext, downloadId)
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            for (c in cues) {
                arr.put(JSONArray().put(c.id).put(c.startMs).put(c.endMs).put(c.text))
            }
            val root = JSONObject()
                .put("lang", langCode)
                .put("cues", arr)
            file.writeText(root.toString(), Charsets.UTF_8)
        }
    }

    fun loadOfflineSubtitleCues(context: Context, movie: MovieItem, episode: Episode): Pair<String, List<SubtitleCue>>? {
        val id = buildDownloadId(movie, episode)
        return loadOfflineSubtitleCuesById(context, id)
    }

    fun loadOfflineSubtitleCuesById(context: Context, downloadId: String): Pair<String, List<SubtitleCue>>? {
        val file = offlineSubFile(context.applicationContext, downloadId)
        if (!file.exists() || file.length() < 10L) return null
        return runCatching {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val lang = obj.optString("lang", "vi")
            val arr = obj.optJSONArray("cues") ?: return@runCatching null
            val out = ArrayList<SubtitleCue>(arr.length())
            for (i in 0 until arr.length()) {
                val r = arr.optJSONArray(i) ?: continue
                if (r.length() < 4) continue
                out.add(SubtitleCue(r.optInt(0, i + 1), r.optLong(1), r.optLong(2), r.optString(3)))
            }
            if (out.isEmpty()) null else (lang to out)
        }.getOrNull()
    }

    fun formatCuesAsSrt(cues: List<SubtitleCue>): String {
        if (cues.isEmpty()) return ""
        fun fmtMs(ms: Long): String {
            val safe = ms.coerceAtLeast(0L)
            val h = safe / 3_600_000L
            val m = (safe % 3_600_000L) / 60_000L
            val s = (safe % 60_000L) / 1_000L
            val rem = safe % 1_000L
            return "%02d:%02d:%02d,%03d".format(h, m, s, rem)
        }
        return cues.filter { it.text.isNotBlank() }.mapIndexed { idx, c ->
            val end = c.endMs.coerceAtLeast(c.startMs + 450L)
            "${idx + 1}\r\n${fmtMs(c.startMs)} --> ${fmtMs(end)}\r\n${c.text.trim()}"
        }.joinToString("\r\n\r\n") + "\r\n"
    }

    /**
     * Exports a translated subtitle list as a standard UTF-8 `.srt` file directly into the
     * device's public `Downloads/ChillFilm/` folder so external players (VLC / MX Player) can open it too!
     */
    fun exportSrtToPublicDownloads(
        context: Context,
        fileNameBase: String,
        cues: List<SubtitleCue>
    ): Result<String> {
        val srtContent = formatCuesAsSrt(cues)
        if (srtContent.isBlank()) return Result.failure(IllegalStateException("Chưa có phụ đề để xuất"))
        val safeName = fileNameBase.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "chillfilm-sub" }
        val finalName = if (safeName.endsWith(".srt", ignoreCase = true)) safeName else "$safeName.srt"
        val bytes = ("\uFEFF" + srtContent).toByteArray(Charsets.UTF_8)

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ChillFilm")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Không tạo được file trong Downloads")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Không ghi được file .SRT")
                "Downloads/ChillFilm/$finalName"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ChillFilm"
                ).apply { mkdirs() }
                val outFile = File(dir, finalName)
                outFile.writeBytes(bytes)
                outFile.absolutePath
            }
        }
    }

    private fun startProgressPoller(app: Context) {
        if (pollingStarted) return
        pollingStarted = true
        scope.launch {
            while (isActive) {
                val hasActive = _downloads.value.any {
                    it.status == OfflineDownloadStatus.QUEUED || it.status == OfflineDownloadStatus.DOWNLOADING
                }
                if (hasActive) {
                    syncRecordsFromDownloadManager(app)
                    delay(800L)
                } else {
                    delay(2500L)
                }
            }
        }
    }

    private fun syncRecordsFromDownloadManager(app: Context) {
        val dm = downloadManager ?: return
        val liveById = HashMap<String, Download>()
        runCatching {
            dm.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val d = cursor.download
                    liveById[d.request.id] = d
                }
            }
        }
        synchronized(indexLock) {
            var changed = false
            val updated = _downloads.value.map { rec ->
                val d = liveById[rec.id] ?: return@map rec
                val st = when (d.state) {
                    Download.STATE_COMPLETED -> OfflineDownloadStatus.COMPLETED
                    Download.STATE_DOWNLOADING -> OfflineDownloadStatus.DOWNLOADING
                    Download.STATE_QUEUED, Download.STATE_RESTARTING -> OfflineDownloadStatus.QUEUED
                    Download.STATE_STOPPED -> OfflineDownloadStatus.PAUSED
                    Download.STATE_FAILED -> OfflineDownloadStatus.FAILED
                    else -> rec.status
                }
                val pct = if (st == OfflineDownloadStatus.COMPLETED) {
                    100
                } else {
                    d.percentDownloaded.takeIf { it >= 0f }?.toInt()?.coerceIn(0, 99) ?: rec.progressPercent
                }
                val bytes = d.bytesDownloaded.coerceAtLeast(rec.bytesDownloaded)
                if (st != rec.status || pct != rec.progressPercent || bytes != rec.bytesDownloaded) {
                    changed = true
                    rec.copy(status = st, progressPercent = pct, bytesDownloaded = bytes)
                } else {
                    rec
                }
            }
            if (changed) {
                _downloads.value = updated
                persistIndexToDisk(app, updated)
            }
        }
    }

    private fun indexFile(app: Context) = File(app.filesDir, "chillfilm_offline_index_v1.json")
    private fun offlineSubFile(app: Context, id: String) = File(File(app.filesDir, "chillfilm_offline_subs"), "$id.json")

    private fun loadIndexFromDisk(app: Context) {
        synchronized(indexLock) {
            val f = indexFile(app)
            if (!f.exists()) return
            runCatching {
                val type = object : TypeToken<List<OfflineEpisodeRecord>>() {}.type
                val list: List<OfflineEpisodeRecord> = gson.fromJson(f.readText(Charsets.UTF_8), type) ?: emptyList()
                _downloads.value = list.sortedByDescending { it.addedAtMs }
            }
        }
    }

    private fun upsertIndexRecord(app: Context, record: OfflineEpisodeRecord) {
        synchronized(indexLock) {
            val next = listOf(record) + _downloads.value.filterNot { it.id == record.id }
            _downloads.value = next
            persistIndexToDisk(app, next)
        }
    }

    private fun persistIndexToDisk(app: Context, list: List<OfflineEpisodeRecord>) {
        runCatching {
            val f = indexFile(app)
            val tmp = File(app.filesDir, "${f.name}.tmp")
            tmp.writeText(gson.toJson(list), Charsets.UTF_8)
            if (f.exists()) f.delete()
            if (!tmp.renameTo(f)) {
                f.writeText(gson.toJson(list), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }
}
