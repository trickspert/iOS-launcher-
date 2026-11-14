package dev.liquidglass.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquidglass.launcher.LauncherState
import dev.liquidglass.launcher.Overlay
import dev.liquidglass.launcher.Source
import dev.liquidglass.launcher.data.AppCategory
import dev.liquidglass.launcher.data.AppEntry
import dev.liquidglass.launcher.glass.liquidGlass

private class LibraryGroup(val title: String, val apps: List<AppEntry>)

private fun buildGroups(apps: List<AppEntry>, counts: Map<String, Int>): List<LibraryGroup> {
    val out = ArrayList<LibraryGroup>()
    val suggested = (apps.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] ?: 0 } +
        apps.filter { it.glyph != null }).distinctBy { it.key }
    if (suggested.isNotEmpty()) out += LibraryGroup("Suggestions", suggested.take(8))
    val recent = apps.filter { !it.isInternal && it.installTime > 0 }.sortedByDescending { it.installTime }
    if (recent.isNotEmpty()) out += LibraryGroup("Recently Added", recent.take(8))
    val byCat = apps.groupBy { it.category }
    for (c in AppCategory.entries) {
        val list = byCat[c] ?: continue
        out += LibraryGroup(c.title, list)
    }
    return out
}

/** iOS-style App Library: every app, auto-sorted into glass category boxes. */
@Composable
fun AppLibrary(state: LauncherState) {
    val apps = state.repo.apps
    val groups = remember(apps, state.repo.launchCounts) { buildGroups(apps, state.repo.launchCounts) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            GlassButton(
                onClick = { state.overlay = Overlay.Spotlight(library = true) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                corner = 22.dp,
                tint = dev.liquidglass.launcher.glass.darkGlassTint(state.settings),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Symbol(Sym.SEARCH, 16.dp, Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(8.dp))
                    BasicText("App Library", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp))
                }
            }
        }
        items(groups, key = { it.title }) { g -> LibraryBox(state, g) }
    }
}

@Composable
private fun LibraryBox(state: LauncherState, g: LibraryGroup) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .liquidGlass(30.dp, refraction = 1.2f)
                .padding(14.dp),
        ) {
            val gap = 12.dp
            val big = (maxWidth - gap) / 2
            val iconSize = big
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Slot(state, g.apps.getOrNull(0), iconSize)
                    Slot(state, g.apps.getOrNull(1), iconSize)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Slot(state, g.apps.getOrNull(2), iconSize)
                    if (g.apps.size <= 4) {
                        Slot(state, g.apps.getOrNull(3), iconSize)
                    } else {
                        Cluster(state, g, big) { state.overlay = Overlay.Folder(null, g.title, g.apps.map { it.key }, bounds) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(g.title, style = LabelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun Slot(state: LauncherState, app: AppEntry?, size: Dp) {
    if (app == null) {
        Spacer(Modifier.size(size))
        return
    }
    Box(Modifier.size(size), contentAlignment = Alignment.TopCenter) {
        // AppIcon adds 11dp either side for its label column; no labels here.
        Box(Modifier.width(size + 22.dp), contentAlignment = Alignment.TopCenter) {
            AppIcon(app, size, showLabel = false, source = Source.Library)
        }
    }
}

@Composable
private fun Cluster(state: LauncherState, g: LibraryGroup, size: Dp, onOpen: () -> Unit) {
    val gap = 6.dp
    val mini = (size - gap) / 2
    Column(
        Modifier.size(size).plainClick(onOpen),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (r in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until 2) {
                    val a = g.apps.getOrNull(3 + r * 2 + c)
                    if (a != null) MiniIcon(a, mini) else Spacer(Modifier.size(mini))
                }
            }
        }
    }
}
