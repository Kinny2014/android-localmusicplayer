package com.kinny.localmusicplayer.data.lyrics

/**
 * Description: LRC 歌词文件解析器
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
object LrcParser {

    private val TIME_PATTERN = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val HOUR_TIME_PATTERN = Regex("""\[(\d{1,2}):(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** 解析 LRC 文本为歌词行列表 */
    fun parse(content: String): List<LrcLine> {
        val normalized = content.removePrefix("\uFEFF")
        val lines = mutableListOf<LrcLine>()
        var skippedMetadata = 0
        var skippedNoTimestamp = 0
        var skippedEmptyText = 0

        for (rawLine in normalized.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.matches(Regex("""\[[a-zA-Z]+:.*]"""))) {
                skippedMetadata++
                continue
            }

            val hourMatches = HOUR_TIME_PATTERN.findAll(trimmed).toList()
            if (hourMatches.isNotEmpty()) {
                val text = HOUR_TIME_PATTERN.replace(trimmed, "").trim()
                if (text.isEmpty()) {
                    skippedEmptyText++
                    continue
                }
                for (match in hourMatches) {
                    parseHourMatch(match)?.let { lines.add(it.copy(text = text)) }
                }
                continue
            }

            val matches = TIME_PATTERN.findAll(trimmed).toList()
            if (matches.isEmpty()) {
                skippedNoTimestamp++
                continue
            }

            val text = TIME_PATTERN.replace(trimmed, "").trim()
            if (text.isEmpty()) {
                skippedEmptyText++
                continue
            }

            for (match in matches) {
                parseMinuteMatch(match)?.let { lines.add(it.copy(text = text)) }
            }
        }

        val result = mergeBilingualLines(lines.sortedBy { it.timestampMs })
        LyricsDebugLog.d("LrcParser.parse") {
            "lines=${result.size}, skippedMetadata=$skippedMetadata, " +
                "skippedNoTimestamp=$skippedNoTimestamp, skippedEmptyText=$skippedEmptyText, " +
                "contentLen=${content.length}"
        }
        if (result.isEmpty() && content.isNotBlank()) {
            val sample = content.lines().filter { it.isNotBlank() }.take(3).joinToString(" | ")
            LyricsDebugLog.w("LrcParser.parse.empty") {
                "sample=${LyricsDebugLog.preview(sample, 200)}"
            }
        }
        return result
    }

    /** 合并相同时间戳的双语行，并拆分单行内的英文/中文 */
    private fun mergeBilingualLines(lines: List<LrcLine>): List<LrcLine> {
        if (lines.isEmpty()) return lines
        val merged = mutableListOf<LrcLine>()
        var index = 0
        while (index < lines.size) {
            val timestamp = lines[index].timestampMs
            val group = mutableListOf<LrcLine>()
            while (index < lines.size && lines[index].timestampMs == timestamp) {
                group.add(lines[index])
                index++
            }
            merged.add(mergeTimestampGroup(group))
        }
        return merged
    }

    private fun mergeTimestampGroup(group: List<LrcLine>): LrcLine {
        if (group.size == 1) {
            val split = LyricsTextHelper.splitBilingualLine(group[0].text)
            return group[0].copy(text = split.primary, translation = split.translation)
        }
        val englishLine = group.firstOrNull { LyricsTextHelper.isPrimarilyEnglish(it.text) }
        val chineseLine = group.firstOrNull { LyricsTextHelper.isPrimarilyChinese(it.text) }
        val inlineSplit = LyricsTextHelper.splitBilingualLine(group.first().text)
        val primary = englishLine?.text ?: inlineSplit.primary
        val translation = chineseLine?.text?.takeIf { it != primary }
            ?: group.firstNotNullOfOrNull { line ->
                LyricsTextHelper.splitBilingualLine(line.text).translation
            }
            ?: inlineSplit.translation
        return LrcLine(
            timestampMs = group.first().timestampMs,
            text = primary,
            translation = translation,
        )
    }

    private fun parseMinuteMatch(match: MatchResult): LrcLine? {
        val min = match.groupValues[1].toLongOrNull() ?: return null
        val sec = match.groupValues[2].toLongOrNull() ?: return null
        val frac = match.groupValues.getOrNull(3)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
        return LrcLine(timestampMs = min * 60_000 + sec * 1_000 + frac, text = "")
    }

    private fun parseHourMatch(match: MatchResult): LrcLine? {
        val hour = match.groupValues[1].toLongOrNull() ?: return null
        val min = match.groupValues[2].toLongOrNull() ?: return null
        val sec = match.groupValues[3].toLongOrNull() ?: return null
        val frac = match.groupValues.getOrNull(4)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
        return LrcLine(
            timestampMs = hour * 3_600_000 + min * 60_000 + sec * 1_000 + frac,
            text = "",
        )
    }
}
