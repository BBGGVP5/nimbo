package com.danila.nimbo.ui.screens

import android.content.Context
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.components.NimboPanel
import com.danila.nimbo.ui.components.extractFlagEmoji
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Общая карта Нимбо из assets/nimbo-world-map.json (формат: assets/geo/README.md). */
internal class NimboWorldMapData(
    /** Пары lon, lat центров клеток суши. */
    val land: FloatArray,
    val step: Float,
    val countries: Map<String, GeoPoint>,
    val timezones: Map<String, GeoPoint>
)

internal data class GeoPoint(val lat: Float, val lon: Float)

internal object NimboWorldMap {
    private const val ASSET = "nimbo-world-map.json"
    private val MOSCOW = GeoPoint(55.75f, 37.62f)

    @Volatile private var cached: NimboWorldMapData? = null

    fun load(context: Context): NimboWorldMapData = cached ?: synchronized(this) {
        cached ?: parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }).also { cached = it }
    }

    private fun parse(text: String): NimboWorldMapData {
        val root = JSONObject(text)
        val grid = root.getJSONObject("grid")
        val step = grid.getDouble("step").toFloat()
        val lonWest = grid.getDouble("lonWest").toFloat()
        val latNorth = grid.getDouble("latNorth").toFloat()
        val cols = grid.getInt("cols")
        val rows = root.getJSONArray("land")
        val out = ArrayList<Float>(32_000)
        for (r in 0 until rows.length()) {
            val hex = rows.getString(r)
            val lat = latNorth - (r + 0.5f) * step
            for (c in 0 until min(cols, hex.length * 4)) {
                val nibble = Character.digit(hex[c / 4], 16)
                if (nibble < 0) continue
                if ((nibble shr (3 - c % 4)) and 1 == 1) {
                    out += lonWest + (c + 0.5f) * step
                    out += lat
                }
            }
        }
        fun points(name: String): Map<String, GeoPoint> {
            val obj = root.optJSONObject(name) ?: return emptyMap()
            return obj.keys().asSequence().associateWith { key ->
                val a = obj.getJSONArray(key)
                GeoPoint(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
            }
        }
        return NimboWorldMapData(out.toFloatArray(), step, points("countries"), points("timezones"))
    }

    /** «Вы здесь»: часовой пояс устройства → страна региона/локали → Москва. Сеть не используется. */
    fun homeLocation(data: NimboWorldMapData): GeoPoint {
        data.timezones[TimeZone.getDefault().id]?.let { return it }
        val region = Locale.getDefault().country.uppercase(Locale.ROOT)
        return data.countries[region] ?: MOSCOW
    }
}

/** ISO 3166-1 alpha-2 сервера по его флагу/названию страны у провайдера (как и везде в приложении). */
internal fun serverCountryCode(server: Server): String? {
    val flag = extractFlagEmoji(server.name)
    if (flag.isEmpty()) return null
    val cps = flag.codePoints().toArray()
    if (cps.size != 2 || cps.any { it !in 0x1F1E6..0x1F1FF }) return null
    val code = cps.map { 'A' + (it - 0x1F1E6) }.joinToString("")
    return if (code == "UK") "GB" else code
}

private data class MapWindow(val lonC: Float, val latC: Float, val spanLat: Float)

private class MapFlag(val code: String, val point: GeoPoint, val server: Server, val emoji: String)

/**
 * Карта на главном экране: точки суши, флаги стран серверов текущего профиля,
 * светящаяся дуга «вы здесь» → выбранный/подключённый сервер.
 *
 * Без graphicsLayer: под стеклянными стилями полноразмерный render layer даёт бледные квадраты.
 */
