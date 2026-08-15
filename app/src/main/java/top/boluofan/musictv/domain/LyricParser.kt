package top.boluofan.musictv.domain

import top.boluofan.musictv.data.model.LyricLine
import top.boluofan.musictv.data.model.LyricWord

object LyricParser {

    private val timeRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val lineTimeRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

    // 洛雪相对偏移逐字标记：<起始ms,持续ms>文本
    private val lxWordRegex = Regex("""<(\d+),(\d+)>([^<]*)""")

    // 绝对时间戳逐字标记：[[mm:ss.xx]]文本
    private val absWordRegex = Regex("""\[\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]]([^\[]*)""")

    fun parsePayload(
        lyric: String?,
        tlyric: String? = null,
        rlyric: String? = null,
        lxlyric: String? = null
    ): List<LyricLine> {
        val base = when {
            !lxlyric.isNullOrBlank() -> parseWordByWord(lxlyric)
            !lyric.isNullOrBlank() && containsWordByWord(lyric) -> parseWordByWord(lyric)
            !lyric.isNullOrBlank() -> parse(lyric)
            else -> emptyList()
        }
        return mergeTranslations(base, tlyric, rlyric)
    }

    fun parse(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in lrcText.lines()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue

            val matches = timeRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val text = trimmed.replace(timeRegex, "").trim()
            for (match in matches) {
                lines.add(LyricLine(time = timeOf(match), text = text))
            }
        }
        return lines.sortedBy { it.time }
    }

    fun containsWordByWord(content: String): Boolean =
        content.contains("[[") || lxWordRegex.containsMatchIn(content)

    fun parseWordByWord(content: String): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()
        for (raw in content.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val ltMatch = lineTimeRegex.find(line)
            val lineTime = ltMatch?.let { timeOf(it) }
            val body = if (ltMatch != null) line.substring(ltMatch.range.last + 1) else line

            val words: List<LyricWord>? = when {
                lxWordRegex.containsMatchIn(body) -> parseLxWords(body, lineTime ?: 0L)
                body.contains("[[") -> parseAbsWords(body)
                else -> null
            }

            if (!words.isNullOrEmpty()) {
                lyrics.add(
                    LyricLine(
                        time = lineTime ?: words.first().start,
                        text = words.joinToString("") { it.text },
                        words = words
                    )
                )
            } else if (ltMatch != null) {
                lyrics.add(LyricLine(time = lineTime!!, text = body.trim()))
            }
        }

        lyrics.sortBy { it.time }

        // 绝对格式每行最后一字的 end 用下一行行首时间补齐，末行兜底 +4s
        for (i in lyrics.indices) {
            val ws = lyrics[i].words ?: continue
            if (ws.isEmpty()) continue
            val last = ws.last()
            if (last.end > last.start) continue
            val fallbackEnd = if (i + 1 < lyrics.size) lyrics[i + 1].time else last.start + 4000L
            val fixed = ws.toMutableList()
            fixed[fixed.lastIndex] = last.copy(end = maxOf(fallbackEnd, last.start))
            lyrics[i] = lyrics[i].copy(words = fixed)
        }

        return lyrics
    }

    fun mergeTranslations(
        base: List<LyricLine>,
        tlyric: String?,
        rlyric: String? = null,
        toleranceMs: Long = 600L
    ): List<LyricLine> {
        val tLines = if (!tlyric.isNullOrBlank()) parse(tlyric) else emptyList()
        val rLines = if (!rlyric.isNullOrBlank()) parse(rlyric) else emptyList()
        if (tLines.isEmpty() && rLines.isEmpty()) return base

        fun nearest(lines: List<LyricLine>, t: Long): String? {
            var best: LyricLine? = null
            var bestDiff = toleranceMs
            for (l in lines) {
                val diff = kotlin.math.abs(l.time - t)
                if (diff <= bestDiff) {
                    bestDiff = diff
                    best = l
                }
            }
            return best?.text?.trim()?.takeIf { it.isNotEmpty() }
        }

        return base.map { line ->
            line.copy(
                translation = nearest(tLines, line.time),
                romaji = nearest(rLines, line.time)
            )
        }
    }

    private fun parseLxWords(body: String, lineTime: Long): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        for (m in lxWordRegex.findAll(body)) {
            val off = m.groupValues[1].toLong()
            val dur = m.groupValues[2].toLong()
            val text = m.groupValues[3]
            if (text.isEmpty()) continue
            val start = lineTime + off
            words.add(LyricWord(start = start, end = start + dur, text = text))
        }
        return words
    }

    // 每字 end 先取下一字 start；行内最后一字暂置为 start，跨行阶段补齐
    private fun parseAbsWords(body: String): List<LyricWord> {
        val starts = mutableListOf<Long>()
        val texts = mutableListOf<String>()
        for (m in absWordRegex.findAll(body)) {
            val text = m.groupValues[4]
            if (text.isEmpty()) continue
            starts.add(timeOf(m))
            texts.add(text)
        }
        return starts.indices.map { i ->
            LyricWord(
                start = starts[i],
                end = if (i == starts.lastIndex) starts[i] else starts[i + 1],
                text = texts[i]
            )
        }
    }

    private fun timeOf(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val msStr = match.groupValues[3]
        val millis = if (msStr.isEmpty()) 0L else msStr.padEnd(3, '0').toLong()
        return (minutes * 60 + seconds) * 1000 + millis
    }
}
