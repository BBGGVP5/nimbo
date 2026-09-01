package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Значения взяты один в один из андроидной темы (`ui/theme/Color.kt` и
 * `getNebulaColors` для тёмной темы с акцентом по умолчанию), чтобы iOS не
 * расходился с Android по цвету.
 */
/**
 * Стили интерфейса — те же, что на Android, с теми же названиями. Ключи
 * совпадают с андроидными индексами `ElementStyleMode`, поэтому смысл
 * сохранённого значения на обеих платформах одинаковый.
 */
internal enum class NimboElementStyle(
    val key: String,
    val title: String,
    val subtitle: String,
    val cornerScale: Float
) {
    NIMBO_GLASS("glass", "Nimbo Glass", "iOS Liquid Glass", 1f),
    MATERIAL_YOU("material", "Material You", "Expressive", 1f),
    DOTTED("dotted", "Dotted", "Точечная сетка", 0.34f),
    SIGNAL("signal", "Signal", "Приборная панель", 0.75f),

    /**
     * Чернильные панели комикса: бумага, толстый контур, прямые углы и жёсткая
     * тень со смещением. Идёт после Signal — там же, где на Android.
     */
    MANGA("manga", "Manga", "Чернильные панели", 0.12f);

    companion object {
        fun fromKey(value: String): NimboElementStyle =
            entries.firstOrNull { it.key == value } ?: NIMBO_GLASS
    }
}

internal val LocalNimboElementStyle = staticCompositionLocalOf { NimboElementStyle.NIMBO_GLASS }

@Composable
internal fun nimboStyledShape(defaultRadius: Dp, mangaRadius: Dp = 3.dp): RoundedCornerShape {
    val style = LocalNimboElementStyle.current
    return RoundedCornerShape(if (style == NimboElementStyle.MANGA) mangaRadius else defaultRadius * style.cornerScale)
}

@Composable
internal fun nimboStyledContainer(default: Color, selected: Boolean = false): Color =
    if (LocalNimboElementStyle.current == NimboElementStyle.MANGA) {
        if (selected) NimboPalette.Accent.copy(alpha = 0.14f) else NimboMangaPalette.Paper
    } else default

@Composable
internal fun nimboStyledBorder(default: Color, selected: Boolean = false): Color =
    if (LocalNimboElementStyle.current == NimboElementStyle.MANGA) {
        if (selected) NimboPalette.Accent else NimboMangaPalette.Ink
    } else default

/** Бумага и чернила стиля Manga: те же цвета, что в андроидной теме. */
internal object NimboMangaPalette {
    // Тёплый тон вместо синевы: холодная бумага читается как погашенный
    // экран, а не как страница.
    val Paper = Color(0xFF1B1814)
    val PaperDeep = Color(0xFF15130F)
    val Ink = Color(0xFFF4EEDF)
    val Accent = Color(0xFFE63329)
}

internal object NimboPalette {
    val Background = Color(0xFF091321)
    val BackgroundDeep = Color(0xFF080F1C)
    val Surface = Color(0xFF101D31)
    val SurfaceStrong = Color(0xFF14243A)
    val Control = Color(0x09FFFFFF)
    val Soft = Color(0x14FFFFFF)
    val Border = Color(0x13FFFFFF)
    val Hairline = Color(0x13FFFFFF)
    val Accent = Color(0xFF75A7FF)
    val AccentStrong = Color(0xFF4E8CFF)
    val Text = Color(0xFFEAEBF2)
    val TextSecondary = Color(0xA8EAEBF2)
    val TextTertiary = Color(0x6BEAEBF2)
    val Green = Color(0xFF5DD9A1)
    val Amber = Color(0xFFE2A75F)
    val Red = Color(0xFFFF7B7B)
}

internal val NimboTitleStyle = TextStyle(
    color = NimboPalette.Text,
    fontSize = 28.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.ExtraBold
)

internal val NimboSectionTitleStyle = TextStyle(
    color = NimboPalette.Text,
    fontSize = 21.sp,
    lineHeight = 25.sp,
    fontWeight = FontWeight.Bold
)

internal val NimboBodyStyle = TextStyle(
    color = NimboPalette.TextSecondary,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Medium
)

