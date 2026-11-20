package com.phyll.filmhub.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Int
)

data class RemoteReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String = "",
    val buildTime: String = "",
    val assetSizeBytes: Long = 0L
)

data class AppUpdateUiState(
    val currentVersion: AppVersionInfo = AppVersionInfo("3.0.1", 101),
    val latestRelease: RemoteReleaseInfo? = null,
    val hasUpdate: Boolean = false,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val needsInstallPermission: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Automated In-App Updater backed by GitHub Releases (`PrimiasArt/Chillfilm.net`).
 * Combined with the persistent signing keystore (`chillfilm.keystore`), updates install cleanly
 * over the existing app without wiping saved API keys, TV devices, history, or subtitle preferences.
 */
class AppUpdater {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getInstalledVersion(context: Context): AppVersionInfo {
        return runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(pInfo).toInt()
            val name = pInfo.versionName ?: "3.0.1"
            AppVersionInfo(versionName = name, versionCode = code)
        }.getOrDefault(AppVersionInfo("3.0.1", 101))
    }

    suspend fun checkForUpdates(): Result<RemoteReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Prefer GitHub Releases API (android-apk-latest, then /releases/latest) so asset URL & size are exact
            val releaseUrls = listOf(
                "https://api.github.com/repos/$GITHUB_REPO/releases/tags/android-apk-latest",
                "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            )
            for (apiUrl in releaseUrls) {
                val jsonText = fetchText(apiUrl, acceptJson = true) ?: continue
                val parsed = parseGitHubReleaseJson(jsonText)
                if (parsed != null) return@runCatching parsed
            }

            // 2. Fallback: raw version.json in repository main branch
            val rawUrl = "https://raw.githubusercontent.com/$GITHUB_REPO/main/version.json?t=${System.currentTimeMillis()}"
            val rawVersionJson = fetchText(rawUrl, acceptJson = true)
            if (rawVersionJson != null) {
                val parsed = parseVersionJson(rawVersionJson)
                if (parsed != null) return@runCatching parsed
            }

            throw IllegalStateException("Chưa tìm thấy bản phát hành mới trên GitHub Releases ($GITHUB_REPO).")
        }
    }

    suspend fun downloadApkToCache(
        context: Context,
        downloadUrl: String,
        onProgress: (receivedBytes: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(updateDir, "ChillFilm-latest.apk")
            if (outFile.exists()) outFile.delete()

            val req = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "ChillFilm-Updater/3.0")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Tải APK thất bại (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw IllegalStateException("Phản hồi tải APK rỗng")
                val totalLen = body.contentLength()
                var received = 0L
                val buf = ByteArray(32 * 1024)

                body.byteStream().use { inp ->
                    FileOutputStream(outFile).use { fos ->
                        var lastPct = -1
                        while (true) {
                            val r = inp.read(buf)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            received += r
                            val pct = if (totalLen > 0) {
                                ((received * 100L) / totalLen).toInt().coerceIn(0, 100)
                            } else {
                                ((received * 100L) / (15L * 1024L * 1024L)).toInt().coerceIn(0, 99)
                            }
                            if (pct != lastPct || r == 0) {
                                lastPct = pct
                                onProgress(received, totalLen, pct)
                            }
                        }
                        fos.flush()
                    }
                }
                if (outFile.length() < 100_000L) {
                    outFile.delete()
                    throw IllegalStateException("File APK tải về bị lỗi hoặc không đầy đủ.")
                }
                onProgress(outFile.length(), outFile.length(), 100)
            }
            outFile
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun launchUnknownSourcesPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetchText(url: String, acceptJson: Boolean): String? {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "ChillFilm-Updater/3.0")
        if (acceptJson) {
            reqBuilder.header("Accept", "application/vnd.github.v3+json")
        }
        return runCatching {
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    companion object {
        const val GITHUB_REPO = "PrimiasArt/Chillfilm.net"
        const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_REPO/releases"
        const val FALLBACK_APK_URL = "https://github.com/$GITHUB_REPO/releases/download/android-apk-latest/ChillFilm-latest.apk"

        fun isUpdateAvailable(current: AppVersionInfo, remote: RemoteReleaseInfo): Boolean {
            if (remote.versionCode > 0 && current.versionCode > 0) {
                return remote.versionCode > current.versionCode
            }
            return compareSemver(remote.versionName, current.versionName) > 0
        }

        fun compareSemver(a: String, b: String): Int {
            val cleanA = a.trim().removePrefix("v").split('.', '-', '+').mapNotNull { it.toIntOrNull() }
            val cleanB = b.trim().removePrefix("v").split('.', '-', '+').mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(cleanA.size, cleanB.size)
            for (i in 0 until maxLen) {
                val pA = cleanA.getOrElse(i) { 0 }
                val pB = cleanB.getOrElse(i) { 0 }
                if (pA != pB) return pA.compareTo(pB)
            }
            return 0
        }

        fun parseVersionJson(rawJson: String): RemoteReleaseInfo? {
            val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
            val vCode = obj.optInt("versionCode", 0)
            val vName = obj.optString("versionName").trim()
            if (vName.isBlank() && vCode <= 0) return null
            return RemoteReleaseInfo(
                versionName = vName.ifBlank { "3.0" },
                versionCode = vCode,
                downloadUrl = obj.optString("downloadUrl").trim().ifBlank { FALLBACK_APK_URL },
                releaseNotes = obj.optString("releaseNotes").trim(),
                buildTime = obj.optString("buildTime").trim()
            )
        }

        fun parseGitHubReleaseJson(rawJson: String): RemoteReleaseInfo? {
            val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
            val tagName = obj.optString("tag_name").trim()
            val name = obj.optString("name").trim()
            val body = obj.optString("body").trim()
            val assets = obj.optJSONArray("assets")

            var chosenUrl = ""
            var chosenSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val aName = a.optString("name")
                    if (aName.endsWith(".apk", ignoreCase = true)) {
                        if (aName.contains("latest", ignoreCase = true) || aName.contains("universal", ignoreCase = true)) {
                            chosenUrl = a.optString("browser_download_url")
                            chosenSize = a.optLong("size", 0L)
                            break
                        } else if (chosenUrl.isEmpty()) {
                            chosenUrl = a.optString("browser_download_url")
                            chosenSize = a.optLong("size", 0L)
                        }
                    }
                }
            }
            if (chosenUrl.isBlank()) {
                chosenUrl = FALLBACK_APK_URL
            }

            val codeRegex = Regex("versionCode\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val parsedCode = codeRegex.find("$name\n$body")?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("Build\\s*(\\d+)", RegexOption.IGNORE_CASE).find("$name\n$body")?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 0

            val verRegex = Regex("v?(\\d+\\.\\d+(?:\\.\\d+)?)")
            val parsedName = verRegex.find(tagName)?.groupValues?.getOrNull(1)
                ?: verRegex.find(name)?.groupValues?.getOrNull(1)
                ?: tagName.removePrefix("v").ifBlank { name }

            return RemoteReleaseInfo(
                versionName = parsedName.ifBlank { "3.0" },
                versionCode = parsedCode,
                downloadUrl = chosenUrl,
                releaseNotes = body.ifBlank { "Bản phát hành ChillFilm mới trên GitHub" },
                buildTime = obj.optString("published_at").trim(),
                assetSizeBytes = chosenSize
            )
        }
    }
}
