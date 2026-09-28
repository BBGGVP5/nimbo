package com.danila.nimbo.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import com.danila.nimbo.shared.resources.*

/** Full Cyrillic/Latin TTFs from the approved preview, bundled for offline rendering. */
internal object NimboTypography {
    val body: FontFamily @Composable get() = nimboPlatformFontFamily(false)
    val heading: FontFamily @Composable get() = nimboPlatformFontFamily(true)
}

@Composable
internal expect fun nimboPlatformFontFamily(heading: Boolean): FontFamily

/** Android/desktop package these via Compose resources. iOS shares the native app TTFs. */
internal object NimboResourceTypography {
    val body: FontFamily
        @Composable get() = FontFamily(
            Font(Res.font.golos_regular, FontWeight.Normal),
            Font(Res.font.golos_medium, FontWeight.Medium),
            Font(Res.font.golos_semibold, FontWeight.SemiBold),
            Font(Res.font.golos_bold, FontWeight.Bold)
        )
    val heading: FontFamily
        @Composable get() = FontFamily(
            Font(Res.font.manrope_regular, FontWeight.Normal),
            Font(Res.font.manrope_medium, FontWeight.Medium),
            Font(Res.font.manrope_semibold, FontWeight.SemiBold),
            Font(Res.font.manrope_bold, FontWeight.Bold),
            Font(Res.font.manrope_extrabold, FontWeight.ExtraBold)
        )
}
