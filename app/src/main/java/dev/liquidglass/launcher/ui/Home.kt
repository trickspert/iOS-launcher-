package dev.liquidglass.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.liquidglass.launcher.LauncherState
import dev.liquidglass.launcher.MenuTarget
import dev.liquidglass.launcher.Overlay
import dev.liquidglass.launcher.GridMetrics
import dev.liquidglass.launcher.Source
import dev.liquidglass.launcher.data.AppItem
import dev.liquidglass.launcher.data.FolderItem
import dev.liquidglass.launcher.data.HomeItem
import dev.liquidglass.launcher.data.WidgetItem
import dev.liquidglass.launcher.data.pack
import dev.liquidglass.launcher.glass.Backdrop
import dev.liquidglass.launcher.glass.GlassEnv
import dev.liquidglass.launcher.glass.GlassMode
import dev.liquidglass.launcher.glass.LocalBackdrop
import dev.liquidglass.launcher.glass.LocalGlassMode
import dev.liquidglass.launcher.glass.backdropSource
import dev.liquidglass.launcher.glass.GlassTouch
import dev.liquidglass.launcher.glass.LocalGlass
import dev.liquidglass.launcher.glass.liquidGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Sizes shared by every home page. */
data class HomeMetrics(
    val cols: Int,
    val rows: Int,
    val hPad: Dp,
    val topPad: Dp,
    val cellW: Dp,
    val cellH: Dp,
    val icon: Dp,
    val iconTop: Dp,
) {
    val widgetW get() = cellW * 2 - (cellW - icon)
    val widgetH get() = cellH + icon
}

