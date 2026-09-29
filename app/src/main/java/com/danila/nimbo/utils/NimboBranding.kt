package com.danila.nimbo.utils

/** Branding-only revision: never replaces an imported user image or resets saved icon choices. */
internal object NimboBranding {
    const val REVISION = "cloud-2026-09-23"
    const val BACKGROUND = 0xFF101010.toInt()
    const val CLOUD_PATH = "M329 728C244 728 184 669 184 587C184 507 254 429 344 428C370 350 440 296 526 296C620 296 682 357 694 454C777 452 840 512 840 591C840 624 830 650 809 657C769 672 673 639 630 601C603 577 590 548 593 512C569 531 570 569 588 600C624 663 687 704 764 706C740 722 710 728 681 728Z"

    fun shouldRefreshGeneratedIcon(hasBitmap: Boolean, usesImportedImage: Boolean, revision: String?): Boolean =
        !hasBitmap || (!usesImportedImage && revision != REVISION)
}
