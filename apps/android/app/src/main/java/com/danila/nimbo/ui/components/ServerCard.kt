package com.danila.nimbo.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.theme.ElementStyleMode
import com.danila.nimbo.ui.theme.LocalElementStyleMode
import com.danila.nimbo.ui.theme.LocalNebulaColors

/**
 * Извлекает флаг из начала названия сервера
 * @return флаг (2 символа) или пустую строку
 */
private val leadingCountryFlags = mapOf(
    "ru" to "🇷🇺", "russia" to "🇷🇺", "россия" to "🇷🇺", "москва" to "🇷🇺",
    "de" to "🇩🇪", "germany" to "🇩🇪", "германия" to "🇩🇪",
    "nl" to "🇳🇱", "netherlands" to "🇳🇱", "нидерланды" to "🇳🇱",
    "fi" to "🇫🇮", "finland" to "🇫🇮", "финляндия" to "🇫🇮",
    "fr" to "🇫🇷", "france" to "🇫🇷", "франция" to "🇫🇷",
    "us" to "🇺🇸", "usa" to "🇺🇸", "united" to "🇺🇸", "сша" to "🇺🇸",
    "uk" to "🇬🇧", "gb" to "🇬🇧", "london" to "🇬🇧", "британия" to "🇬🇧",
    "tr" to "🇹🇷", "turkey" to "🇹🇷", "турция" to "🇹🇷",
    "pl" to "🇵🇱", "poland" to "🇵🇱", "польша" to "🇵🇱",
    "se" to "🇸🇪", "sweden" to "🇸🇪", "швеция" to "🇸🇪",
    "ch" to "🇨🇭", "switzerland" to "🇨🇭", "швейцария" to "🇨🇭",
    "jp" to "🇯🇵", "japan" to "🇯🇵", "япония" to "🇯🇵",
    "kr" to "🇰🇷", "korea" to "🇰🇷", "корея" to "🇰🇷",
    "sg" to "🇸🇬", "singapore" to "🇸🇬", "сингапур" to "🇸🇬",
    "hk" to "🇭🇰", "hong" to "🇭🇰", "гонконг" to "🇭🇰",
    "ca" to "🇨🇦", "canada" to "🇨🇦", "канада" to "🇨🇦",
    "br" to "🇧🇷", "brazil" to "🇧🇷", "бразилия" to "🇧🇷",
    "ua" to "🇺🇦", "ukraine" to "🇺🇦", "украина" to "🇺🇦",
    "kz" to "🇰🇿", "kazakhstan" to "🇰🇿", "казахстан" to "🇰🇿",
    "am" to "🇦🇲", "armenia" to "🇦🇲", "армения" to "🇦🇲",
    "ge" to "🇬🇪", "georgia" to "🇬🇪", "грузия" to "🇬🇪",
    "ae" to "🇦🇪", "uae" to "🇦🇪", "dubai" to "🇦🇪", "дубай" to "🇦🇪",
    "es" to "🇪🇸", "spain" to "🇪🇸", "испания" to "🇪🇸",
    "it" to "🇮🇹", "italy" to "🇮🇹", "италия" to "🇮🇹"
)

private fun leadingCountryToken(name: String): MatchResult? {
    return Regex("""^\s*([A-Za-zА-Яа-яЁё]{2,}|[A-Za-z]{2})\b[·\s|:,_-]*""")
        .find(name)
        ?.takeIf { leadingCountryFlags.containsKey(it.groupValues[1].lowercase()) }
}

fun extractFlagEmoji(name: String): String {
    val trimmed = name.trimStart()
    if (trimmed.isEmpty()) return ""

    // Флаги состоят из 2 региональных индикаторов (каждый - суррогатная пара)
    // В строке это 4 Char или 2 codePoint
    val firstCodePoint = trimmed.codePointAt(0)

    // Проверяем, является ли первый codePoint региональным индикатором
    // Regional Indicator Symbols: U+1F1E6 - U+1F1FF
    if (firstCodePoint in 0x1F1E6..0x1F1FF) {
        val secondCodePoint = if (Character.charCount(firstCodePoint) < trimmed.length) {
            trimmed.codePointAt(Character.charCount(firstCodePoint))
        } else -1

        if (secondCodePoint in 0x1F1E6..0x1F1FF) {
            // Возвращаем оба символа флага
            return String(Character.toChars(firstCodePoint)) + String(Character.toChars(secondCodePoint))
        }
    }
    val token = leadingCountryToken(trimmed)?.groupValues?.getOrNull(1)?.lowercase()
    return token?.let { leadingCountryFlags[it] }.orEmpty()
}