@Composable
fun LauncherRoot(state: LauncherState) {
    CompositionLocalProvider(LocalLauncher provides state) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF5D88D6))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull() ?: continue
                            if (state.onRootPointer(ch.position, ch.pressed, viewConfiguration.touchSlop)) {
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
        ) {
            val wPx = constraints.maxWidth
            val hPx = constraints.maxHeight
            val s = state.settings
            LaunchedEffect(wPx, hPx, s.wallpaper, s.wallpaperVersion) {
                state.screen = Size(wPx.toFloat(), hPx.toFloat())
                state.ensureWallpaper(wPx, hPx)
            }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
                    state.clockTick++
                }
            }
            val wp = state.wallpaper
            val bl = state.blurred
            val sh = state.sharp
            if (wp != null && bl != null && sh != null) {
                val env = remember(bl, sh, wPx, hPx) {
                    GlassEnv(sh, bl, Size(wPx.toFloat(), hPx.toFloat()), state.light, s) { state.zoom.value }
                }
                val backdropLayer = rememberGraphicsLayer()
                val backdrop = remember(backdropLayer) { Backdrop(backdropLayer) }
                LaunchedEffect(Unit) { state.playEnter() }
                CompositionLocalProvider(LocalGlass provides env) {
                    val pull = state.spotlightPull.coerceIn(0f, 1f)
                    // Panels do their own frosting and refraction; the home only softens a little behind them.
                    val blurTarget = when (state.overlay) {
                        is Overlay.Spotlight -> 20.dp
                        is Overlay.Folder -> 12.dp
                        is Overlay.Menu -> 7.dp
                        is Overlay.AddWidget -> 5.dp
                        else -> 14.dp * pull
                    }
                    val blur by animateDpAsState(blurTarget, spring(stiffness = 500f), label = "blur")
                    Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                        Image(
                            wp, null,
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val sc = env.wallScale
                                    val shift = env.wallShift
                                    scaleX = sc; scaleY = sc
                                    translationX = shift.x; translationY = shift.y
                                },
                            contentScale = ContentScale.FillBounds,
                        )
                        HomeScreen(
                            state,
                            Modifier
                                .graphicsLayer { translationY = state.spotlightPull.coerceIn(0f, 1.2f) * 40.dp.toPx() }
                                .then(if (blur > 0.5.dp) Modifier.blur(blur) else Modifier),
                        )
                    }
                    CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassMode provides GlassMode.BACKDROP) {
                        Overlays(state)
                        DragLayer(state)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: LauncherState, modifier: Modifier = Modifier) {
    val s = state.settings
    val pages = state.layout.pages
    val homeCount = max(1, pages.size)
    val pager = rememberPagerState { homeCount + 1 }
    SideEffect { state.pager = pager }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.repo.apps, state.repo.loaded) { state.syncLayout() }

    BoxWithConstraints(modifier.fillMaxSize()) {
    val cols = s.columns
    val hPad = if (cols == 4) 22.dp else 12.dp
    val cellW = (maxWidth - hPad * 2) / cols
    val icon = min((cellW * 0.76f).value, if (cols == 4) 66f else 58f).dp
    Column(Modifier.fillMaxSize()) {
        // Ignore visibility so the grid doesn't jump when edit mode hides the status bar.
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val topPad = 14.dp
            val availH = maxHeight - topPad
            val rows = (availH / (icon + 30.dp)).toInt().coerceIn(4, if (cols == 4) 6 else 7)
            val cellH = min((availH / rows).value, (icon + 50.dp).value).dp
            val metrics = HomeMetrics(cols, rows, hPad, topPad, cellW, cellH, icon, 2.dp)
            val density = LocalDensity.current
            var origin by remember { mutableStateOf(Offset.Zero) }
            SideEffect {
                with(density) {
                    state.grid = GridMetrics(
                        origin + Offset(hPad.toPx(), topPad.toPx()), cellW.toPx(), cellH.toPx(), cols, rows, icon.toPx(),
                    )
                }
            }
            LaunchedEffect(cols, rows) { state.syncLayout() }
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        origin = it.positionInWindow()
                        with(density) {
                            state.grid = state.grid?.copy(origin = origin + Offset(hPad.toPx(), topPad.toPx()))
                        }
                    }
                    .spotlightPull(state, pager, homeCount, scope),
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    if (page < homeCount) HomePage(state, page, metrics) else AppLibrary(state)
                }
            }
        }
        // The dock and search pill sink away as icons fly out (app launch) and on the App Library page.
        val sink = Modifier
            .offset {
                val lib = ((pager.currentPage + pager.currentPageOffsetFraction) - (homeCount - 1)).coerceIn(0f, 1f)
                IntOffset(0, ((flyEase(state.zoom.value) + lib).coerceAtMost(1f) * 110.dp.toPx()).roundToInt())
            }
            .graphicsLayer {
                val lib = ((pager.currentPage + pager.currentPageOffsetFraction) - (homeCount - 1)).coerceIn(0f, 1f)
                alpha = (1f - lib) * (1f - flyEase(state.zoom.value)).coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
        Box(Modifier.fillMaxWidth().height(46.dp).graphicsLayer { alpha = 1f - flyEase(state.zoom.value) }, contentAlignment = Alignment.Center) {
            SearchOrDots(state, pager, homeCount)
        }
        if (state.crashReport != null) CrashBanner(state)
        else if (!state.isDefaultHome && !s.bannerDismissed) DefaultBanner(state)
        Dock(state, icon, hPad, cellW, sink)
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(Modifier.height(10.dp))
    }
    EditBar(state, Modifier.align(Alignment.TopCenter))
    }
}

/** Pull down anywhere on a home page to reveal Search. */
private fun Modifier.spotlightPull(state: LauncherState, pager: PagerState, homeCount: Int, scope: CoroutineScope): Modifier =
    this.pointerInput(homeCount) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (state.editMode || state.overlay != null || state.drag != null || pager.currentPage >= homeCount) return@awaitEachGesture
            var total = Offset.Zero
            var pulling = false
            val dist = 240.dp.toPx()
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                total += c.positionChange()
                if (!pulling) {
                    if (c.isConsumed) break
                    if (total.y > viewConfiguration.touchSlop && abs(total.y) > abs(total.x) * 1.4f) pulling = true
                    else if (abs(total.x) > viewConfiguration.touchSlop || total.y < -viewConfiguration.touchSlop) break
                }
                if (pulling) {
                    c.consume()
                    state.spotlightPull = (total.y / dist).coerceIn(0f, 1.2f)
                }
            }
            if (pulling) {
                val p = state.spotlightPull
                if (p > 0.32f) {
                    state.overlay = Overlay.Spotlight()
                    state.spotlightPull = 0f
                } else {
                    scope.launch {
                        animate(p, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> state.spotlightPull = v }
                    }
                }
            }
        }
    }

