package com.phyll.filmhub.model

data class SubtitleCue(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class SubtitleFontChoice(val displayName: String) {
    SARABUN("Sarabun (Chuẩn Cinema Thái)"),
    KANIT("Kanit (Hiện đại)"),
    PROMPT("Prompt (Thanh thoát)"),
    SYSTEM("Mặc định hệ thống")
}

enum class SubtitleColorChoice(val argb: Long, val displayName: String, val hexCss: String) {
    CINEMA_YELLOW(0xFFFFD600, "Vàng Rạp Phim", "#FFD600"),
    WHITE(0xFFFFFFFF, "Trắng Tinh", "#FFFFFF"),
    CYAN(0xFF00E5FF, "Xanh Cyan", "#00E5FF"),
    MINT_GREEN(0xFF69F0AE, "Xanh Ngọc", "#69F0AE"),
    SAKURA_PINK(0xFFFF80AB, "Hồng Đào", "#FF80AB"),
    AMBER_GOLD(0xFFFFAB40, "Cam Hổ Phách", "#FFAB40")
}

enum class SubtitleEdgeStyle(val displayName: String) {
    BACKGROUND_BOX("Khung mờ gọn theo chữ"),
    OUTLINE("Viền đen sắc nét"),
    DROP_SHADOW("Đổ bóng rạp phim"),
    NONE("Trong suốt")
}

enum class SecondarySubLang(val langCode: String, val displayName: String) {
    VIETNAMESE("vi", "Tiếng Việt"),
    ENGLISH("en", "Tiếng Anh"),
    ORIGINAL("orig", "Ngôn ngữ gốc"),
    OFF("off", "Tắt dòng phụ")
}

enum class TranslationEngineMode(val displayName: String) {
    FAST_FREE("Neural AI Multi-Batch (Miễn phí • Tức thì)"),
    GEMINI_AI("Google Gemini AI Cinema (Dịch & Nghe Audio tạo Sub)")
}

data class SubtitleStyleConfig(
    val preferredPrimaryLang: String = "th", // Mặc định Sub Thái (ไทย)
    val subtitlesEnabled: Boolean = true,
    val autoTranslateToThai: Boolean = true,
    val translationEngine: TranslationEngineMode = TranslationEngineMode.FAST_FREE,
    val geminiModelId: String = "gemini-3-flash-preview",
    val autoGenerateAudioSubWhenMissing: Boolean = true,
    val dualSubEnabled: Boolean = false,
    val secondaryLang: SecondarySubLang = SecondarySubLang.VIETNAMESE,
    val fontChoice: SubtitleFontChoice = SubtitleFontChoice.SARABUN,
    val fontSizeSp: Float = 20f,
    val secondaryFontSizeSp: Float = 16f,
    val boldText: Boolean = true,
    val primaryColor: SubtitleColorChoice = SubtitleColorChoice.CINEMA_YELLOW,
    val secondaryColor: SubtitleColorChoice = SubtitleColorChoice.WHITE,
    val linkPrimarySecondaryStyle: Boolean = false,
    // Khung mờ gọn ôm sát câu thoại (không hiện khối đen to khi nhân vật không nói)
    val edgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.BACKGROUND_BOX,
    val boxOpacity: Float = 0.78f,
    val bottomOffsetDp: Float = 18f,
    val hardsubMaskEnabled: Boolean = false,
    val hardsubMaskHeightDp: Float = 52f,
    val hardsubMaskOpacity: Float = 0.88f,
    val syncOffsetMs: Long = 0L,
    val subSpeedScale: Float = 1.0f,
    val autoSyncDownloadedSub: Boolean = true
)

data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val languageCode: String,
    val isEmbedded: Boolean,
    val isThai: Boolean,
    val externalRef: ExternalSubtitleRef? = null,
    val exoGroupIndex: Int = -1,
    val exoTrackIndex: Int = -1
)

data class PreFlightSubtitleState(
    val isPreparing: Boolean = false,
    val stepTitle: String = "",
    val stepDetail: String = "",
    val progressFraction: Float = 0f,
    val loadedCueCount: Int = 0,
    val firstCueTimeMs: Long = -1L,
    val noSubtitleFound: Boolean = false,
    val needsGeminiKeyQr: Boolean = false,
    val searchedQuery: String = ""
)

data class ActiveSubtitleRenderState(
    val primaryThaiText: String? = null,
    val secondaryText: String? = null,
    val statusBadge: String = "Sub: ไทย (Đang chuẩn bị...)",
    val isTranslating: Boolean = false,
    val totalCuesLoaded: Int = 0,
    val nextCueStartMs: Long = -1L
)

/**
 * Per-Series / Per-Movie persistent subtitle sync & mode profile.
 * Ensures that once Episode 1 of a 100-episode series is synced (or switched to Gemini Audio mode),
 * Episodes 2..100 inherit the exact sync profile automatically with zero manual hunting!
 * (Contains ONLY timing/mode metadata — NEVER any API key.)
 */
data class SeriesSubtitleProfile(
    val seriesId: String,
    val syncOffsetMs: Long = 0L,
    val subSpeedScale: Float = 1.0f,
    val preferGeminiAudioSub: Boolean = false,
    val preferredPlatform: String = "",
    val updatedAtMs: Long = 0L
)

/**
 * Bilingual dialogue cue candidate for the 1-Tap Auto-Pause Visual Snapper on TV & Phone.
 * Displays BOTH the Thai line and the Vietnamese / English original line so any viewer
 * can identify the exact sentence the actor just spoke in 2 seconds.
 */
data class SnapCueCandidate(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val thaiText: String,
    val referenceText: String,
    val isNearestToAnchor: Boolean = false
)
