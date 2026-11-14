package dev.liquidglass.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.liquidglass.launcher.data.AppCategory
import dev.liquidglass.launcher.data.AppEntry
import dev.liquidglass.launcher.data.AppItem
import dev.liquidglass.launcher.data.FolderItem
import dev.liquidglass.launcher.data.HomeItem
import dev.liquidglass.launcher.data.HomeLayout
import dev.liquidglass.launcher.data.LayoutStore
import dev.liquidglass.launcher.data.WidgetItem
import dev.liquidglass.launcher.data.WidgetType
import dev.liquidglass.launcher.data.newId
import dev.liquidglass.launcher.data.normalize
import dev.liquidglass.launcher.data.pack
import dev.liquidglass.launcher.glass.GlassLight
import dev.liquidglass.launcher.glass.Wallpapers
import dev.liquidglass.launcher.icons.Glyph
import dev.liquidglass.launcher.icons.IconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

sealed class Source {
    data class Page(val index: Int) : Source()
    data object Dock : Source()
    data class InFolder(val folderId: String) : Source()
    data object Library : Source()
}

sealed class MenuTarget {
    data class App(val appKey: String, val source: Source) : MenuTarget()
    data class Folder(val folderId: String) : MenuTarget()
    data class Widget(val widgetId: String) : MenuTarget()
}

sealed class Overlay {
    data class Menu(val target: MenuTarget, val bounds: Rect) : Overlay()
    /** [folderId] null means a read-only App Library category. */
    data class Folder(val folderId: String?, val title: String, val apps: List<String>, val origin: Rect?) : Overlay()
    data class Spotlight(val library: Boolean = false) : Overlay()
    data object Settings : Overlay()
    data object AddWidget : Overlay()
}

class ArmedDrag(val item: HomeItem, val source: Source, val bounds: Rect, val downAt: Offset)

class DragState(val item: HomeItem, source: Source, val grab: Offset, val itemSize: Size, start: Offset) {
    var source by mutableStateOf(source)
    var pointer by mutableStateOf(start)
    var mergeTarget by mutableStateOf<String?>(null)
    var mergeReady by mutableStateOf(false)
    var mergeSince = 0L
    var reorderCandidate = -1
    var reorderSince = 0L
    var edgeDir = 0
    var edgeSince = 0L
}

data class GridMetrics(val origin: Offset, val cellW: Float, val cellH: Float, val cols: Int, val rows: Int, val iconPx: Float)

object BadgeCenter {
    val counts = mutableStateMapOf<String, Int>()
}

class LauncherState(val app: LauncherApplication, val scope: CoroutineScope) {
    val context: Context get() = app
    val settings = app.settings
    val repo = app.repo
    val icons: IconCache = app.icons
    private val store = LayoutStore(app)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    val light = GlassLight()

    var layout by mutableStateOf(HomeLayout(emptyList(), emptyList()))
        private set
    var editMode by mutableStateOf(false)
    var overlay by mutableStateOf<Overlay?>(null)
    var drag by mutableStateOf<DragState?>(null)
        private set
    var lastDrop by mutableStateOf<Pair<String, Offset>?>(null)
    var dropSerial by mutableIntStateOf(0)
    var spotlightPull by mutableFloatStateOf(0f)

    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set
    var blurred by mutableStateOf<Bitmap?>(null)
        private set
    private var wallKey = ""
    private var sharpCache: Pair<String, Bitmap>? = null

    var isDefaultHome by mutableStateOf(true)
    var badgesEnabled by mutableStateOf(false)
    var calendarGranted by mutableStateOf(false)
    var clockTick by mutableIntStateOf(0)

    val enter = Animatable(1f)
    private var launchedSomething = false

    var pager: PagerState? = null
    var grid: GridMetrics? = null
    var dockBounds: Rect = Rect.Zero
    var screen: Size = Size.Zero
    private var armed: ArmedDrag? = null
    private var saveJob: Job? = null
    private var tickJob: Job? = null
    var hostView: View? = null

    // Hooks installed by the activity.
    var requestDefaultHome: () -> Unit = {}
    var pickWallpaper: () -> Unit = {}
    var requestCalendar: () -> Unit = {}

    val dockCapacity get() = settings.columns

    // ---------------------------------------------------------------- layout

    fun appFor(key: String): AppEntry? = repo.byKey[key]

