package dev.liquidglass.launcher

import android.app.Application
import dev.liquidglass.launcher.data.AppRepository
import dev.liquidglass.launcher.data.LauncherSettings
import dev.liquidglass.launcher.icons.IconCache

class LauncherApplication : Application() {
    lateinit var settings: LauncherSettings
        private set
    lateinit var repo: AppRepository
        private set
    lateinit var icons: IconCache
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashReporter()
        settings = LauncherSettings(this)
        repo = AppRepository(this)
        icons = IconCache(this)
        repo.start()
    }

    /** Saves the stack trace of a crash so the next launch can offer to share it. */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val info = "Glass ${packageManager.getPackageInfo(packageName, 0).versionName} · " +
                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                    "Thread: ${t.name}\n\n"
                crashFile().writeText(info + sw.toString().take(12000))
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun crashFile() = java.io.File(filesDir, "last_crash.txt")

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) icons.clear()
    }
}
