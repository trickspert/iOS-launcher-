package dev.liquidglass.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.liquidglass.launcher.icons.Glyph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class AppCategory(val title: String) {
    SOCIAL("Social"),
    PRODUCTIVITY("Productivity & Finance"),
    ENTERTAINMENT("Entertainment"),
    CREATIVITY("Creativity"),
    GAMES("Games"),
    INFO("Information & Reading"),
    TRAVEL("Travel"),
    UTILITIES("Utilities"),
}

data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val glyph: Glyph?,
    val category: AppCategory,
    val installTime: Long,
    val isWork: Boolean,
) {
    val isInternal get() = key == SETTINGS_KEY

    companion object {
        const val SETTINGS_KEY = "glass:settings"
    }
}

/** Loads launchable activities for every profile and keeps them up to date. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val usagePrefs = context.getSharedPreferences("glass_usage", Context.MODE_PRIVATE)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var byKey by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    var loaded by mutableStateOf(false)
        private set
    var launchCounts by mutableStateOf<Map<String, Int>>(loadCounts())
        private set

    private var refreshJob: Job? = null
    private var started = false

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh(250)
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh(250)
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh(250)
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh(250)
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh(250)
    }

    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh(0)
    }

    fun refresh(delayMs: Long = 0) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val list = withContext(Dispatchers.IO) { load() }
            apps = list
            byKey = list.associateBy { it.key }
            loaded = true
        }
    }

    fun recordLaunch(key: String) {
        val m = launchCounts.toMutableMap()
        m[key] = (m[key] ?: 0) + 1
        launchCounts = m
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        usagePrefs.edit().putString("counts", o.toString()).apply()
    }

    private fun loadCounts(): Map<String, Int> = runCatching {
        val o = JSONObject(usagePrefs.getString("counts", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun load(): List<AppEntry> {
        val glyphs = GlyphResolver.resolve(context)
        val me = Process.myUserHandle()
        val out = ArrayList<AppEntry>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            for (info in list) {
                val cn = info.componentName
                if (cn.packageName == context.packageName) continue
                val ai = info.applicationInfo
                out += AppEntry(
                    key = cn.flattenToShortString() + "#" + serial,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    packageName = cn.packageName,
                    component = cn,
                    user = user,
                    glyph = if (user == me) glyphs[cn.packageName] else null,
                    category = categoryOf(ai),
                    installTime = info.firstInstallTime,
                    isWork = user != me,
                )
            }
        }
        out += AppEntry(
            key = AppEntry.SETTINGS_KEY,
            label = "Glass Settings",
            packageName = context.packageName,
            component = ComponentName(context.packageName, "dev.liquidglass.launcher.MainActivity"),
            user = me,
            glyph = Glyph.LAUNCHER,
            category = AppCategory.UTILITIES,
            installTime = 0L,
            isWork = false,
        )
        return out.sortedBy { it.label.lowercase() }
    }

    private fun categoryOf(ai: ApplicationInfo): AppCategory {
        val p = ai.packageName.lowercase()
        val byName = when {
            SOCIAL_HINTS.any { it in p } -> AppCategory.SOCIAL
            MEDIA_HINTS.any { it in p } -> AppCategory.ENTERTAINMENT
            else -> null
        }
        return when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.CREATIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.INFO
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            else -> byName ?: AppCategory.UTILITIES
        }
    }

    companion object {
        private val SOCIAL_HINTS = listOf(
            "whatsapp", "telegram", "instagram", "facebook", "twitter", "snapchat", "tiktok", "musically",
            "discord", "reddit", "messenger", "signal", "linkedin", "threads", "pinterest", "viber", "wechat",
        )
        private val MEDIA_HINTS = listOf(
            "netflix", "youtube", "spotify", "music", "primevideo", "disney", "twitch", "hulu", "podcast",
            "soundcloud", "deezer", "tidal",
        )
    }
}

/** Picks one app per custom glyph (the system's default handler where possible). */
object GlyphResolver {
    fun resolve(ctx: Context): Map<String, Glyph> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, Glyph>()

        fun installed(pkg: String) = pm.getLaunchIntentForPackage(pkg) != null

        fun handler(intent: Intent): String? {
            val def = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()
            if (def != null && def != "android" && installed(def)) return def
            val all = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
                .map { it.activityInfo }
                .filter { installed(it.packageName) }
            return (all.firstOrNull { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: all.firstOrNull())
                ?.packageName
        }

        fun cat(category: String) = Intent(Intent.ACTION_MAIN).addCategory(category)

        fun assign(glyph: Glyph, known: List<String>, vararg intents: Intent) {
            if (out.containsValue(glyph)) return
            val pkg = known.firstOrNull { installed(it) && it !in out }
                ?: intents.firstNotNullOfOrNull { i -> handler(i)?.takeIf { it !in out } }
                ?: return
            out[pkg] = glyph
        }

        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()
        assign(Glyph.PHONE, listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer"), Intent(Intent.ACTION_DIAL))
        assign(Glyph.MESSAGES, listOfNotNull(sms, "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"), cat(Intent.CATEGORY_APP_MESSAGING))
        assign(Glyph.CAMERA, listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera2", "com.android.camera"), Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        assign(Glyph.PHOTOS, listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.android.gallery3d"), cat(Intent.CATEGORY_APP_GALLERY))
        assign(Glyph.SETTINGS, listOf("com.android.settings"), Intent(Settings.ACTION_SETTINGS))
        assign(Glyph.MAIL, listOf("com.google.android.gm", "com.samsung.android.email.provider"), cat(Intent.CATEGORY_APP_EMAIL))
        assign(Glyph.BROWSER, listOf("com.android.chrome", "com.sec.android.app.sbrowser"), Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), cat(Intent.CATEGORY_APP_BROWSER))
        assign(Glyph.MUSIC, listOf("com.google.android.apps.youtube.music", "com.spotify.music", "com.sec.android.app.music"), cat(Intent.CATEGORY_APP_MUSIC))
        assign(Glyph.CALENDAR, listOf("com.google.android.calendar", "com.samsung.android.calendar"), cat(Intent.CATEGORY_APP_CALENDAR))
        assign(Glyph.CLOCK, listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"), Intent(AlarmClock.ACTION_SHOW_ALARMS))
        assign(Glyph.CALCULATOR, listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2"), cat(Intent.CATEGORY_APP_CALCULATOR))
        assign(Glyph.MAPS, listOf("com.google.android.apps.maps"), cat(Intent.CATEGORY_APP_MAPS))
        assign(Glyph.CONTACTS, listOf("com.google.android.contacts", "com.samsung.android.app.contacts", "com.android.contacts"), cat(Intent.CATEGORY_APP_CONTACTS))
        assign(Glyph.FILES, listOf("com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.google.android.documentsui", "com.android.documentsui"), cat("android.intent.category.APP_FILES"))
        assign(Glyph.STORE, listOf("com.android.vending", "com.sec.android.app.samsungapps"))
        assign(Glyph.NOTES, listOf("com.google.android.keep", "com.samsung.android.app.notes"))
        assign(Glyph.WEATHER, listOf("com.google.android.apps.weather", "com.sec.android.daemonapp"), cat("android.intent.category.APP_WEATHER"))
        assign(Glyph.HEALTH, listOf("com.google.android.apps.fitness", "com.sec.android.app.shealth"), cat("android.intent.category.APP_FITNESS"))
        assign(Glyph.VIDEO, listOf("com.google.android.youtube"))
        return out
    }
}
