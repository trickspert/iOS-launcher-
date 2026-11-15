package dev.liquidglass.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquidglass.launcher.BadgeCenter
import dev.liquidglass.launcher.LauncherState
import dev.liquidglass.launcher.MenuTarget
import dev.liquidglass.launcher.Overlay
import dev.liquidglass.launcher.Source
import dev.liquidglass.launcher.data.AppEntry
import dev.liquidglass.launcher.data.AppItem
import dev.liquidglass.launcher.data.FolderItem
import dev.liquidglass.launcher.data.HomeItem
import dev.liquidglass.launcher.data.IconStyle
import dev.liquidglass.launcher.data.LauncherSettings
import dev.liquidglass.launcher.glass.GlassMode
import dev.liquidglass.launcher.glass.GlassTouch
import dev.liquidglass.launcher.glass.LocalGlassMode
import dev.liquidglass.launcher.glass.defaultTint
import dev.liquidglass.launcher.glass.liquidGlass
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

val LocalLauncher = staticCompositionLocalOf<LauncherState> { error("LauncherState not provided") }

val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
    letterSpacing = 0.1.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 1f), 5f),
)

/** Everything inside a glass panel is a thin clear layer on that panel, not another lens on the wallpaper. */
@Composable
fun InGlass(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalGlassMode provides GlassMode.FLAT, content = content)

/** Forces wallpaper-refracting glass (used for lifted copies of home screen items). */
@Composable
fun OnWallpaper(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalGlassMode provides GlassMode.WALL, content = content)

val TitleStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
val BodyStyle = TextStyle(color = Color.White, fontSize = 16.sp)
val CaptionStyle = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)

fun tileTint(style: IconStyle, s: LauncherSettings): Color = when (style) {
    IconStyle.DEFAULT -> defaultTint(s)
    IconStyle.DARK -> Color(0xFF0B0C10).copy(alpha = 0.46f)
    IconStyle.CLEAR -> Color.White.copy(alpha = 0.04f + 0.2f * s.glassTint)
    IconStyle.TINTED -> s.accent.copy(alpha = 0.3f)
}

/** Corner radius used for app tiles (close to the iOS continuous corner). */
fun tileRadius(size: Dp) = size * 0.27f

/** The glass tile with the app's glyph / icon. */
@Composable
fun IconTile(app: AppEntry, size: Dp, modifier: Modifier = Modifier, touch: GlassTouch? = null) {
    val state = LocalLauncher.current
    val s = state.settings
    val style = s.iconStyle
    val accent = s.accent.toArgb()
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(8)
    val tick = if (app.glyph?.dynamic == true) state.clockTick else 0
    val now = remember(tick) { Calendar.getInstance() }
    val key = state.icons.key(app, style, accent, px, now)
    val render by produceState(initialValue = state.icons.peek(key), key) {
        value = state.icons.get(app, style, accent, px, now)
    }
    val r = render
    val radius = tileRadius(size)
    Box(modifier.size(size).liquidGlass(radius, tint = r?.tileTint ?: tileTint(style, s), touch = touch, refraction = 0.85f)) {
        if (r != null) {
            Image(
                bitmap = r.bitmap,
                contentDescription = app.label,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().then(if (r.fullBleed) Modifier.clip(RoundedCornerShape(radius)) else Modifier),
            )
            if (r.fullBleed) GlassRim(radius)
        }
    }
}

