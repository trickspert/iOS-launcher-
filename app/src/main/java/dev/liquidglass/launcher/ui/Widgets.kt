package dev.liquidglass.launcher.ui

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.liquidglass.launcher.data.WidgetType
import dev.liquidglass.launcher.glass.GlassTouch
import dev.liquidglass.launcher.glass.liquidGlass
import dev.liquidglass.launcher.icons.Glyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val WidgetCorner = 26.dp

@Composable
fun WidgetBody(type: WidgetType, width: Dp, height: Dp, touch: GlassTouch?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(width)
            .height(height)
            .liquidGlass(WidgetCorner, touch = touch, refraction = 1.2f),
    ) {
        when (type) {
            WidgetType.BATTERY -> BatteryWidget()
            WidgetType.CALENDAR -> CalendarWidget()
            WidgetType.CLOCK -> ClockWidget()
            WidgetType.MUSIC -> MusicWidget()
        }
    }
}

/** What tapping a widget does. */
fun widgetTap(type: WidgetType, state: dev.liquidglass.launcher.LauncherState) {
    when (type) {
        WidgetType.BATTERY -> state.openPackageIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        WidgetType.CALENDAR -> {
            if (!state.calendarGranted) state.requestCalendar()
            else state.repo.apps.firstOrNull { it.glyph == Glyph.CALENDAR }?.let { state.launch(it, null) }
                ?: state.openPackageIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
        }
        WidgetType.CLOCK -> state.repo.apps.firstOrNull { it.glyph == Glyph.CLOCK }?.let { state.launch(it, null) }
            ?: state.openPackageIntent(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
        WidgetType.MUSIC -> openMusic(state)
    }
}

@Composable
private fun BatteryWidget() {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) level = l * 100 / s
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val sticky = ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED) // protected system broadcast
        sticky?.let { r.onReceive(ctx, it) }
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    val frac by animateFloatAsState((level.coerceAtLeast(0)) / 100f, spring(dampingRatio = 0.8f, stiffness = 60f), label = "batt")
    val low = level in 0..20 && !charging
    // Like the iOS Batteries widget: a 2×2 set of rings. The first is this phone; the rest wait for devices.
    Canvas(Modifier.fillMaxSize().padding(13.dp)) {
        val gap = size.minDimension * 0.07f
        val d = (size.minDimension - gap) / 2f
        val sw = d * 0.105f
        for (i in 0 until 4) {
            val ox = (i % 2) * (d + gap) + (size.width - (2 * d + gap)) / 2f
            val oy = (i / 2) * (d + gap) + (size.height - (2 * d + gap)) / 2f
            val tl = Offset(ox + sw / 2, oy + sw / 2)
            val arc = Size(d - sw, d - sw)
            val c = Offset(ox + d / 2, oy + d / 2)
            drawArc(Color.White.copy(alpha = if (i == 0) 0.22f else 0.13f), 0f, 360f, false, tl, arc, style = Stroke(sw))
            if (i != 0) continue
            val ring = if (low) Color(0xFFFF453A) else Color.White
            if (charging) {
                // Leave a notch at the top for the charging bolt.
                val sweep = (360f * frac - 24f).coerceAtLeast(0f)
                drawArc(ring, -78f, sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                val bw = d * 0.085f
                val bx = c.x
                val by = oy - bw * 0.35f
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(bx + bw * 0.35f, by); lineTo(bx - bw * 0.55f, by + bw * 1.25f); lineTo(bx - bw * 0.02f, by + bw * 1.25f)
                    lineTo(bx - bw * 0.35f, by + bw * 2.3f); lineTo(bx + bw * 0.55f, by + bw * 0.95f); lineTo(bx + bw * 0.02f, by + bw * 0.95f); close()
                }
                drawPath(bolt, Color.White)
            } else {
                drawArc(ring, -90f, 360f * frac, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
            val pw = d * 0.2f
            val ph = d * 0.36f
            drawRoundRect(
                Color.White, topLeft = Offset(c.x - pw / 2, c.y - ph / 2), size = Size(pw, ph),
                cornerRadius = CornerRadius(pw * 0.26f, pw * 0.26f), style = Stroke(d * 0.03f),
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.35f), topLeft = Offset(c.x - pw / 2, c.y - ph / 2), size = Size(pw, ph),
                cornerRadius = CornerRadius(pw * 0.26f, pw * 0.26f),
            )
            drawLine(Color.White, Offset(c.x - pw * 0.16f, c.y + ph * 0.36f), Offset(c.x + pw * 0.16f, c.y + ph * 0.36f), strokeWidth = d * 0.022f, cap = StrokeCap.Round)
        }
    }
}

// ------------------------------------------------------------------ music

