package com.danila.nimbo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
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
    return RoundedCornerShape((if (style == NimboElementStyle.MANGA) mangaRadius else defaultRadius * style.cornerScale) * LocalNimboAppearance.current.corners)
}

@Composable
internal fun nimboStyledContainer(default: Color, selected: Boolean = false): Color =
    if (LocalNimboElementStyle.current == NimboElementStyle.MANGA) {
        if (selected) NimboPalette.Accent.copy(alpha = 0.14f).compositeOver(NimboMangaPalette.Paper) else NimboMangaPalette.Paper
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
    val Paper: Color @Composable get() = LocalNimboColors.current.paper
    val PaperDeep: Color @Composable get() = LocalNimboColors.current.paperDeep
    val Ink: Color @Composable get() = LocalNimboColors.current.ink
    val Accent = Color(0xFFE63329)
}

internal object NimboPalette {
    val Background: Color @Composable get() = LocalNimboColors.current.background
    val BackgroundDeep: Color @Composable get() = LocalNimboColors.current.backgroundDeep
    val Surface: Color @Composable get() = LocalNimboColors.current.surface
    val SurfaceStrong: Color @Composable get() = LocalNimboColors.current.surfaceStrong
    val Control: Color @Composable get() = LocalNimboColors.current.control
    val Soft: Color @Composable get() = LocalNimboColors.current.soft
    val Border: Color @Composable get() = LocalNimboColors.current.border
    val Hairline: Color @Composable get() = LocalNimboColors.current.border
    val Accent: Color @Composable get() = LocalNimboColors.current.accent
    val AccentStrong: Color @Composable get() = LocalNimboColors.current.accent
    val Text: Color @Composable get() = LocalNimboColors.current.text
    val TextSecondary: Color @Composable get() = LocalNimboColors.current.text.copy(alpha = 0.72f)
    val TextTertiary: Color @Composable get() = LocalNimboColors.current.text.copy(alpha = 0.55f)
    val Green: Color @Composable get() = LocalNimboColors.current.green
    val Amber: Color @Composable get() = LocalNimboColors.current.amber
    val Red: Color @Composable get() = LocalNimboColors.current.red
}

internal val NimboTitleStyle: TextStyle @Composable get() = TextStyle(
    color = NimboPalette.Text,
    fontSize = 28.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.4).sp
)

internal val NimboSectionTitleStyle: TextStyle @Composable get() = TextStyle(
    color = NimboPalette.Text,
    fontSize = 19.sp,
    lineHeight = 25.sp,
    fontWeight = FontWeight.SemiBold
)

internal val NimboBodyStyle: TextStyle @Composable get() = TextStyle(
    color = NimboPalette.TextSecondary,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Normal
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
    val appearance = LocalNimboAppearance.current
    val shape = nimboStyledShape(cornerRadius)
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
                        isDark = LocalNimboDark.current,
                        panelAlpha = (1f - appearance.transparency * 0.65f),
                        refractionEnabled = appearance.refraction,
                        brightness = appearance.brightness
                    )
                    // Material You: плотная тональная поверхность, подкрашенная
                    // акцентом, и никаких волосяных границ.
                    NimboElementStyle.MATERIAL_YOU -> Modifier
                        .clip(shape)
                        .background(
                            NimboPalette.Accent
                                .copy(alpha = if (strong) 0.12f else 0.045f)
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
                            alpha = 0.055f
                        )
                        .nimboDottedOutline(
                            NimboPalette.Text.copy(alpha = 0.36f),
                            cornerRadius = cornerRadius * style.cornerScale * appearance.corners
                        )
                    // Signal: ровная подложка приборной панели и одна волосяная
                    // линия по краю — глубину даёт она, а не подсветка.
                    NimboElementStyle.SIGNAL -> Modifier
                        .clip(shape)
                        .background(NimboPalette.Text.copy(alpha = 0.02f).compositeOver(NimboPalette.Background))
                        .border(1.dp, NimboPalette.Text.copy(alpha = 0.075f), shape)
                    // Manga: бумага под чернилами. Контур вдвое толще обычного —
                    // без него панель перестаёт читаться как нарисованная, — а
                    // жёсткая тень со смещением превращает панель в кадр: на
                    // компьютере именно она отличает стиль от «просто рамки».
                    NimboElementStyle.MANGA -> Modifier
                        .nimboInkShadow(shape, offset = if (strong) 4.dp else 2.dp)
                        .clip(shape)
                        .background(NimboMangaPalette.Paper)
                        .border(if (strong) 2.dp else 1.5.dp, NimboMangaPalette.Ink, shape)
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

