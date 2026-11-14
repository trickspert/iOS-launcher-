package dev.liquidglass.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class IconStyle(val label: String) {
    DEFAULT("Default"), DARK("Dark"), CLEAR("Clear"), TINTED("Tinted");

    val colorful get() = this == DEFAULT || this == DARK
}

/** All user-tunable options. Every property is Compose state and is written through to prefs. */
class LauncherSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("glass_settings", Context.MODE_PRIVATE)

    private var iconStyleIndex by intPref("iconStyle", 0)
    var iconStyle: IconStyle
        get() = IconStyle.entries.getOrElse(iconStyleIndex) { IconStyle.DEFAULT }
        set(value) { iconStyleIndex = value.ordinal }

    /** Hue (0..360) used by the Tinted icon style. */
    var tintHue by floatPref("tintHue", 222f)
    var wallpaper by stringPref("wallpaper", "aurora")
    /** 0..1 strength of the lens refraction at glass edges. */
    var refraction by floatPref("refraction", 0.65f)
    /** 0..1 how frosted (blurred) the glass is. */
    var frost by floatPref("frost", 0.45f)
    /** 0..1 how milky / white the glass is. */
    var glassTint by floatPref("glassTint", 0.35f)
    var motionLight by boolPref("motionLight", true)
    var showLabels by boolPref("showLabels", true)
    var columns by intPref("columns", 4)
    var bannerDismissed by boolPref("bannerDismissed", false)
    var wallpaperVersion by intPref("wallpaperVersion", 0)

    val accent: Color get() = Color.hsv(tintHue.coerceIn(0f, 360f), 0.55f, 1f)

    fun resetGlass() {
        refraction = 0.65f; frost = 0.45f; glassTint = 0.35f
    }

    private fun floatPref(key: String, def: Float) = object : ReadWriteProperty<Any?, Float> {
        val state = mutableFloatStateOf(prefs.getFloat(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.floatValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            state.floatValue = value; prefs.edit().putFloat(key, value).apply()
        }
    }

    private fun intPref(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        val state = mutableIntStateOf(prefs.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.intValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.intValue = value; prefs.edit().putInt(key, value).apply()
        }
    }

    private fun boolPref(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        val state = mutableStateOf(prefs.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value; prefs.edit().putBoolean(key, value).apply()
        }
    }

    private fun stringPref(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        val state = mutableStateOf(prefs.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value; prefs.edit().putString(key, value).apply()
        }
    }
}
