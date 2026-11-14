package dev.liquidglass.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dev.liquidglass.launcher.glass.LightSensor
import dev.liquidglass.launcher.ui.LauncherRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState
    private lateinit var sensor: LightSensor

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) state.importWallpaper(uri)
    }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state.onResume()
    }
    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.calendarGranted = granted
        state.clockTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val app = application as LauncherApplication
        // Animations (enter zoom, page flips) need Compose's frame clock, which plain lifecycleScope lacks.
        state = LauncherState(app, CoroutineScope(lifecycleScope.coroutineContext + AndroidUiDispatcher.Main))
        state.hostView = window.decorView
        state.requestDefaultHome = ::requestDefaultHome
        state.pickWallpaper = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        state.requestCalendar = { calendarPermission.launch(android.Manifest.permission.READ_CALENDAR) }
        sensor = LightSensor(this, state.light)

        onBackPressedDispatcher.addCallback(this) { state.onBack() }

        lifecycleScope.launch {
            snapshotFlow { state.settings.motionLight }.collect { on ->
                if (on && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) sensor.start()
                if (!on) { sensor.stop(); sensor.reset() }
            }
        }

        app.repo.start()
        setContent { LauncherRoot(state) }
        state.playEnter()
    }

    override fun onResume() {
        super.onResume()
        state.onResume()
        if (state.settings.motionLight) sensor.start()
    }

    override fun onPause() {
        sensor.stop()
        state.onPause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val alreadyOnHome = hasWindowFocus() &&
                (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            state.onHomePressed(alreadyOnHome)
        }
    }

    private fun requestDefaultHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }
}
