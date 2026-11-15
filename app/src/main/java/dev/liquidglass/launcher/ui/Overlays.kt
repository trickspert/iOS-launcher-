package dev.liquidglass.launcher.ui

import android.content.pm.ShortcutInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.liquidglass.launcher.LauncherState
import dev.liquidglass.launcher.MenuTarget
import dev.liquidglass.launcher.Overlay
import dev.liquidglass.launcher.Source
import dev.liquidglass.launcher.data.AppEntry
import dev.liquidglass.launcher.data.AppItem
import dev.liquidglass.launcher.data.WidgetType
import dev.liquidglass.launcher.glass.GlassTouch
import dev.liquidglass.launcher.glass.darkGlassTint
import dev.liquidglass.launcher.glass.liquidGlass
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun Modifier.plainClick(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun Overlays(state: LauncherState) {
    val ov = state.overlay
    val scrim by animateFloatAsState(
        if (ov != null && ov !is Overlay.Settings) 1f else state.spotlightPull.coerceIn(0f, 1f) * 0.8f,
        tween(220), label = "scrim",
    )
    if (scrim > 0.01f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (if (android.os.Build.VERSION.SDK_INT >= 31) 0.22f else 0.5f) * scrim))
                .then(if (ov != null) Modifier.plainClick { state.overlay = null } else Modifier),
        )
    }

    var lastMenu by remember { mutableStateOf<Overlay.Menu?>(null) }
    var lastFolder by remember { mutableStateOf<Overlay.Folder?>(null) }
    if (ov is Overlay.Menu) lastMenu = ov
    if (ov is Overlay.Folder) lastFolder = ov

    // Context menu
    AnimatedVisibility(
        visible = ov is Overlay.Menu,
        enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.6f, transformOrigin = menuOrigin(state, lastMenu)),
        exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.7f, transformOrigin = menuOrigin(state, lastMenu)),
    ) {
        lastMenu?.let { ContextMenu(state, it) }
    }

    // Folder
    AnimatedVisibility(
        visible = ov is Overlay.Folder,
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 420f), initialScale = 0.25f, transformOrigin = folderOrigin(state, lastFolder)),
        exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.25f, transformOrigin = folderOrigin(state, lastFolder)),
    ) {
        lastFolder?.let { FolderOverlay(state, it) }
    }

    // Search (also previewed while pulling down)
    val pull = state.spotlightPull
    AnimatedVisibility(
        visible = ov is Overlay.Spotlight,
        enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { -it / 6 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 8 },
    ) {
        Spotlight(state, (ov as? Overlay.Spotlight)?.library ?: false)
    }
    if (ov == null && pull > 0.01f) {
        Box(Modifier.graphicsLayer { alpha = pull.coerceIn(0f, 1f); translationY = (pull - 1f) * 60.dp.toPx() }) {
            SearchFieldPreview()
        }
    }

    AnimatedVisibility(
        visible = ov is Overlay.AddWidget,
        enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it },
        exit = fadeOut() + slideOutVertically(tween(220)) { it },
    ) {
        AddWidgetSheet(state)
    }

    AnimatedVisibility(
        visible = ov is Overlay.Settings,
        enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.86f, stiffness = 340f)) { it },
        exit = fadeOut() + slideOutVertically(tween(240)) { it },
    ) {
        SettingsSheet(state)
    }
}

@Composable
private fun menuOrigin(state: LauncherState, m: Overlay.Menu?): TransformOrigin {
    val sc = state.screen
    if (m == null || sc.width <= 0f) return TransformOrigin.Center
    return TransformOrigin((m.bounds.center.x / sc.width).coerceIn(0f, 1f), (m.bounds.center.y / sc.height).coerceIn(0f, 1f))
}

@Composable
private fun folderOrigin(state: LauncherState, f: Overlay.Folder?): TransformOrigin {
    val sc = state.screen
    val b = f?.origin
    if (b == null || sc.width <= 0f) return TransformOrigin.Center
    return TransformOrigin((b.center.x / sc.width).coerceIn(0f, 1f), (b.center.y / sc.height).coerceIn(0f, 1f))
}

