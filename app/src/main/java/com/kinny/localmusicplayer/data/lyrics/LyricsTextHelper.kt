package com.kinny.localmusicplayer.data.lyrics

/**
 * Description: 双语歌词文本处理，识别英文主歌词与中文翻译
 * Author: kinny
 * Created: 2026/8/5 16:32
 */
object LyricsTextHelper {

    private val CJK_REGEX = Regex("""[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]""")
    private val LATIN_REGEX = Regex("""[A-Za-z]""")

    data class BilingualSplit(
        val primary: String,
        val translation: String?,
    )

    fun containsCjk(text: String): Boolean = CJK_REGEX.containsMatchIn(text)

    fun containsLatin(text: String): Boolean = LATIN_REGEX.containsMatchIn(text)

    /** 以拉丁字母为主（英文歌词行） */
    fun isPrimarilyEnglish(text: String): Boolean {
        val latin = LATIN_REGEX.findAll(text).count()
        val cjk = CJK_REGEX.findAll(text).count()
        return latin > 0 && latin >= cjk
    }

    /** 以中日韩字符为主（翻译行） */
    fun isPrimarilyChinese(text: String): Boolean {
        val cjk = CJK_REGEX.findAll(text).count()
        val latin = LATIN_REGEX.findAll(text).count()
        return cjk > 0 && cjk > latin
    }

    /** 从单行文本中拆分「英文在前、中文在后」的双语内容 */
    fun splitBilingualLine(text: String): BilingualSplit {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return BilingualSplit("", null)

        listOf(" / ", " | ", " \\ ", "／", "｜").forEach { separator ->
            if (separator in trimmed) {
                val parts = trimmed.split(separator, limit = 2).map { it.trim() }
                if (parts.size == 2) {
                    return pickPrimaryAndTranslation(parts[0], parts[1])
                }
            }
        }

        Regex("""^(.+?)\s*[(（]([^)）]+)[)）]\s*$""").find(trimmed)?.let { match ->
            return pickPrimaryAndTranslation(
                match.groupValues[1].trim(),
                match.groupValues[2].trim(),
            )
        }

        return BilingualSplit(trimmed, null)
    }

    private fun pickPrimaryAndTranslation(first: String, second: String): BilingualSplit {
        return when {
            isPrimarilyEnglish(first) && isPrimarilyChinese(second) -> BilingualSplit(first, second)
            isPrimarilyChinese(first) && isPrimarilyEnglish(second) -> BilingualSplit(second, first)
            isPrimarilyEnglish(first) -> BilingualSplit(first, second.takeIf { isPrimarilyChinese(it) })
            isPrimarilyEnglish(second) -> BilingualSplit(second, first.takeIf { isPrimarilyChinese(it) })
            else -> BilingualSplit(first, second.takeIf { it.isNotBlank() && it != first })
        }
    }
}