/**
 * Очищает название сервера от флага и лишних символов
 */
fun cleanServerName(name: String): String {
    val withoutFlag = name
        .let { n ->
            val flag = extractFlagEmoji(n)
            val trimmedStart = n.trimStart()
            val leadingWhitespace = n.length - trimmedStart.length
            val firstCodePoint = if (trimmedStart.isNotEmpty()) trimmedStart.codePointAt(0) else -1
            if (flag.isNotEmpty() && firstCodePoint in 0x1F1E6..0x1F1FF) {
                // Удаляем длину флага в codePoint'ах (4 Char = 2 суррогатные пары)
                val firstCount = Character.charCount(firstCodePoint)
                val secondCodePoint = trimmedStart.codePointAt(firstCount)
                val secondCount = Character.charCount(secondCodePoint)
                n.substring(leadingWhitespace + firstCount + secondCount)
            } else n
        }
    return withoutFlag
        .removePrefix("·")
        .removePrefix("-")
        .removePrefix("|")
        .removePrefix(":")
        .removePrefix(" ")
        .trim()
}

@Composable
fun ServerCard(server: Server, ping: Int? = null, onClick: () -> Unit, onPing: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
    val elementStyle = LocalElementStyleMode.current
    val flagEmoji = extractFlagEmoji(server.name)
    val serverNameClean = cleanServerName(server.name)
    val cardShape = when (elementStyle) {
        ElementStyleMode.MORPHISM -> RoundedCornerShape(16.dp)
        ElementStyleMode.MATERIAL3 -> RoundedCornerShape(14.dp)
        ElementStyleMode.NOTHING_DOTS -> RoundedCornerShape(10.dp)
        ElementStyleMode.OUTLINED -> RoundedCornerShape(8.dp)
        ElementStyleMode.SOFT_NEO -> RoundedCornerShape(18.dp)
    }
    val cardColor = when (elementStyle) {
        ElementStyleMode.MORPHISM -> nebulaColors.cardBackground
        ElementStyleMode.MATERIAL3 -> nebulaColors.surface.copy(alpha = 0.88f)
        ElementStyleMode.NOTHING_DOTS -> nebulaColors.surface.copy(alpha = 0.8f)
        ElementStyleMode.OUTLINED -> Color.Transparent
        ElementStyleMode.SOFT_NEO -> nebulaColors.surface.copy(alpha = 0.76f)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .animateContentSize()
            .then(
                if (elementStyle == ElementStyleMode.NOTHING_DOTS) {
                    Modifier.dotPatternOverlay(nebulaColors.textPrimary, spacing = 11.dp, radius = 0.8.dp, alpha = 0.11f)
                } else Modifier
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (elementStyle) {
                ElementStyleMode.OUTLINED -> nebulaColors.onSurface.copy(alpha = 0.28f)
                ElementStyleMode.NOTHING_DOTS -> nebulaColors.accent.copy(alpha = 0.2f)
                else -> nebulaColors.onSurface.copy(alpha = 0.12f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(nebulaColors.accent.copy(alpha = 0.25f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (flagEmoji.isNotEmpty()) {
                        Text(
                            text = flagEmoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = nebulaColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = serverNameClean,
                    style = MaterialTheme.typography.bodyMedium,
                    color = nebulaColors.textPrimary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Отображение пинга
            if (ping != null) {
                val pingColor = when {
                    ping == -1 -> nebulaColors.textTertiary
                    ping <= 70 -> nebulaColors.statusConnected
                    ping <= 120 -> nebulaColors.textSecondary
                    ping <= 220 -> Color(0xFFFFA500)
                    else -> nebulaColors.statusDisconnected
                }

                val pingText = if (ping == -1) "н/д" else "${ping}мс"

                Surface(
                    color = pingColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = pingColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = pingText,
                            style = MaterialTheme.typography.labelSmall,
                            color = pingColor
                        )
                    }
                }
            } else {
                Surface(
                    color = nebulaColors.textTertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "пинг...",
                        style = MaterialTheme.typography.labelSmall,
                        color = nebulaColors.textTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