    /** Called whenever the app list or grid size changes. */
    fun syncLayout() {
        val g = grid ?: return
        if (!repo.loaded) return
        val apps = repo.byKey
        val current = if (layout.isEmpty) store.load() else layout
        val next = if (current == null || current.isEmpty) buildDefault(repo.apps, g.cols, g.rows)
        else reconcile(current, apps)
        val normalized = normalize(next, g.cols, g.rows)
        if (normalized != layout) {
            layout = normalized
            save()
        }
    }

    private fun reconcile(l: HomeLayout, apps: Map<String, AppEntry>): HomeLayout {
        val valid = apps.keys
        val pages = l.pages.map { p ->
            p.mapNotNull { item ->
                when (item) {
                    is AppItem -> item.takeIf { it.appKey in valid }
                    is FolderItem -> {
                        val a = item.apps.filter { it in valid }
                        when {
                            a.isEmpty() -> null
                            a.size == 1 -> AppItem(a[0])
                            else -> item.copy(apps = a)
                        }
                    }
                    is WidgetItem -> item
                }
            }
        }.toMutableList()
        val dock = l.dock.filter { it in valid }
        val placed = l.copy(pages = pages, dock = dock).placedKeys()
        val fresh = if (l.known.isEmpty()) emptyList() else
            (valid - l.known - placed).mapNotNull { apps[it] }.sortedBy { it.installTime }.map { AppItem(it.key) }
        if (fresh.isNotEmpty()) {
            if (pages.isEmpty()) pages += emptyList<HomeItem>()
            pages[pages.size - 1] = pages.last() + fresh
        }
        return HomeLayout(pages, dock, valid)
    }

    private fun buildDefault(apps: List<AppEntry>, cols: Int, rows: Int): HomeLayout {
        val byGlyph = apps.filter { it.glyph != null }.associateBy { it.glyph!! }
        val used = HashSet<String>()
        val dock = ArrayList<String>()
        for (g in listOf(Glyph.PHONE, Glyph.BROWSER, Glyph.MESSAGES, Glyph.MUSIC, Glyph.CAMERA, Glyph.MAIL)) {
            if (dock.size >= minOf(4, cols)) break
            byGlyph[g]?.let { dock += it.key; used += it.key }
        }
        val page0 = ArrayList<HomeItem>()
        page0 += WidgetItem(newId("w"), WidgetType.BATTERY)
        page0 += WidgetItem(newId("w"), WidgetType.CALENDAR)
        val favourites = listOf(
            Glyph.PHOTOS, Glyph.CAMERA, Glyph.CLOCK, Glyph.SETTINGS, Glyph.MAIL, Glyph.MAPS, Glyph.WEATHER,
            Glyph.NOTES, Glyph.CALCULATOR, Glyph.FILES, Glyph.STORE, Glyph.LAUNCHER, Glyph.CONTACTS,
            Glyph.HEALTH, Glyph.VIDEO, Glyph.MUSIC, Glyph.CALENDAR,
        )
        val room = cols * rows - 8 - 2
        for (g in favourites) {
            if (page0.size - 2 >= room) break
            val a = byGlyph[g] ?: continue
            if (a.key in used) continue
            page0 += AppItem(a.key); used += a.key
        }
        val rest = apps.filter { it.key !in used }
        val page1 = ArrayList<HomeItem>()
        page1 += WidgetItem(newId("w"), WidgetType.CLOCK)
        val groups = rest.groupBy { it.category }.toList().sortedByDescending { it.second.size }
        val singles = ArrayList<HomeItem>()
        for ((cat, list) in groups) {
            if (list.size >= 2) page1 += FolderItem(newId("f"), shortName(cat), list.map { it.key })
            else singles += list.map { AppItem(it.key) }
        }
        page1 += singles
        return HomeLayout(listOf(page0, page1), dock, apps.map { it.key }.toSet())
    }

    private fun shortName(c: AppCategory) = when (c) {
        AppCategory.PRODUCTIVITY -> "Productivity"
        AppCategory.INFO -> "Reading"
        else -> c.title
    }

    fun resetLayout() {
        store.clear()
        layout = HomeLayout(emptyList(), emptyList())
        editMode = false
        overlay = null
        syncLayout()
        scope.launch { pager?.animateScrollToPage(0) }
    }

    private fun update(block: (HomeLayout) -> HomeLayout) {
        val g = grid
        val n = block(layout)
        layout = if (g != null) normalize(n, g.cols, g.rows) else n
        save()
    }

