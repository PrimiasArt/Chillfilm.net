package com.phyll.filmhub.model

data class SubtitleCue(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class SubtitleFontChoice(val displayName: String) {
    SARABUN("Sarabun (Rõ nét đa ngôn ngữ)"),
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

/**
 * Universal AI Subtitle Language definition supporting both rich curated presets
 * and ANY custom language code or name supported by Gemini AI & Neural translation.
 */
data class SubtitleLanguageOption(
    val code: String,
    val shortBadge: String,
    val displayName: String,
    val nativeLabel: String,
    val openSubtitlesId: String,
    val geminiInstructionName: String,
    val samplePreview: String
)

object SubtitleLanguageCatalog {
    val PRESETS: List<SubtitleLanguageOption> = listOf(
        SubtitleLanguageOption(
            code = "vi",
            shortBadge = "VI",
            displayName = "Tiếng Việt",
            nativeLabel = "Tiếng Việt",
            openSubtitlesId = "vie",
            geminiInstructionName = "natural cinema-quality Vietnamese movie subtitles (Tiếng Việt tự nhiên, mượt mà, chuẩn phim chiếu rạp)",
            samplePreview = "Chào mừng đến với ChillFilm • Hệ sinh thái NKI"
        ),
        SubtitleLanguageOption(
            code = "en",
            shortBadge = "EN",
            displayName = "Tiếng Anh (English)",
            nativeLabel = "English",
            openSubtitlesId = "eng",
            geminiInstructionName = "natural, idiomatic cinema-quality English movie subtitles",
            samplePreview = "Welcome to ChillFilm Cinema • Powered by NKI AI"
        ),
        SubtitleLanguageOption(
            code = "th",
            shortBadge = "TH",
            displayName = "Tiếng Thái (ไทย)",
            nativeLabel = "ภาษาไทย",
            openSubtitlesId = "tha",
            geminiInstructionName = "natural cinema-quality Thai movie dialogue (ภาษาไทยธรรมชาติแบบซับหนังโรง กระชับ อ่านง่าย)",
            samplePreview = "ยินดีต้อนรับสู่ ChillFilm Cinema • โดย NKI"
        ),
        SubtitleLanguageOption(
            code = "zh",
            shortBadge = "ZH",
            displayName = "Tiếng Trung (Giản thể)",
            nativeLabel = "简体中文",
            openSubtitlesId = "chi",
            geminiInstructionName = "natural cinema-quality Simplified Chinese movie subtitles (自然流畅的电影院级简体中文字幕)",
            samplePreview = "欢迎来到 ChillFilm 影院 • NKI 生态"
        ),
        SubtitleLanguageOption(
            code = "zh-TW",
            shortBadge = "TW",
            displayName = "Tiếng Trung (Phồn thể)",
            nativeLabel = "繁體中文",
            openSubtitlesId = "zht",
            geminiInstructionName = "natural cinema-quality Traditional Chinese movie subtitles (自然流暢的影院級繁體中文字幕)",
            samplePreview = "歡迎來到 ChillFilm 影院 • NKI 生態"
        ),
        SubtitleLanguageOption(
            code = "ko",
            shortBadge = "KO",
            displayName = "Tiếng Hàn (한국어)",
            nativeLabel = "한국어",
            openSubtitlesId = "kor",
            geminiInstructionName = "natural cinema-quality Korean movie subtitles (자연스러운 영화 자막 한국어)",
            samplePreview = "ChillFilm 시네마에 오신 것을 환영합니다"
        ),
        SubtitleLanguageOption(
            code = "ja",
            shortBadge = "JA",
            displayName = "Tiếng Nhật (日本語)",
            nativeLabel = "日本語",
            openSubtitlesId = "jpn",
            geminiInstructionName = "natural cinema-quality Japanese movie subtitles (自然な映画字幕の日本語)",
            samplePreview = "ChillFilmシネマへようこそ • NKI AI"
        ),
        SubtitleLanguageOption(
            code = "fr",
            shortBadge = "FR",
            displayName = "Tiếng Pháp (Français)",
            nativeLabel = "Français",
            openSubtitlesId = "fre",
            geminiInstructionName = "natural cinema-quality French movie subtitles (sous-titres de cinéma en français naturel)",
            samplePreview = "Bienvenue sur ChillFilm Cinema • Écosystème NKI"
        ),
        SubtitleLanguageOption(
            code = "es",
            shortBadge = "ES",
            displayName = "Tiếng Tây Ban Nha",
            nativeLabel = "Español",
            openSubtitlesId = "spa",
            geminiInstructionName = "natural cinema-quality Spanish movie subtitles (subtítulos naturales de cine en español)",
            samplePreview = "Bienvenido a ChillFilm Cinema • Ecosistema NKI"
        ),
        SubtitleLanguageOption(
            code = "de",
            shortBadge = "DE",
            displayName = "Tiếng Đức (Deutsch)",
            nativeLabel = "Deutsch",
            openSubtitlesId = "ger",
            geminiInstructionName = "natural cinema-quality German movie subtitles (natürliche Kinountertitel auf Deutsch)",
            samplePreview = "Willkommen bei ChillFilm Cinema • NKI Ökosystem"
        ),
        SubtitleLanguageOption(
            code = "id",
            shortBadge = "ID",
            displayName = "Tiếng Indonesia",
            nativeLabel = "Bahasa Indonesia",
            openSubtitlesId = "ind",
            geminiInstructionName = "natural cinema-quality Indonesian movie subtitles (subtitle film Bahasa Indonesia yang alami)",
            samplePreview = "Selamat datang di ChillFilm Cinema • NKI"
        ),
        SubtitleLanguageOption(
            code = "ru",
            shortBadge = "RU",
            displayName = "Tiếng Nga (Русский)",
            nativeLabel = "Русский",
            openSubtitlesId = "rus",
            geminiInstructionName = "natural cinema-quality Russian movie subtitles (естественные кинематографические субтитры на русском)",
            samplePreview = "Добро пожаловать в ChillFilm • Экосистема NKI"
        ),
        SubtitleLanguageOption(
            code = "pt",
            shortBadge = "PT",
            displayName = "Tiếng Bồ Đào Nha",
            nativeLabel = "Português",
            openSubtitlesId = "por",
            geminiInstructionName = "natural cinema-quality Portuguese movie subtitles (legendas naturais de cinema em português)",
            samplePreview = "Bem-vindo ao ChillFilm Cinema • Ecossistema NKI"
        ),
        SubtitleLanguageOption(
            code = "it",
            shortBadge = "IT",
            displayName = "Tiếng Ý (Italiano)",
            nativeLabel = "Italiano",
            openSubtitlesId = "ita",
            geminiInstructionName = "natural cinema-quality Italian movie subtitles (sottotitoli cinematografici naturali in italiano)",
            samplePreview = "Benvenuti su ChillFilm Cinema • NKI"
        ),
        SubtitleLanguageOption(
            code = "ar",
            shortBadge = "AR",
            displayName = "Tiếng Ả Rập (العربية)",
            nativeLabel = "العربية",
            openSubtitlesId = "ara",
            geminiInstructionName = "natural cinema-quality Modern Standard Arabic movie subtitles",
            samplePreview = "مرحباً بكم في سينما ChillFilm • NKI"
        ),
        SubtitleLanguageOption(
            code = "hi",
            shortBadge = "HI",
            displayName = "Tiếng Hindi (हिन्दी)",
            nativeLabel = "हिन्दी",
            openSubtitlesId = "hin",
            geminiInstructionName = "natural cinema-quality Hindi movie subtitles",
            samplePreview = "ChillFilm सिनेमा में आपका स्वागत है • NKI"
        ),
        SubtitleLanguageOption(
            code = "ms",
            shortBadge = "MS",
            displayName = "Tiếng Mã Lai (Melayu)",
            nativeLabel = "Bahasa Melayu",
            openSubtitlesId = "may",
            geminiInstructionName = "natural cinema-quality Malay movie subtitles",
            samplePreview = "Selamat datang ke ChillFilm Cinema • NKI"
        ),
        SubtitleLanguageOption(
            code = "lo",
            shortBadge = "LO",
            displayName = "Tiếng Lào (ລາວ)",
            nativeLabel = "ພາສາລາວ",
            openSubtitlesId = "lao",
            geminiInstructionName = "natural cinema-quality Lao movie subtitles",
            samplePreview = "ຍິນດີຕ້ອນຮັບສູ່ ChillFilm • NKI"
        ),
        SubtitleLanguageOption(
            code = "km",
            shortBadge = "KM",
            displayName = "Tiếng Khmer (ភាសាខ្មែរ)",
            nativeLabel = "ភាសាខ្មែរ",
            openSubtitlesId = "khm",
            geminiInstructionName = "natural cinema-quality Khmer movie subtitles",
            samplePreview = "សូមស្វាគមន៍មកកាន់ ChillFilm • NKI"
        )
    )

    fun normalizeCode(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return "vi"
        val lower = clean.lowercase()
        return when (lower) {
            "vie", "vietnamese", "tiếng việt", "viet" -> "vi"
            "eng", "english", "tiếng anh" -> "en"
            "tha", "thai", "tiếng thái", "ไทย" -> "th"
            "chi", "zho", "zh-cn", "zh-hans", "chinese", "tiếng trung" -> "zh"
            "zht", "zh-tw", "zh-hant", "traditional chinese" -> "zh-TW"
            "kor", "korean", "tiếng hàn" -> "ko"
            "jpn", "japanese", "tiếng nhật" -> "ja"
            "fre", "fra", "french", "tiếng pháp" -> "fr"
            "spa", "spl", "spanish", "tiếng tây ban nha" -> "es"
            "ger", "deu", "german", "tiếng đức" -> "de"
            "ind", "indonesian", "tiếng indonesia" -> "id"
            "rus", "russian", "tiếng nga" -> "ru"
            "por", "pob", "portuguese", "tiếng bồ đào nha" -> "pt"
            "ita", "italian", "tiếng ý" -> "it"
            "ara", "arabic", "tiếng ả rập" -> "ar"
            "hin", "hindi", "tiếng hindi" -> "hi"
            "may", "msa", "malay", "tiếng mã lai" -> "ms"
            "lao", "tiếng lào" -> "lo"
            "khm", "khmer", "tiếng khmer" -> "km"
            "orig", "original" -> "orig"
            "off", "none" -> "off"
            else -> clean
        }
    }

    fun resolve(codeOrName: String): SubtitleLanguageOption {
        val norm = normalizeCode(codeOrName)
        PRESETS.firstOrNull { it.code.equals(norm, ignoreCase = true) }?.let { return it }
        PRESETS.firstOrNull {
            it.openSubtitlesId.equals(norm, ignoreCase = true) ||
                it.displayName.contains(norm, ignoreCase = true) ||
                it.nativeLabel.equals(norm, ignoreCase = true)
        }?.let { return it }

        // Dynamic custom AI language (supports literally any language Gemini / Neural AI understands!)
        val clean = codeOrName.trim().ifBlank { "en" }
        val badge = clean.filter { it.isLetter() }.take(3).uppercase().ifBlank { "AI" }
        return SubtitleLanguageOption(
            code = clean,
            shortBadge = badge,
            displayName = clean,
            nativeLabel = clean,
            openSubtitlesId = clean.lowercase().take(3),
            geminiInstructionName = "natural cinema-quality movie subtitles in $clean",
            samplePreview = "ChillFilm AI Subtitle Preview ($clean) • NKI"
        )
    }

    fun neuralTranslateCode(codeOrName: String): String {
        val norm = normalizeCode(codeOrName)
        return when (norm) {
            "zh" -> "zh-CN"
            "zh-TW" -> "zh-TW"
            else -> norm.lowercase()
        }
    }

    fun isTrackMatchingLanguage(trackLangCode: String, targetLangCode: String): Boolean {
        val a = normalizeCode(trackLangCode).lowercase()
        val b = normalizeCode(targetLangCode).lowercase()
        if (a == b) return true
        if (a.startsWith("$b-") || b.startsWith("$a-")) return true
        return false
    }

    fun isTextAlreadyInTargetLanguage(text: String, targetLangCode: String): Boolean {
        val norm = normalizeCode(targetLangCode).lowercase()
        if (text.isBlank()) return false
        return when (norm) {
            "th" -> text.any { it in '\u0E00'..'\u0E7F' }
            "ko" -> text.any { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' }
            "ja" -> text.any { it in '\u3040'..'\u30FF' }
            "zh", "zh-tw" -> text.any { it in '\u4E00'..'\u9FFF' } && text.none { it in '\u3040'..'\u30FF' }
            "ru" -> text.any { it in '\u0400'..'\u04FF' }
            "ar" -> text.any { it in '\u0600'..'\u06FF' }
            "hi" -> text.any { it in '\u0900'..'\u097F' }
            "lo" -> text.any { it in '\u0E80'..'\u0EFF' }
            "km" -> text.any { it in '\u1780'..'\u17FF' }
            "vi" -> text.any { it in "ăâđêôơưĂÂĐÊÔƠƯáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ" }
            else -> false
        }
    }
}

data class SubtitleStyleConfig(
    val preferredPrimaryLang: String = "th",
    val subtitlesEnabled: Boolean = true,
    val autoTranslateToThai: Boolean = true,
    val translationEngine: TranslationEngineMode = TranslationEngineMode.FAST_FREE,
    val geminiModelId: String = "gemini-3-flash-preview",
    val autoGenerateAudioSubWhenMissing: Boolean = true,
    val dualSubEnabled: Boolean = false,
    val secondaryLang: SecondarySubLang = SecondarySubLang.VIETNAMESE,
    val secondaryLangCode: String = "",
    val fontChoice: SubtitleFontChoice = SubtitleFontChoice.SARABUN,
    val fontSizeSp: Float = 20f,
    val secondaryFontSizeSp: Float = 16f,
    val boldText: Boolean = true,
    val primaryColor: SubtitleColorChoice = SubtitleColorChoice.CINEMA_YELLOW,
    val secondaryColor: SubtitleColorChoice = SubtitleColorChoice.WHITE,
    val linkPrimarySecondaryStyle: Boolean = false,
    val edgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.BACKGROUND_BOX,
    val boxOpacity: Float = 0.78f,
    val bottomOffsetDp: Float = 18f,
    val hardsubMaskEnabled: Boolean = false,
    val hardsubMaskHeightDp: Float = 52f,
    val hardsubMaskOpacity: Float = 0.88f,
    val syncOffsetMs: Long = 0L,
    val subSpeedScale: Float = 1.0f,
    val autoSyncDownloadedSub: Boolean = true
) {
    val autoTranslateEnabled: Boolean
        get() = autoTranslateToThai

    fun resolvedPrimaryLanguage(): SubtitleLanguageOption =
        SubtitleLanguageCatalog.resolve(preferredPrimaryLang.ifBlank { "vi" })

    fun resolvedSecondaryLangCode(): String {
        val explicit = secondaryLangCode.trim()
        if (explicit.isNotEmpty()) return SubtitleLanguageCatalog.normalizeCode(explicit)
        return secondaryLang.langCode
    }

    fun resolvedSecondaryDisplayName(): String {
        return when (val code = resolvedSecondaryLangCode()) {
            "off" -> "Tắt dòng phụ"
            "orig" -> "Ngôn ngữ gốc"
            else -> SubtitleLanguageCatalog.resolve(code).displayName
        }
    }
}

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
    val statusBadge: String = "AI Sub (Đang chuẩn bị...)",
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
 * Displays BOTH the primary translated line and the original reference line so any viewer
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
