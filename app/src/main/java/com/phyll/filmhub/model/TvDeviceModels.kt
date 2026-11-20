package com.phyll.filmhub.model

/**
 * Operating mode of the standalone Android app:
 * - [LOCAL_DEVICE]: Watch movies, translate subtitles, and use all features directly on this Android device.
 * - [REMOTE_TV]: Control the selected TV device on the LAN (play movies, adjust subtitles, stream screen, sync API key).
 */
enum class ControlTargetMode(val displayName: String, val shortBadge: String) {
    LOCAL_DEVICE(
        displayName = "📱 Dùng trực tiếp trên máy Android này",
        shortBadge = "📱 Xem trên ĐT"
    ),
    REMOTE_TV(
        displayName = "📺 Điều khiển TV đang kết nối",
        shortBadge = "📺 Điều khiển TV"
    )
}

/**
 * Represents a Smart TV / Android TV Box managed by the Android app.
 */
data class ManagedTvDevice(
    val ip: String,
    val name: String,
    val model: String = "",
    val adbPort: Int = 5555,
    val companionPort: Int = 8899,
    val adbOnline: Boolean = false,
    val adbNeedsAuth: Boolean = false,
    val companionOnline: Boolean = false,
    val appInstalled: Boolean = false,
    val appVersion: String = "",
    val activeScreen: String = "",
    val nowPlayingTitle: String = "",
    val hasGeminiKeyOnTv: Boolean = false,
    val lastSeenMs: Long = 0L
) {
    val displayTitle: String
        get() = when {
            name.isNotBlank() && model.isNotBlank() && !name.contains(model, ignoreCase = true) -> "$name ($model)"
            name.isNotBlank() -> name
            model.isNotBlank() -> "$model ($ip)"
            else -> "Android TV ($ip)"
        }
}

/**
 * Live progress state for ADB operations (Install APK, Update APK, Uninstall App, Launch App, Scan LAN).
 */
data class AdbTaskProgress(
    val isRunning: Boolean = false,
    val targetIp: String = "",
    val operationName: String = "",
    val progressPercent: Int = 0,
    val statusMessage: String = "",
    val isError: Boolean = false
)

/**
 * Real-time telemetry snapshot polled from the currently selected TV (`GET http://<ip>:8899/api/state`).
 */
data class RemoteTvLiveState(
    val tvIp: String = "",
    val online: Boolean = false,
    val screen: String = "HOME",
    val selectedCountry: String = "all",
    val deviceUiMode: String = "TV",
    val hasGeminiKey: Boolean = false,
    val geminiModel: String = "gemini-2.5-flash",
    val banner: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playerActive: Boolean = false,
    val movieId: String = "",
    val movieTitle: String = "",
    val episodeId: String = "",
    val episodeName: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val syncOffsetMs: Long = 0L,
    val subSpeedScale: Float = 1.0f,
    val primaryThaiText: String = "",
    val secondaryText: String = "",
    val statusBadge: String = "",
    val subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig()
)
