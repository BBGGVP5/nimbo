package com.danila.nimbo.ui.screens

import android.content.Context
import android.graphics.Paint
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.danila.nimbo.model.Server
import com.danila.nimbo.network.ServiceStatusProbe
import com.danila.nimbo.ui.components.NimboPanel
import com.danila.nimbo.ui.components.extractFlagEmoji
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalBackgroundAnimationEnabled
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.ui.theme.LocalReducedTransparencyEnabled
import com.danila.nimbo.vpn.HealthProxySession
import com.danila.nimbo.vpn.HealthProxySessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

internal fun countryDisplayName(code: String): String =
    Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

/**
 * Реальная страна выхода: один запрос cloudflare trace через туннель (зеркальный вход Xray,
 * само приложение исключено из VPN). Кеш — на сессию подключения; ошибки молча игнорируются,
 * тогда карта остаётся на стране из названия сервера.
 */
internal object MapExitCountry {
    private class Entry(val session: HealthProxySession, val code: String)

    private val entry = mutableStateOf<Entry?>(null)

    fun current(): String? = entry.value?.takeIf { it.session === HealthProxySessions.active }?.code

    fun clear() { entry.value = null }

    suspend fun resolve() {
        if (current() != null) return
        // Ядру нужно мгновение, чтобы поднять зеркальный вход после подключения.
        repeat(12) {
            val session = ServiceStatusProbe.session()
            if (session != null) {
                val info = withTimeoutOrNull(5_000) { ServiceStatusProbe.exitInfo(session) }
                val code = info?.countryCode
                if (code != null && ServiceStatusProbe.isCurrent(session)) entry.value = Entry(session, code)
                return
            }
            delay(1_000)
        }
    }
}

/** Страна выхода для текущего подключения (null — не подключено или ещё не известна). */
@Composable
internal fun rememberExitCountry(connected: Boolean, routeKey: String?): String? {
    LaunchedEffect(connected, routeKey) {
        if (!connected || routeKey == null) { MapExitCountry.clear(); return@LaunchedEffect }
        MapExitCountry.resolve()
    }
    return if (connected) MapExitCountry.current() else null
}

private data class MapWindow(val lonC: Float, val latC: Float, val spanLat: Float)

/** Проекция окна: равнопромежуточная с поправкой cos(широты центра). */
private class MapView(val lonC: Float, val latC: Float, val spanLat: Float, val w: Float, val h: Float) {
    val scale = h / spanLat
    val kx = cos(latC * PI / 180).toFloat().coerceIn(0.25f, 1f)
    fun project(p: GeoPoint) = Offset(w / 2f + (p.lon - lonC) * kx * scale, h / 2f - (p.lat - latC) * scale)
    fun unproject(o: Offset) = GeoPoint(latC - (o.y - h / 2f) / scale, lonC + (o.x - w / 2f) / (kx * scale))
}

private class MapFlag(val code: String, val point: GeoPoint, val servers: List<Server>, val emoji: String)

private class FlagCluster(val center: Offset, val items: List<MapFlag>)

private class EdgePointer(val pos: Offset, val angleDeg: Float, val flag: MapFlag, val count: Int)

private const val MIN_SPAN_LAT = 5f
private const val MAX_SPAN_LAT = 140f

/** Окно по умолчанию: «вы здесь» + активный сервер, минимум ~25° по долготе. */
private fun autoWindow(points: List<GeoPoint>, w: Float, h: Float): MapWindow {
    val west = points.minOf { it.lon }; val east = points.maxOf { it.lon }
    val south = points.minOf { it.lat }; val north = points.maxOf { it.lat }
    val aspect = w / h
    val spanLon = max(east - west, 25f) * 1.5f + 12f
    val spanLatMin = max(north - south, 10f) * 1.6f + 8f
    val lonC = (west + east) / 2f
    var latC = (north + south) / 2f
    var spanLat = spanLatMin
    repeat(3) {
        val k = cos(latC * PI / 180).toFloat().coerceIn(0.25f, 1f)
        spanLat = max(spanLatMin, spanLon * k / aspect)
        latC = if (spanLat < 130f) latC.coerceIn(-56f + spanLat * 0.4f, max(-56f + spanLat * 0.4f, 80f - spanLat * 0.4f)) else 12f
    }
    spanLat = max(spanLatMin, spanLon * cos(latC * PI / 180).toFloat().coerceIn(0.25f, 1f) / aspect)
    // Маршрут поднимаем чуть выше центра: внизу карточки лежит больше суши.
    return MapWindow(lonC, latC - spanLat * 0.04f, spanLat.coerceIn(MIN_SPAN_LAT, MAX_SPAN_LAT))
}