/**
 * Жёсткая тень кадра: сплошная заливка со смещением, без размытия.
 *
 * Размытая тень — примета глянцевого интерфейса; в комиксе панель отбрасывает
 * ровный чернильный прямоугольник, и именно он читается как «нарисовано».
 */
@Composable
internal fun Modifier.nimboInkShadow(
    shape: RoundedCornerShape,
    offset: Dp,
    color: Color = NimboMangaPalette.Ink
): Modifier = drawBehind {
    val shift = offset.toPx()
    val outline = shape.createOutline(
        androidx.compose.ui.geometry.Size(size.width, size.height),
        layoutDirection,
        this
    )
    translate(left = shift, top = shift) {
        drawOutline(outline, color = color)
    }
}

/** Красная косая засечка перед заголовком — та же, что на компьютере. */
@Composable
internal fun NimboMangaSlash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(end = 8.dp)
            .width(5.dp)
            .height(16.dp)
            .graphicsLayer {
                // Косой срез: прямая полоска выглядит разделителем, а не
                // засечкой заголовка.
                rotationZ = 0f
                shape = ParallelogramShape
                clip = true
            }
            .background(NimboMangaPalette.Accent)
    )
}

/** Скос в 12 градусов: столько же, сколько у засечки на компьютере. */
private val ParallelogramShape = GenericShape { size, _ ->
    val slant = size.height * 0.21f
    moveTo(slant, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - slant, size.height)
    lineTo(0f, size.height)
    close()
}

