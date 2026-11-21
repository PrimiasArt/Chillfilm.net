package com.phyll.filmhub.data

import com.phyll.filmhub.model.MovieItem
import java.text.Normalizer

/**
 * Hard Anti-Porn & Vietnam-Safe Content Filter (mirrors Vercel Backend `api/_lib/safety.js`).
 *
 * - ALLOWS legitimate mainstream 18+ / R / TV-MA cinema (Deadpool, John Wick, Oppenheimer, Exhuma,
 *   Squid Game, Sex Education, etc.).
 * - STRICTLY BLOCKS phim sex, pornography, JAV, Hentai, phim-18 / cấp 3 adult categories, and
 *   titles officially banned from release in Vietnam.
 */
object ContentSafetyFilter {

    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

    private val BLOCKED_CATEGORY_SLUGS = setOf(
        "phim-18", "18", "18+", "adult", "adults", "adult-only", "xxx", "porn", "pornography",
        "porno", "jav", "hentai", "ecchi-18", "erotic", "erotica", "softcore", "sex", "phim-sex",
        "phim-cap-3", "cap-3", "phim-nguoi-lon", "phim-khieu-dam", "khieu-dam", "clip-nong",
        "uncensored", "av", "cam", "webcam", "onlyfans", "luan-ly",
        "伦理片", "伦理", "理论片", "理论", "福利", "福利片", "情色", "色情", "里番", "里番动漫",
        "午夜", "写真", "成人", "无码", "有码", "無修正", "アダルト"
    )

    private val MAINSTREAM_SAFE_WHITELIST = listOf(
        Regex("\\bsex\\s+education\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsex\\s+and\\s+the\\s+city\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsex\\s+tape\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsussex\\b", RegexOption.IGNORE_CASE),
        Regex("\\bessex\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmiddlesex\\b", RegexOption.IGNORE_CASE)
    )

    private val EXPLICIT_PORN_REGEX = Regex(
        listOf(
            "\\b(?:porn|porno|pornhub|xvideos|xnxx|xhamster|brazzers|onlyfans|camgirl|chaturbate|stripchat)\\b",
            "\\b(?:hentai|jav\\s*hd|jav\\s*sub|av\\s*idol|uncensored\\s*jav|softcore|erotica|xxx)\\b",
            "\\b(?:fc2[-_\\s]?ppv|ssis|stars|ipx|ipzz|mide|midv|abp|abw|pred|juq|jul|sone|sdde|snis|meyd|dasd|kawd|ebod|pppd|rbd|shkd|atid|adn|miaa|miab|cawd|venx|blk|hmn|dldss|fsdss|1pondo|caribbeancom|heyzo|tokyo[-_\\s]?hot)[-_\\s]?\\d{2,6}\\b",
            "(?:phim\\s*sex|phim\\s*heo|phim\\s*cấp\\s*3|phim\\s*cap\\s*3|phim\\s*khiêu\\s*dâm|phim\\s*khieu\\s*dam|clip\\s*nóng|clip\\s*nong|sex\\s*không\\s*che|jav\\s*không\\s*che|phim\\s*người\\s*lớn)",
            "(?:无码|有码|中出|巨乳|人妻|痴汉|里番|色情片|三级片|倫理片|伦理片|福利片|アダルト|無修正)"
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    private val BUILTIN_VN_BANNED_SLUGS = listOf(
        "abominable",
        "everest-nguoi-tuyet-be-nho",
        "uncharted",
        "tho-san-co-vat",
        "barbie-2023",
        "flight-to-you",
        "huong-gio-ma-di",
        "pine-gap",
        "put-your-head-on-my-shoulder",
        "gui-thoi-thanh-xuan-am-ap",
        "love-is-true",
        "madam-secretary"
    )

    private fun stripDiacritics(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .lowercase()
            .trim()
    }

    fun isBlockedCategoryTag(raw: String): Boolean {
        val clean = stripDiacritics(raw)
        val rawLower = raw.trim().lowercase()
        if (clean.isEmpty() && rawLower.isEmpty()) return false
        if (clean in BLOCKED_CATEGORY_SLUGS || rawLower in BLOCKED_CATEGORY_SLUGS) return true
        return clean.contains("phim 18+") ||
            clean.contains("phim-18") ||
            clean.contains("cap 3") ||
            clean.contains("khieu dam") ||
            clean.contains("nguoi lon") ||
            clean.contains("porn") ||
            clean.contains("hentai") ||
            clean.contains("erotic") ||
            clean == "xxx" ||
            clean == "jav" ||
            rawLower.contains("伦理") ||
            rawLower.contains("理论") ||
            rawLower.contains("福利") ||
            rawLower.contains("色情") ||
            rawLower.contains("里番") ||
            rawLower.contains("午夜") ||
            rawLower.contains("成人")
    }

    fun isBlockedRaw(
        slug: String,
        title: String,
        originalTitle: String = "",
        description: String = "",
        categoryTags: List<String> = emptyList()
    ): Boolean {
        if (categoryTags.any { isBlockedCategoryTag(it) }) return true

        val combined = "$title $originalTitle $slug"
        val isWhitelisted = MAINSTREAM_SAFE_WHITELIST.any { it.containsMatchIn(combined) }
        if (!isWhitelisted) {
            if (EXPLICIT_PORN_REGEX.containsMatchIn(combined)) return true
            if (description.isNotBlank() && EXPLICIT_PORN_REGEX.containsMatchIn(description.take(300))) return true
        }

        val normSlug = stripDiacritics(slug.removePrefix("modern_"))
        val normTitle = stripDiacritics("$title $originalTitle")
        for (banned in BUILTIN_VN_BANNED_SLUGS) {
            if (normSlug == banned || normSlug.startsWith("$banned-")) return true
            val phrase = banned.replace('-', ' ')
            if (phrase.length >= 8 && normTitle.contains(phrase)) return true
        }
        return false
    }

    fun isBlocked(movie: MovieItem): Boolean {
        val tags = movie.genre.split("•", ",", "|", "/").map { it.trim() }.filter { it.isNotEmpty() }
        return isBlockedRaw(
            slug = movie.detailLookupId ?: movie.id,
            title = movie.title,
            originalTitle = movie.originalTitle,
            description = movie.description,
            categoryTags = tags
        )
    }

    fun filterSafe(items: List<MovieItem>): List<MovieItem> = items.filterNot { isBlocked(it) }
}