/** Specular rim and sheen drawn over opaque content so it still reads as glass. */
@Composable
fun GlassRim(radius: Dp) {
    val state = LocalLauncher.current
    Canvas(Modifier.fillMaxSize()) {
        val r = radius.toPx()
        val l = if (state.settings.motionLight) state.light.dir else dev.liquidglass.launcher.glass.DEFAULT_LIGHT
        val c = center
        val reach = max(size.width, size.height) / 2f
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), 0f, size.height * 0.5f),
            cornerRadius = CornerRadius(r, r),
        )
        drawRoundRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.9f), 0.5f to Color.White.copy(alpha = 0.1f), 1f to Color.White.copy(alpha = 0.5f),
                start = c + l * reach, end = c - l * reach,
            ),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(1.3.dp.toPx()),
        )
    }
}

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    val s = LocalLauncher.current.settings
    val light = !s.iconStyle.colorful
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier
            .height(21.dp)
            .background(if (light) Color.White else Color(0xFFFF3B30), RoundedCornerShape(50))
            .padding(horizontal = 6.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = if (light) Color(0xFF1C1C1E) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Shared tap / long-press / drag handling for anything on the home screen. */
@Composable
fun Modifier.launcherGestures(
    key: Any,
    touch: GlassTouch?,
    bounds: () -> Rect,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    dragItem: HomeItem? = null,
    source: Source = Source.Library,
    onEditTap: (() -> Unit)? = null,
): Modifier {
    val state = LocalLauncher.current
    val scope = rememberCoroutineScope()
    val edit = state.editMode
    return this.pointerInput(key, edit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val b = bounds()
            if (edit) {
                if (dragItem != null) state.armDrag(dragItem, source, b, b.topLeft + down.position)
                touch?.down(scope, down.position)
                val up = waitForUpOrCancellation()
                touch?.up(scope)
                if (up != null) { up.consume(); onEditTap?.invoke() }
                return@awaitEachGesture
            }
            touch?.down(scope, down.position)
            val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var r = 0
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    if (c == null || c.isConsumed) { r = 2; break }
                    if (!c.pressed) { c.consume(); r = 1; break }
                    touch?.point = c.position
                    if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) { r = 2; break }
                }
                r
            }
            when (result) {
                null -> {
                    if (onLongPress != null) {
                        state.haptic()
                        onLongPress()
                        if (dragItem != null) state.armDrag(dragItem, source, b, b.topLeft + down.position)
                    }
                    touch?.up(scope)
                }
                1 -> { touch?.up(scope); onTap() }
                else -> touch?.up(scope)
            }
        }
    }
}

