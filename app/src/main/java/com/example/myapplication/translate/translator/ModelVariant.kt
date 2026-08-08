package com.example.myapplication.translate.translator

/**
 * The Gemma builds the app can run.
 *
 * Only E2B is supported. It is the build that fits on a phone that cannot spare 3.7 GB of RAM,
 * which is most of them; supporting the larger E4B as well meant a second multi-gigabyte download
 * that the majority of devices could not load anyway.
 *
 * This stays an enum rather than collapsing into constants because [ModelLocation] and
 * [ModelDownloader] are keyed on it, and because a second build may well be worth offering again.
 *
 * Every URL below was verified to return 200 for its file. The four sources are not independent
 * origins — they all terminate at the same Hugging Face CDN — so they cover a blocked domain, a
 * failed DNS lookup or a moved branch, not an outage at the source.
 */
enum class ModelVariant(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    private val repo: String,
    private val pinnedRevision: String,
) {
    E2B(
        id = "e2b",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        pinnedRevision = "9262660a1676eed6d0c477ab1a86344430854664",
    );

    /** Approximate size for display, e.g. "2.59 GB". */
    val sizeGb: String get() = String.format(java.util.Locale.US, "%.2f GB", sizeBytes / 1_000_000_000.0)

    val downloadUrls: List<String>
        get() = listOf(
            "https://huggingface.co/$repo/resolve/main/$fileName",
            "https://hf.co/$repo/resolve/main/$fileName",
            "https://hf-mirror.com/$repo/resolve/main/$fileName",
            "https://huggingface.co/$repo/resolve/$pinnedRevision/$fileName",
        )

    companion object {
        val DEFAULT = E2B

        /**
         * Falls back to [DEFAULT] for anything unrecognised, which is what carries a preference
         * left behind by a build that offered E4B.
         */
        fun fromId(id: String?): ModelVariant = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
