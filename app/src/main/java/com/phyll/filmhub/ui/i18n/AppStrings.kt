package com.phyll.filmhub.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.phyll.filmhub.model.CountryCategory

enum class AppLanguage(
    val code: String,
    val shortBadge: String,
    val nativeName: String
) {
    EN("en", "EN", "English"),
    VI("vi", "VI", "Tiếng Việt"),
    TH("th", "TH", "ภาษาไทย"),
    ZH("zh", "ZH", "中文"),
    KO("ko", "KO", "한국어");

    companion object {
        fun fromCode(raw: String?): AppLanguage {
            val clean = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.code == clean } ?: EN
        }
    }
}

data class AppStrings(
    val lang: AppLanguage,
    // Header & Navigation
    val searchPlaceholder: String,
    val searchAction: String,
    val closeAction: String,
    val refreshDesc: String,
    val castTvDesc: String,
    val languageLabel: String,
    val chooseLanguageTitle: String,
    val navDiscover: String,
    val navSeries: String,
    val navYouTube: String,
    val navSettings: String,
    // Home & Catalog
    val aiSubMultiBadge: String,
    val watchNow: String,
    val detailsBtn: String,
    val continueWatching: String,
    val loadingCatalog: String,
    val moviesCountSuffix: String,
    val searchResultsPrefix: String,
    val noSearchResults: String,
    val resumeLabel: String,
    // Detail Screen
    val backBtn: String,
    val favoriteDesc: String,
    val castToTvBtn: String,
    val continueEpFmt: String,
    val watchNowEpFmt: String,
    val episodesTitle: String,
    val noStreamAvailable: String,
    val showMore: String,
    val showLess: String,
    // Player HUD & Mini Pop-out
    val lockBtn: String,
    val lockedToast: String,
    val unlockedToast: String,
    val syncSubBtn: String,
    val nextEpBtn: String,
    val aspectBtn: String,
    val rotateDesc: String,
    val moreOptionsDesc: String,
    val subtitleDesc: String,
    val miniExpandDesc: String,
    val miniCloseDesc: String,
    val miniPlayingHint: String,
    val resumeFromFmt: String,
    val backToChooseSource: String,
    // Settings
    val settingsTitle: String,
    val uiLanguageSectionTitle: String,
    val uiLanguageSectionDesc: String,
    val geminiKeyTitle: String,
    val saveKeyBtn: String,
    val checkUpdatesBtn: String,
    val clearHistoryBtn: String,
    val clearCacheBtn: String,
    val tvManagerBtn: String
) {
    fun countryName(category: CountryCategory): String = when (lang) {
        AppLanguage.EN -> when (category) {
            CountryCategory.ALL -> "Trending"
            CountryCategory.YOUTUBE -> "YouTube"
            CountryCategory.THAILAND -> "Thai"
            CountryCategory.KOREA -> "Korean"
            CountryCategory.CHINA -> "Chinese"
            CountryCategory.USA -> "US / Hollywood"
            CountryCategory.UK -> "UK"
            CountryCategory.JAPAN -> "Anime & JP"
            CountryCategory.GLOBAL -> "Movies"
            CountryCategory.LIVE_TV -> "TV Series"
        }
        AppLanguage.VI -> category.displayName
        AppLanguage.TH -> when (category) {
            CountryCategory.ALL -> "มาแรง"
            CountryCategory.YOUTUBE -> "YouTube"
            CountryCategory.THAILAND -> "หนังไทย"
            CountryCategory.KOREA -> "เกาหลี"
            CountryCategory.CHINA -> "จีน"
            CountryCategory.USA -> "อเมริกา"
            CountryCategory.UK -> "อังกฤษ"
            CountryCategory.JAPAN -> "ญี่ปุ่น"
            CountryCategory.GLOBAL -> "ภาพยนตร์"
            CountryCategory.LIVE_TV -> "ซีรีส์ใหม่"
        }
        AppLanguage.ZH -> when (category) {
            CountryCategory.ALL -> "热门推荐"
            CountryCategory.YOUTUBE -> "YouTube"
            CountryCategory.THAILAND -> "泰剧"
            CountryCategory.KOREA -> "韩剧"
            CountryCategory.CHINA -> "华语"
            CountryCategory.USA -> "美国"
            CountryCategory.UK -> "英国"
            CountryCategory.JAPAN -> "日漫"
            CountryCategory.GLOBAL -> "电影"
            CountryCategory.LIVE_TV -> "剧集"
        }
        AppLanguage.KO -> when (category) {
            CountryCategory.ALL -> "인기 신작"
            CountryCategory.YOUTUBE -> "YouTube"
            CountryCategory.THAILAND -> "태국"
            CountryCategory.KOREA -> "한국"
            CountryCategory.CHINA -> "중국"
            CountryCategory.USA -> "미국"
            CountryCategory.UK -> "영국"
            CountryCategory.JAPAN -> "일본"
            CountryCategory.GLOBAL -> "영화"
            CountryCategory.LIVE_TV -> "시리즈"
        }
    }

    companion object {
        val EN = AppStrings(
            lang = AppLanguage.EN,
            searchPlaceholder = "Search movies, series, actors or paste YouTube URL...",
            searchAction = "Search",
            closeAction = "Close",
            refreshDesc = "Refresh",
            castTvDesc = "Cast / Connect TV",
            languageLabel = "Language",
            chooseLanguageTitle = "App Interface Language",
            navDiscover = "Discover",
            navSeries = "Series",
            navYouTube = "YouTube",
            navSettings = "Settings",
            aiSubMultiBadge = "MULTI-LANG AI SUB",
            watchNow = "Watch Now",
            detailsBtn = "Details",
            continueWatching = "Continue Watching",
            loadingCatalog = "Loading ChillFilm catalog...",
            moviesCountSuffix = "titles ›",
            searchResultsPrefix = "Results for",
            noSearchResults = "No matching titles found. Try another keyword or English title.",
            resumeLabel = "Resume",
            backBtn = "Back",
            favoriteDesc = "Favorite",
            castToTvBtn = "Cast to TV",
            continueEpFmt = "Continue (%s • %02d:%02d)",
            watchNowEpFmt = "Watch Now (%s)",
            episodesTitle = "Episodes",
            noStreamAvailable = "No playable stream found.",
            showMore = "Show more",
            showLess = "Show less",
            lockBtn = "Lock",
            lockedToast = "Screen locked",
            unlockedToast = "Screen unlocked",
            syncSubBtn = "Sync Sub",
            nextEpBtn = "Next Ep",
            aspectBtn = "Aspect",
            rotateDesc = "Rotate",
            moreOptionsDesc = "Player options",
            subtitleDesc = "Subtitles",
            miniExpandDesc = "Expand player",
            miniCloseDesc = "Close player",
            miniPlayingHint = "Playing in mini pop-out",
            resumeFromFmt = "▶ Resuming from %s",
            backToChooseSource = "Back to choose source",
            settingsTitle = "Settings & Sources",
            uiLanguageSectionTitle = "Interface Language",
            uiLanguageSectionDesc = "Switch app UI language anytime (default: English).",
            geminiKeyTitle = "Gemini API Key",
            saveKeyBtn = "Save Key",
            checkUpdatesBtn = "Check for Updates",
            clearHistoryBtn = "Clear Watch History",
            clearCacheBtn = "Clear Cache",
            tvManagerBtn = "TV Manager"
        )

        val VI = AppStrings(
            lang = AppLanguage.VI,
            searchPlaceholder = "Tìm tên phim, diễn viên hoặc dán link YouTube...",
            searchAction = "Tìm",
            closeAction = "Đóng",
            refreshDesc = "Làm mới",
            castTvDesc = "Stream / Kết nối TV",
            languageLabel = "Ngôn ngữ",
            chooseLanguageTitle = "Ngôn Ngữ Giao Diện App",
            navDiscover = "Khám phá",
            navSeries = "Phim bộ",
            navYouTube = "YouTube",
            navSettings = "Cài đặt",
            aiSubMultiBadge = "AI SUB ĐA NGÔN NGỮ",
            watchNow = "Xem Ngay",
            detailsBtn = "Chi Tiết",
            continueWatching = "Tiếp Tục Xem",
            loadingCatalog = "Đang tải kho phim ChillFilm...",
            moviesCountSuffix = "phim ›",
            searchResultsPrefix = "Kết quả",
            noSearchResults = "Không tìm thấy phim phù hợp. Thử nhập tên tiếng Anh.",
            resumeLabel = "Xem tiếp",
            backBtn = "Quay lại",
            favoriteDesc = "Yêu thích",
            castToTvBtn = "Phát lên TV",
            continueEpFmt = "Tiếp Tục (%s • %02d:%02d)",
            watchNowEpFmt = "Xem Ngay (%s)",
            episodesTitle = "Danh Sách Tập",
            noStreamAvailable = "Chưa có luồng phát trực tiếp.",
            showMore = "Xem thêm",
            showLess = "Thu gọn",
            lockBtn = "Khóa",
            lockedToast = "Đã khóa màn hình",
            unlockedToast = "Đã mở khóa",
            syncSubBtn = "Khớp Sub",
            nextEpBtn = "Tập sau",
            aspectBtn = "Tỉ lệ",
            rotateDesc = "Xoay ngang",
            moreOptionsDesc = "Tùy chọn phát",
            subtitleDesc = "Phụ đề",
            miniExpandDesc = "Mở rộng khung phim",
            miniCloseDesc = "Tắt khung phim",
            miniPlayingHint = "Đang phát thu nhỏ",
            resumeFromFmt = "▶ Tiếp tục xem từ %s",
            backToChooseSource = "Quay lại chọn nguồn khác",
            settingsTitle = "Cài Đặt & Nguồn Phim",
            uiLanguageSectionTitle = "Ngôn Ngữ Giao Diện",
            uiLanguageSectionDesc = "Đổi ngôn ngữ giao diện nhanh giữa Anh - Việt - Thái - Trung - Hàn.",
            geminiKeyTitle = "Gemini API Key",
            saveKeyBtn = "Lưu Key",
            checkUpdatesBtn = "Kiểm Tra Cập Nhật",
            clearHistoryBtn = "Xóa Lịch Sử Xem",
            clearCacheBtn = "Dọn Bộ Nhớ Đệm",
            tvManagerBtn = "Quản Lý TV"
        )

        val TH = AppStrings(
            lang = AppLanguage.TH,
            searchPlaceholder = "ค้นหาหนัง ซีรีส์ นักแสดง หรือวางลิงก์ YouTube...",
            searchAction = "ค้นหา",
            closeAction = "ปิด",
            refreshDesc = "รีเฟรช",
            castTvDesc = "แคสต์ขึ้นทีวี",
            languageLabel = "ภาษา",
            chooseLanguageTitle = "ภาษาของอินเทอร์เฟซแอป",
            navDiscover = "หน้าแรก",
            navSeries = "ซีรีส์",
            navYouTube = "YouTube",
            navSettings = "ตั้งค่า",
            aiSubMultiBadge = "ซับ AI หลายภาษา",
            watchNow = "ดูเลย",
            detailsBtn = "รายละเอียด",
            continueWatching = "ดูต่อจากเดิม",
            loadingCatalog = "กำลังโหลดคลังหนัง ChillFilm...",
            moviesCountSuffix = "เรื่อง ›",
            searchResultsPrefix = "ผลลัพธ์",
            noSearchResults = "ไม่พบเรื่องที่ค้นหา ลองใช้ชื่อภาษาอังกฤษ",
            resumeLabel = "ดูต่อ",
            backBtn = "กลับ",
            favoriteDesc = "รายการโปรด",
            castToTvBtn = "ส่งขึ้นทีวี",
            continueEpFmt = "ดูต่อ (%s • %02d:%02d)",
            watchNowEpFmt = "ดูเลย (%s)",
            episodesTitle = "รายการตอน",
            noStreamAvailable = "ไม่พบลิงก์สตรีม",
            showMore = "เพิ่มเติม",
            showLess = "ย่อลง",
            lockBtn = "ล็อก",
            lockedToast = "ล็อกหน้าจอแล้ว",
            unlockedToast = "ปลดล็อกหน้าจอแล้ว",
            syncSubBtn = "ซิงก์ซับ",
            nextEpBtn = "ตอนถัดไป",
            aspectBtn = "สัดส่วน",
            rotateDesc = "หมุนจอ",
            moreOptionsDesc = "ตัวเลือกเพิ่มเติม",
            subtitleDesc = "คำบรรยาย",
            miniExpandDesc = "ขยายเต็มจอ",
            miniCloseDesc = "ปิดหน้าต่างย่อ",
            miniPlayingHint = "กำลังเล่นแบบหน้าต่างย่อ",
            resumeFromFmt = "▶ เล่นต่อจาก %s",
            backToChooseSource = "กลับไปเลือกแหล่งอื่น",
            settingsTitle = "ตั้งค่าและแหล่งหนัง",
            uiLanguageSectionTitle = "ภาษาอินเทอร์เฟซ",
            uiLanguageSectionDesc = "สลับภาษาได้ทันที: อังกฤษ - เวียดนาม - ไทย - จีน - เกาหลี",
            geminiKeyTitle = "Gemini API Key",
            saveKeyBtn = "บันทึกคีย์",
            checkUpdatesBtn = "ตรวจสอบอัปเดต",
            clearHistoryBtn = "ล้างประวัติการดู",
            clearCacheBtn = "ล้างแคช",
            tvManagerBtn = "จัดการทีวี"
        )

        val ZH = AppStrings(
            lang = AppLanguage.ZH,
            searchPlaceholder = "搜索电影、剧集、演员或粘贴 YouTube 链接...",
            searchAction = "搜索",
            closeAction = "关闭",
            refreshDesc = "刷新",
            castTvDesc = "投屏到电视",
            languageLabel = "语言",
            chooseLanguageTitle = "应用界面语言",
            navDiscover = "发现",
            navSeries = "剧集",
            navYouTube = "YouTube",
            navSettings = "设置",
            aiSubMultiBadge = "多语言 AI 字幕",
            watchNow = "立即播放",
            detailsBtn = "详情",
            continueWatching = "继续观看",
            loadingCatalog = "正在加载 ChillFilm 片库...",
            moviesCountSuffix = "部 ›",
            searchResultsPrefix = "搜索结果",
            noSearchResults = "未找到匹配影片，请尝试英文片名。",
            resumeLabel = "续播",
            backBtn = "返回",
            favoriteDesc = "收藏",
            castToTvBtn = "投屏到电视",
            continueEpFmt = "继续播放 (%s • %02d:%02d)",
            watchNowEpFmt = "立即播放 (%s)",
            episodesTitle = "剧集列表",
            noStreamAvailable = "暂无可播放源。",
            showMore = "展开",
            showLess = "收起",
            lockBtn = "锁屏",
            lockedToast = "已锁定屏幕",
            unlockedToast = "已解锁屏幕",
            syncSubBtn = "同步字幕",
            nextEpBtn = "下一集",
            aspectBtn = "比例",
            rotateDesc = "横屏",
            moreOptionsDesc = "播放设置",
            subtitleDesc = "字幕",
            miniExpandDesc = "展开播放器",
            miniCloseDesc = "关闭小窗",
            miniPlayingHint = "小窗播放中",
            resumeFromFmt = "▶ 从 %s 继续播放",
            backToChooseSource = "返回切换片源",
            settingsTitle = "设置与片源",
            uiLanguageSectionTitle = "界面语言",
            uiLanguageSectionDesc = "随时切换界面语言：英 / 越 / 泰 / 中 / 韩",
            geminiKeyTitle = "Gemini API Key",
            saveKeyBtn = "保存密钥",
            checkUpdatesBtn = "检查更新",
            clearHistoryBtn = "清除观看记录",
            clearCacheBtn = "清理缓存",
            tvManagerBtn = "电视管理"
        )

        val KO = AppStrings(
            lang = AppLanguage.KO,
            searchPlaceholder = "영화, 시리즈, 배우 검색 또는 YouTube 링크 붙여넣기...",
            searchAction = "검색",
            closeAction = "닫기",
            refreshDesc = "새로고침",
            castTvDesc = "TV로 전송",
            languageLabel = "언어",
            chooseLanguageTitle = "앱 인터페이스 언어",
            navDiscover = "탐색",
            navSeries = "시리즈",
            navYouTube = "YouTube",
            navSettings = "설정",
            aiSubMultiBadge = "다국어 AI 자막",
            watchNow = "지금 시청",
            detailsBtn = "상세 정보",
            continueWatching = "이어서 시청",
            loadingCatalog = "ChillFilm 카탈로그 불러오는 중...",
            moviesCountSuffix = "편 ›",
            searchResultsPrefix = "검색 결과",
            noSearchResults = "일치하는 작품이 없습니다. 영문 제목으로 검색해 보세요.",
            resumeLabel = "이어보기",
            backBtn = "뒤로",
            favoriteDesc = "즐겨찾기",
            castToTvBtn = "TV로 전송",
            continueEpFmt = "이어보기 (%s • %02d:%02d)",
            watchNowEpFmt = "지금 시청 (%s)",
            episodesTitle = "에피소드",
            noStreamAvailable = "재생 가능한 스트림이 없습니다.",
            showMore = "더보기",
            showLess = "접기",
            lockBtn = "잠금",
            lockedToast = "화면 잠금됨",
            unlockedToast = "화면 잠금 해제됨",
            syncSubBtn = "자막 싱크",
            nextEpBtn = "다음 화",
            aspectBtn = "화면 비율",
            rotateDesc = "화면 회전",
            moreOptionsDesc = "재생 옵션",
            subtitleDesc = "자막",
            miniExpandDesc = "플레이어 확대",
            miniCloseDesc = "팝업 닫기",
            miniPlayingHint = "미니 팝업 재생 중",
            resumeFromFmt = "▶ %s부터 이어보기",
            backToChooseSource = "돌아가서 다른 소스 선택",
            settingsTitle = "설정 및 소스",
            uiLanguageSectionTitle = "인터페이스 언어",
            uiLanguageSectionDesc = "앱 언어를 즉시 변경할 수 있습니다 (영어/베트남어/태국어/중국어/한국어).",
            geminiKeyTitle = "Gemini API Key",
            saveKeyBtn = "키 저장",
            checkUpdatesBtn = "업데이트 확인",
            clearHistoryBtn = "시청 기록 삭제",
            clearCacheBtn = "캐시 지우기",
            tvManagerBtn = "TV 관리"
        )

        fun forLanguage(lang: AppLanguage): AppStrings = when (lang) {
            AppLanguage.EN -> EN
            AppLanguage.VI -> VI
            AppLanguage.TH -> TH
            AppLanguage.ZH -> ZH
            AppLanguage.KO -> KO
        }
    }
}

val LocalAppStrings = staticCompositionLocalOf { AppStrings.EN }