@Composable
internal fun NimboSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    strong: Boolean = false,
    padding: PaddingValues = PaddingValues(15.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val style = LocalNimboElementStyle.current
    val shape = RoundedCornerShape(cornerRadius * style.cornerScale)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            // Тот же стеклянный материал, что и на Android, а не самодельная
            // карточка с градиентом: он лежит в общем модуле.
            .then(
                when (style) {
                    // Стекло: тонировка, блик и ободок — как у системных
                    // материалов iOS.
                    NimboElementStyle.NIMBO_GLASS -> Modifier.nimboGlassSurface(
                        shape = shape,
                        depth = if (strong) LiquidGlassDepth.FLOATING else LiquidGlassDepth.PANEL,
                        accent = NimboPalette.Accent,
                        isDark = true,
                        panelAlpha = 1f
                    )
                    // Material You: плотная тональная поверхность, подкрашенная
                    // акцентом, и никаких волосяных границ.
                    NimboElementStyle.MATERIAL_YOU -> Modifier
                        .clip(shape)
                        .background(
                            NimboPalette.Accent
                                .copy(alpha = if (strong) 0.20f else 0.13f)
                                .compositeOver(NimboPalette.Surface)
                        )
                    // Dotted: почти квадратная панель, точечная сетка внутри и
                    // контур из отдельных точек.
                    NimboElementStyle.DOTTED -> Modifier
                        .clip(shape)
                        .background(NimboPalette.Surface.copy(alpha = 0.94f))
                        .nimboDotPattern(
                            NimboPalette.Text,
                            spacing = 11.dp,
                            radius = 0.72.dp,
                            alpha = 0.12f
                        )
                        .nimboDottedOutline(
                            NimboPalette.Accent.copy(alpha = 0.75f),
                            cornerRadius = cornerRadius * style.cornerScale
                        )
                    // Signal: ровная подложка приборной панели и одна волосяная
                    // линия по краю — глубину даёт она, а не подсветка.
                    NimboElementStyle.SIGNAL -> Modifier
                        .clip(shape)
                        .background(NimboPalette.Text.copy(alpha = 0.02f).compositeOver(NimboPalette.Background))
                        .border(1.dp, NimboPalette.Text.copy(alpha = 0.075f), shape)
                    // Manga: бумага под чернилами. Контур вдвое толще обычного —
                    // без него панель перестаёт читаться как нарисованная.
                    NimboElementStyle.MANGA -> Modifier
                        .clip(shape)
                        .background(NimboMangaPalette.Paper)
                        .border(2.dp, NimboMangaPalette.Ink, shape)
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(padding),
        content = content
    )
}

internal enum class NimboIconName {
    HOME, PROFILES, APPS, SETTINGS, ADD, SEARCH, REFRESH, MORE, LIST,
    CLOUD, POWER, ROUTE, CONNECTION, NOTIFICATIONS, STATS, SYNC, LOGS,
    SUPPORT, SITE, SECURITY, INFO, DELETE, DOWNLOAD, FAVORITE, FAVORITE_OFF,
    PING, BACK
}

private fun iconVector(name: NimboIconName, selected: Boolean): ImageVector = when (name) {
    NimboIconName.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    NimboIconName.PROFILES -> if (selected) Icons.Filled.Public else Icons.Outlined.Public
    NimboIconName.APPS -> if (selected) Icons.Filled.Route else Icons.Filled.Route
    NimboIconName.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    NimboIconName.ADD -> Icons.Filled.Add
    NimboIconName.SEARCH -> Icons.Filled.Search
    NimboIconName.REFRESH -> Icons.Filled.Refresh
    NimboIconName.MORE -> Icons.Filled.MoreVert
    NimboIconName.LIST -> Icons.Filled.List
    NimboIconName.CLOUD -> Icons.Filled.Cloud
    NimboIconName.POWER -> Icons.Filled.PowerSettingsNew
    NimboIconName.ROUTE -> Icons.Filled.Route
    NimboIconName.CONNECTION -> Icons.Filled.VpnKey
    NimboIconName.NOTIFICATIONS -> Icons.Filled.Notifications
    NimboIconName.STATS -> Icons.Filled.BarChart
    NimboIconName.SYNC -> Icons.Filled.Sync
    NimboIconName.LOGS -> Icons.Filled.Description
    // «Поддержка» — это помощь, а не замок: щит читался как безопасность.
    NimboIconName.SUPPORT -> Icons.Filled.SupportAgent
    NimboIconName.SITE -> Icons.Filled.Language
    NimboIconName.SECURITY -> Icons.Filled.Security
    NimboIconName.INFO -> Icons.Filled.Info
    NimboIconName.DELETE -> Icons.Filled.Delete
    NimboIconName.DOWNLOAD -> Icons.Filled.CloudDownload
    NimboIconName.FAVORITE -> Icons.Filled.Favorite
    NimboIconName.FAVORITE_OFF -> Icons.Filled.FavoriteBorder
    NimboIconName.PING -> Icons.Filled.Speed
    NimboIconName.BACK -> Icons.AutoMirrored.Filled.ArrowBack
}

@Composable
internal fun NimboIcon(
    name: NimboIconName,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = NimboPalette.Text,
    selected: Boolean = false
) {
    Icon(
        imageVector = iconVector(name, selected),
        contentDescription = null,
        modifier = modifier,
        tint = tint
    )
}

@Composable
internal fun NimboIconButton(
    name: NimboIconName,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(18.dp, 2.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                nimboStyledContainer(
                    if (selected) NimboPalette.Accent.copy(alpha = 0.20f) else NimboPalette.Control,
                    selected
                )
            )
            .border(
                if (style == NimboElementStyle.MANGA) if (selected) 2.dp else 1.5.dp else 1.dp,
                nimboStyledBorder(
                    if (selected) NimboPalette.Accent.copy(alpha = 0.72f) else NimboPalette.Border,
                    selected
                ),
                shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        NimboIcon(
            name = name,
            tint = if (selected) NimboPalette.Accent else NimboPalette.Text,
            selected = selected
        )
    }
}

/** Ряд без подсветки нажатия — так же ведут себя ряды настроек на Android. */
@Composable
internal fun Modifier.nimboRowClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * Имя сервера без флага-эмодзи впереди.
 *
 * Compose на iOS рисует цветные эмодзи не всегда: в списке вместо флага
 * появлялись пустые прямоугольники. Страну и так видно по названию.
 */
internal fun withoutFlagEmoji(name: String): String {
    val trimmed = name.trimStart()
    var index = 0
    while (index + 3 < trimmed.length) {
        val isPair = trimmed[index] == '\uD83C' &&
            trimmed[index + 1] in '\uDDE6'..'\uDDFF' &&
            trimmed[index + 2] == '\uD83C' &&
            trimmed[index + 3] in '\uDDE6'..'\uDDFF'
        if (!isPair) break
        index += 4
    }
    return trimmed.substring(index).trimStart()
}

@Composable
internal fun NimboLinkButton(
    icon: NimboIconName,
    label: String,
    onClick: () -> Unit
) {
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(12.dp, 2.dp)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(nimboStyledContainer(NimboPalette.Accent.copy(alpha = 0.10f)))
            .border(
                if (style == NimboElementStyle.MANGA) 1.5.dp else 1.dp,
                nimboStyledBorder(NimboPalette.Accent.copy(alpha = 0.55f)),
                shape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NimboIcon(icon, tint = NimboPalette.Text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = NimboPalette.Text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
    }
}

/**
 * Плашка со значком: та же геометрия, что и у [NimboPill], но слева стоит
 * настоящая иконка. Раньше её роль играли символы вроде «◉», и на экране это
 * читалось как случайные знаки.
 */
@Composable
internal fun NimboIconPill(
    icon: NimboIconName,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(18.dp, 2.dp)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(shape)
            .background(nimboStyledContainer(NimboPalette.Control))
            .border(
                if (style == NimboElementStyle.MANGA) 1.5.dp else 1.dp,
                nimboStyledBorder(NimboPalette.Hairline),
                shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NimboIcon(icon, tint = NimboPalette.Accent, modifier = Modifier.size(15.dp))
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = NimboBodyStyle.copy(color = NimboPalette.Text, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
internal fun NimboPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val style = LocalNimboElementStyle.current
    val shape = nimboStyledShape(18.dp, 2.dp)
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(
                nimboStyledContainer(
                    if (selected) NimboPalette.Accent.copy(alpha = 0.22f) else NimboPalette.Control,
                    selected
                )
            )
            .border(
                if (style == NimboElementStyle.MANGA) if (selected) 2.dp else 1.5.dp else 1.dp,
                nimboStyledBorder(
                    if (selected) NimboPalette.Accent.copy(alpha = 0.85f) else NimboPalette.Hairline,
                    selected
                ),
                shape
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
        style = TextStyle(
            color = if (selected) NimboPalette.Accent else NimboPalette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
internal fun NimboToggle(
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    val style = LocalNimboElementStyle.current
    if (style == NimboElementStyle.MANGA) {
        val shape = RoundedCornerShape(2.dp)
        val thumbShape = RoundedCornerShape(1.dp)
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(shape)
                .background(if (checked) NimboPalette.Accent.copy(alpha = 0.34f) else NimboMangaPalette.Paper)
                .border(2.dp, if (checked) NimboPalette.Accent else NimboMangaPalette.Ink, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null
                ) { onChange(!checked) },
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(thumbShape)
                    .background(if (enabled) NimboMangaPalette.Ink else NimboMangaPalette.Ink.copy(alpha = 0.4f))
            )
        }
    } else {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NimboPalette.Accent,
                uncheckedThumbColor = NimboPalette.TextSecondary,
                uncheckedTrackColor = NimboPalette.Control,
                uncheckedBorderColor = NimboPalette.Border
            )
        )
    }
}

internal fun Modifier.nimboScreenPadding(): Modifier =
    fillMaxWidth().padding(horizontal = 20.dp)