fun openMusic(state: dev.liquidglass.launcher.LauncherState) {
    val np = dev.liquidglass.launcher.NowPlaying
    when {
        !state.badgesEnabled -> state.openPackageIntent(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        np.pkg != null -> state.repo.apps.firstOrNull { it.packageName == np.pkg }?.let { state.launch(it, null) }
        else -> state.repo.apps.firstOrNull { it.glyph == Glyph.MUSIC }?.let { state.launch(it, null) }
    }
}

/** Now Playing, fed by the active media session (needs notification access, which also powers badges). */
@Composable
private fun MusicWidget() {
    val state = LocalLauncher.current
    val np = dev.liquidglass.launcher.NowPlaying
    val art = np.art
    Column(Modifier.fillMaxSize().padding(13.dp)) {
        Row(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF6B81), Color(0xFFB04CE6))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    androidx.compose.foundation.Image(art, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    NoteGlyph(Modifier.fillMaxSize(0.5f), Color.White.copy(alpha = 0.9f))
                }
            }
            Spacer(Modifier.weight(1f))
            NoteGlyph(Modifier.width(17.dp).height(17.dp), Color.White.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            np.title ?: "Not Playing",
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            when {
                !state.badgesEnabled -> "Tap to connect"
                np.title == null -> "Music"
                else -> np.artist ?: ""
            },
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        InGlass {
            GlassButton(
                onClick = { if (np.canControl) np.toggle() else openMusic(state) },
                modifier = Modifier.height(28.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(10.dp).height(11.dp)) {
                        if (np.playing) {
                            val w = size.width * 0.32f
                            drawRoundRect(Color.White, size = Size(w, size.height), cornerRadius = CornerRadius(w * 0.3f))
                            drawRoundRect(Color.White, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(w * 0.3f))
                        } else {
                            val tri = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close()
                            }
                            drawPath(tri, Color.White)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    BasicText(if (np.playing) "Pause" else "Play", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun NoteGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val s = size.minDimension
        val beam = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.34f, s * 0.20f); lineTo(s * 0.86f, s * 0.08f); lineTo(s * 0.86f, s * 0.26f); lineTo(s * 0.34f, s * 0.38f); close()
        }
        drawPath(beam, color)
        drawRect(color, topLeft = Offset(s * 0.34f, s * 0.2f), size = Size(s * 0.085f, s * 0.58f))
        drawRect(color, topLeft = Offset(s * 0.775f, s * 0.1f), size = Size(s * 0.085f, s * 0.56f))
        drawOval(color, topLeft = Offset(s * 0.13f, s * 0.66f), size = Size(s * 0.3f, s * 0.24f))
        drawOval(color, topLeft = Offset(s * 0.565f, s * 0.54f), size = Size(s * 0.3f, s * 0.24f))
    }
}

private data class Event(val title: String, val time: String, val color: Int)

@Composable
private fun CalendarWidget() {
    val state = LocalLauncher.current
    val ctx = LocalContext.current
    val now = remember(state.clockTick) { Calendar.getInstance() }
    val events by produceState(initialValue = emptyList<Event>(), state.calendarGranted, state.clockTick) {
        value = if (state.calendarGranted) withContext(Dispatchers.IO) { loadEvents(ctx) } else emptyList()
    }
    val colorful = state.settings.iconStyle.colorful
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
        BasicText(
            now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())?.uppercase(Locale.getDefault()) ?: "",
            style = TextStyle(color = if (colorful) Color(0xFFFF453A) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        )
        BasicText(
            now.get(Calendar.DAY_OF_MONTH).toString(),
            style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light),
        )
        Spacer(Modifier.weight(1f))
        when {
            !state.calendarGranted -> BasicText("Tap to show events", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp))
            events.isEmpty() -> BasicText("No more events today", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp))
            else -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                events.take(2).forEach { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Box(Modifier.width(3.dp).height(28.dp).background(Color(e.color or 0xFF000000.toInt()), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            BasicText(e.title, style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(e.time, style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun loadEvents(ctx: Context): List<Event> = runCatching {
    val now = System.currentTimeMillis()
    val end = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now); ContentUris.appendId(it, end)
    }.build()
    val proj = arrayOf(
        CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
    )
    val out = ArrayList<Event>()
    val fmt = DateFormat.getTimeFormat(ctx)
    ctx.contentResolver.query(uri, proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
        while (c.moveToNext() && out.size < 3) {
            val title = c.getString(0) ?: "(No title)"
            val allDay = c.getInt(2) == 1
            val time = if (allDay) "All day" else fmt.format(java.util.Date(c.getLong(1)))
            out += Event(title, time, c.getInt(3))
        }
    }
    out
}.getOrDefault(emptyList())

@Composable
private fun ClockWidget() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val sec = cal.get(Calendar.SECOND)
    val minute = cal.get(Calendar.MINUTE)
    val hour = cal.get(Calendar.HOUR)
    val secAnim by animateFloatAsState(
        (minute * 60 + sec) * 6f, spring(dampingRatio = 0.4f, stiffness = 900f), label = "sec",
    )
    val colorful = LocalLauncher.current.settings.iconStyle.colorful
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(Color.White.copy(alpha = 0.10f), r)
            for (i in 0 until 60) {
                val a = i * PI / 30
                val major = i % 5 == 0
                val r1 = r * (if (major) 0.84f else 0.89f)
                val r2 = r * 0.95f
                drawLine(
                    Color.White.copy(alpha = if (major) 0.95f else 0.45f),
                    Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()),
                    Offset(c.x + (cos(a) * r2).toFloat(), c.y + (sin(a) * r2).toFloat()),
                    strokeWidth = if (major) r * 0.03f else r * 0.014f,
                    cap = StrokeCap.Round,
                )
            }
            val hourAngle = (hour + minute / 60f) * 30f
            val minAngle = (minute + sec / 60f) * 6f
            rotate(hourAngle, c) {
                drawLine(Color.White, c, Offset(c.x, c.y - r * 0.5f), strokeWidth = r * 0.075f, cap = StrokeCap.Round)
            }
            rotate(minAngle, c) {
                drawLine(Color.White, c, Offset(c.x, c.y - r * 0.76f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
            }
            val secColor = if (colorful) Color(0xFFFF9F0A) else Color.White
            rotate(secAnim, c) {
                drawLine(secColor, Offset(c.x, c.y + r * 0.16f), Offset(c.x, c.y - r * 0.84f), strokeWidth = r * 0.018f, cap = StrokeCap.Round)
            }
            drawCircle(secColor, r * 0.05f, c)
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.02f, c)
        }
    }
}