@Composable
internal fun NimboHomeMap(
    servers: List<Server>,
    targetServer: Server?,
    connected: Boolean,
    connecting: Boolean,
    statusText: String,
    onSelectServer: (Server) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val isLight = colors.background.luminance() > 0.5f
    val motion = LocalBackgroundAnimationEnabled.current && !LocalReducedTransparencyEnabled.current
    val data by produceState<NimboWorldMapData?>(null, context) {
        value = withContext(Dispatchers.Default) { runCatching { NimboWorldMap.load(context.applicationContext) }.getOrNull() }
    }
    val map = data
    val accent = colors.accent

    NimboPanel(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(accent.copy(alpha = if (isLight) 0.05f else 0.08f), Color.Transparent))
        )) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            if (map == null || widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

            val home = remember(map) { NimboWorldMap.homeLocation(map) }
            val targetCode = targetServer?.let { serverCountryCode(it) }
            val flags = remember(map, servers, targetServer) {
                servers.mapNotNull { s -> serverCountryCode(s)?.let { it to s } }
                    .groupBy({ it.first }, { it.second })
                    .mapNotNull { (code, list) ->
                        val point = map.countries[code] ?: return@mapNotNull null
                        val pick = list.firstOrNull { targetServer?.matchesSelection(it) == true }
                            ?: list.filter { (it.ping ?: -1) > 0 }.minByOrNull { it.ping ?: Int.MAX_VALUE }
                            ?: list.first()
                        MapFlag(code, point, pick, extractFlagEmoji(pick.name))
                    }
            }
            val dest = targetCode?.let { map.countries[it] }

            // Окно: bbox «вы здесь» + серверы, с отступом; соотношение сторон под холст.
            val window = remember(flags, home, dest, widthPx, heightPx) {
                val pts = flags.map { it.point } + home + listOfNotNull(dest)
                val west = pts.minOf { it.lon }; val east = pts.maxOf { it.lon }
                val south = pts.minOf { it.lat }; val north = pts.maxOf { it.lat }
                val aspect = widthPx / heightPx
                val spanLon = max(east - west, 30f) * 1.3f + 14f
                val spanLatMin = max(north - south, 14f) * 1.3f + 12f
                val lonC = (west + east) / 2f
                // Поправка cos зависит от центра, а центр — от высоты окна: пара итераций сходится.
                var latC = (north + south) / 2f
                var spanLat = spanLatMin
                repeat(3) {
                    val k = cos(latC * PI / 180).toFloat().coerceIn(0.25f, 1f)
                    spanLat = max(spanLatMin, spanLon * k / aspect)
                    // Не уводим окно в полярную пустоту (данные — от 80° с.ш. до 56° ю.ш.).
                    latC = if (spanLat < 136f) latC.coerceIn(-56f + spanLat * 0.4f, max(-56f + spanLat * 0.4f, 80f - spanLat * 0.4f)) else 12f
                }
                spanLat = max(spanLatMin, spanLon * cos(latC * PI / 180).toFloat().coerceIn(0.25f, 1f) / aspect)
                MapWindow(lonC, latC, spanLat)
            }
            val anim = tween<Float>(700, easing = FastOutSlowInEasing)
            val lonC by animateFloatAsState(window.lonC, anim, label = "map-lon")
            val latC by animateFloatAsState(window.latC, anim, label = "map-lat")
            val spanLat by animateFloatAsState(window.spanLat, anim, label = "map-span")
            // Поправка по ширине: фиксируем по целевому окну, чтобы не пересчитывать точки каждый кадр.
            val kx = remember(window.latC) { cos(window.latC * PI / 180).toFloat().coerceIn(0.25f, 1f) }

            fun project(p: GeoPoint): Offset {
                val scale = heightPx / spanLat
                return Offset(widthPx / 2f + (p.lon - lonC) * kx * scale, heightPx / 2f - (p.lat - latC) * scale)
            }

            val phase = rememberMapPhase(motion)
            val dotPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }
            val landBuf = remember(map) { FloatArray(map.land.size) }
            val landColor = colors.textPrimary.copy(alpha = if (isLight) 0.17f else 0.16f)
            val routeOff = colors.textSecondary.copy(alpha = 0.55f)
            val homeColor = colors.textPrimary

            Canvas(Modifier.fillMaxSize()) {
                val scale = size.height / spanLat
                // Точки суши: один нативный вызов, буфер переиспользуется между кадрами.
                drawLand(map.land, landBuf, dotPaint, lonC, latC, kx, scale, map.step, landColor)

                val from = project(home)
                val to = dest?.let { project(it) }
                val t = phase.value
                if (to != null && hypot(to.x - from.x, to.y - from.y) > 10.dp.toPx()) {
                    val path = arcPath(from, to)
                    val glowAlpha = when { connected -> 0.30f; connecting -> 0.20f; else -> 0f }
                    if (glowAlpha > 0f) {
                        drawPath(path, accent.copy(alpha = glowAlpha * 0.5f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(path, accent.copy(alpha = glowAlpha), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), -t * 24.dp.toPx())
                    when {
                        connected -> drawPath(path, Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent), from, to),
                            style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                        connecting -> drawPath(path, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
                        else -> drawPath(path, routeOff, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
                    }
                    if (connected || connecting) {
                        val measure = PathMeasure().apply { setPath(path, false) }
                        val travel = ((t * if (connected) 0.45f else 0.8f) % 1f)
                        val pos = measure.getPosition(measure.length * travel)
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), pos, 9.dp.toPx()),
                            9.dp.toPx(), pos)
                        drawCircle(Color.White.copy(alpha = 0.92f), 2.4.dp.toPx(), pos)
                    }
                }
                to?.let { p ->
                    val r = 34.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (connected) 0.32f else 0.16f), Color.Transparent), p, r), r, p)
                }
                // «Вы здесь».
                val pulse = (t * 0.6f) % 1f
                if (motion) drawCircle(homeColor.copy(alpha = 0.28f * (1f - pulse)), 6.dp.toPx() + 14.dp.toPx() * pulse, from,
                    style = Stroke(1.5.dp.toPx()))
                drawCircle(colors.panelFill.copy(alpha = 1f), 7.dp.toPx(), from)
                drawCircle(homeColor, 4.5.dp.toPx(), from)
            }

            // Подпись «Вы здесь».
            HomeLabel(offset = { val p = project(home); IntOffset((p.x + 10.dp.toPx()).toInt(), (p.y - 9.dp.toPx()).toInt()) })

            // Флаги серверов.
            flags.sortedBy { it.code == targetCode }.forEach { flag ->
                val selected = flag.code == targetCode
                val sizeDp = if (selected) 38.dp else 30.dp
                val sizePx = with(density) { sizeDp.toPx() }
                val label = Locale("", flag.code).getDisplayCountry(Locale.getDefault())
                Surface(
                    shape = CircleShape,
                    color = colors.panelFill,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else colors.panelBorder),
                    modifier = Modifier
                        .offset { val p = project(flag.point); IntOffset((p.x - sizePx / 2).toInt(), (p.y - sizePx / 2).toInt()) }
                        .size(sizeDp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onSelectServer(flag.server) }
                        .semantics { contentDescription = label }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(flag.emoji, fontSize = if (selected) 20.sp else 15.sp)
                    }
                }
            }

            // Статус соединения поверх карты.
            Row(Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(colors.panelFill.copy(alpha = 0.92f), RoundedCornerShape(50))
                .border(1.dp, colors.panelBorder, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(
                    when { connected -> accent; connecting -> accent.copy(alpha = 0.6f); else -> colors.textSecondary.copy(alpha = 0.6f) },
                    CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(statusText, style = MaterialTheme.typography.labelSmall,
                    color = if (connected) colors.textPrimary else colors.textSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun HomeLabel(offset: androidx.compose.ui.unit.Density.() -> IntOffset) {
    val colors = LocalNebulaColors.current
    Text(t("Вы здесь", "You are here"), Modifier.offset(offset)
        .background(colors.panelFill.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colors.textSecondary)
}

/** Точки суши в окне. Квадратные ячейки → круглые точки фиксированного радиуса. */
private fun DrawScope.drawLand(land: FloatArray, buf: FloatArray, paint: Paint, lonC: Float, latC: Float,
    kx: Float, scale: Float, step: Float, color: Color) {
    val halfW = size.width / 2f; val halfH = size.height / 2f
    val sx = kx * scale
    val lonHalf = halfW / sx + 1f; val latHalf = halfH / scale + 1f
    var n = 0
    var i = 0
    while (i < land.size) {
        val lon = land[i]; val lat = land[i + 1]
        if (abs(lon - lonC) <= lonHalf && abs(lat - latC) <= latHalf) {
            buf[n++] = halfW + (lon - lonC) * sx
            buf[n++] = halfH - (lat - latC) * scale
        }
        i += 2
    }
    if (n == 0) return
    paint.color = color.toArgb()
    paint.strokeWidth = (step * min(sx, scale) * 0.5f).coerceIn(1.4f, 9.dp.toPx())
    drawIntoCanvas { it.nativeCanvas.drawPoints(buf, 0, n, paint) }
}

/** Дуга по квадратичной Безье, выгнутая вверх. */
private fun arcPath(from: Offset, to: Offset): Path {
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    val dx = to.x - from.x; val dy = to.y - from.y
    val len = hypot(dx, dy)
    var nx = -dy / len; var ny = dx / len
    if (ny > 0f) { nx = -nx; ny = -ny }
    val lift = len * 0.28f
    return Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x + nx * lift, mid.y + ny * lift, to.x, to.y)
    }
}

/** Монотонная фаза (секунды) от кадровых часов; без движения — статичный кадр. Читается только в отрисовке. */
@Composable
private fun rememberMapPhase(enabled: Boolean): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) { state.floatValue = 0.35f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) withFrameNanos { state.floatValue = (it - start) / 1_000_000_000f }
    }
    return state
}