@Composable
fun HomePage(state: LauncherState, page: Int, m: HomeMetrics) {
    val items = state.layout.pages.getOrNull(page) ?: emptyList()
    val placement = remember(items, m.cols, m.rows) { pack(items, m.cols, m.rows).first }
    val density = LocalDensity.current
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state.editMode) {
                detectTapGestures(
                    onTap = { if (state.editMode) state.exitEdit() },
                    onLongPress = { state.enterEdit() },
                )
            }
            .padding(start = m.hPad, end = m.hPad, top = m.topPad)
            .onGloballyPositioned { pageOrigin = it.positionInWindow() },
    ) {
        for (item in items) {
            key(item.id) {
                val cell = placement[item.id]
                if (cell != null) {
                    val target = with(density) { IntOffset((cell.x * m.cellW.toPx()).roundToInt(), (cell.y * m.cellH.toPx()).roundToInt()) }
                    val inset = with(density) { Offset(((m.cellW - m.icon) / 2).toPx(), m.iconTop.toPx()) }
                    val centre = with(density) { Offset(m.cellW.toPx() * item.spanX / 2f, m.cellH.toPx() * item.spanY / 2f) }
                    GridSlot(state, item, target, { pageOrigin + inset }, centre - inset) {
                        HomeItemView(state, item, page, m)
                    }
                }
            }
        }
    }
}

/** Eased amount (0..1) by which icons are flown apart. */
fun flyEase(z: Float): Float {
    val t = z.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
private fun GridSlot(
    state: LauncherState,
    item: HomeItem,
    target: IntOffset,
    contentOrigin: () -> Offset,
    centerOffset: Offset,
    content: @Composable () -> Unit,
) {
    val anim = remember { Animatable(target, IntOffset.VectorConverter) }
    LaunchedEffect(target, state.dropSerial) {
        state.takeDrop(item.id)?.let { win -> anim.snapTo((win - contentOrigin()).round()) }
        if (anim.value != target) anim.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 380f))
    }
    val hidden = state.drag?.item?.id == item.id || state.lastDrop?.first == item.id
    // How far this slot is "dived": slots further from the pivot lag behind, so icons arrive in a wave.
    fun dive(): Pair<Float, Offset> {
        val z = state.zoom.value
        if (z <= 0.001f) return 0f to Offset.Zero
        val sc = state.screen
        val pivot = state.zoomPivot.takeIf { it != Offset.Unspecified } ?: Offset(sc.width / 2f, sc.height / 2f)
        val from = contentOrigin() + Offset(target.x.toFloat(), target.y.toFloat()) + centerOffset - pivot
        val far = (from.getDistance() / sc.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return flyEase(z * (1f + 0.5f * far)) to from
    }
    Box(
        Modifier
            .offset {
                val (e, from) = dive()
                anim.value + (from * (e * 0.85f)).round()
            }
            .graphicsLayer {
                val (e, _) = dive()
                val sc = 1f + 0.5f * e
                scaleX = sc; scaleY = sc
                alpha = if (hidden) 0f else (1f - e * 1.15f).coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) { content() }
}

@Composable
fun HomeItemView(state: LauncherState, item: HomeItem, page: Int, m: HomeMetrics) {
    val d = state.drag
    val highlight = d != null && d.mergeTarget == item.id && d.mergeReady
    val showLabels = state.settings.showLabels
    Box(Modifier.width(m.cellW * item.spanX).padding(top = m.iconTop), contentAlignment = Alignment.TopCenter) {
        when (item) {
            is AppItem -> {
                val app = state.appFor(item.appKey)
                if (app != null) {
                    AppIcon(
                        app, m.icon, showLabels, Source.Page(page),
                        homeItem = item, highlight = highlight,
                        onRemove = { state.removeFromHome(MenuTarget.App(item.appKey, Source.Page(page))) },
                    )
                }
            }
            is FolderItem -> FolderIcon(item, m.icon, showLabels, page, highlight = highlight)
            is WidgetItem -> WidgetSlot(state, item, page, m)
        }
    }
}

@Composable
private fun WidgetSlot(state: LauncherState, item: WidgetItem, page: Int, m: HomeMetrics) {
    val touch = remember { GlassTouch() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .jiggle(state.editMode, item.id.hashCode())
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .graphicsLayer {
                    val p = touch.press.value
                    scaleX = 1f + 0.03f * p; scaleY = 1f + 0.03f * p
                }
                .launcherGestures(
                    key = item.id,
                    touch = touch,
                    bounds = { bounds },
                    onTap = { widgetTap(item.type, state) },
                    onLongPress = { state.overlay = Overlay.Menu(MenuTarget.Widget(item.id), bounds) },
                    dragItem = item,
                    source = Source.Page(page),
                ),
        ) {
            WidgetBody(item.type, m.widgetW, m.widgetH, touch)
            RemoveBadge(
                state.editMode, { state.removeFromHome(MenuTarget.Widget(item.id)) },
                Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = (-7).dp),
            )
        }
        if (state.settings.showLabels) {
            Spacer(Modifier.height(6.dp))
            BasicText(item.type.title, style = LabelStyle)
        }
    }
}

