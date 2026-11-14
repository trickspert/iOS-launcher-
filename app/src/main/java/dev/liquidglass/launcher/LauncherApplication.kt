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
        settings = LauncherSettings(this)
        repo = AppRepository(this)
        icons = IconCache(this)
        repo.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) icons.clear()
    }
}