// ------------------------------------------------------------------ context menu

private class MenuAction(val label: String, val sym: Sym?, val destructive: Boolean = false, val image: ImageBitmap? = null, val run: () -> Unit)

@Composable
private fun ContextMenu(state: LauncherState, m: Overlay.Menu) {
    val density = LocalDensity.current
    val actions = remember(m, state.layout) { buildActions(state, m) }
    val b = m.bounds
    Box(Modifier.fillMaxSize().plainClick { state.overlay = null }) {
        // Lifted copy of the pressed item.
        when (val t = m.target) {
            is MenuTarget.App -> state.appFor(t.appKey)?.let { app ->
                val sz = with(density) { b.width.toDp() }
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(b.left.roundToInt(), b.top.roundToInt()) }.graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }) {
                    OnWallpaper { IconTile(app, sz) }
                }
            }
            is MenuTarget.Folder -> state.folder(t.folderId)?.let { f ->
                val sz = with(density) { b.width.toDp() }
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(b.left.roundToInt(), b.top.roundToInt()) }.graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }) {
                    OnWallpaper { FolderTile(f.apps, sz) }
                }
            }
            is MenuTarget.Widget -> {}
        }
        val margin = with(density) { 14.dp.toPx() }
        Layout(content = { MenuPanel(state, actions) }) { measurables, constraints ->
            val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val below = b.bottom + margin + p.height < h - margin * 4
            val y = if (below) b.bottom + margin else b.top - margin - p.height
            val x = b.left.coerceIn(margin, max(margin, w - p.width - margin))
            layout(w, h) { p.place(x.roundToInt(), y.roundToInt().coerceAtLeast(margin.roundToInt())) }
        }
    }
}

private fun buildActions(state: LauncherState, m: Overlay.Menu): List<MenuAction> {
    val out = ArrayList<MenuAction>()
    when (val t = m.target) {
        is MenuTarget.App -> {
            val app = state.appFor(t.appKey) ?: return out
            for (sc in state.shortcutsFor(app)) {
                val img = state.shortcutIcon(sc)?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                out += MenuAction(sc.shortLabel?.toString() ?: sc.longLabel?.toString() ?: "Shortcut", null, image = img) {
                    state.overlay = null; state.launchShortcut(sc, m.bounds)
                }
            }
            if (t.source !is Source.Library) out += MenuAction("Edit Home Screen", Sym.GRID) { state.enterEdit() }
            if (!app.isInternal) out += MenuAction("App Info", Sym.INFO) { state.overlay = null; state.appInfo(app) }
            when (val s = t.source) {
                is Source.InFolder -> {
                    out += MenuAction("Move to Home Screen", Sym.FOLDER_OUT) { state.moveOutOfFolder(s.folderId, app.key) }
                    out += MenuAction("Remove from Folder", Sym.MINUS) { state.removeFromHome(t) }
                }
                is Source.Library -> if (!state.isOnHome(app.key)) out += MenuAction("Add to Home Screen", Sym.PLUS) { state.addToHome(app.key) }
                else -> out += MenuAction("Remove from Home", Sym.MINUS) { state.removeFromHome(t) }
            }
            if (!app.isInternal) out += MenuAction("Uninstall App", Sym.TRASH, destructive = true) { state.overlay = null; state.uninstall(app) }
        }
        is MenuTarget.Folder -> {
            val f = state.folder(t.folderId)
            out += MenuAction("Rename", Sym.PENCIL) {
                state.overlay = if (f != null) Overlay.Folder(f.id, f.name, f.apps, m.bounds) else null
            }
            out += MenuAction("Edit Home Screen", Sym.GRID) { state.enterEdit() }
            out += MenuAction("Remove Folder", Sym.TRASH, destructive = true) { state.removeFromHome(t) }
        }
        is MenuTarget.Widget -> {
            out += MenuAction("Edit Home Screen", Sym.GRID) { state.enterEdit() }
            out += MenuAction("Remove Widget", Sym.TRASH, destructive = true) { state.removeFromHome(t) }
        }
    }
    return out
}