/** iOS-style wiggle while editing the home screen. */
@Composable
fun Modifier.jiggle(active: Boolean, seed: Int): Modifier {
    if (!active) return this
    val t = rememberInfiniteTransition(label = "jiggle")
    val dur = 118 + abs(seed % 5) * 9
    val rot by t.animateFloat(
        initialValue = -1.7f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(dur, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rot",
    )
    val bob by t.animateFloat(
        initialValue = -0.6f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(dur + 37, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    return this.graphicsLayer { rotationZ = if (seed % 2 == 0) rot else -rot; translationY = bob * density }
}

/** App icon with label, badge, edit-mode wiggle and full gesture handling. */
@Composable
fun AppIcon(
    app: AppEntry,
    iconSize: Dp,
    showLabel: Boolean,
    source: Source,
    modifier: Modifier = Modifier,
    homeItem: HomeItem? = null,
    highlight: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    val state = LocalLauncher.current
    val touch = remember { GlassTouch() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val badge = BadgeCenter.counts[app.packageName] ?: 0
    val grow by animateFloatAsState(if (highlight) 1.18f else 1f, spring(dampingRatio = 0.55f, stiffness = 400f), label = "grow")
    Column(modifier.width(iconSize + 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(iconSize)
                .jiggle(state.editMode && onRemove != null, app.key.hashCode())
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .graphicsLayer {
                    val p = touch.press.value
                    scaleX = grow * (1f - 0.08f * p); scaleY = grow * (1f - 0.08f * p)
                }
                .launcherGestures(
                    key = app.key,
                    touch = touch,
                    bounds = { bounds },
                    onTap = { state.launch(app, bounds) },
                    onLongPress = { state.overlay = Overlay.Menu(MenuTarget.App(app.key, source), bounds) },
                    dragItem = homeItem,
                    source = source,
                ),
        ) {
            IconTile(app, iconSize, touch = touch)
            if (badge > 0 && !state.editMode) Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp))
            if (onRemove != null) RemoveBadge(state.editMode, onRemove, Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = (-7).dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                if (app.isWork) "${app.label} ·" else app.label,
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RemoveBadge(visible: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "rm")
    if (scale <= 0.01f) return
    Box(
        modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .liquidGlass(12.dp, tint = Color(0xFF3A3A3C).copy(alpha = 0.55f), refraction = 0.5f, shadow = 0.5f)
            .pointerInput(Unit) { detectTapGestures { onRemove() } },
        contentAlignment = Alignment.Center,
    ) {
        Symbol(Sym.MINUS, 12.dp, Color.White)
    }
}

/** Folder tile: glass square holding a 3×3 preview of its apps. */
@Composable
fun FolderIcon(
    folder: FolderItem,
    iconSize: Dp,
    showLabel: Boolean,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val state = LocalLauncher.current
    val touch = remember { GlassTouch() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val grow by animateFloatAsState(if (highlight) 1.18f else 1f, spring(dampingRatio = 0.55f, stiffness = 400f), label = "grow")
    val open = { state.overlay = Overlay.Folder(folder.id, folder.name, folder.apps, bounds) }
    Column(modifier.width(iconSize + 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(iconSize)
                .jiggle(state.editMode, folder.id.hashCode())
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .graphicsLayer {
                    val p = touch.press.value
                    scaleX = grow * (1f - 0.08f * p); scaleY = grow * (1f - 0.08f * p)
                }
                .launcherGestures(
                    key = folder.id,
                    touch = touch,
                    bounds = { bounds },
                    onTap = open,
                    onLongPress = { state.overlay = Overlay.Menu(MenuTarget.Folder(folder.id), bounds) },
                    dragItem = folder,
                    source = Source.Page(pageIndex),
                    onEditTap = open,
                ),
        ) {
            FolderTile(folder.apps, iconSize, touch)
            RemoveBadge(state.editMode, { state.removeFromHome(MenuTarget.Folder(folder.id)) }, Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = (-7).dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            BasicText(folder.name, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FolderTile(keys: List<String>, size: Dp, touch: GlassTouch? = null) {
    val state = LocalLauncher.current
    val apps = keys.mapNotNull { state.appFor(it) }.take(9)
    val pad = size * 0.13f
    val gap = size * 0.05f
    val mini = (size - pad * 2 - gap * 2) / 3
    Box(Modifier.size(size).liquidGlass(tileRadius(size), touch = touch, refraction = 0.85f)) {
        Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until 3) {
                        val a = apps.getOrNull(row * 3 + col)
                        if (a != null) MiniIcon(a, mini) else Spacer(Modifier.size(mini))
                    }
                }
            }
        }
    }
}

/** Small flat icon used inside folder previews and the App Library clusters. */
@Composable
fun MiniIcon(app: AppEntry, size: Dp) {
    val state = LocalLauncher.current
    val s = state.settings
    val style = s.iconStyle
    val accent = s.accent.toArgb()
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(8)
    val now = remember { Calendar.getInstance() }
    val key = state.icons.key(app, style, accent, px, now)
    val render by produceState(initialValue = state.icons.peek(key), key) { value = state.icons.get(app, style, accent, px, now) }
    val r = render
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(r?.tileTint ?: Color.White.copy(alpha = if (style == IconStyle.DARK) 0.08f else 0.2f)),
    ) {
        if (r != null) Image(r.bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
}

/** Capsule / rounded glass button with liquid press response (swells, glows and stretches with the finger). */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 50.dp,
    tint: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val touch = remember { GlassTouch() }
    val scope = rememberCoroutineScope()
    val stretch = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    Box(
        modifier
            .graphicsLayer {
                val p = touch.press.value
                val st = stretch.value
                scaleX = (1f + 0.08f * p) * (1f + (abs(st.x) - abs(st.y) * 0.5f).coerceIn(-0.06f, 0.12f))
                scaleY = (1f + 0.08f * p) * (1f + (abs(st.y) - abs(st.x) * 0.5f).coerceIn(-0.06f, 0.12f))
                translationX = st.x * size.width * 0.25f
                translationY = st.y * size.height * 0.25f
            }
            .liquidGlass(corner, tint = tint, touch = touch)
            .pointerInput(onClick) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    touch.down(scope, down.position)
                    var inside = true
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) {
                            c.consume()
                            break
                        }
                        c.consume()
                        touch.point = c.position
                        val d = c.position - down.position
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        scope.launch { stretch.snapTo(Offset((d.x / w).coerceIn(-0.6f, 0.6f) * 0.35f, (d.y / h).coerceIn(-0.6f, 0.6f) * 0.35f)) }
                        inside = c.position.x in -24f..w + 24f && c.position.y in -24f..h + 24f
                    }
                    touch.up(scope)
                    scope.launch { stretch.animateTo(Offset.Zero, spring(dampingRatio = 0.35f, stiffness = 420f)) }
                    if (inside) onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ------------------------------------------------------------------ controls

/** iOS 26 switch: the thumb turns into a liquid glass lens while pressed. */
@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "sw")
    val touch = remember { GlassTouch() }
    val scope = rememberCoroutineScope()
    val pressed = touch.press.value
    Box(
        Modifier
            .size(width = 58.dp, height = 30.dp)
            .pointerInput(checked) {
                awaitEachGesture {
                    val d = awaitFirstDown(); d.consume()
                    touch.down(scope, d.position)
                    val up = waitForUpOrCancellation()
                    touch.up(scope)
                    if (up != null) { up.consume(); onChange(!checked) }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val track = lerpColor(Color.White.copy(alpha = 0.18f), Color(0xFF34C759), pos)
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2, size.height / 2))
        }
        val thumbW = 36.dp + 10.dp * pressed
        val thumbH = 26.dp + 8.dp * pressed
        val travel = 58.dp - 4.dp - 36.dp
        Box(
            Modifier
                .offset(x = 2.dp + travel * pos - 5.dp * pressed, y = 2.dp - 4.dp * pressed)
                .size(thumbW, thumbH)
                .then(
                    if (pressed > 0.05f) Modifier.liquidGlass(50.dp, tint = Color.White.copy(alpha = 0.08f), refraction = 1.6f, touch = touch)
                    else Modifier.background(Color.White, RoundedCornerShape(50)),
                ),
        )
    }
}

/** Slider whose thumb becomes a magnifying glass lens while dragged. */
@Composable
fun GlassSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, track: Brush? = null) {
    val touch = remember { GlassTouch() }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown(); d.consume()
                    touch.down(scope, Offset(size.height / 2f, size.height / 2f))
                    val thumb = 30.dp.toPx()
                    fun set(x: Float) = onChange(((x - thumb / 2) / (size.width - thumb)).coerceIn(0f, 1f))
                    set(d.position.x)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                        if (!c.pressed) break
                        c.consume(); set(c.position.x)
                    }
                    touch.up(scope)
                }
            },
    ) {
        val p = touch.press.value
        val w = maxWidth
        val thumb = 30.dp
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val h = 5.dp.toPx()
            val x = thumb.toPx() / 2 + (size.width - thumb.toPx()) * value
            if (track != null) {
                drawRoundRect(track, topLeft = Offset(0f, y - h / 2), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = CornerRadius(h, h))
            } else {
                drawRoundRect(Color.White.copy(alpha = 0.2f), topLeft = Offset(0f, y - h / 2), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = CornerRadius(h, h))
                drawRoundRect(Color(0xFF0A84FF), topLeft = Offset(0f, y - h / 2), size = androidx.compose.ui.geometry.Size(x, h), cornerRadius = CornerRadius(h, h))
            }
        }
        val tw = thumb + 14.dp * p
        val th = 22.dp + 12.dp * p
        Box(
            Modifier
                .offset(x = (w - thumb) * value - 7.dp * p, y = (34.dp - th) / 2)
                .size(tw, th)
                .then(
                    if (p > 0.05f) Modifier.liquidGlass(50.dp, tint = Color.White.copy(alpha = 0.06f), refraction = 1.8f, touch = touch)
                    else Modifier.background(Color.White, RoundedCornerShape(50)),
                ),
        )
    }
}