    private fun save() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            val l = layout
            withContext(Dispatchers.IO) { store.save(l) }
        }
    }

    // ---------------------------------------------------------------- wallpaper

    fun ensureWallpaper(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val key = "${settings.wallpaper}|${settings.frost}|$w|$h|${settings.wallpaperVersion}"
        if (key == wallKey) return
        wallKey = key
        val id = settings.wallpaper
        val frost = settings.frost
        val sharpKey = "$id|$w|$h|${settings.wallpaperVersion}"
        scope.launch {
            if (wallpaper != null) delay(60) // coalesce rapid slider changes
            if (key != wallKey) return@launch
            val cached = sharpCache?.takeIf { it.first == sharpKey }?.second
            val (sharp, soft) = withContext(Dispatchers.Default) {
                val s = cached ?: Wallpapers.load(app, id, w, h)
                s to Wallpapers.blur(s, frost, w)
            }
            sharpCache = sharpKey to sharp
            if (key != wallKey) return@launch
            sharp.prepareToDraw()
            wallpaper = sharp.asImageBitmap()
            blurred = soft
        }
    }

    fun importWallpaper(uri: Uri) {
        val s = screen
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                Wallpapers.importPhoto(app, uri, s.width.toInt().coerceAtLeast(720), s.height.toInt().coerceAtLeast(1280))
            }
            if (ok) {
                settings.wallpaper = "custom"
                settings.wallpaperVersion = settings.wallpaperVersion + 1
            } else {
                Toast.makeText(app, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------- actions

    fun launch(entry: AppEntry, bounds: Rect?) {
        if (entry.isInternal) {
            overlay = Overlay.Settings
            return
        }
        val view = hostView
        val opts = if (view != null && bounds != null) {
            ActivityOptions.makeScaleUpAnimation(
                view, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt().coerceAtLeast(1), bounds.height.toInt().coerceAtLeast(1),
            ).toBundle()
        } else null
        val src = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        try {
            launcherApps.startMainActivity(entry.component, entry.user, src, opts)
            repo.recordLaunch(entry.key)
            launchedSomething = true
        } catch (e: Exception) {
            Toast.makeText(app, "Can't open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchShortcut(s: ShortcutInfo, bounds: Rect?) {
        try {
            val src = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
            launcherApps.startShortcut(s, src, null)
            launchedSomething = true
        } catch (e: Exception) {
            Toast.makeText(app, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun shortcutsFor(entry: AppEntry): List<ShortcutInfo> {
        if (entry.isInternal) return emptyList()
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery()
                .setPackage(entry.packageName)
                .setActivity(entry.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            (launcherApps.getShortcuts(q, entry.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun shortcutIcon(s: ShortcutInfo): android.graphics.drawable.Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(s, app.resources.displayMetrics.densityDpi) }.getOrNull()

    fun appInfo(entry: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null) }
    }

    fun uninstall(entry: AppEntry) {
        runCatching {
            val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entry.packageName))
                .putExtra(Intent.EXTRA_USER, entry.user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(i)
        }
    }

    fun webSearch(q: String) {
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (i.resolveActivity(app.packageManager) != null) app.startActivity(i) else app.startActivity(fallback)
            launchedSomething = true
        } catch (e: Exception) {
            runCatching { app.startActivity(fallback) }
        }
    }

    fun openPackageIntent(intent: Intent) {
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); launchedSomething = true }
    }

    fun haptic(kind: Int = HapticFeedbackConstants.LONG_PRESS) {
        hostView?.performHapticFeedback(kind)
    }

    fun enterEdit() {
        overlay = null
        if (!editMode) haptic()
        editMode = true
    }

    fun exitEdit() {
        editMode = false
    }

    fun isOnHome(key: String) = key in layout.placedKeys()

    fun removeFromHome(target: MenuTarget) {
        overlay = null
        update { l ->
            when (target) {
                is MenuTarget.App -> when (val s = target.source) {
                    is Source.Dock -> l.copy(dock = l.dock - target.appKey)
                    is Source.InFolder -> removeFromFolderLayout(l, s.folderId, target.appKey, putOnHome = false)
                    else -> l.copy(pages = l.pages.map { p -> p.filterNot { it is AppItem && it.appKey == target.appKey } })
                }
                is MenuTarget.Folder -> l.copy(pages = l.pages.map { p -> p.filterNot { it.id == target.folderId } })
                is MenuTarget.Widget -> l.copy(pages = l.pages.map { p -> p.filterNot { it.id == target.widgetId } })
            }
        }
    }

    fun addToHome(key: String) {
        overlay = null
        if (isOnHome(key)) return
        update { l ->
            val pages = l.pages.toMutableList()
            if (pages.isEmpty()) pages += emptyList<HomeItem>()
            pages[pages.size - 1] = pages.last() + AppItem(key)
            l.copy(pages = pages)
        }
        Toast.makeText(app, "Added to Home Screen", Toast.LENGTH_SHORT).show()
    }

    fun addWidget(type: WidgetType) {
        overlay = null
        val page = (pager?.currentPage ?: 0).coerceIn(0, max(0, layout.pages.size - 1))
        update { l ->
            val pages = l.pages.toMutableList()
            if (pages.isEmpty()) pages += emptyList<HomeItem>()
            pages[page] = listOf(WidgetItem(newId("w"), type)) + pages[page]
            l.copy(pages = pages)
        }
    }

    fun renameFolder(id: String, name: String) {
        update { l -> l.copy(pages = l.pages.map { p -> p.map { if (it is FolderItem && it.id == id) it.copy(name = name) else it } }) }
    }

    fun folder(id: String): FolderItem? = layout.pages.firstNotNullOfOrNull { p -> p.firstOrNull { it.id == id } as? FolderItem }

    private fun removeFromFolderLayout(l: HomeLayout, folderId: String, key: String, putOnHome: Boolean): HomeLayout {
        return l.copy(pages = l.pages.map { p ->
            p.flatMap { item ->
                if (item is FolderItem && item.id == folderId) {
                    val rest = item.apps - key
                    val folderPart: List<HomeItem> = when (rest.size) {
                        0 -> emptyList()
                        1 -> listOf(AppItem(rest[0]))
                        else -> listOf(item.copy(apps = rest))
                    }
                    if (putOnHome) folderPart + AppItem(key) else folderPart
                } else listOf(item)
            }
        })
    }

    fun moveOutOfFolder(folderId: String, key: String) {
        overlay = null
        update { removeFromFolderLayout(it, folderId, key, putOnHome = true) }
    }

    // ---------------------------------------------------------------- drag & drop

    fun armDrag(item: HomeItem, source: Source, bounds: Rect, downAt: Offset) {
        armed = ArmedDrag(item, source, bounds, downAt)
    }

    /** Root-level pointer feed. Returns true when the event should be consumed. */
    fun onRootPointer(pos: Offset, pressed: Boolean, slop: Float): Boolean {
        val d = drag
        if (d != null) {
            if (pressed) d.pointer = pos else drop()
            return true
        }
        val a = armed ?: return false
        if (!pressed) {
            armed = null
            return false
        }
        if ((pos - a.downAt).getDistance() > slop) {
            armed = null
            startDrag(a, pos)
        }
        return true
    }

    private fun startDrag(a: ArmedDrag, pos: Offset) {
        var source = a.source
        var item = a.item
        if (source is Source.InFolder && item is AppItem) {
            val page = (pager?.currentPage ?: 0).coerceIn(0, max(0, layout.pages.size - 1))
            val fid = source.folderId
            val key = item.appKey
            update { l ->
                val removed = removeFromFolderLayout(l, fid, key, putOnHome = false)
                val pages = removed.pages.toMutableList()
                if (pages.isEmpty()) pages += emptyList<HomeItem>()
                pages[page] = pages[page] + AppItem(key)
                removed.copy(pages = pages)
            }
            val newPage = layout.pages.indexOfFirst { p -> p.any { it.id == item.id } }
            source = Source.Page(if (newPage >= 0) newPage else page)
            item = AppItem(key)
        }
        overlay = null
        editMode = true
        haptic()
        drag = DragState(item, source, a.downAt - a.bounds.topLeft, a.bounds.size, pos)
        tickJob?.cancel()
        tickJob = scope.launch {
            while (drag != null) {
                tickDrag()
                delay(40)
            }
        }
    }

    fun cancelDrag() {
        armed = null
        if (drag != null) drop()
    }

    private fun tickDrag() {
        val d = drag ?: return
        val g = grid ?: return
        val pos = d.pointer
        val now = SystemClock.uptimeMillis()
        val p = pager ?: return

        // Paging by holding the item at the screen edge.
        val edge = g.cellW * 0.32f
        val inDock = dockBounds.inflate(g.cellH * 0.15f).contains(pos)
        val dir = if (!inDock && pos.x < edge) -1 else if (!inDock && pos.x > screen.width - edge) 1 else 0
        if (dir == 0) {
            d.edgeDir = 0
        } else if (d.edgeDir != dir) {
            d.edgeDir = dir; d.edgeSince = now
        } else if (now - d.edgeSince > 650 && !p.isScrollInProgress) {
            d.edgeSince = now
            flipPage(d, dir, p)
            return
        }

        val src = d.source
        if (src is Source.Dock) {
            if (inDock) reorderDock(d, pos)
            return
        }
        if (src !is Source.Page || p.currentPage != src.index || p.isScrollInProgress) return
        val pageItems = layout.pages.getOrNull(src.index) ?: return

        val lx = (pos.x - g.origin.x) / g.cellW
        val ly = (pos.y - g.origin.y) / g.cellH
        if (lx < 0 || ly < 0 || lx >= g.cols || ly >= g.rows) {
            d.mergeTarget = null; d.mergeReady = false
            return
        }
        val cx = lx.toInt()
        val cy = ly.toInt()
        val fx = lx - cx
        val fy = ly - cy
        val placement = pack(pageItems, g.cols, g.rows).first
        val target = pageItems.firstOrNull {
            it.id != d.item.id && placement[it.id]?.let { c -> cx in c.x until c.x + it.spanX && cy in c.y until c.y + it.spanY } == true
        }
        val canMerge = d.item is AppItem && (target is AppItem || target is FolderItem)
        val iconFrac = g.iconPx / g.cellH
        if (canMerge && abs(fx - 0.5f) < 0.3f && fy < iconFrac * 0.95f) {
            if (d.mergeTarget != target!!.id) {
                d.mergeTarget = target.id; d.mergeSince = now; d.mergeReady = false
            } else if (!d.mergeReady && now - d.mergeSince > 380) {
                d.mergeReady = true
                haptic(HapticFeedbackConstants.CLOCK_TICK)
            }
            d.reorderCandidate = -1
            return
        }
        d.mergeTarget = null; d.mergeReady = false
        val from = pageItems.indexOfFirst { it.id == d.item.id }
        if (from < 0) return
        val to = if (target != null) pageItems.indexOf(target) else {
            val linear = cy * g.cols + cx
            val last = placement.values.maxOfOrNull { it.y * g.cols + it.x } ?: -1
            if (linear > last) pageItems.size - 1 else -1
        }
        if (to < 0 || to == from) { d.reorderCandidate = -1; return }
        if (d.reorderCandidate != to) { d.reorderCandidate = to; d.reorderSince = now; return }
        if (now - d.reorderSince < 110) return
        val list = pageItems.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        if (pack(list, g.cols, g.rows).second.isNotEmpty()) return
        layout = layout.copy(pages = layout.pages.toMutableList().also { pages -> pages[src.index] = list })
        d.reorderCandidate = -1
        haptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun reorderDock(d: DragState, pos: Offset) {
        val key = (d.item as? AppItem)?.appKey ?: return
        val dock = layout.dock
        val from = dock.indexOf(key)
        if (from < 0 || dock.isEmpty()) return
        val slot = dockBounds.width / dock.size
        val to = ((pos.x - dockBounds.left) / slot).toInt().coerceIn(0, dock.size - 1)
        if (to == from) return
        val list = dock.toMutableList()
        list.removeAt(from); list.add(to, key)
        layout = layout.copy(dock = list)
        haptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun flipPage(d: DragState, dir: Int, p: PagerState) {
        val g = grid ?: return
        val cur = p.currentPage
        val target = cur + dir
        if (target < 0) return
        val src = d.source
        var pages = layout.pages
        if (target >= pages.size) {
            val curItems = pages.getOrNull(cur) ?: return
            if (curItems.size <= 1) return
            pages = pages + listOf(emptyList<HomeItem>())
        }
        if (src is Source.Page) {
            val targetItems = pages[target] + d.item
            if (pack(targetItems, g.cols, g.rows).second.isNotEmpty()) return
            pages = pages.mapIndexed { i, list ->
                when (i) {
                    src.index -> list.filterNot { it.id == d.item.id }
                    target -> targetItems
                    else -> list
                }
            }
            d.source = Source.Page(target)
        }
        layout = layout.copy(pages = pages)
        scope.launch { p.animateScrollToPage(target) }
    }

    private fun drop() {
        val d = drag ?: return
        val pos = d.pointer
        val src = d.source
        val g = grid
        val item = d.item
        var l = layout
        val mergeId = d.mergeTarget
        if (d.mergeReady && mergeId != null && item is AppItem && g != null) {
            l = removeDragged(l, item, src)
            l = l.copy(pages = l.pages.map { page ->
                page.map { t ->
                    when {
                        t.id != mergeId -> t
                        t is FolderItem -> t.copy(apps = t.apps + item.appKey)
                        t is AppItem -> FolderItem(newId("f"), folderName(t.appKey, item.appKey), listOf(t.appKey, item.appKey))
                        else -> t
                    }
                }
            })
            haptic()
        } else if (src is Source.Page && item is AppItem && dockBounds.contains(pos) && l.dock.size < dockCapacity) {
            val n = l.dock.size + 1
            val slot = dockBounds.width / n
            val idx = ((pos.x - dockBounds.left) / slot).toInt().coerceIn(0, l.dock.size)
            l = removeDragged(l, item, src)
            l = l.copy(dock = l.dock.toMutableList().also { it.add(idx, item.appKey) })
        } else if (src is Source.Dock && item is AppItem && !dockBounds.inflate(8f).contains(pos) && g != null) {
            val page = (pager?.currentPage ?: 0).coerceIn(0, max(0, l.pages.size - 1))
            l = l.copy(dock = l.dock - item.appKey)
            val pages = l.pages.toMutableList()
            if (pages.isEmpty()) pages += emptyList<HomeItem>()
            val items = pages[page].toMutableList()
            val cx = ((pos.x - g.origin.x) / g.cellW).toInt().coerceIn(0, g.cols - 1)
            val cy = ((pos.y - g.origin.y) / g.cellH).toInt().coerceIn(0, g.rows - 1)
            val placement = pack(items, g.cols, g.rows).first
            val at = items.indexOfFirst { t -> placement[t.id]?.let { c -> cx in c.x until c.x + t.spanX && cy in c.y until c.y + t.spanY } == true }
            if (at >= 0) items.add(at, item) else items.add(item)
            pages[page] = items
            l = l.copy(pages = pages)
        }
        lastDrop = item.id to (pos - d.grab)
        dropSerial++
        drag = null
        tickJob?.cancel()
        val gg = grid
        layout = if (gg != null) normalize(l, gg.cols, gg.rows) else l
        save()
    }

    private fun removeDragged(l: HomeLayout, item: AppItem, src: Source): HomeLayout = when (src) {
        is Source.Dock -> l.copy(dock = l.dock - item.appKey)
        else -> l.copy(pages = l.pages.map { p -> p.filterNot { it.id == item.id } })
    }

    private fun folderName(a: String, b: String): String {
        val ca = appFor(a)?.category
        val cb = appFor(b)?.category
        return if (ca != null && ca == cb) shortName(ca) else "Folder"
    }

    fun takeDrop(id: String): Offset? {
        val d = lastDrop ?: return null
        if (d.first != id) return null
        lastDrop = null
        return d.second
    }

    // ---------------------------------------------------------------- lifecycle

    fun onHomePressed(alreadyOnHome: Boolean) {
        armed = null
        if (drag != null) cancelDrag()
        if (!alreadyOnHome) {
            overlay = null
            editMode = false
            return
        }
        when {
            overlay != null -> overlay = null
            editMode -> editMode = false
            else -> scope.launch { pager?.animateScrollToPage(0) }
        }
    }

    fun onBack() {
        when {
            drag != null -> cancelDrag()
            overlay != null -> overlay = null
            editMode -> editMode = false
            (pager?.currentPage ?: 0) != 0 -> scope.launch { pager?.animateScrollToPage(0) }
        }
    }

    fun onResume() {
        isDefaultHome = checkDefaultHome()
        badgesEnabled = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        calendarGranted = app.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        clockTick++
        if (launchedSomething) {
            launchedSomething = false
            scope.launch {
                enter.snapTo(0f)
                enter.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
            }
        }
    }

    fun onPause() {
        armed = null
        if (drag != null) cancelDrag()
        if (overlay is Overlay.Menu) overlay = null
    }

    fun playEnter() {
        scope.launch {
            enter.snapTo(0f)
            enter.animateTo(1f, tween(520))
        }
    }

    private fun checkDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = app.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == app.packageName
    }
}
