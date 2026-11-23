package com.phyll.filmhub.subtitle

import com.phyll.filmhub.model.SubtitleCue
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object SubtitleParser {

    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val HTML_BR_REGEX = Regex("(?i)<br\\s*/?>")
    private val NUMERIC_DEC_ENTITY_REGEX = Regex("&#(\\d+);")
    private val NUMERIC_HEX_ENTITY_REGEX = Regex("(?i)&#x([0-9a-f]+);")
    private val YT_P_CUE_REGEX = Regex(
        """(?is)<p\b([^>]*)>(.*?)</p>"""
    )
    private val YT_TEXT_CUE_REGEX = Regex(
        """(?is)<text\b([^>]*)>(.*?)</text>"""
    )
    private val XML_ATTR_T_REGEX = Regex("""\bt="(\d+)"""")
    private val XML_ATTR_D_REGEX = Regex("""\bd="(\d+)"""")
    private val XML_ATTR_START_REGEX = Regex("""\bstart="([\d.]+)""")
    private val XML_ATTR_DUR_REGEX = Regex("""\bdur="([\d.]+)""")
    private val ASS_OVERRIDE_TAG_REGEX = Regex("\\{[^}]*\\}")
    private val SRT_TIME_ARROW_REGEX = Regex(
        """(\d{1,2}:)?\d{1,2}:\d{2}[,\.]\d{1,3}\s*-->\s*(\d{1,2}:)?\d{1,2}:\d{2}[,\.]\d{1,3}"""
    )

    /**
     * Unpacks GZIP (.gz from OpenSubtitles.org REST) or ZIP (.zip from YTS/YIFY/SubDL)
     * archives automatically before smart UTF-8 / TIS-620 / Windows-874 decoding.
     */
    fun decodeArchiveAndBytesSmart(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val unpacked = unpackArchiveIfCompressed(bytes)
        return decodeBytesSmart(unpacked)
    }

    private fun unpackArchiveIfCompressed(bytes: ByteArray): ByteArray {
        if (bytes.size < 4) return bytes
        return try {
            // 1. GZIP magic header: 0x1F 0x8B
            if (bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            }
            // 2. ZIP magic header: PK\x03\x04 (0x50 0x4B 0x03 0x04)
            else if (
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()
            ) {
                var bestBytes: ByteArray? = null
                var bestScore = -1
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val lower = entry.name.lowercase()
                            val score = when {
                                lower.endsWith(".srt") -> 4
                                lower.endsWith(".vtt") -> 3
                                lower.endsWith(".ass") || lower.endsWith(".ssa") -> 2
                                lower.endsWith(".sub") || lower.endsWith(".txt") -> 1
                                else -> 0
                            }
                            if (score > bestScore) {
                                val entryBytes = zis.readBytes()
                                if (entryBytes.isNotEmpty()) {
                                    bestBytes = entryBytes
                                    bestScore = score
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                bestBytes ?: bytes
            } else {
                bytes
            }
        } catch (_: Exception) {
            bytes
        }
    }

    /**
     * Decodes subtitle bytes with automatic detection of UTF-8, UTF-16 BOM,
     * and legacy Thai encodings (Windows-874 / TIS-620) commonly found in Thai .srt files.
     */
    fun decodeBytesSmart(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // 1. Check BOMs
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        // 2. Try strict UTF-8 decoding
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            // Not valid UTF-8 -> check for Thai TIS-620 / Windows-874 byte range (0xA1..0xFB)
            val thaiByteCount = bytes.count { b ->
                val u = b.toInt() and 0xFF
                u in 0xA1..0xFB
            }
            if (thaiByteCount > 0) {
                val thaiCharset = runCatching { Charset.forName("windows-874") }
                    .recoverCatching { Charset.forName("TIS-620") }
                    .getOrDefault(Charsets.ISO_8859_1)
                return String(bytes, thaiCharset)
            }
            return String(bytes, Charsets.UTF_8)
        }
    }

    /**
     * Auto-detects format (.srt, .vtt, .ass/.ssa, YouTube TimedText XML) and parses sorted SubtitleCues.
     */
    fun parse(rawContent: String): List<SubtitleCue> {
        val cleaned = rawContent.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (cleaned.isBlank()) return emptyList()

        val trimmedLead = cleaned.trimStart()
        return when {
            trimmedLead.contains("<timedtext", ignoreCase = true) ||
                trimmedLead.contains("<transcript", ignoreCase = true) -> parseYouTubeTimedTextXml(cleaned)
            trimmedLead.contains("[Events]", ignoreCase = true) &&
                trimmedLead.contains("Dialogue:", ignoreCase = true) -> parseAss(cleaned)
            else -> parseSrtOrVtt(cleaned)
        }
    }

    fun parseBytes(bytes: ByteArray): List<SubtitleCue> = parse(decodeArchiveAndBytesSmart(bytes))

    private fun parseYouTubeTimedTextXml(xml: String): List<SubtitleCue> {
        val rawCues = ArrayList<SubtitleCue>(256)
        var cueId = 1

        // Format 3: <p t="1367" d="2084">...</p> (supports nested <s> tags in YouTube ASR)
        for (m in YT_P_CUE_REGEX.findAll(xml)) {
            val attrs = m.groupValues[1]
            val inner = m.groupValues[2]
            val startMs = XML_ATTR_T_REGEX.find(attrs)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: continue
            val durMs = XML_ATTR_D_REGEX.find(attrs)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 2_500L
            val text = cleanSubtitleText(inner.replace(HTML_BR_REGEX, "\n"))
            if (text.isNotEmpty() && durMs > 0L) {
                rawCues.add(
                    SubtitleCue(
                        id = cueId++,
                        startMs = startMs,
                        endMs = startMs + durMs,
                        text = text
                    )
                )
            }
        }

        // Format 1 fallback: <text start="1.367" dur="2.084">...</text>
        if (rawCues.isEmpty()) {
            for (m in YT_TEXT_CUE_REGEX.findAll(xml)) {
                val attrs = m.groupValues[1]
                val inner = m.groupValues[2]
                val startSec = XML_ATTR_START_REGEX.find(attrs)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: continue
                val durSec = XML_ATTR_DUR_REGEX.find(attrs)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 2.5
                val startMs = (startSec * 1000.0).toLong()
                val durMs = (durSec * 1000.0).toLong().coerceAtLeast(300L)
                val text = cleanSubtitleText(inner.replace(HTML_BR_REGEX, "\n"))
                if (text.isNotEmpty()) {
                    rawCues.add(
                        SubtitleCue(
                            id = cueId++,
                            startMs = startMs,
                            endMs = startMs + durMs,
                            text = text
                        )
                    )
                }
            }
        }

        if (rawCues.isEmpty()) return emptyList()
        val sorted = rawCues.sortedBy { it.startMs }
        // Clamp overlapping YouTube rolling ASR cues so two consecutive rolling lines don't double-render
        val normalized = ArrayList<SubtitleCue>(sorted.size)
        for (i in sorted.indices) {
            val cur = sorted[i]
            val nextStart = sorted.getOrNull(i + 1)?.startMs
            val clampedEnd = if (nextStart != null && nextStart > cur.startMs + 250L && cur.endMs > nextStart) {
                nextStart - 15L
            } else {
                cur.endMs
            }
            normalized.add(cur.copy(endMs = clampedEnd))
        }
        return normalized
    }

    private fun parseSrtOrVtt(content: String): List<SubtitleCue> {
        val lines = content.split('\n')
        val cues = ArrayList<SubtitleCue>(256)
        var i = 0
        var cueId = 1

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() ||
                line.startsWith("WEBVTT", ignoreCase = true) ||
                line.startsWith("NOTE", ignoreCase = true) ||
                line.startsWith("STYLE", ignoreCase = true)
            ) {
                i++
                continue
            }

            // Find timestamp line (either current line or next line after numeric index)
            val timeLine: String
            if (SRT_TIME_ARROW_REGEX.containsMatchIn(line)) {
                timeLine = line
                i++
            } else if (i + 1 < lines.size && SRT_TIME_ARROW_REGEX.containsMatchIn(lines[i + 1])) {
                timeLine = lines[i + 1].trim()
                i += 2
            } else {
                i++
                continue
            }

            val arrowParts = timeLine.split("-->")
            if (arrowParts.size < 2) continue

            val startMs = parseTimestampMs(arrowParts[0].trim())
            // WebVTT may have cue settings after end timestamp, e.g. "00:01:12.500 line:85% align:center"
            val endToken = arrowParts[1].trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            val endMs = parseTimestampMs(endToken)

            val textBuilder = StringBuilder()
            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                // Stop early if malformed file omits blank line before next cue index + timestamp
                if (i + 1 < lines.size &&
                    lines[i].trim().all { it.isDigit() } &&
                    SRT_TIME_ARROW_REGEX.containsMatchIn(lines[i + 1])
                ) {
                    break
                }
                if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                textBuilder.append(cleanSubtitleText(lines[i]))
                i++
            }

            val finalText = textBuilder.toString().trim()
            if (startMs >= 0L && endMs > startMs && finalText.isNotEmpty()) {
                cues.add(SubtitleCue(id = cueId++, startMs = startMs, endMs = endMs, text = finalText))
            }
        }

        return cues.sortedBy { it.startMs }
    }

    private fun parseAss(content: String): List<SubtitleCue> {
        val lines = content.split('\n')
        var inEvents = false
        var startIdx = 1
        var endIdx = 2
        var textIdx = 9
        var formatColumnCount = 10
        val cues = ArrayList<SubtitleCue>(256)
        var cueId = 1

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue

            if (line.startsWith("Format:", ignoreCase = true)) {
                val cols = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                formatColumnCount = cols.size.coerceAtLeast(3)
                cols.indexOf("start").takeIf { it >= 0 }?.let { startIdx = it }
                cols.indexOf("end").takeIf { it >= 0 }?.let { endIdx = it }
                cols.indexOf("text").takeIf { it >= 0 }?.let { textIdx = it }
            } else if (line.startsWith("Dialogue:", ignoreCase = true)) {
                val payload = line.substringAfter(':')
                val parts = payload.split(',', limit = formatColumnCount)
                if (parts.size > maxOf(startIdx, endIdx, textIdx)) {
                    val startMs = parseTimestampMs(parts[startIdx].trim())
                    val endMs = parseTimestampMs(parts[endIdx].trim())
                    val rawText = parts[textIdx]
                        .replace("\\N", "\n")
                        .replace("\\n", "\n")
                        .replace("\\h", " ")
                    val cleanedText = cleanSubtitleText(rawText)
                    if (startMs >= 0L && endMs > startMs && cleanedText.isNotEmpty()) {
                        cues.add(
                            SubtitleCue(
                                id = cueId++,
                                startMs = startMs,
                                endMs = endMs,
                                text = cleanedText
                            )
                        )
                    }
                }
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun parseTimestampMs(raw: String): Long {
        val cleaned = raw.trim().replace(',', '.')
        if (cleaned.isEmpty()) return -1L
        val parts = cleaned.split(':')
        return try {
            val hours: Long
            val minutes: Long
            val secPart: String
            when (parts.size) {
                3 -> {
                    hours = parts[0].toLong()
                    minutes = parts[1].toLong()
                    secPart = parts[2]
                }
                2 -> {
                    hours = 0L
                    minutes = parts[0].toLong()
                    secPart = parts[1]
                }
                else -> return -1L
            }
            val secPieces = secPart.split('.')
            val seconds = secPieces[0].toLong()
            val millis = if (secPieces.size > 1) {
                val frac = secPieces[1].take(3).padEnd(3, '0')
                frac.toLong()
            } else {
                0L
            }
            ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
        } catch (_: NumberFormatException) {
            -1L
        }
    }

    fun cleanSubtitleText(raw: String): String {
        var text = raw
            .replace(ASS_OVERRIDE_TAG_REGEX, "")
            .replace(HTML_TAG_REGEX, "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'", ignoreCase = true)
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        text = NUMERIC_DEC_ENTITY_REGEX.replace(text) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else ""
        }
        text = NUMERIC_HEX_ENTITY_REGEX.replace(text) { m ->
            val code = m.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else ""
        }
        return text
            .replace("&amp;", "&")
            .trim()
    }

    /**
     * Fast lookup of active subtitle cue(s) at [effectivePositionMs].
     * Combines overlapping cues (common in ASS/SRT dual dialogue) onto separate lines.
     */
    fun findActiveText(cues: List<SubtitleCue>, effectivePositionMs: Long): String? {
        if (cues.isEmpty() || effectivePositionMs < 0L) return null

        // Binary search for last cue with startMs <= effectivePositionMs
        var low = 0
        var high = cues.lastIndex
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= effectivePositionMs) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidate < 0) return null

        // Check small window backwards in case an earlier long cue overlaps current position,
        // suppressing near-duplicate rephrased AI lines at the exact same timestamp!
        val windowStart = (candidate - 10).coerceAtLeast(0)
        val matched = ArrayList<SubtitleCue>(2)
        for (idx in windowStart..candidate) {
            val cue = cues[idx]
            if (effectivePositionMs in cue.startMs..cue.endMs) {
                val dupIdx = matched.indexOfFirst { existing ->
                    if (existing.text == cue.text) return@indexOfFirst true
                    val sim = cueTextSimilarity(existing.text, cue.text)
                    if (sim >= 0.48) return@indexOfFirst true
                    val ov = cueOverlapRatio(existing.startMs, existing.endMs, cue.startMs, cue.endMs)
                    kotlin.math.abs(existing.startMs - cue.startMs) <= 850L &&
                        ov >= 0.65 &&
                        (kotlin.math.abs(existing.endMs - cue.endMs) <= 950L || sim >= 0.36)
                }
                if (dupIdx >= 0) {
                    val prev = matched[dupIdx]
                    if (scoreSubtitleCueQuality(cue.text, cue.endMs - cue.startMs) >
                        scoreSubtitleCueQuality(prev.text, prev.endMs - prev.startMs)
                    ) {
                        matched[dupIdx] = cue
                    }
                } else {
                    matched.add(cue)
                }
            }
        }
        return if (matched.isEmpty()) null else matched.takeLast(2).joinToString("\n") { it.text }
    }

    internal fun scoreSubtitleCueQuality(text: String, durMs: Long = 2_500L): Double {
        val s = text.trim()
        if (s.isEmpty()) return -100.0
        var score = minOf(s.length, 95) * 0.6
        if (s.length > 140) score -= (s.length - 140) * 0.5
        if (s.endsWith(".") || s.endsWith("?") || s.endsWith("!") || s.endsWith("…")) score += 6.0
        if (s.any { it == ',' || it == ':' || it == ';' || it == '?' || it == '!' }) score += 3.0
        if (s.any { it.code > 127 }) score += 5.0
        if (durMs in 900L..5_500L) score += 4.0
        return score
    }

    internal fun cueOverlapRatio(s1: Long, e1: Long, s2: Long, e2: Long): Double {
        val ov = minOf(e1, e2) - maxOf(s1, s2)
        if (ov <= 0L) return 0.0
        val minDur = minOf(e1 - s1, e2 - s2).coerceAtLeast(400L)
        return ov.toDouble() / minDur.toDouble()
    }

    internal fun cueTextSimilarity(a: String, b: String): Double {
        val na = a.trim().replace(Regex("\\s+"), " ").lowercase()
        val nb = b.trim().replace(Regex("\\s+"), " ").lowercase()
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        val pa = na.replace(Regex("[^\\p{L}\\p{N}]+"), "")
        val pb = nb.replace(Regex("[^\\p{L}\\p{N}]+"), "")
        if (pa.isEmpty() || pb.isEmpty()) return 0.0
        if (pa == pb) return 1.0
        if (pa.length >= 6 && pb.length >= 6 && (pa.contains(pb) || pb.contains(pa))) {
            return maxOf(0.75, minOf(pa.length, pb.length).toDouble() / maxOf(pa.length, pb.length).toDouble())
        }
        fun tokenize(s: String): Set<String> {
            val cleaned = s.replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ").replace(Regex("\\s+"), " ").trim()
            if (!cleaned.contains(' ') && cleaned.length >= 4) {
                val grams = HashSet<String>(cleaned.length)
                for (i in 0 until cleaned.length - 1) grams.add(cleaned.substring(i, i + 2))
                return grams
            }
            return cleaned.split(' ').filter { it.isNotEmpty() }.toHashSet()
        }
        val setA = tokenize(na)
        val setB = tokenize(nb)
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        var inter = 0
        for (w in setA) if (w in setB) inter++
        val union = setA.size + setB.size - inter
        val jaccard = if (union > 0) inter.toDouble() / union.toDouble() else 0.0
        val minSize = minOf(setA.size, setB.size)
        val overlapCoeff = if (minSize >= 3) inter.toDouble() / minSize.toDouble() else 0.0
        return maxOf(jaccard, overlapCoeff * 0.92)
    }
}
