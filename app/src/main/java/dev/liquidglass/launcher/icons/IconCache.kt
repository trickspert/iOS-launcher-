package dev.liquidglass.launcher.icons

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import dev.liquidglass.launcher.data.AppEntry
import dev.liquidglass.launcher.data.IconStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * @param fullBleed the bitmap is a complete square icon that should be clipped to the tile shape.
 * @param tileTint colour to mix into the glass tile under this icon, or null for the style default.
 */
class IconRender(val bitmap: ImageBitmap, val fullBleed: Boolean, val tileTint: Color?)

class IconCache(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 6).toInt()
    private val cache = object : LruCache<String, IconRender>(maxBytes) {
        override fun sizeOf(key: String, value: IconRender) = value.bitmap.width * value.bitmap.height * 4
    }

    fun peek(key: String): IconRender? = cache.get(key)

    fun key(app: AppEntry, style: IconStyle, accent: Int, sizePx: Int, now: Calendar): String {
        val dyn = when (app.glyph) {
            Glyph.CLOCK -> "|" + now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE)
            Glyph.CALENDAR -> "|" + now.get(Calendar.DAY_OF_YEAR)
            else -> ""
        }
        val tintPart = if (style == IconStyle.TINTED) "|$accent" else ""
        return "${app.key}|${style.name}|$sizePx$tintPart$dyn"
    }

    fun clear() = cache.evictAll()

    suspend fun get(app: AppEntry, style: IconStyle, accent: Int, sizePx: Int, now: Calendar = Calendar.getInstance()): IconRender {
        val k = key(app, style, accent, sizePx, now)
        cache.get(k)?.let { return it }
        val r = withContext(Dispatchers.Default) {
            runCatching { render(app, style, accent, sizePx, now) }.getOrElse { fallback(app, sizePx) }
        }
        cache.put(k, r)
        return r
    }

    private fun render(app: AppEntry, style: IconStyle, accent: Int, size: Int, now: Calendar): IconRender {
        app.glyph?.let { g ->
            return IconRender(GlyphPainter.render(g, style, accent, size, now).prepared(), false, null)
        }
        val info = launcherApps.resolveActivity(Intent().setComponent(app.component), app.user)
        val d = info?.getIcon(context.resources.displayMetrics.densityDpi)
            ?: context.packageManager.getApplicationIcon(app.packageName)
        val darkTile = style == IconStyle.DARK

        if (d is AdaptiveIconDrawable) {
            val fg = d.foreground
            val bg = d.background
            when (style) {
                IconStyle.DEFAULT, IconStyle.DARK -> {
                    val fgBmp = fg?.let { layer(it, size) }
                    val cover = fgBmp?.let { coverage(it) } ?: 0f
                    if (fgBmp == null || bg == null || cover < 0.03f || cover > 0.78f) {
                        return IconRender(full(d, size).prepared(), true, null)
                    }
                    val bgColor = averageColor(bg)
                    val tint = if (darkTile) Color(0xFF101114).copy(alpha = 0.55f)
                    else Color(bgColor).copy(alpha = if (isNearWhite(bgColor)) 0.28f else 0.42f)
                    return IconRender(fgBmp.prepared(), false, tint)
                }
                IconStyle.CLEAR, IconStyle.TINTED -> {
                    val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
                    val src = mono?.let { layer(it, size) }
                        ?: fg?.let { layer(it, size) }?.takeIf { coverage(it) in 0.03f..0.78f }
                    val color = if (style == IconStyle.TINTED) ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.55f) else 0xFFFFFFFF.toInt()
                    return if (src != null) IconRender(silhouette(src, color).prepared(), false, null)
                    else IconRender(washed(full(d, size), color).prepared(), true, null)
                }
            }
        }
        // Legacy (non adaptive) icon.
        val legacy = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = (size * 0.13f).toInt()
        d.setBounds(inset, inset, size - inset, size - inset)
        d.draw(Canvas(legacy))
        return when (style) {
            IconStyle.CLEAR, IconStyle.TINTED -> {
                val color = if (style == IconStyle.TINTED) ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.55f) else 0xFFFFFFFF.toInt()
                IconRender(washed(legacy, color).prepared(), false, null)
            }
            else -> IconRender(legacy.prepared(), false, null)
        }
    }

    private fun fallback(app: AppEntry, size: Int): IconRender {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF2FFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = size * 0.42f
        }
        c.drawText(app.label.take(1).uppercase(), size / 2f, size / 2f - (p.descent() + p.ascent()) / 2f, p)
        return IconRender(b.prepared(), false, null)
    }

    /** Draws an adaptive layer full-bleed (108dp layer -> 72dp visible tile). */
    private fun layer(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val e = size / 4
        d.setBounds(-e, -e, size + e, size + e)
        d.draw(Canvas(b))
        return b
    }

    private fun full(d: AdaptiveIconDrawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val e = size / 4
        d.background?.let { it.setBounds(-e, -e, size + e, size + e); it.draw(c) }
        d.foreground?.let { it.setBounds(-e, -e, size + e, size + e); it.draw(c) }
        return b
    }

    private fun coverage(b: Bitmap): Float {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true)
        val px = IntArray(24 * 24)
        s.getPixels(px, 0, 24, 0, 0, 24, 24)
        return px.count { (it ushr 24) > 40 } / px.size.toFloat()
    }

    private fun averageColor(d: Drawable): Int {
        val b = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 12, 12)
        d.draw(Canvas(b))
        val px = IntArray(144)
        b.getPixels(px, 0, 12, 0, 0, 12, 12)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (c in px) {
            if ((c ushr 24) < 60) continue
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF; n++
        }
        if (n == 0L) return 0xFFFFFFFF.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }

    private fun isNearWhite(c: Int) = ColorUtils.calculateLuminance(c) > 0.8

    /** Solid glyph in [color] with a soft top-to-bottom glass falloff. */
    private fun silhouette(src: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, p)
        val g = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, src.height.toFloat(), 0xFF000000.toInt(), 0xC0000000.toInt(), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), g)
        return out
    }

    /** Desaturated, lightened version for icons without a usable glyph layer. */
    private fun washed(src: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    0.45f * r, 0f, 0f, 0f, 140f * r,
                    0f, 0.45f * g, 0f, 0f, 140f * g,
                    0f, 0f, 0.45f * b, 0f, 140f * b,
                    0f, 0f, 0f, 0.9f, 0f,
                ),
            ),
        )
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    private fun Bitmap.prepared(): ImageBitmap {
        prepareToDraw()
        return asImageBitmap()
    }
}