@Composable
private fun MenuPanel(state: LauncherState, actions: List<MenuAction>) {
    Column(
        Modifier
            .width(254.dp)
            .liquidGlass(24.dp, tint = darkGlassTint(state.settings), refraction = 1.3f, frosted = true)
            .plainClick { }
            .padding(vertical = 6.dp),
    ) {
        actions.forEachIndexed { i, a ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
            MenuRow(a)
        }
    }
}

@Composable
private fun MenuRow(a: MenuAction) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val color = if (a.destructive) Color(0xFFFF6961) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .background(if (pressed) Color.White.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = null, onClick = a.run)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(a.label, style = TextStyle(color = color, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        when {
            a.image != null -> Image(a.image, null, Modifier.size(20.dp))
            a.sym != null -> Symbol(a.sym, 18.dp, color)
        }
    }
}

// ------------------------------------------------------------------ folder

@Composable
private fun FolderOverlay(state: LauncherState, ov: Overlay.Folder) {
    val folder = ov.folderId?.let { state.folder(it) }
    val keys = folder?.apps ?: ov.apps
    val apps = keys.mapNotNull { state.appFor(it) }
    if (ov.folderId != null && folder == null) {
        LaunchedEffect(Unit) { if (state.overlay == ov) state.overlay = null }
    }
    Box(Modifier.fillMaxSize().plainClick { state.overlay = null }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 34.dp)) {
            if (folder != null) {
                var name by remember(folder.id) { mutableStateOf(folder.name) }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(28); state.renameFolder(folder.id, name) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                BasicText(ov.title, style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
            Spacer(Modifier.height(18.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(40.dp, refraction = 1.5f, frosted = true)
                    .plainClick { }
                    .padding(vertical = 22.dp, horizontal = 10.dp),
            ) {
                val cols = 3
                val cell = maxWidth / cols
                val icon = minOf(64.dp, cell * 0.72f)
                InGlass { Column(
                    Modifier.heightIn(max = (cell + 16.dp) * 4).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    apps.chunked(cols).forEach { row ->
                        Row {
                            row.forEach { app ->
                                Box(Modifier.width(cell), contentAlignment = Alignment.TopCenter) {
                                    AppIcon(
                                        app, icon, true,
                                        source = if (folder != null) Source.InFolder(folder.id) else Source.Library,
                                        homeItem = if (folder != null) AppItem(app.key) else null,
                                    )
                                }
                            }
                        }
                    }
                } }
            }
        }
    }
}

// ------------------------------------------------------------------ search

@Composable
private fun SearchFieldPreview() {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).liquidGlass(24.dp, tint = darkGlassTint(LocalLauncher.current.settings), frosted = true).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(Sym.SEARCH, 17.dp, Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.width(10.dp))
            BasicText("Search", style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp))
        }
    }
}

private fun rank(apps: List<AppEntry>, q: String): List<AppEntry> {
    val query = q.trim().lowercase()
    if (query.isEmpty()) return emptyList()
    return apps.mapNotNull { a ->
        val l = a.label.lowercase()
        val score = when {
            l.startsWith(query) -> 0
            l.split(' ', '-', '.').any { it.startsWith(query) } -> 1
            l.contains(query) -> 2
            a.packageName.lowercase().contains(query) -> 3
            else -> return@mapNotNull null
        }
        score to a
    }.sortedWith(compareBy({ it.first }, { it.second.label.lowercase() })).map { it.second }
}