/** Segmented control with a sliding glass selection lens. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = 380f), label = "seg")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50)),
    ) {
        val segW = maxWidth / options.size
        Box(
            Modifier
                .offset(x = segW * anim + 3.dp, y = 3.dp)
                .size(segW - 6.dp, 32.dp)
                .liquidGlass(50.dp, tint = Color.White.copy(alpha = 0.22f), refraction = 1.2f),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .pointerInput(i) { detectTapGestures { onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(o, style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal))
                }
            }
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

// ------------------------------------------------------------------ symbols

enum class Sym { SEARCH, PLUS, MINUS, INFO, TRASH, GRID, PENCIL, CHEVRON, CLOSE, FOLDER_OUT, SHARE, CHECK }

@Composable
fun Symbol(sym: Sym, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawSymbol(sym, color) }
}

fun DrawScope.drawSymbol(sym: Sym, color: Color) {
    val s = size.minDimension
    val w = s * 0.11f
    val st = Stroke(width = w, cap = StrokeCap.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = w, cap = StrokeCap.Round)
    when (sym) {
        Sym.SEARCH -> {
            drawCircle(color, radius = s * 0.3f, center = Offset(s * 0.42f, s * 0.42f), style = st)
            line(0.64f, 0.64f, 0.88f, 0.88f)
        }
        Sym.PLUS -> { line(0.5f, 0.18f, 0.5f, 0.82f); line(0.18f, 0.5f, 0.82f, 0.5f) }
        Sym.MINUS -> line(0.2f, 0.5f, 0.8f, 0.5f)
        Sym.CLOSE -> { line(0.25f, 0.25f, 0.75f, 0.75f); line(0.75f, 0.25f, 0.25f, 0.75f) }
        Sym.CHECK -> { line(0.2f, 0.52f, 0.42f, 0.74f); line(0.42f, 0.74f, 0.82f, 0.28f) }
        Sym.CHEVRON -> { line(0.38f, 0.2f, 0.66f, 0.5f); line(0.66f, 0.5f, 0.38f, 0.8f) }
        Sym.INFO -> {
            drawCircle(color, radius = s * 0.42f, center = center, style = Stroke(w * 0.8f))
            line(0.5f, 0.46f, 0.5f, 0.72f)
            drawCircle(color, radius = w * 0.7f, center = Offset(s * 0.5f, s * 0.3f))
        }
        Sym.TRASH -> {
            line(0.18f, 0.25f, 0.82f, 0.25f)
            line(0.4f, 0.14f, 0.6f, 0.14f)
            val p = Path().apply {
                moveTo(0.26f * s, 0.32f * s); lineTo(0.31f * s, 0.86f * s); lineTo(0.69f * s, 0.86f * s); lineTo(0.74f * s, 0.32f * s)
            }
            drawPath(p, color, style = Stroke(w * 0.85f, cap = StrokeCap.Round))
        }
        Sym.GRID -> {
            val r = CornerRadius(s * 0.07f, s * 0.07f)
            for (i in 0 until 2) for (j in 0 until 2) {
                drawRoundRect(
                    color, topLeft = Offset(s * (0.14f + i * 0.4f), s * (0.14f + j * 0.4f)),
                    size = androidx.compose.ui.geometry.Size(s * 0.32f, s * 0.32f), cornerRadius = r, style = Stroke(w * 0.8f),
                )
            }
        }
        Sym.PENCIL -> {
            line(0.22f, 0.78f, 0.74f, 0.26f)
            line(0.22f, 0.78f, 0.2f, 0.84f)
        }
        Sym.FOLDER_OUT -> {
            drawRoundRect(
                color, topLeft = Offset(s * 0.12f, s * 0.3f), size = androidx.compose.ui.geometry.Size(s * 0.76f, s * 0.54f),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f), style = Stroke(w * 0.8f),
            )
            line(0.5f, 0.68f, 0.5f, 0.12f); line(0.36f, 0.26f, 0.5f, 0.12f); line(0.64f, 0.26f, 0.5f, 0.12f)
        }
        Sym.SHARE -> {
            drawCircle(color, s * 0.11f, Offset(s * 0.28f, s * 0.5f))
            drawCircle(color, s * 0.11f, Offset(s * 0.72f, s * 0.25f))
            drawCircle(color, s * 0.11f, Offset(s * 0.72f, s * 0.75f))
            line(0.28f, 0.5f, 0.72f, 0.25f); line(0.28f, 0.5f, 0.72f, 0.75f)
        }
    }
}