@Composable
fun Dock(state: LauncherState, iconSize: Dp, hPad: Dp, cellW: Dp, modifier: Modifier = Modifier) {
    val keys = state.layout.dock
    val apps = keys.mapNotNull { state.appFor(it) }
    val density = LocalDensity.current
    val side = 12.dp
    var dockOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .padding(horizontal = side)
            .fillMaxWidth()
            .height(iconSize + 28.dp)
            .onGloballyPositioned {
                state.dockBounds = it.boundsInWindow()
                dockOrigin = it.positionInWindow()
                with(density) {
                    state.dockSlotLeft = dockOrigin.x + (hPad - side).toPx()
                    state.dockSlotW = cellW.toPx()
                }
            }
            .liquidGlass(38.dp, refraction = 1.5f),
    ) {
        // Dock icons sit exactly under the home grid columns, centred when there are fewer of them.
        val start = (hPad - side) + cellW * (state.dockCapacity - apps.size) / 2f
        apps.forEachIndexed { i, app ->
            key(app.key) {
                val target = with(density) { IntOffset((start + cellW * i + (cellW - iconSize) / 2).roundToPx(), 14.dp.roundToPx()) }
                val anim = remember { Animatable(target, IntOffset.VectorConverter) }
                LaunchedEffect(target, state.dropSerial) {
                    state.takeDrop("a:" + app.key)?.let { win -> anim.snapTo((win - dockOrigin).round()) }
                    if (anim.value != target) anim.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 380f))
                }
                val hidden = state.drag?.item?.id == "a:" + app.key || state.lastDrop?.first == "a:" + app.key
                Box(Modifier.offset { anim.value }.graphicsLayer { alpha = if (hidden) 0f else 1f; compositingStrategy = CompositingStrategy.ModulateAlpha }) {
                    AppIcon(
                        app, iconSize, showLabel = false, source = Source.Dock,
                        homeItem = AppItem(app.key),
                        onRemove = { state.removeFromHome(MenuTarget.App(app.key, Source.Dock)) },
                        modifier = Modifier.offset(x = (-11).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchOrDots(state: LauncherState, pager: PagerState, homeCount: Int) {
    val showDots = pager.isScrollInProgress || state.editMode || state.drag != null
    val dotsAlpha by animateFloatAsState(if (showDots) 1f else 0f, spring(stiffness = 600f), label = "dots")
    val onLibrary = pager.currentPage >= homeCount
    if (dotsAlpha < 0.99f && !onLibrary) {
        GlassButton(
            onClick = { state.overlay = Overlay.Spotlight() },
            modifier = Modifier
                .graphicsLayer { alpha = 1f - dotsAlpha; scaleX = 1f - 0.2f * dotsAlpha; scaleY = 1f - 0.2f * dotsAlpha }
                .height(32.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Symbol(Sym.SEARCH, 14.dp, Color.White)
                Spacer(Modifier.width(6.dp))
                BasicText("Search", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
    if (dotsAlpha > 0.01f || onLibrary) {
        val pos = pager.currentPage + pager.currentPageOffsetFraction
        Row(
            Modifier
                .graphicsLayer { alpha = if (onLibrary) 1f else dotsAlpha }
                .height(26.dp)
                .liquidGlass(13.dp, refraction = 0.6f)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in 0..homeCount) {
                val active = (1f - abs(pos - i)).coerceIn(0f, 1f)
                if (i == homeCount) {
                    // App Library indicator (a small search glyph, like iOS)
                    Symbol(Sym.GRID, 8.dp, Color.White.copy(alpha = 0.45f + 0.55f * active))
                } else {
                    Box(Modifier.size(7.dp).background(Color.White.copy(alpha = 0.4f + 0.6f * active), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun EditBar(state: LauncherState, modifier: Modifier) {
    AnimatedVisibility(
        visible = state.editMode,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(onClick = { state.overlay = Overlay.AddWidget }, modifier = Modifier.size(34.dp)) {
                Symbol(Sym.PLUS, 16.dp, Color.White)
            }
            Spacer(Modifier.width(8.dp))
            GlassButton(onClick = { state.overlay = Overlay.Settings }, modifier = Modifier.height(34.dp)) {
                BasicText("Customize", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 16.dp))
            }
            Spacer(Modifier.weight(1f))
            GlassButton(onClick = { state.exitEdit() }, modifier = Modifier.height(34.dp), tint = Color(0xFF0A84FF).copy(alpha = 0.55f)) {
                BasicText("Done", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

@Composable
private fun DefaultBanner(state: LauncherState) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .liquidGlass(22.dp, tint = dev.liquidglass.launcher.glass.darkGlassTint(state.settings))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { InGlass {
        Column(Modifier.weight(1f)) {
            BasicText("Make Glass your Home Screen", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            BasicText("So the Home button always brings you here.", style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp))
        }
        GlassButton(onClick = { state.requestDefaultHome() }, modifier = Modifier.height(32.dp), tint = Color(0xFF0A84FF).copy(alpha = 0.6f)) {
            BasicText("Set", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.width(6.dp))
        GlassButton(onClick = { state.settings.bannerDismissed = true }, modifier = Modifier.size(32.dp)) {
            Symbol(Sym.CLOSE, 12.dp, Color.White)
        }
    } }
}

@Composable
private fun CrashBanner(state: LauncherState) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .liquidGlass(22.dp, tint = dev.liquidglass.launcher.glass.darkGlassTint(state.settings))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { InGlass {
        Column(Modifier.weight(1f)) {
            BasicText("Glass closed unexpectedly", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Share the report so it can be fixed.", style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp))
        }
        GlassButton(onClick = { state.shareCrashReport() }, modifier = Modifier.height(32.dp), tint = Color(0xFF0A84FF).copy(alpha = 0.6f)) {
            BasicText("Share", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 14.dp))
        }
        Spacer(Modifier.width(6.dp))
        GlassButton(onClick = { state.dismissCrashReport() }, modifier = Modifier.size(32.dp)) {
            Symbol(Sym.CLOSE, 12.dp, Color.White)
        }
    } }
}

/** The lifted copy of whatever is being dragged; follows the finger above everything. */
@Composable
private fun DragLayer(state: LauncherState) {
    val d = state.drag ?: return
    val density = LocalDensity.current
    val lift = remember(d) { Animatable(1f) }
    LaunchedEffect(d) { lift.animateTo(1.12f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
    val topLeft = d.pointer - d.grab
    Box(
        Modifier
            .zIndex(10f)
            .offset { topLeft.round() }
            .graphicsLayer { scaleX = lift.value; scaleY = lift.value; alpha = 0.96f },
    ) {
        val w = with(density) { d.itemSize.width.toDp() }
        val h = with(density) { d.itemSize.height.toDp() }
        when (val item = d.item) {
            is AppItem -> state.appFor(item.appKey)?.let { IconTile(it, w) }
            is FolderItem -> FolderTile(item.apps, w)
            is WidgetItem -> WidgetBody(item.type, w, h, null)
        }
    }
}
