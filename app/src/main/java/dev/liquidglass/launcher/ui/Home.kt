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
import dev.liquidglass.launcher.glass.GlassEnv
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
            LaunchedEffect(wPx, hPx, s.wallpaper, s.frost, s.wallpaperVersion) {
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
            if (wp != null && bl != null) {
                val env = remember(bl, wPx, hPx) { GlassEnv(bl, Size(wPx.toFloat(), hPx.toFloat()), state.light, s) }
                CompositionLocalProvider(LocalGlass provides env) {
                    val ov = state.overlay
                    val blurOn = ov != null && ov !is Overlay.Settings
                    val blur by animateDpAsState(
                        if (blurOn) 22.dp else (16.dp * state.spotlightPull.coerceIn(0f, 1f)),
                        spring(stiffness = 500f), label = "blur",
                    )
                    val enter = state.enter.value
                    Image(
                        wp, null,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val sc = 1f + (1f - enter) * 0.06f
                                scaleX = sc; scaleY = sc
                            },
                        contentScale = ContentScale.FillBounds,
                    )
                    HomeScreen(
                        state,
                        Modifier
                            .graphicsLayer {
                                val sc = 1f + (1f - enter) * 0.18f
                                scaleX = sc; scaleY = sc
                                alpha = enter.coerceIn(0f, 1f)
                                translationY = state.spotlightPull.coerceIn(0f, 1.2f) * 40.dp.toPx()
                            }
                            .then(if (blur > 0.5.dp) Modifier.blur(blur) else Modifier),
                    )
                    Overlays(state)
                    DragLayer(state)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(state: LauncherState, modifier: Modifier = Modifier) {
    val s = state.settings
    val pages = state.layout.pages
    val homeCount = max(1, pages.size)
    val pager = rememberPagerState { homeCount + 1 }
    SideEffect { state.pager = pager }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.repo.apps, state.repo.loaded) { state.syncLayout() }

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cols = s.columns
            val hPad = if (cols == 4) 22.dp else 12.dp
            val cellW = (maxWidth - hPad * 2) / cols
            val icon = min((cellW * 0.76f).value, if (cols == 4) 66f else 58f).dp
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
                EditBar(state, Modifier.align(Alignment.TopCenter))
            }
        }
        val libraryProgress = ((pager.currentPage + pager.currentPageOffsetFraction) - (homeCount - 1)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
            SearchOrDots(state, pager, homeCount)
        }
        if (state.crashReport != null) CrashBanner(state)
        else if (!state.isDefaultHome && !s.bannerDismissed) DefaultBanner(state)
        Dock(
            state,
            iconSize = if (s.columns == 4) 62.dp else 56.dp,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - libraryProgress
                translationY = libraryProgress * 60.dp.toPx()
            },
        )
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(Modifier.height(10.dp))
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
                    GridSlot(state, item, target, { pageOrigin + inset }) {
                        HomeItemView(state, item, page, m)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridSlot(
    state: LauncherState,
    item: HomeItem,
    target: IntOffset,
    contentOrigin: () -> Offset,
    content: @Composable () -> Unit,
) {
    val anim = remember { Animatable(target, IntOffset.VectorConverter) }
    LaunchedEffect(target, state.dropSerial) {
        state.takeDrop(item.id)?.let { win -> anim.snapTo((win - contentOrigin()).round()) }
        if (anim.value != target) anim.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 380f))
    }
    val hidden = state.drag?.item?.id == item.id || state.lastDrop?.first == item.id
    Box(
        Modifier
            .offset { anim.value }
            .graphicsLayer { alpha = if (hidden) 0f else 1f },
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
fun Dock(state: LauncherState, iconSize: Dp, modifier: Modifier = Modifier) {
    val keys = state.layout.dock
    val apps = keys.mapNotNull { state.appFor(it) }
    val density = LocalDensity.current
    var dockOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(iconSize + 28.dp)
            .onGloballyPositioned {
                state.dockBounds = it.boundsInWindow()
                dockOrigin = it.positionInWindow()
            }
            .liquidGlass(36.dp, refraction = 1.35f),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val n = max(1, apps.size)
            val slot = maxWidth / n
            apps.forEachIndexed { i, app ->
                key(app.key) {
                    val target = with(density) { IntOffset((slot * i + (slot - iconSize) / 2).roundToPx(), 14.dp.roundToPx()) }
                    val anim = remember { Animatable(target, IntOffset.VectorConverter) }
                    LaunchedEffect(target, state.dropSerial) {
                        state.takeDrop("a:" + app.key)?.let { win -> anim.snapTo((win - dockOrigin).round()) }
                        if (anim.value != target) anim.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 380f))
                    }
                    val hidden = state.drag?.item?.id == "a:" + app.key || state.lastDrop?.first == "a:" + app.key
                    Box(Modifier.offset { anim.value }.graphicsLayer { alpha = if (hidden) 0f else 1f }) {
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
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(onClick = { state.overlay = Overlay.AddWidget }, modifier = Modifier.size(38.dp)) {
                Symbol(Sym.PLUS, 16.dp, Color.White)
            }
            Spacer(Modifier.width(8.dp))
            GlassButton(onClick = { state.overlay = Overlay.Settings }, modifier = Modifier.height(38.dp)) {
                BasicText("Customize", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 16.dp))
            }
            Spacer(Modifier.weight(1f))
            GlassButton(onClick = { state.exitEdit() }, modifier = Modifier.height(38.dp), tint = Color(0xFF0A84FF).copy(alpha = 0.55f)) {
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
    ) {
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
    }
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
    ) {
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
    }
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