@Composable
private fun Spotlight(state: LauncherState, library: Boolean) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val apps = state.repo.apps
    val results = remember(query, apps) { rank(apps, query) }
    val suggestions = remember(apps, state.repo.launchCounts) {
        val counts = state.repo.launchCounts
        val used = apps.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] ?: 0 }
        (used + apps.filter { it.glyph != null }).distinctBy { it.key }.take(8)
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .liquidGlass(24.dp, tint = darkGlassTint(state.settings), frosted = true)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(Sym.SEARCH, 17.dp, Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) BasicText(if (library) "App Library" else "Search", style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        cursorBrush = SolidColor(Color(0xFF0A84FF)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val first = results.firstOrNull()
                            if (first != null) { state.overlay = null; state.launch(first, null) }
                            else if (query.isNotBlank()) { state.overlay = null; state.webSearch(query) }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.size(20.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).plainClick { query = "" }, contentAlignment = Alignment.Center) {
                        Symbol(Sym.CLOSE, 9.dp, Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            BasicText(
                "Cancel",
                style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.plainClick { keyboard?.hide(); state.overlay = null },
            )
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (query.isBlank()) {
                item { SectionTitle("Suggestions") }
                item { IconGridPanel(state, suggestions) }
                if (library) {
                    item { SectionTitle("All Apps") }
                    item { AppListPanel(state, apps) }
                }
            } else {
                if (results.isNotEmpty()) {
                    item { SectionTitle("Top Hits") }
                    item { IconGridPanel(state, results.take(4)) }
                }
                if (results.size > 4) {
                    item { SectionTitle("Apps") }
                    item { AppListPanel(state, results.drop(4).take(30)) }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlass(22.dp, tint = darkGlassTint(state.settings), frosted = true)
                            .plainClick { state.overlay = null; state.webSearch(query) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Symbol(Sym.SEARCH, 18.dp, Color(0xFF64B5FF))
                        Spacer(Modifier.width(12.dp))
                        BasicText("Search the web for “${query.trim()}”", style = BodyStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    BasicText(t, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun IconGridPanel(state: LauncherState, apps: List<AppEntry>) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .liquidGlass(28.dp, tint = darkGlassTint(state.settings).copy(alpha = 0.25f), frosted = true)
            .padding(vertical = 16.dp, horizontal = 6.dp),
    ) {
        val cell = maxWidth / 4
        val icon = minOf(60.dp, cell * 0.74f)
        InGlass { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            apps.chunked(4).forEach { row ->
                Row {
                    row.forEach { app ->
                        Box(Modifier.width(cell), contentAlignment = Alignment.TopCenter) {
                            AppIcon(app, icon, true, Source.Library)
                        }
                    }
                }
            }
        } }
    }
}

@Composable
private fun AppListPanel(state: LauncherState, apps: List<AppEntry>) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(24.dp, tint = darkGlassTint(state.settings), frosted = true)
            .padding(vertical = 6.dp),
    ) {
        InGlass {
            apps.forEachIndexed { i, app ->
                if (i > 0) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f)))
                AppRow(state, app)
            }
        }
    }
}

@Composable
private fun AppRow(state: LauncherState, app: AppEntry) {
    val touch = remember { GlassTouch() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .launcherGestures(
                key = app.key, touch = touch, bounds = { bounds },
                onTap = { state.overlay = null; state.launch(app, bounds) },
                onLongPress = { state.overlay = Overlay.Menu(MenuTarget.App(app.key, Source.Library), bounds) },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) { IconTile(app, 38.dp, touch = touch) }
        Spacer(Modifier.width(14.dp))
        BasicText(app.label, style = BodyStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ widgets sheet

@Composable
private fun AddWidgetSheet(state: LauncherState) {
    Box(Modifier.fillMaxSize().plainClick { state.overlay = null }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .navigationBarsPadding()
                .liquidGlass(38.dp, tint = darkGlassTint(state.settings), refraction = 1.4f, frosted = true)
                .plainClick { }
                .padding(20.dp),
        ) { InGlass {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Add Widget", style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                GlassButton(onClick = { state.overlay = null }, modifier = Modifier.size(32.dp)) { Symbol(Sym.CLOSE, 12.dp, Color.White) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetType.entries.forEach { t ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val w = maxWidth
                            WidgetBody(t, w, w, null, Modifier.plainClick { state.addWidget(t) })
                        }
                        Spacer(Modifier.height(8.dp))
                        BasicText(t.title, style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText("Tap a widget to add it to this page.", style = CaptionStyle)
        } }
    }
}