/** Системное «убрать анимации» или «уменьшить движение» в Нимбо. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val appMotion = LocalBackgroundAnimationEnabled.current && !LocalReducedTransparencyEnabled.current
    val systemOff = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    return !appMotion || systemOff
}

/**
 * Карта на главном экране: точки суши, флаги стран серверов текущего профиля (с кластерами),
 * маршрут «вы здесь» → реальная страна выхода. Щипок — зум, перетаскивание — сдвиг,
 * двойной тап — вернуть авто-окно.
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
    exitCountry: String?,
    trafficBytesPerSecond: () -> Long,
    onSelectServer: (Server) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalNebulaColors.current
    val isLight = colors.background.luminance() > 0.5f
    val reduced = rememberReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
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
            val scope = rememberCoroutineScope()

            val home = remember(map) { NimboWorldMap.homeLocation(map) }
            val serverCode = targetServer?.let { serverCountryCode(it) }
            // «Куда» — реальная страна выхода, если уже известна, иначе страна из названия.
            val destCode = (exitCountry?.takeIf { map.countries.containsKey(it) }) ?: serverCode
            val dest = destCode?.let { map.countries[it] }
            val flags = remember(map, servers) {
                servers.mapNotNull { s -> serverCountryCode(s)?.let { it to s } }
                    .groupBy({ it.first }, { it.second })
                    .mapNotNull { (code, list) ->
                        val point = map.countries[code] ?: return@mapNotNull null
                        MapFlag(code, point, list, countryFlagEmoji(code) ?: extractFlagEmoji(list.first().name))
                    }
                    .sortedByDescending { it.servers.size }
            }

            // --- Окно: авто или ручное (жесты), с плавным перелётом.
            val auto = remember(home, dest, widthPx, heightPx) { autoWindow(listOfNotNull(home, dest), widthPx, heightPx) }
            val lonC = remember { mutableFloatStateOf(auto.lonC) }
            val latC = remember { mutableFloatStateOf(auto.latC) }
            val span = remember { mutableFloatStateOf(auto.spanLat) }
            var manual by remember { mutableStateOf(false) }
            val flight = remember { arrayOfNulls<Job>(1) }
            fun flyTo(target: MapWindow, animated: Boolean) {
                flight[0]?.cancel()
                if (!animated) {
                    lonC.floatValue = target.lonC; latC.floatValue = target.latC; span.floatValue = target.spanLat
                    return
                }
                val from = MapWindow(lonC.floatValue, latC.floatValue, span.floatValue)
                flight[0] = scope.launch {
                    animate(0f, 1f, animationSpec = tween(900, easing = FastOutSlowInEasing)) { f, _ ->
                        lonC.floatValue = from.lonC + (target.lonC - from.lonC) * f
                        latC.floatValue = from.latC + (target.latC - from.latC) * f
                        span.floatValue = from.spanLat + (target.spanLat - from.spanLat) * f
                    }
                }
            }
            val connState = when { connected -> 2; connecting -> 1; else -> 0 }
            // Смена сервера и подключение возвращают авто-окно.
            LaunchedEffect(destCode, connState) { manual = false }
            LaunchedEffect(auto, manual) { if (!manual) flyTo(auto, animated = !reduced) }

            val view = MapView(lonC.floatValue, latC.floatValue, span.floatValue, widthPx, heightPx)
            val viewState = rememberUpdatedState(view)

            // --- Конец дуги перетекает к новой точке (смена сервера, уточнилась страна выхода).
            val destLat = remember { mutableFloatStateOf(dest?.lat ?: home.lat) }
            val destLon = remember { mutableFloatStateOf(dest?.lon ?: home.lon) }
            LaunchedEffect(dest) {
                val d = dest ?: return@LaunchedEffect
                val fromLat = destLat.floatValue; val fromLon = destLon.floatValue
                if (reduced) { destLat.floatValue = d.lat; destLon.floatValue = d.lon; return@LaunchedEffect }
                animate(0f, 1f, animationSpec = tween(800, easing = FastOutSlowInEasing)) { f, _ ->
                    destLat.floatValue = fromLat + (d.lat - fromLat) * f
                    destLon.floatValue = fromLon + (d.lon - fromLon) * f
                }
            }

            // --- Прорисовка маршрута: подключение рисует дугу, отключение втягивает её к вам.
            val live = remember { mutableFloatStateOf(if (connected) 1f else 0f) }
            val hint = remember { mutableFloatStateOf(if (connState == 0) 1f else 0f) }
            LaunchedEffect(connState, reduced) {
                when (connState) {
                    1 -> {
                        if (!reduced) launch { animate(hint.floatValue, 0.35f, animationSpec = tween(300)) { v, _ -> hint.floatValue = v } }
                        if (reduced) live.floatValue = 1f
                        else {
                            if (live.floatValue >= 0.999f) live.floatValue = 0f
                            animate(live.floatValue, 1f, animationSpec = tween(1000, easing = FastOutSlowInEasing)) { v, _ -> live.floatValue = v }
                        }
                    }
                    2 -> {
                        hint.floatValue = 0f
                        if (reduced) live.floatValue = 1f
                        else animate(live.floatValue, 1f, animationSpec = tween(400)) { v, _ -> live.floatValue = v }
                    }
                    else -> {
                        if (reduced) { live.floatValue = 0f; hint.floatValue = 1f }
                        else {
                            animate(live.floatValue, 0f, animationSpec = tween(650, easing = FastOutSlowInEasing)) { v, _ -> live.floatValue = v }
                            animate(hint.floatValue, 1f, animationSpec = tween(350)) { v, _ -> hint.floatValue = v }
                        }
                    }
                }
            }

            // --- Флаги: активный отдельно и поверх, остальные — кластеры в экранных координатах.
            val activeSize = 44.dp; val flagSize = 32.dp
            val activePx = with(density) { activeSize.toPx() }
            val flagPx = with(density) { flagSize.toPx() }
            val clusterDist = with(density) { 34.dp.toPx() }
            val edgeInset = with(density) { 22.dp.toPx() }
            val topInset = with(density) { 52.dp.toPx() }
            val others = flags.filter { it.code != destCode }
            // Флаги стоят строго в своей стране; сдвигается только приблизительный маркер «вы здесь».
            val activePos = dest?.let { view.project(GeoPoint(destLat.floatValue, destLon.floatValue)) }
            val clusters = ArrayList<FlagCluster>()
            val pointers = ArrayList<EdgePointer>()
            val cx = widthPx / 2f; val cy = heightPx / 2f
            for (flag in others) {
                val p = view.project(flag.point)
                val inside = p.x in edgeInset..(widthPx - edgeInset) && p.y in topInset..(heightPx - edgeInset)
                if (inside) {
                    val hit = clusters.indexOfFirst { hypot(it.center.x - p.x, it.center.y - p.y) < clusterDist }
                    if (hit >= 0) clusters[hit] = FlagCluster(clusters[hit].center, clusters[hit].items + flag)
                    else clusters += FlagCluster(p, listOf(flag))
                } else {
                    // Указатель у края по лучу из центра.
                    val dx = p.x - cx; val dy = p.y - cy
                    val tx = if (dx != 0f) ((if (dx > 0) widthPx - edgeInset else edgeInset) - cx) / dx else Float.MAX_VALUE
                    val ty = if (dy != 0f) ((if (dy > 0) heightPx - edgeInset else topInset) - cy) / dy else Float.MAX_VALUE
                    val k = min(abs(tx), abs(ty))
                    val pos = Offset(cx + dx * k, cy + dy * k)
                    val near = pointers.indexOfFirst { hypot(it.pos.x - pos.x, it.pos.y - pos.y) < clusterDist * 1.2f }
                    if (near >= 0) pointers[near] = EdgePointer(pointers[near].pos, pointers[near].angleDeg, pointers[near].flag, pointers[near].count + 1)
                    else pointers += EdgePointer(pos, (atan2(dy, dx) * 180f / PI.toFloat()), flag, 1)
                }
            }

            // Маркер «вы здесь» (точка по часовому поясу — приблизительная) не прячется под флагом.
            val homePos = run {
                var p = view.project(home)
                val circles = listOfNotNull(activePos?.let { it to activePx / 2f }) + clusters.map { it.center to flagPx / 2f }
                repeat(3) {
                    circles.forEach { (o, r) ->
                        val need = r + with(density) { 9.dp.toPx() }
                        val dx = p.x - o.x; val dy = p.y - o.y
                        val d = hypot(dx, dy)
                        if (d < need) p = if (d < 1f) Offset(o.x, o.y + need) else Offset(o.x + dx / d * need, o.y + dy / d * need)
                    }
                }
                p
            }
            val phase = rememberMapPhase(!reduced && visible)
            val dotPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }
            val landBuf = remember(map) { FloatArray(map.land.size) }
            val glowBuf = remember(map) { FloatArray(map.land.size) }
            val landColor = colors.textPrimary.copy(alpha = if (isLight) 0.24f else 0.27f)
            val routeGlowColor = accent.copy(alpha = if (isLight) 0.45f else 0.5f)
            val hintColor = colors.textSecondary.copy(alpha = 0.6f)
            val homeColor = colors.textPrimary
            val vignette = colors.panelFill
            val traffic = rememberUpdatedState(trafficBytesPerSecond)

            Canvas(Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { manual = false; flyTo(auto, animated = !reduced) })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        flight[0]?.cancel()
                        manual = true
                        val v = viewState.value
                        val anchor = v.unproject(centroid)
                        val newSpan = (span.floatValue / zoom).coerceIn(MIN_SPAN_LAT, MAX_SPAN_LAT)
                        // Точка под пальцами остаётся под пальцами, затем — сдвиг.
                        val zoomed = MapView(v.lonC, v.latC, newSpan, v.w, v.h)
                        val drift = zoomed.project(anchor) - centroid
                        lonC.floatValue = (v.lonC + (drift.x - pan.x) / (zoomed.kx * zoomed.scale)).coerceIn(-180f, 180f)
                        latC.floatValue = (v.latC - (drift.y - pan.y) / zoomed.scale).coerceIn(-60f, 82f)
                        span.floatValue = newSpan
                    }
                }) {
                val t = phase.value
                val from = homePos
                val to = if (dest != null) view.project(GeoPoint(destLat.floatValue, destLon.floatValue)) else null
                val route = to?.takeIf { hypot(it.x - from.x, it.y - from.y) > 12.dp.toPx() }?.let { arcPath(from, it) }
                val liveP = live.floatValue
                val hintA = hint.floatValue

                // Суша + «дорожка» вдоль маршрута.
                val glowRadius = 26.dp.toPx()
                val bezMid = to?.let { arcMidpoint(from, it) }
                drawLand(map.land, landBuf, glowBuf, dotPaint, view, map.step, landColor,
                    routeGlowColor.copy(alpha = routeGlowColor.alpha * max(liveP, hintA * 0.35f)),
                    if (route != null && bezMid != null) floatArrayOf(from.x, from.y, bezMid.x, bezMid.y, to.x, to.y) else null,
                    glowRadius)
                // Виньетка к краям.
                drawRect(Brush.radialGradient(
                    0.62f to Color.Transparent, 1f to vignette.copy(alpha = if (isLight) 0.4f else 0.5f),
                    center = Offset(size.width / 2f, size.height / 2f), radius = max(size.width, size.height) * 0.72f))

                if (route != null && to != null) {
                    val measure = PathMeasure().apply { setPath(route, false) }
                    val length = measure.length
                    if (hintA > 0.01f) drawPath(route, hintColor.copy(alpha = hintColor.alpha * hintA),
                        style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))))
                    if (liveP > 0.01f) {
                        val drawn = if (liveP >= 0.999f) route else Path().also { measure.getSegment(0f, length * liveP, it, true) }
                        val glowA = if (connected) 1f else 0.7f
                        drawPath(drawn, accent.copy(alpha = 0.14f * glowA), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(drawn, accent.copy(alpha = 0.28f * glowA), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(drawn, Brush.linearGradient(listOf(accent.copy(alpha = 0.6f), accent), from, to),
                            style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                        if (!reduced) {
                            if (connected && liveP >= 0.999f) {
                                // Поток частиц: плотность и скорость — от текущего трафика.
                                val bps = traffic.value().coerceAtLeast(0L).toFloat()
                                val load = (ln(1f + bps / 2048f) / ln(1f + 4_000_000f / 2048f)).coerceIn(0f, 1f)
                                val count = 7 + (load * 11).toInt()
                                val speed = 0.16f + load * 0.5f
                                for (i in 0 until count) {
                                    val f = ((t * speed + i.toFloat() / count) % 1f)
                                    val p = measure.getPosition(length * f)
                                    val a = sin(PI * f).toFloat()
                                    drawCircle(accent.copy(alpha = 0.35f * a), 4.5.dp.toPx(), p)
                                    drawCircle(Color.White.copy(alpha = 0.9f * a), (1.4f + load).dp.toPx(), p)
                                }
                            } else {
                                // Комета по прорисованной части.
                                val head = ((t * 0.9f) % 1f) * liveP
                                for (k in 0 until 6) {
                                    val f = head - k * 0.025f
                                    if (f <= 0f) break
                                    val p = measure.getPosition(length * f)
                                    val a = 1f - k / 6f
                                    if (k == 0) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f), Color.Transparent), p, 10.dp.toPx()), 10.dp.toPx(), p)
                                    drawCircle(Color.White.copy(alpha = 0.85f * a), (2.6f - k * 0.3f).dp.toPx(), p)
                                }
                            }
                        }
                    }
                }
                to?.let { p ->
                    val r = 40.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (connected) 0.30f else 0.14f), Color.Transparent), p, r), r, p)
                    if (!reduced && connected) for (k in 0 until 3) {
                        // Мягкие расходящиеся кольца вокруг страны выхода.
                        val f = ((t * 0.45f + k / 3f) % 1f)
                        drawCircle(accent.copy(alpha = 0.4f * (1f - f)), 20.dp.toPx() + 34.dp.toPx() * f, p, style = Stroke(1.4.dp.toPx()))
                    } else if (!reduced && connecting) {
                        val f = (t * 1.2f) % 1f
                        drawCircle(accent.copy(alpha = 0.5f * (1f - f)), 20.dp.toPx() + 16.dp.toPx() * f, p, style = Stroke(2.dp.toPx()))
                    }
                }
                // «Вы здесь» — лёгкое дыхание.
                if (!reduced) {
                    val pulse = (t * 0.5f) % 1f
                    drawCircle(homeColor.copy(alpha = 0.28f * (1f - pulse)), 7.dp.toPx() + 16.dp.toPx() * pulse, from, style = Stroke(1.5.dp.toPx()))
                }
                drawCircle(colors.panelFill, 7.5.dp.toPx(), from)
                drawCircle(if (connected) accent else homeColor, 4.8.dp.toPx(), from)
            }

            // Подпись «Вы здесь» — в свободную сторону.
            val home2 = homePos
            val labelText = t("Вы здесь", "You are here")
            val measurer = rememberTextMeasurer()
            val labelW = remember(labelText, density) {
                measurer.measure(labelText, TextStyle(fontSize = 10.sp)).size.width + with(density) { 12.dp.toPx() }
            }
            val labelH = with(density) { 20.dp.toPx() }
            val gap = with(density) { 12.dp.toPx() }
            val arcSamples = if (dest != null) {
                val end = view.project(GeoPoint(destLat.floatValue, destLon.floatValue))
                val c = arcControl(home2, end)
                if (hypot(end.x - home2.x, end.y - home2.y) < 12f) emptyList()
                else (1..16).map { i ->
                    val q = i / 16f; val u = 1 - q
                    Offset(u * u * home2.x + 2 * u * q * c.x + q * q * end.x, u * u * home2.y + 2 * u * q * c.y + q * q * end.y) to 6f
                }
            } else emptyList()
            val obstacles = clusters.map { it.center to flagPx / 2f } + listOfNotNull(activePos?.let { it to activePx / 2f }) +
                pointers.map { it.pos to flagPx * 0.8f } + arcSamples
            val candidates = listOf(
                Offset(home2.x + gap, home2.y - labelH / 2f), Offset(home2.x - gap - labelW, home2.y - labelH / 2f),
                Offset(home2.x - labelW / 2f, home2.y + gap), Offset(home2.x - labelW / 2f, home2.y - gap - labelH),
                Offset(home2.x + gap * 0.7f, home2.y + gap * 0.7f), Offset(home2.x - gap * 0.7f - labelW, home2.y + gap * 0.7f),
                Offset(home2.x + gap * 0.7f, home2.y - gap * 0.7f - labelH), Offset(home2.x - gap * 0.7f - labelW, home2.y - gap * 0.7f - labelH)
            )
            val labelPos = candidates.minBy { c ->
                obstacles.sumOf { (o, r) ->
                    val nx = o.x.coerceIn(c.x, c.x + labelW); val ny = o.y.coerceIn(c.y, c.y + labelH)
                    val d = hypot(o.x - nx, o.y - ny)
                    (if (d < r + 4f) (r + 4f - d).toDouble() else 0.0)
                } + (if (c.x < 0 || c.x + labelW > widthPx) 1000.0 else 0.0)
            }
            HomeLabel(labelText, offset = { IntOffset(labelPos.x.toInt(), labelPos.y.toInt()) })

            clusters.forEach { cluster ->
                val lead = cluster.items.first()
                val extra = cluster.items.size - 1
                val label = cluster.items.joinToString { countryDisplayName(it.code) }
                FlagBubble(lead.emoji, flagSize, selected = false, badge = if (extra > 0) "+$extra" else null,
                    contentDescription = label,
                    offset = { IntOffset((cluster.center.x - flagPx / 2).toInt(), (cluster.center.y - flagPx / 2).toInt()) }) {
                    if (extra > 0) {
                        // Тап по кластеру — приблизить к нему.
                        manual = true
                        val g = view.unproject(cluster.center)
                        flyTo(MapWindow(g.lon, g.lat, (span.floatValue / 2.6f).coerceAtLeast(MIN_SPAN_LAT)), animated = !reduced)
                    } else onSelectServer(pickServer(lead.servers, targetServer))
                }
            }
            if (activePos != null && destCode != null) {
                val serversHere = flags.firstOrNull { it.code == destCode }?.servers
                FlagBubble(countryFlagEmoji(destCode) ?: "", activeSize, selected = true, badge = null,
                    contentDescription = countryDisplayName(destCode),
                    offset = { IntOffset((activePos.x - activePx / 2).toInt(), (activePos.y - activePx / 2).toInt()) }) {
                    serversHere?.let { onSelectServer(pickServer(it, targetServer)) }
                }
            }
            pointers.forEach { pointer ->
                EdgePointerChip(pointer, density) {
                    manual = true
                    flyTo(MapWindow(pointer.flag.point.lon, pointer.flag.point.lat, max(span.floatValue, 24f)), animated = !reduced)
                }
            }

            // Статус соединения.
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
            // Вернуть авто-окно (то же, что двойной тап).
            if (manual) Surface(onClick = { manual = false; flyTo(auto, animated = !reduced) }, shape = CircleShape,
                color = colors.panelFill.copy(alpha = 0.92f), border = BorderStroke(1.dp, colors.panelBorder),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MyLocation, t("Показать маршрут", "Show route"), Modifier.size(18.dp), tint = colors.textPrimary)
                }
            }
        }
    }
}

private fun pickServer(list: List<Server>, target: Server?): Server =
    list.firstOrNull { target?.matchesSelection(it) == true }
        ?: list.filter { (it.ping ?: -1) > 0 }.minByOrNull { it.ping ?: Int.MAX_VALUE }
        ?: list.first()

@Composable
private fun FlagBubble(
    emoji: String,
    size: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    badge: String?,
    contentDescription: String,
    offset: androidx.compose.ui.unit.Density.() -> IntOffset,
    onClick: () -> Unit
) {
    val colors = LocalNebulaColors.current
    Box(Modifier.offset(offset)) {
        Surface(
            shape = CircleShape,
            color = colors.panelFill,
            border = BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) colors.accent else colors.panelBorder),
            modifier = Modifier.size(size).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
        ) {
            Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = if (selected) 23.sp else 16.sp) }
        }
        if (badge != null) Text(badge, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp)
            .background(colors.accent, RoundedCornerShape(50)).padding(horizontal = 4.dp),
            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun EdgePointerChip(pointer: EdgePointer, density: androidx.compose.ui.unit.Density, onClick: () -> Unit) {
    val colors = LocalNebulaColors.current
    val w = with(density) { 50.dp.toPx() }; val h = with(density) { 26.dp.toPx() }
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = colors.panelFill.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.panelBorder),
        modifier = Modifier.offset { IntOffset((pointer.pos.x - w / 2).toInt(), (pointer.pos.y - h / 2).toInt()) }
            .size(50.dp, 26.dp)
            .semantics { contentDescription = countryDisplayName(pointer.flag.code) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            // Треугольник строго по лучу на страну (0° — вправо, по часовой — вниз).
            val arrowColor = colors.textSecondary
            Canvas(Modifier.size(10.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val a = pointer.angleDeg * PI.toFloat() / 180f
                fun at(angle: Float, len: Float) = Offset(c.x + cos(angle) * len, c.y + sin(angle) * len)
                drawPath(Path().apply {
                    val tip = at(a, r); val l = at(a + 2.5f, r * 0.9f); val rr = at(a - 2.5f, r * 0.9f)
                    moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(c.x, c.y); lineTo(rr.x, rr.y); close()
                }, arrowColor)
            }
            Spacer(Modifier.width(3.dp))
            Text(pointer.flag.emoji, fontSize = 13.sp)
            if (pointer.count > 1) Text("+${pointer.count - 1}", fontSize = 8.sp, color = colors.textSecondary)
        }
    }
}

@Composable
private fun HomeLabel(text: String, offset: androidx.compose.ui.unit.Density.() -> IntOffset) {
    val colors = LocalNebulaColors.current
    Text(text, Modifier.offset(offset).height(20.dp)
        .background(colors.panelFill.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
        .padding(horizontal = 6.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colors.textSecondary, maxLines = 1)
}

/** Точки суши в окне; точки у маршрута (polyline [route]) — акцентной «дорожкой». */
private fun DrawScope.drawLand(land: FloatArray, buf: FloatArray, glow: FloatArray, paint: Paint, view: MapView,
    step: Float, color: Color, glowColor: Color, route: FloatArray?, glowRadius: Float) {
    val halfW = size.width / 2f; val halfH = size.height / 2f
    val sx = view.kx * view.scale
    val lonHalf = halfW / sx + 1f; val latHalf = halfH / view.scale + 1f
    val r2 = glowRadius * glowRadius
    var n = 0; var g = 0
    var i = 0
    while (i < land.size) {
        val lon = land[i]; val lat = land[i + 1]
        if (abs(lon - view.lonC) <= lonHalf && abs(lat - view.latC) <= latHalf) {
            val x = halfW + (lon - view.lonC) * sx
            val y = halfH - (lat - view.latC) * view.scale
            if (route != null && glowColor.alpha > 0.01f &&
                (segDist2(x, y, route[0], route[1], route[2], route[3]) < r2 || segDist2(x, y, route[2], route[3], route[4], route[5]) < r2)) {
                glow[g++] = x; glow[g++] = y
            } else { buf[n++] = x; buf[n++] = y }
        }
        i += 2
    }
    paint.strokeWidth = (step * min(sx, view.scale) * 0.44f).coerceIn(1.4f, 6.dp.toPx())
    drawIntoCanvas {
        if (n > 0) { paint.color = color.toArgb(); it.nativeCanvas.drawPoints(buf, 0, n, paint) }
        if (g > 0) { paint.color = glowColor.toArgb(); it.nativeCanvas.drawPoints(glow, 0, g, paint) }
    }
}