internal enum class NimboIconName {
    HOME, PROFILES, APPS, SETTINGS, ADD, SEARCH, REFRESH, MORE, LIST,
    CLOUD, POWER, ROUTE, CONNECTION, NOTIFICATIONS, STATS, SYNC, LOGS,
    SUPPORT, SITE, SECURITY, INFO, DELETE, DOWNLOAD, FAVORITE, FAVORITE_OFF,
    PING, BACK, PALETTE, SAVE, COPY, SHARE, EDIT
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
    NimboIconName.SAVE -> Icons.Filled.Check
    NimboIconName.COPY -> Icons.Filled.ContentCopy
    NimboIconName.SHARE -> Icons.Filled.IosShare
    NimboIconName.EDIT -> Icons.Filled.Edit
    NimboIconName.DOWNLOAD -> Icons.Filled.CloudDownload
    NimboIconName.FAVORITE -> Icons.Filled.Favorite
    NimboIconName.FAVORITE_OFF -> Icons.Filled.FavoriteBorder
    NimboIconName.PING -> Icons.Filled.Speed
    NimboIconName.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    NimboIconName.PALETTE -> Icons.Filled.Palette
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
            .nimboControlSurface(shape, accented = selected)
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
    val haptic = LocalHapticFeedback.current
    val enabled = LocalNimboAppearance.current.haptics
    return this.clickable(interactionSource = interaction, indication = null) {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
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
            .nimboControlSurface(shape)
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
 * Поверхность мелкого элемента: поля ввода, плитки, ярлыки.
 *
 * В стиле «жидкого стекла» это настоящее стекло, а не плоская заливка: рядом
 * со стеклянными карточками плоский прямоугольник читается как вставка из
 * другого приложения. В Manga — бумага с чернильным контуром, в остальных —
 * прежняя заливка.
 */
@Composable
internal fun Modifier.nimboControlSurface(
    shape: RoundedCornerShape,
    accented: Boolean = false
): Modifier {
    val style = LocalNimboElementStyle.current
    return when (style) {
        NimboElementStyle.NIMBO_GLASS -> this.nimboGlassSurface(
            shape = shape,
            depth = LiquidGlassDepth.CONTROL,
            accent = NimboPalette.Accent,
            isDark = LocalNimboDark.current,
            panelAlpha = 1f - LocalNimboAppearance.current.transparency * 0.65f,
            refractionEnabled = LocalNimboAppearance.current.refraction,
            brightness = LocalNimboAppearance.current.brightness
        )
        NimboElementStyle.MANGA -> this
            .clip(shape)
            .background(nimboStyledContainer(Color.Transparent, selected = accented))
            .border(1.5.dp, NimboMangaPalette.Ink, shape)
        NimboElementStyle.MATERIAL_YOU -> this.clip(shape)
            .background(NimboPalette.Accent.copy(alpha = if (accented) 0.18f else 0.045f).compositeOver(NimboPalette.Surface))
        NimboElementStyle.DOTTED -> this.clip(shape)
            .background(NimboPalette.Surface)
            .nimboDotPattern(NimboPalette.Text, spacing = 11.dp, radius = 0.72.dp, alpha = 0.055f)
            .nimboDottedOutline(if (accented) NimboPalette.Accent else NimboPalette.Text.copy(alpha = 0.36f), cornerRadius = 6.dp * LocalNimboAppearance.current.corners)
        else -> this
            .clip(shape)
            .background(
                if (accented) NimboPalette.Accent.copy(alpha = 0.16f) else NimboPalette.Control
            )
            .border(1.dp, NimboPalette.Hairline, shape)
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
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalNimboAppearance.current.haptics
    val change: (Boolean) -> Unit = {
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChange(it)
    }
    if (style == NimboElementStyle.MANGA) {
        val shape = RoundedCornerShape(2.dp)
        val thumbShape = RoundedCornerShape(1.dp)
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(shape)
                .background(nimboStyledContainer(Color.Transparent, selected = checked))
                .border(2.dp, if (checked) NimboPalette.Accent else NimboMangaPalette.Ink, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null
                ) { change(!checked) },
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
            onCheckedChange = change,
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

/** Короткий идентификатор для новых записей: UUID в общем коде недоступен. */
internal fun nimboRandomId(): String =
    kotlin.random.Random.nextLong(100_000_000L, 999_999_999L).toString(36)

/** Вариант выпадающего списка. */
internal data class NimboDropdownOption(
    val key: String,
    val title: String,
    val subtitle: String? = null
)

/**
 * Выпадающий список в духе iOS.
 *
 * Длинный перечень вариантов, из которых обычно не меняют ни одного, занимал
 * целый экран строками с галочками. Здесь на виду только выбранное значение, а
 * список раскрывается по нажатию: он в материале включённого стиля и
 * распахивается от строки вниз, как системное меню.
 */
@Composable
internal fun NimboDropdownRow(
    title: String,
    options: List<NimboDropdownOption>,
    selectedKey: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.key == selectedKey }
    val chevron by animateFloatAsState(if (expanded) -90f else 90f, tween(220))
    val shape = nimboStyledShape(16.dp, 2.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .nimboRowClickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicText(
                    title,
                    style = TextStyle(
                        color = NimboPalette.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                if (subtitle != null) {
                    androidx.compose.foundation.text.BasicText(
                        subtitle,
                        style = NimboBodyStyle.copy(fontSize = 12.sp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.text.BasicText(
                // Пока список свёрнут, выбранное значение — единственное, что о
                // нём известно, поэтому оно стоит в самой строке.
                selected?.title ?: "—",
                style = TextStyle(
                    color = NimboPalette.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                "›",
                modifier = Modifier.graphicsLayer { rotationZ = chevron },
                style = TextStyle(color = NimboPalette.TextSecondary, fontSize = 17.sp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .nimboControlSurface(shape)
            ) {
                options.forEachIndexed { index, option ->
                    val active = option.key == selectedKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nimboRowClickable {
                                onSelect(option.key)
                                expanded = false
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            androidx.compose.foundation.text.BasicText(
                                option.title,
                                style = TextStyle(
                                    color = if (active) NimboPalette.Accent else NimboPalette.Text,
                                    fontSize = 15.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                            if (option.subtitle != null) {
                                androidx.compose.foundation.text.BasicText(
                                    option.subtitle,
                                    style = NimboBodyStyle.copy(fontSize = 12.sp)
                                )
                            }
                        }
                        if (active) {
                            androidx.compose.foundation.text.BasicText(
                                "✓",
                                style = TextStyle(
                                    color = NimboPalette.Accent,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    if (index != options.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp)
                                .height(1.dp)
                                .background(NimboPalette.Hairline)
                        )
                    }
                }
            }
        }
    }
}
