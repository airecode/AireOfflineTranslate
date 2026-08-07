package com.example.myapplication.translate.translator

/**
 * The Gemma builds the app can run.
 *
 * E4B is the better translator; E2B is the one that fits on a phone that cannot spare 3.7 GB of
 * RAM. Offering both is the difference between supporting flagships only and supporting the
 * mid-range, so the choice is the user's rather than ours.
 *
 * Every URL below was verified to return 200 for its file. As with a single model, the four
 * sources are not independent origins — they all terminate at the same Hugging Face CDN — so they
 * cover a blocked domain, a failed DNS lookup or a moved branch, not an outage at the source.
 */
enum class ModelVariant(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    private val repo: String,
    private val pinnedRevision: String,
) {
    // Declaration order is the order shown in the model manager. E2B comes first because it is
    // the sensible default: a gigabyte smaller to download, and it runs on phones that cannot
    // spare the memory E4B needs.
    E2B(
        id = "e2b",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        pinnedRevision = "9262660a1676eed6d0c477ab1a86344430854664",
    ),
    E4B(
        id = "e4b",
        displayName = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        repo = "litert-community/gemma-4-E4B-it-litert-lm",
        pinnedRevision = "f7ad3343bd6ebc9607f4dc3bc4f2398bd5749bc5",
    );

    /** Approximate size for display, e.g. "3.66 GB". */
    val sizeGb: String get() = String.format(java.util.Locale.US, "%.2f GB", sizeBytes / 1_000_000_000.0)

    val downloadUrls: List<String>
        get() = listOf(
            "https://huggingface.co/$repo/resolve/main/$fileName",
            "https://hf.co/$repo/resolve/main/$fileName",
            "https://hf-mirror.com/$repo/resolve/main/$fileName",
            "https://huggingface.co/$repo/resolve/$pinnedRevision/$fileName",
        )

    companion object {
        /**
         * Only applies to a fresh install. Anyone who has already chosen a variant keeps it —
         * [fromId] resolves their stored preference before this is consulted.
         */
        val DEFAULT = E2B

        fun fromId(id: String?): ModelVariant = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