private fun segDist2(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    val tt = if (len2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    val qx = ax + dx * tt - px; val qy = ay + dy * tt - py
    return qx * qx + qy * qy
}

private fun arcControl(from: Offset, to: Offset): Offset {
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    val dx = to.x - from.x; val dy = to.y - from.y
    val len = hypot(dx, dy)
    var nx = -dy / len; var ny = dx / len
    if (ny > 0f) { nx = -nx; ny = -ny }
    val lift = len * 0.3f
    return Offset(mid.x + nx * lift, mid.y + ny * lift)
}

/** Точка дуги при t = 0.5. */
private fun arcMidpoint(from: Offset, to: Offset): Offset {
    val c = arcControl(from, to)
    return Offset(0.25f * from.x + 0.5f * c.x + 0.25f * to.x, 0.25f * from.y + 0.5f * c.y + 0.25f * to.y)
}

/** Дуга по квадратичной Безье, выгнутая вверх. */
private fun arcPath(from: Offset, to: Offset): Path {
    val c = arcControl(from, to)
    return Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(c.x, c.y, to.x, to.y)
    }
}

/**
 * Монотонная фаза (секунды) от кадровых часов; когда движение выключено или экран не виден —
 * часы не крутятся (статичный кадр). Читается только в отрисовке.
 */
@Composable
private fun rememberMapPhase(enabled: Boolean): State<Float> {
    val state = remember { mutableFloatStateOf(0.35f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val base = state.floatValue
        val start = withFrameNanos { it }
        while (true) withFrameNanos { state.floatValue = base + (it - start) / 1_000_000_000f }
    }
    return state
}
