package com.danila.nimbo.ui.screens

import android.os.Build as AndroidBuild
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.components.jellyScrollAnimation
import com.danila.nimbo.ui.components.dotPatternOverlay
import com.danila.nimbo.ui.theme.*
import com.danila.nimbo.utils.AppIconManager
import com.danila.nimbo.R
import androidx.compose.ui.text.style.TextOverflow
import com.danila.nimbo.ui.components.SubscriptionBrandLogo
import com.danila.nimbo.ui.theme.LocalGlobalCornerRadius
import com.danila.nimbo.ui.LocalPreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppIconSettings: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val preferencesManager = LocalPreferencesManager.current
    val activeProfile = remember {
        val profiles = preferencesManager.loadProfiles()
        val lastUrl = preferencesManager.loadLastSelectedProfileUrl()
        profiles.find { it.url == lastUrl } ?: profiles.firstOrNull()
    }
    val themeSpec = preferencesManager.subscriptionThemeSpec

    var colorTheme by remember { mutableStateOf(preferencesManager.colorTheme) }
    var themeMode by remember { mutableIntStateOf(preferencesManager.themeMode) }
    var textScale by remember { mutableFloatStateOf(preferencesManager.textScale) }
    var isCustomAccent by remember { mutableStateOf(preferencesManager.isCustomAccent) }
    var customAccentColor by remember { mutableStateOf(Color(preferencesManager.customAccentColor)) }
    var showVersionInHeader by remember { mutableStateOf(preferencesManager.showVersionInHeader) }
    var gradientEffectsEnabled by remember { mutableStateOf(preferencesManager.gradientEffectsEnabled) }
    var glowEffectsEnabled by remember { mutableStateOf(preferencesManager.glowEffectsEnabled) }

    var customGradientCount by remember { mutableIntStateOf(preferencesManager.customGradientCount) }
    var customGradColor1 by remember { mutableStateOf(Color(preferencesManager.customGradientColor1)) }
    var customGradColor2 by remember { mutableStateOf(Color(preferencesManager.customGradientColor2)) }
    var customGradColor3 by remember { mutableStateOf(Color(preferencesManager.customGradientColor3)) }
    var useDynamicColor by remember { mutableStateOf(preferencesManager.useDynamicColor) }
    var selectedAppIcon by remember { mutableStateOf(preferencesManager.selectedAppIcon) }
    var backgroundStyle by remember { mutableIntStateOf(preferencesManager.backgroundStyle) }
    var elementStyle by remember { mutableIntStateOf(preferencesManager.elementStyle) }
    var backgroundAnimationEnabled by remember { mutableStateOf(preferencesManager.backgroundAnimationEnabled) }
    var highContrastUi by remember { mutableStateOf(preferencesManager.highContrastUi) }
    var reducedTransparency by remember { mutableStateOf(preferencesManager.reducedTransparency) }
    var splashScreenEnabled by remember { mutableStateOf(preferencesManager.splashScreenEnabled) }
    var globalBrightness by remember { mutableFloatStateOf(preferencesManager.globalBrightness) }
    var globalTransparency by remember { mutableFloatStateOf(preferencesManager.globalTransparency) }
    var globalBlur by remember { mutableFloatStateOf(preferencesManager.globalBlur) }
    var globalCorners by remember { mutableFloatStateOf(preferencesManager.globalCorners) }
    var useSubscriptionTheme by remember { mutableStateOf(preferencesManager.useSubscriptionTheme) }
    var showSubscriptionLogo by remember { mutableStateOf(preferencesManager.showSubscriptionLogo) }
    var activeGradientColorIndex by remember { mutableIntStateOf(1) }

    val systemDarkTheme = isSystemInDarkTheme()
    val colorIndex = colorTheme.mod(9)
    val isDarkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDarkTheme
    }

    LaunchedEffect(colorTheme, themeMode, textScale, isCustomAccent, customAccentColor, showVersionInHeader, gradientEffectsEnabled, glowEffectsEnabled, customGradientCount, customGradColor1, customGradColor2, customGradColor3, useDynamicColor, selectedAppIcon, backgroundStyle, elementStyle, backgroundAnimationEnabled, highContrastUi, reducedTransparency, splashScreenEnabled, globalBrightness, globalTransparency, globalBlur, globalCorners, useSubscriptionTheme, showSubscriptionLogo) {
        preferencesManager.colorTheme = if (isDarkTheme) colorIndex else colorIndex + 9
        preferencesManager.themeMode = themeMode
        preferencesManager.textScale = textScale
        preferencesManager.isCustomAccent = isCustomAccent
        preferencesManager.customAccentColor = customAccentColor.toArgb()
        preferencesManager.showVersionInHeader = showVersionInHeader
        preferencesManager.gradientEffectsEnabled = gradientEffectsEnabled
        preferencesManager.glowEffectsEnabled = glowEffectsEnabled
        preferencesManager.customGradientCount = customGradientCount
        preferencesManager.customGradientColor1 = customGradColor1.toArgb()
        preferencesManager.customGradientColor2 = customGradColor2.toArgb()
        preferencesManager.customGradientColor3 = customGradColor3.toArgb()
        preferencesManager.useDynamicColor = useDynamicColor
        preferencesManager.selectedAppIcon = selectedAppIcon
        preferencesManager.backgroundStyle = backgroundStyle
        preferencesManager.elementStyle = elementStyle
        preferencesManager.backgroundAnimationEnabled = backgroundAnimationEnabled
        preferencesManager.highContrastUi = highContrastUi
        preferencesManager.reducedTransparency = reducedTransparency
        preferencesManager.splashScreenEnabled = splashScreenEnabled
        preferencesManager.globalBrightness = globalBrightness
        preferencesManager.globalTransparency = globalTransparency
        preferencesManager.globalBlur = globalBlur
        preferencesManager.globalCorners = globalCorners
        preferencesManager.useSubscriptionTheme = useSubscriptionTheme
        preferencesManager.showSubscriptionLogo = showSubscriptionLogo
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .jellyScrollAnimation()
        ) {
            GlassHeader(
                title = "Тема",
                icon = Icons.Default.Palette,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ПРЕВЬЮ ТЕМЫ
                ThemePreviewCard(
                    isDarkTheme,
                    nebulaColors.accent,
                    if (gradientEffectsEnabled) {
                        listOf(
                            nebulaColors.primaryGradientStart,
                            nebulaColors.primaryGradientMiddle,
                            nebulaColors.primaryGradientEnd
                        )
                    } else listOf(nebulaColors.accent)
                )

                Spacer(Modifier.height(24.dp))

                // РЕЖИМ ТЕМЫ
                GlassSection(title = "Режим", icon = Icons.Default.Brightness4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeModeItem(
                            title = "Авто",
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = themeMode == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { themeMode = 0 }
                        )
                        ThemeModeItem(
                            title = "Светлая",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == 1,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                themeMode = 1
                                colorTheme = colorIndex + 9
                            }
                        )
                        ThemeModeItem(
                            title = "Тёмная",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == 2,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                themeMode = 2
                                colorTheme = colorIndex
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // АКЦЕНТНЫЙ ЦВЕТ
                GlassSection(title = "Акцентный цвет", icon = Icons.Default.ColorLens) {
                    val colors = listOf(
                        Triple(AccentPurple, "Фиолетовый", "Стильный и современный"),
                        Triple(AccentBlue, "Голубой Nimbo", "Фирменный цвет сайта"),
                        Triple(AccentGreen, "Зелёный", "Спокойный статусный"),
                        Triple(AccentRed, "Красный", "Мягкий предупреждающий"),
                        Triple(AccentOrange, "Янтарный", "Тёплый и сдержанный"),
                        Triple(AccentPink, "Лиловый", "Мягкий дополнительный"),
                        Triple(AccentCyan, "Морской", "Чистый и спокойный"),
                        Triple(AccentLime, "Графит", "Нейтральный системный"),
                        Triple(AccentSilver, "Серебро", "Премиальный стальной")
                    )

                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // СИСТЕМНЫЙ ЦВЕТ (MATERIAL YOU)
                        if (AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.S) {
                            val systemColor = if (isDarkTheme) dynamicDarkColorScheme(LocalContext.current).primary
                                             else dynamicLightColorScheme(LocalContext.current).primary
                            AccentColorItem(
                                color = systemColor,
                                name = "Системная палитра",
                                description = "Использовать цвета вашей системы (Android 12+)",
                                isSelected = useDynamicColor,
                                onClick = {
                                    useDynamicColor = true
                                    isCustomAccent = false
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = nebulaColors.onSurface.copy(alpha = 0.1f)
                            )
                        }

                        colors.forEachIndexed { index, (color, name, desc) ->
                            AccentColorItem(
                                color = color,
                                name = name,
                                description = desc,
                                isSelected = !isCustomAccent && !useDynamicColor && colorIndex == index,
                                onClick = {
                                    useDynamicColor = false
                                    isCustomAccent = false
                                    colorTheme = if (isDarkTheme) index else index + 9
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = nebulaColors.onSurface.copy(alpha = 0.1f)
                            )
                        }

                        // КАСТОМНЫЙ ЦВЕТ
                        AccentColorItem(
                            color = customGradColor1,
                            name = "Свой цвет",
                            description = "Настройте палитру и градиенты под себя",
                            isSelected = isCustomAccent,
                            onClick = {
                                useDynamicColor = false
                                isCustomAccent = true
                            }
                        )

                        AnimatedVisibility(visible = isCustomAccent) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Количество цветов в градиенте
                                Text(
                                    "Количество цветов в градиенте",
                                    color = nebulaColors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(1, 2, 3).forEach { count ->
                                        Surface(
                                            onClick = {
                                                customGradientCount = count
                                                if (activeGradientColorIndex > count) activeGradientColorIndex = 1
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (customGradientCount == count) nebulaColors.accent.copy(alpha = 0.15f) else Color.Transparent,
                                            border = BorderStroke(1.dp, if (customGradientCount == count) nebulaColors.accent else nebulaColors.onSurface.copy(alpha = 0.1f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = when(count) {
                                                        1 -> "Один"
                                                        2 -> "Два"
                                                        else -> "Три"
                                                    },
                                                    color = if (customGradientCount == count) nebulaColors.accent else nebulaColors.textTertiary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Настройка отдельного цвета (вкладки если count > 1)
                                if (customGradientCount > 1) {
                                    Text(
                                        "Настройка отдельного цвета",
                                        color = nebulaColors.textSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        (1..customGradientCount).forEach { idx ->
                                            val colorSample = when(idx) {
                                                1 -> customGradColor1
                                                2 -> customGradColor2
                                                else -> customGradColor3
                                            }
                                            Surface(
                                                onClick = { activeGradientColorIndex = idx },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (activeGradientColorIndex == idx) nebulaColors.accent.copy(alpha = 0.15f) else Color.Transparent,
                                                border = BorderStroke(1.dp, if (activeGradientColorIndex == idx) nebulaColors.accent else nebulaColors.onSurface.copy(alpha = 0.1f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(colorSample))
                                                    Text(
                                                        text = "Цвет $idx",
                                                        color = if (activeGradientColorIndex == idx) nebulaColors.accent else nebulaColors.textTertiary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Готовая палитра (Curated Solid Swatches)
                                Text(
                                    "Готовая палитра",
                                    color = nebulaColors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                                )

                                val swatchColors = listOf(
                                    Color(0xFF7C5DFA), Color(0xFF00B4D8), Color(0xFF00C853), Color(0xFFFF5252),
                                    Color(0xFFFF9800), Color(0xFFDB2777), Color(0xFF0891B2), Color(0xFF65A30D),
                                    Color(0xFFF59E0B), Color(0xFF6366F1), Color(0xFFCBD5E1), Color(0xFF1E293B)
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        swatchColors.take(6).forEach { swatch ->
                                            val isSelected = swatch == when(activeGradientColorIndex) {
                                                1 -> customGradColor1
                                                2 -> customGradColor2
                                                else -> customGradColor3
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1.2f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(swatch)
                                                    .clickable {
                                                        when(activeGradientColorIndex) {
                                                            1 -> { customGradColor1 = swatch; customAccentColor = swatch }
                                                            2 -> customGradColor2 = swatch
                                                            3 -> customGradColor3 = swatch
                                                        }
                                                    }
                                                    .border(
                                                        2.dp,
                                                        if (isSelected) Color.White else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        swatchColors.takeLast(6).forEach { swatch ->
                                            val isSelected = swatch == when(activeGradientColorIndex) {
                                                1 -> customGradColor1
                                                2 -> customGradColor2
                                                else -> customGradColor3
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1.2f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(swatch)
                                                    .clickable {
                                                        when(activeGradientColorIndex) {
                                                            1 -> { customGradColor1 = swatch; customAccentColor = swatch }
                                                            2 -> customGradColor2 = swatch
                                                            3 -> customGradColor3 = swatch
                                                        }
                                                    }
                                                    .border(
                                                        2.dp,
                                                        if (isSelected) Color.White else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Премиум пресеты градиентов
                                Text(
                                    "Премиум пресеты градиентов",
                                    color = nebulaColors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val gradientPresets = listOf(
                                        Pair("Cyber Neon", listOf(Color(0xFF7C5DFA), Color(0xFF00E5B0), Color(0xFF00D2FF))),
                                        Pair("Sunset Glow", listOf(Color(0xFFFF5252), Color(0xFFFF9800), Color(0xFFFFCC00))),
                                        Pair("Ocean Breeze", listOf(Color(0xFF00B4D8), Color(0xFF0891B2), Color(0xFFE6FBFF))),
                                        Pair("Forest Aurora", listOf(Color(0xFF00C853), Color(0xFFBEF264))),
                                        Pair("Retro Wave", listOf(Color(0xFF6366F1), Color(0xFFDB2777), Color(0xFF7C5DFA))),
                                        Pair("Midnight Glow", listOf(Color(0xFF1E293B), Color(0xFFCBD5E1)))
                                    )
                                    gradientPresets.forEach { (name, colors) ->
                                        Surface(
                                            onClick = {
                                                customGradientCount = colors.size
                                                customGradColor1 = colors[0]
                                                customAccentColor = colors[0]
                                                if (colors.size >= 2) customGradColor2 = colors[1]
                                                if (colors.size >= 3) customGradColor3 = colors[2]
                                                activeGradientColorIndex = 1
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.width(130.dp).height(50.dp),
                                            border = BorderStroke(1.dp, nebulaColors.onSurface.copy(alpha = 0.1f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Brush.linearGradient(colors))
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    name,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }

                                // Тонкая настройка Цвета
                                CustomColorPalette(
                                    label = "Тонкая настройка Цвета $activeGradientColorIndex",
                                    selectedColor = when(activeGradientColorIndex) {
                                        1 -> customGradColor1
                                        2 -> customGradColor2
                                        else -> customGradColor3
                                    },
                                    onColorSelect = {
                                        when(activeGradientColorIndex) {
                                            1 -> { customGradColor1 = it; customAccentColor = it }
                                            2 -> customGradColor2 = it
                                            3 -> customGradColor3 = it
                                        }
                                    }
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = nebulaColors.onSurface.copy(alpha = 0.1f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { gradientEffectsEnabled = !gradientEffectsEnabled }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Градиент", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    "Включить или выключить градиентные эффекты интерфейса",
                                    color = nebulaColors.textTertiary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = gradientEffectsEnabled,
                                onCheckedChange = { gradientEffectsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = nebulaColors.accent,
                                    checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = nebulaColors.onSurface.copy(alpha = 0.1f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { glowEffectsEnabled = !glowEffectsEnabled }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Эффекты свечения", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    "Включить или выключить тени и свечение у иконок",
                                    color = nebulaColors.textTertiary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = glowEffectsEnabled,
                                onCheckedChange = { glowEffectsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = nebulaColors.accent,
                                    checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // НАСТРОЙКИ ЗАГОЛОВКА
                GlassSection(title = "Интерфейс", icon = Icons.Default.Settings) {
                    Text(
                        text = "Материал элементов",
                        color = nebulaColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 2.dp)
                    )
                    VisualStyleSelector(
                        selectedStyle = elementStyle,
                        options = elementStyleOptions(),
                        onSelect = { elementStyle = it }
                    )
                    StylePreviewCard(
                        title = "Превью элементов",
                        style = elementStyle,
                        isBackgroundPreview = false
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Text(
                        text = "Стиль фона",
                        color = nebulaColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 2.dp)
                    )
                    VisualStyleSelector(
                        selectedStyle = backgroundStyle,
                        options = backgroundStyleOptions(),
                        onSelect = { backgroundStyle = it }
                    )
                    StylePreviewCard(
                        title = "Превью фона",
                        style = backgroundStyle,
                        isBackgroundPreview = true
                    )
                    Text(
                        text = "Быстрые пресеты",
                        color = nebulaColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 2.dp)
                    )
                    PresetSelector(
                        selectedBackgroundStyle = backgroundStyle,
                        selectedElementStyle = elementStyle,
                        onPresetApply = { preset ->
                            backgroundStyle = preset.backgroundStyle
                            elementStyle = preset.elementStyle
                            gradientEffectsEnabled = preset.gradientEnabled
                            glowEffectsEnabled = preset.glowEnabled
                            backgroundAnimationEnabled = preset.backgroundAnimationEnabled
                            highContrastUi = preset.highContrastEnabled
                            reducedTransparency = preset.reducedTransparencyEnabled
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { backgroundAnimationEnabled = !backgroundAnimationEnabled }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Анимация фона", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Отдельно включает/выключает движение фона", color = nebulaColors.textTertiary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = backgroundAnimationEnabled,
                            onCheckedChange = { backgroundAnimationEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = nebulaColors.accent,
                                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { highContrastUi = !highContrastUi }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Высокий контраст", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Усиливает читаемость текста и акцентов", color = nebulaColors.textTertiary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = highContrastUi,
                            onCheckedChange = { highContrastUi = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = nebulaColors.accent,
                                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reducedTransparency = !reducedTransparency }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Пониженная прозрачность", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Делает панели менее прозрачными для лучшей читаемости", color = nebulaColors.textTertiary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = reducedTransparency,
                            onCheckedChange = { reducedTransparency = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = nebulaColors.accent,
                                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Масштаб текста", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Размер текста интерфейса", color = nebulaColors.textTertiary, fontSize = 12.sp)
                            }
                            Text(
                                "${(textScale * 100).toInt()}%",
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = textScale,
                            onValueChange = { textScale = it.coerceIn(0.85f, 1.25f) },
                            valueRange = 0.85f..1.25f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = nebulaColors.accent,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textTertiary.copy(alpha = 0.22f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("85%", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("125%", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    // СЛАЙДЕР ЯРКОСТИ ИНТЕРФЕЙСА
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Яркость элементов", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Управление яркостью цветов панелей", color = nebulaColors.textTertiary, fontSize = 12.sp)
                            }
                            Text(
                                "${(globalBrightness * 100).toInt()}%",
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = globalBrightness,
                            onValueChange = { globalBrightness = it.coerceIn(0.5f, 2.0f) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = nebulaColors.accent,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textTertiary.copy(alpha = 0.22f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("50%", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("100% (Обычная)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("200%", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    // СЛАЙДЕР ПРОЗРАЧНОСТИ ЭЛЕМЕНТОВ
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Прозрачность элементов", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Коэффициент прозрачности и размытия панелей", color = nebulaColors.textTertiary, fontSize = 12.sp)
                            }
                            Text(
                                "${(globalTransparency * 100).toInt()}%",
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = globalTransparency,
                            onValueChange = { globalTransparency = it.coerceIn(0.0f, 1.0f) },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = nebulaColors.accent,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textTertiary.copy(alpha = 0.22f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0% (Матовая)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("100% (Стекло)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    // СЛАЙДЕР РАЗМЫТИЯ (BLUR RADIUS)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Сила размытия (Blur)", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Регулировка степени размытия стеклянных панелей", color = nebulaColors.textTertiary, fontSize = 12.sp)
                            }
                            Text(
                                "${globalBlur.toInt()} dp",
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = globalBlur,
                            onValueChange = { globalBlur = it.coerceIn(0.0f, 80.0f) },
                            valueRange = 0.0f..80.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = nebulaColors.accent,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textTertiary.copy(alpha = 0.22f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0 dp (Выкл)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("40 dp (Среднее)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("80 dp (Макс)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    // СЛАЙДЕР СКРУГЛЕНИЯ УГЛОВ (BLOCK CORNERS)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Интерфейс блоков (Corners)", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Множитель скругления углов кнопок и панелей", color = nebulaColors.textTertiary, fontSize = 12.sp)
                            }
                            Text(
                                String.format(java.util.Locale.US, "%.2fx", globalCorners),
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = globalCorners,
                            onValueChange = { globalCorners = it.coerceIn(0.25f, 4.0f) },
                            valueRange = 0.25f..4.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = nebulaColors.accent,
                                activeTrackColor = nebulaColors.accent,
                                inactiveTrackColor = nebulaColors.textTertiary.copy(alpha = 0.22f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.25x (Квадратные)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("1.0x (Стандарт)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                            Text("4.0x (Круглые)", color = nebulaColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVersionInHeader = !showVersionInHeader }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Версия в заголовке", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Показывать версию приложения на главном экране", color = nebulaColors.textTertiary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = showVersionInHeader,
                            onCheckedChange = { showVersionInHeader = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = nebulaColors.accent,
                                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { splashScreenEnabled = !splashScreenEnabled }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Экран загрузки", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Показывать анимацию при запуске приложения", color = nebulaColors.textTertiary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = splashScreenEnabled,
                            onCheckedChange = { splashScreenEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = nebulaColors.accent,
                                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                useSubscriptionTheme = !useSubscriptionTheme
                                if (useSubscriptionTheme) {
                                    val spec = preferencesManager.subscriptionThemeSpec
                                    val hex = spec?.split(",")?.map { it.trim() }?.firstOrNull { it.startsWith("#") }
                                    val argb = hex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                                    if (argb != null) {
                                        useDynamicColor = false
                                        isCustomAccent = true
                                        customGradColor1 = Color(argb)
                                        customGradientCount = 1
                                    }
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Тема из подписки", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Использовать акцентные цвета из активной подписки", color = nebulaColors.textTertiary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = useSubscriptionTheme,
                            onCheckedChange = { on ->
                                useSubscriptionTheme = on
                                if (on) {
                                    val spec = preferencesManager.subscriptionThemeSpec
                                    val hex = spec?.split(",")?.map { it.trim() }?.firstOrNull { it.startsWith("#") }
                                    val argb = hex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                                    if (argb != null) {
                                        useDynamicColor = false
                                        isCustomAccent = true
                                        customGradColor1 = Color(argb)
                                        customGradientCount = 1
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = nebulaColors.accent,
                                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = nebulaColors.onSurface.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSubscriptionLogo = !showSubscriptionLogo }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Логотип подписки", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Отображать логотип провайдера вместо стандартного", color = nebulaColors.textTertiary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = showSubscriptionLogo,
                            onCheckedChange = { showSubscriptionLogo = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = nebulaColors.accent,
                                checkedTrackColor = nebulaColors.accent.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                CompositionLocalProvider(LocalGlobalCornerRadius provides globalCorners) {
                    SubscriptionAppearancePreview(
                        themeSpec = themeSpec,
                        logo = activeProfile?.brandLogo,
                        cachedLogo = activeProfile?.brandLogoCache,
                        useSubscriptionTheme = useSubscriptionTheme,
                        showSubscriptionLogo = showSubscriptionLogo
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ИКОНКА ПРИЛОЖЕНИЯ
                GlassSection(title = "Иконка приложения", icon = Icons.Default.Apps) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconResourceImage(
                            resId = AppIconManager.iconPreviewByIndex(selectedAppIcon),
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(2.dp, nebulaColors.accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        )

                        Text(
                            text = AppIconManager.iconTitleByIndex(selectedAppIcon),
                            color = nebulaColors.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Откройте отдельную страницу, чтобы выбрать стиль иконки",
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(Modifier.height(14.dp))

                        Surface(
                            onClick = onNavigateToAppIconSettings,
                            shape = RoundedCornerShape(14.dp),
                            color = nebulaColors.accent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, nebulaColors.accent.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = nebulaColors.accent
                                )
                                Text(
                                    text = "Открыть выбор иконки",
                                    color = nebulaColors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }

}

@Composable
private fun VisualStyleSelector(
    selectedStyle: Int,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (styleId, title) ->
            Surface(
                onClick = { onSelect(styleId) },
                modifier = Modifier.widthIn(min = 96.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (selectedStyle == styleId) nebulaColors.accent.copy(alpha = 0.18f) else nebulaColors.onSurface.copy(alpha = 0.04f),
                border = BorderStroke(
                    1.dp,
                    if (selectedStyle == styleId) nebulaColors.accent.copy(alpha = 0.45f) else nebulaColors.onSurface.copy(alpha = 0.1f)
                )
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selectedStyle == styleId) nebulaColors.accent else nebulaColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun backgroundStyleOptions(): List<Pair<Int, String>> = listOf(
    0 to "Morphism",
    1 to "Material 3",
    2 to "Nothing Dots",
    3 to "Aurora",
    4 to "Grid",
    5 to "Mesh",
    6 to "Waves",
    7 to "Starfield",
    8 to "Cyberpunk",
    9 to "Deep Space",
    10 to "Fire",
    11 to "Lava",
    12 to "Neon",
    13 to "Nordic",
    14 to "Blossom"
)

private fun elementStyleOptions(): List<Pair<Int, String>> = listOf(
    0 to "Morphism",
    1 to "Material 3",
    2 to "Nothing Dots",
    3 to "Outlined",
    4 to "Soft Neo"
)

private data class StylePreset(
    val title: String,
    val subtitle: String,
    val backgroundStyle: Int,
    val elementStyle: Int,
    val gradientEnabled: Boolean,
    val glowEnabled: Boolean,
    val backgroundAnimationEnabled: Boolean,
    val highContrastEnabled: Boolean,
    val reducedTransparencyEnabled: Boolean
)

private fun stylePresets(): List<StylePreset> = listOf(
    StylePreset(
        title = "Neo Glass",
        subtitle = "Мягкий морфизм",
        backgroundStyle = 0,
        elementStyle = 0,
        gradientEnabled = true,
        glowEnabled = true,
        backgroundAnimationEnabled = true,
        highContrastEnabled = false,
        reducedTransparencyEnabled = false
    ),
    StylePreset(
        title = "Material You",
        subtitle = "Чистый Material 3",
        backgroundStyle = 1,
        elementStyle = 1,
        gradientEnabled = false,
        glowEnabled = false,
        backgroundAnimationEnabled = false,
        highContrastEnabled = true,
        reducedTransparencyEnabled = true
    ),
    StylePreset(
        title = "Nordic Light",
        subtitle = "Серебро + Aurora",
        backgroundStyle = 3,
        elementStyle = 4,
        gradientEnabled = true,
        glowEnabled = false,
        backgroundAnimationEnabled = true,
        highContrastEnabled = false,
        reducedTransparencyEnabled = false
    ),
    StylePreset(
        title = "Cyberpunk",
        subtitle = "Сетка + Outlined",
        backgroundStyle = 4,
        elementStyle = 3,
        gradientEnabled = true,
        glowEnabled = true,
        backgroundAnimationEnabled = true,
        highContrastEnabled = true,
        reducedTransparencyEnabled = false
    ),
    StylePreset(
        title = "Deep Ocean",
        subtitle = "Mesh + Мофизм",
        backgroundStyle = 5,
        elementStyle = 0,
        gradientEnabled = true,
        glowEnabled = true,
        backgroundAnimationEnabled = true,
        highContrastEnabled = false,
        reducedTransparencyEnabled = false
    ),
    StylePreset(
        title = "Solarized",
        subtitle = "Waves + Soft Neo",
        backgroundStyle = 6,
        elementStyle = 4,
        gradientEnabled = true,
        glowEnabled = true,
        backgroundAnimationEnabled = true,
        highContrastEnabled = true,
        reducedTransparencyEnabled = false
    ),
    StylePreset(
        title = "Nothing OS",
        subtitle = "Dots + строгие формы",
        backgroundStyle = 2,
        elementStyle = 2,
        gradientEnabled = false,
        glowEnabled = false,
        backgroundAnimationEnabled = false,
        highContrastEnabled = true,
        reducedTransparencyEnabled = true
    )
)

@Composable
private fun PresetSelector(
    selectedBackgroundStyle: Int,
    selectedElementStyle: Int,
    onPresetApply: (StylePreset) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val presets = remember { stylePresets() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            val selected = preset.backgroundStyle == selectedBackgroundStyle && preset.elementStyle == selectedElementStyle
            Surface(
                onClick = { onPresetApply(preset) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) nebulaColors.accent.copy(alpha = 0.16f) else nebulaColors.onSurface.copy(alpha = 0.05f),
                border = BorderStroke(
                    1.dp,
                    if (selected) nebulaColors.accent.copy(alpha = 0.4f) else nebulaColors.onSurface.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = preset.title,
                        color = if (selected) nebulaColors.accent else nebulaColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = preset.subtitle,
                        color = nebulaColors.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StylePreviewCard(
    title: String,
    style: Int,
    isBackgroundPreview: Boolean
) {
    val nebulaColors = LocalNebulaColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = nebulaColors.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, nebulaColors.onSurface.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = nebulaColors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(
                        when {
                            isBackgroundPreview && style == 4 -> RoundedCornerShape(8.dp)
                            !isBackgroundPreview && style == 2 -> RoundedCornerShape(8.dp)
                            !isBackgroundPreview && style == 3 -> RoundedCornerShape(6.dp)
                            else -> RoundedCornerShape(12.dp)
                        }
                    )
                    .background(
                        when {
                            isBackgroundPreview && style == 0 -> Brush.radialGradient(
                                listOf(nebulaColors.accent.copy(alpha = 0.35f), Color.Transparent)
                            )
                            isBackgroundPreview && style == 1 -> Brush.verticalGradient(
                                listOf(nebulaColors.primaryGradientStart.copy(alpha = 0.28f), Color.Transparent)
                            )
                            isBackgroundPreview && style == 2 -> Brush.linearGradient(
                                listOf(nebulaColors.surface.copy(alpha = 0.8f), nebulaColors.surface.copy(alpha = 0.7f))
                            )
                            isBackgroundPreview && style == 3 -> Brush.linearGradient(
                                listOf(nebulaColors.primaryGradientStart.copy(alpha = 0.3f), nebulaColors.primaryGradientEnd.copy(alpha = 0.25f))
                            )
                            isBackgroundPreview && style == 4 -> Brush.linearGradient(
                                listOf(nebulaColors.surface.copy(alpha = 0.7f), nebulaColors.primaryGradientMiddle.copy(alpha = 0.2f))
                            )
                            !isBackgroundPreview && style == 1 -> Brush.verticalGradient(
                                listOf(nebulaColors.surface.copy(alpha = 0.95f), nebulaColors.surface.copy(alpha = 0.78f))
                            )
                            !isBackgroundPreview && style == 2 -> Brush.linearGradient(
                                listOf(nebulaColors.surface.copy(alpha = 0.9f), nebulaColors.surface.copy(alpha = 0.75f))
                            )
                            !isBackgroundPreview && style == 3 -> Brush.linearGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            )
                            !isBackgroundPreview && style == 4 -> Brush.linearGradient(
                                listOf(nebulaColors.onSurface.copy(alpha = 0.1f), nebulaColors.surface.copy(alpha = 0.7f))
                            )
                            else -> Brush.linearGradient(
                                listOf(nebulaColors.textPrimary.copy(alpha = 0.1f), nebulaColors.textPrimary.copy(alpha = 0.04f))
                            )
                        }
                    )
                    .then(
                        if (style == 2) Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 10.dp, radius = 0.9.dp, alpha = 0.16f)
                        else Modifier
                    )
                    .border(
                        1.dp,
                        when {
                            !isBackgroundPreview && style == 3 -> nebulaColors.onSurface.copy(alpha = 0.26f)
                            else -> nebulaColors.onSurface.copy(alpha = 0.14f)
                        },
                        RoundedCornerShape(12.dp)
                    )
            )
        }
    }
}

@Composable
fun ThemePreviewCard(isDark: Boolean, accent: Color, gradientColors: List<Color> = emptyList()) {
    val nebulaColors = LocalNebulaColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(28.dp),
        color = nebulaColors.onSurface.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, nebulaColors.onSurface.copy(alpha = 0.12f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Подложка градиента - Делаем ее "туманной" и плавной
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (gradientColors.size > 1) {
                            // Для градиента используем диагональное смешивание всех цветов
                            Brush.linearGradient(
                                colors = listOf(
                                    gradientColors[0].copy(alpha = 0.25f),
                                    if (gradientColors.size == 3) gradientColors[1].copy(alpha = 0.2f) else androidx.compose.ui.graphics.lerp(gradientColors[0], gradientColors[1], 0.5f).copy(alpha = 0.25f),
                                    gradientColors.last().copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(1000f, 1000f)
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(accent.copy(alpha = 0.25f), Color.Transparent)
                            )
                        }
                    )
            )

            // Дополнительный "мягкий" слой для эффекта глубины
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(200f, 100f),
                            radius = 400f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Иконка теперь использует результирующий (смешанный) акцентный цвет
                Icon(
                    if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    null,
                    modifier = Modifier.size(52.dp),
                    tint = accent
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "NebulaGuard",
                    color = nebulaColors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    if (isDark) "Тёмная тема активна" else "Светлая тема активна",
                    color = nebulaColors.textTertiary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ThemeModeItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val bgColor by animateColorAsState(
        if (isSelected) nebulaColors.accent.copy(alpha = 0.15f) else nebulaColors.onSurface.copy(alpha = 0.04f),
        label = "modeBg"
    )
    val contentColor by animateColorAsState(
        if (isSelected) nebulaColors.accent else nebulaColors.textTertiary,
        label = "modeContent"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(
            1.dp,
            if (isSelected) nebulaColors.accent.copy(alpha = 0.3f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = contentColor)
            Spacer(Modifier.height(4.dp))
            Text(title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun AccentColorItem(
    color: Color,
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (isSelected) nebulaColors.onSurface else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                Text(description, color = nebulaColors.textTertiary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CustomColorPalette(
    label: String,
    selectedColor: Color,
    onColorSelect: (Color) -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(1f) }
    var value by remember { mutableStateOf(1f) }

    LaunchedEffect(selectedColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    fun updateColor(newHue: Float = hue, newSaturation: Float = saturation, newValue: Float = value) {
        onColorSelect(Color.hsv(newHue, newSaturation, newValue))
    }

    val hex = remember(selectedColor) {
        val rgb = selectedColor.toArgb() and 0x00FFFFFF
        String.format("#%06X", rgb)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = nebulaColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(selectedColor)
                .border(1.dp, nebulaColors.onSurface.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(hex, color = if (value > 0.5f) Color.Black else Color.White, fontWeight = FontWeight.Bold)
        }

        Text("Тон", color = nebulaColors.textTertiary, fontSize = 12.sp)
        HsvSlider(
            value = hue,
            onValueChange = {
                hue = it
                updateColor(newHue = it)
            },
            valueRange = 0f..360f,
            trackBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
            ),
            currentColor = Color.hsv(hue, 1f, 1f)
        )

        Text("Насыщенность", color = nebulaColors.textTertiary, fontSize = 12.sp)
        HsvSlider(
            value = saturation,
            onValueChange = {
                saturation = it
                updateColor(newSaturation = it)
            },
            valueRange = 0f..1f,
            trackBrush = Brush.linearGradient(
                colors = listOf(
                    Color.hsv(hue, 0f, 1f),
                    Color.hsv(hue, 1f, 1f)
                )
            ),
            currentColor = Color.hsv(hue, saturation, 1f)
        )

        Text("Яркость", color = nebulaColors.textTertiary, fontSize = 12.sp)
        HsvSlider(
            value = value,
            onValueChange = {
                value = it
                updateColor(newValue = it)
            },
            valueRange = 0f..1f,
            trackBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Black,
                    Color.hsv(hue, saturation, 1f)
                )
            ),
            currentColor = Color.hsv(hue, saturation, value)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HsvSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    trackBrush: Brush,
    currentColor: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .shadow(6.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .padding(4.dp)
                    .background(currentColor, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.05f), CircleShape)
            )
        },
        track = { sliderState ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.1f),
                        CircleShape
                    )
                    .clip(CircleShape)
                    .background(trackBrush)
            )
        }
    )
}

@Composable
private fun SubscriptionAppearancePreview(
    themeSpec: String?,
    logo: String?,
    cachedLogo: String?,
    useSubscriptionTheme: Boolean,
    showSubscriptionLogo: Boolean
) {
    val nebulaColors = LocalNebulaColors.current
    val themeColors = subscriptionPreviewColors(themeSpec, nebulaColors.accent)
    val accent = themeColors.first()
    val hasTheme = !themeSpec.isNullOrBlank()
    val hasLogo = !logo.isNullOrBlank() || !cachedLogo.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubscriptionPreviewTile(
            title = "Тема",
            subtitle = if (hasTheme) "из подписки" else "нет данных",
            icon = Icons.Default.ColorLens,
            accent = accent,
            active = useSubscriptionTheme && hasTheme,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                themeColors.getOrElse(1) { accent }.copy(alpha = 0.34f),
                                themeColors.getOrElse(2) { accent }.copy(alpha = 0.18f),
                                nebulaColors.textPrimary.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                    .alpha(if (useSubscriptionTheme && hasTheme) 1f else 0.48f)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.22f))
                        .border(2.dp, accent, CircleShape)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    themeColors.take(3).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        )
                    }
                }
            }
        }

        SubscriptionPreviewTile(
            title = "Логотип",
            subtitle = if (hasLogo) "из подписки" else "нет данных",
            icon = Icons.Default.Image,
            accent = accent,
            active = showSubscriptionLogo && hasLogo,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(nebulaColors.textPrimary.copy(alpha = 0.04f))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasLogo) {
                    SubscriptionBrandLogo(
                        logo = logo,
                        cachedLogo = cachedLogo,
                        size = 38.dp,
                        modifier = Modifier.alpha(if (showSubscriptionLogo) 1f else 0.42f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = nebulaColors.textTertiary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionPreviewTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .height(130.dp)
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.12f) else nebulaColors.surface)
            .border(1.dp, if (active) accent.copy(alpha = 0.62f) else nebulaColors.onSurface.copy(alpha = 0.12f), shape)
            .padding(10.dp)
    ) {
        content()
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) accent else nebulaColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun subscriptionPreviewColors(themeSpec: String?, fallback: Color): List<Color> {
    val colors = themeSpec
        ?.split(",")
        ?.mapNotNull { part ->
            val trimmed = part.trim()
            if (!trimmed.startsWith("#")) {
                null
            } else {
                runCatching { Color(android.graphics.Color.parseColor(trimmed)) }.getOrNull()
            }
        }
        ?.take(3)
        .orEmpty()

    if (colors.isNotEmpty()) return colors
    return listOf(
        fallback,
        fallback.copy(alpha = 0.82f),
        androidx.compose.ui.graphics.lerp(fallback, Color.White, 0.28f)
    )
}
