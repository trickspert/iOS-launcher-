package dev.liquidglass.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class WidgetType(val title: String) {
    BATTERY("Batteries"), CALENDAR("Calendar"), CLOCK("Clock")
}

sealed class HomeItem {
    abstract val id: String
    open val spanX: Int get() = 1
    open val spanY: Int get() = 1
}

data class AppItem(val appKey: String) : HomeItem() {
    override val id: String get() = "a:$appKey"
}

data class FolderItem(override val id: String, val name: String, val apps: List<String>) : HomeItem()

data class WidgetItem(override val id: String, val type: WidgetType) : HomeItem() {
    override val spanX: Int get() = 2
    override val spanY: Int get() = 2
}

data class HomeLayout(
    val pages: List<List<HomeItem>>,
    val dock: List<String>,
    val known: Set<String> = emptySet(),
) {
    val isEmpty get() = pages.isEmpty() && dock.isEmpty()

    /** Every app key placed somewhere on the home screen (pages, folders, dock). */
    fun placedKeys(): Set<String> {
        val s = HashSet<String>(dock)
        pages.forEach { p ->
            p.forEach {
                when (it) {
                    is AppItem -> s += it.appKey
                    is FolderItem -> s += it.apps
                    else -> {}
                }
            }
        }
        return s
    }
}

data class Cell(val x: Int, val y: Int)

/** First-fit grid packing. Returns placement per item id plus the items that did not fit. */
fun pack(items: List<HomeItem>, cols: Int, rows: Int): Pair<Map<String, Cell>, List<HomeItem>> {
    val occ = Array(rows) { BooleanArray(cols) }
    val placed = LinkedHashMap<String, Cell>()
    val overflow = ArrayList<HomeItem>()
    for (item in items) {
        val sx = item.spanX.coerceAtMost(cols)
        val sy = item.spanY.coerceAtMost(rows)
        var found: Cell? = null
        search@ for (y in 0..rows - sy) {
            for (x in 0..cols - sx) {
                var ok = true
                check@ for (yy in y until y + sy) for (xx in x until x + sx) if (occ[yy][xx]) { ok = false; break@check }
                if (ok) { found = Cell(x, y); break@search }
            }
        }
        if (found == null) {
            overflow += item
        } else {
            for (yy in found.y until found.y + sy) for (xx in found.x until found.x + sx) occ[yy][xx] = true
            placed[item.id] = found
        }
    }
    return placed to overflow
}

fun newId(prefix: String) = prefix + ":" + UUID.randomUUID().toString().take(8)

/** Spills overflowing items onto following pages and drops empty pages. */
fun normalize(layout: HomeLayout, cols: Int, rows: Int): HomeLayout {
    val result = ArrayList<List<HomeItem>>()
    var carry: List<HomeItem> = emptyList()
    // Every id (and app) may appear only once: duplicates would crash keyed Compose lists.
    val dock = layout.dock.distinct()
    val seenIds = HashSet<String>()
    val seenApps = HashSet<String>(dock)
    val src = layout.pages.map { page ->
        page.mapNotNull { item ->
            if (!seenIds.add(item.id)) return@mapNotNull null
            when (item) {
                is AppItem -> if (seenApps.add(item.appKey)) item else null
                is FolderItem -> {
                    val apps = item.apps.filter { seenApps.add(it) }
                    when (apps.size) {
                        0 -> null
                        1 -> AppItem(apps[0]).takeIf { seenIds.add(it.id) }
                        else -> item.copy(apps = apps)
                    }
                }
                is WidgetItem -> item
            }
        }
    }.toMutableList()
    var i = 0
    while (i < src.size || carry.isNotEmpty()) {
        val items = carry + (src.getOrNull(i) ?: emptyList())
        val (placed, overflow) = pack(items, cols, rows)
        result += items.filter { it.id in placed }
        carry = overflow
        i++
        if (i > 64) break
    }
    val pages = result.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) }
    return layout.copy(pages = pages, dock = dock)
}

class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("glass_layout", Context.MODE_PRIVATE)

    fun load(): HomeLayout? = runCatching {
        val s = prefs.getString("layout", null) ?: return null
        val o = JSONObject(s)
        val pages = o.getJSONArray("pages").let { pa ->
            (0 until pa.length()).map { pi ->
                val arr = pa.getJSONArray(pi)
                (0 until arr.length()).mapNotNull { ii -> itemFrom(arr.getJSONObject(ii)) }
            }
        }
        val dock = o.getJSONArray("dock").let { d -> (0 until d.length()).map { d.getString(it) } }
        val known = o.optJSONArray("known")?.let { k -> (0 until k.length()).map { k.getString(it) }.toSet() } ?: emptySet()
        HomeLayout(pages, dock, known)
    }.getOrNull()

    fun save(layout: HomeLayout) {
        val o = JSONObject()
        o.put("pages", JSONArray().apply {
            layout.pages.forEach { p -> put(JSONArray().apply { p.forEach { put(itemTo(it)) } }) }
        })
        o.put("dock", JSONArray(layout.dock))
        o.put("known", JSONArray(layout.known.toList()))
        prefs.edit().putString("layout", o.toString()).apply()
    }

    fun clear() = prefs.edit().remove("layout").apply()

    private fun itemTo(item: HomeItem): JSONObject = JSONObject().apply {
        when (item) {
            is AppItem -> { put("t", "a"); put("k", item.appKey) }
            is FolderItem -> { put("t", "f"); put("id", item.id); put("n", item.name); put("apps", JSONArray(item.apps)) }
            is WidgetItem -> { put("t", "w"); put("id", item.id); put("w", item.type.name) }
        }
    }

    private fun itemFrom(o: JSONObject): HomeItem? = when (o.optString("t")) {
        "a" -> AppItem(o.getString("k"))
        "f" -> FolderItem(
            o.getString("id"), o.optString("n", "Folder"),
            o.getJSONArray("apps").let { a -> (0 until a.length()).map { a.getString(it) } },
        )
        "w" -> runCatching { WidgetItem(o.getString("id"), WidgetType.valueOf(o.getString("w"))) }.getOrNull()
        else -> null
    }
}
