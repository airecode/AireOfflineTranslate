package com.example.myapplication.translate

import java.util.Locale

/**
 * A language the app can listen for, translate to, and speak.
 *
 * [tag] is a BCP-47 tag and is what both the speech recogniser and the TTS engine key off.
 * [promptName] is the English name handed to Gemma in the translation prompt — models follow
 * plain English language names far more reliably than they follow locale codes.
 */
data class Language(
    val tag: String,
    val name: String,
    /**
     * The language's own name in its own script. Shown instead of a country: someone looking for
     * their language recognises 日本語 or ไทย far faster than they recognise "Japan" or "Thailand",
     * and it disambiguates the two Chinese scripts without needing a region at all.
     */
    val nativeName: String,
    val promptName: String,
    /**
     * Idle prompt shown on this side's panel, in this language. Each person is looking at their
     * own half of the screen, so the placeholder should be in a language they can read.
     */
    val readyPrompt: String,
    /**
     * Label for the compact language chip, which shows one line only. Defaults to [name]; set
     * explicitly where the English name alone would be ambiguous.
     */
    val shortName: String = name,
) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

object Languages {
    val ENGLISH = Language("en-US", "English", "English", "English", "Ready to translate")
    val JAPANESE = Language("ja-JP", "Japanese", "日本語", "Japanese", "翻訳の準備完了")
    val CHINESE = Language(
        "zh-CN", "Chinese", "简体中文", "Simplified Chinese", "准备翻译",
        shortName = "Chinese 简体",
    )
    val CHINESE_TRADITIONAL = Language(
        "zh-TW", "Chinese", "繁體中文", "Traditional Chinese", "準備翻譯",
        shortName = "Chinese 繁體",
    )
    val KOREAN = Language("ko-KR", "Korean", "한국어", "Korean", "번역 준비 완료")
    val SPANISH = Language("es-ES", "Spanish", "Español", "Spanish", "Listo para traducir")
    val FRENCH = Language("fr-FR", "French", "Français", "French", "Prêt à traduire")
    val GERMAN = Language("de-DE", "German", "Deutsch", "German", "Bereit zum Übersetzen")
    val MALAY = Language("ms-MY", "Malay", "Bahasa Melayu", "Malay", "Sedia untuk menterjemah")
    val ARABIC = Language("ar-SA", "Arabic", "العربية", "Arabic", "جاهز للترجمة")
    val VIETNAMESE = Language("vi-VN", "Vietnamese", "Tiếng Việt", "Vietnamese", "Sẵn sàng dịch")
    val FILIPINO = Language("fil-PH", "Filipino", "Filipino", "Filipino", "Handa nang magsalin")
    val HEBREW = Language("he-IL", "Hebrew", "עברית", "Hebrew", "מוכן לתרגום")
    val HINDI = Language("hi-IN", "Hindi", "हिन्दी", "Hindi", "अनुवाद के लिए तैयार")
    val TURKISH = Language("tr-TR", "Turkish", "Türkçe", "Turkish", "Çeviriye hazır")
    val TAMIL = Language("ta-IN", "Tamil", "தமிழ்", "Tamil", "மொழிபெயர்க்கத் தயார்")
    val THAI = Language("th-TH", "Thai", "ไทย", "Thai", "พร้อมแปล")
    val RUSSIAN = Language("ru-RU", "Russian", "Русский", "Russian", "Готов к переводу")

    val ALL = listOf(
        ENGLISH, ARABIC, CHINESE, CHINESE_TRADITIONAL, FILIPINO,
        FRENCH, GERMAN, HEBREW, HINDI, JAPANESE,
        KOREAN, MALAY, RUSSIAN, SPANISH, TAMIL,
        THAI, TURKISH, VIETNAMESE,
    )

    /**
     * Resolves a persisted BCP-47 tag. Returns null for anything unrecognised, so a language
     * dropped from a later build falls back to the default rather than crashing on restore.
     */
    fun fromTag(tag: String?): Language? = ALL.firstOrNull { it.tag == tag }
}
