package dev.liquidglass.launcher.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Built-in procedural wallpapers in the spirit of the iOS 26 glass backgrounds, plus photo import. */
object Wallpapers {
    val builtIns = listOf(
        "aurora" to "Aurora",
        "lavender" to "Lavender",
        "midnight" to "Midnight",
        "sunrise" to "Sunrise",
        "mint" to "Mint",
        "graphite" to "Graphite",
    )

    private fun customFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    fun hasCustom(ctx: Context) = customFile(ctx).exists()

    /** Returns a bitmap with exactly the screen's aspect ratio. */
    fun load(ctx: Context, id: String, w: Int, h: Int): Bitmap {
        if (id == "custom") {
            val f = customFile(ctx)
            if (f.exists()) {
                val b = BitmapFactory.decodeFile(f.absolutePath)
                if (b != null) return centerCrop(b, w, h)
            }
        }
        // Crisp edges matter (the glass refracts them), so render at screen resolution up to 1080p wide.
        val scale = minOf(1f, 1080f / w)
        return generate(id, max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
    }

    fun importPhoto(ctx: Context, uri: Uri, w: Int, h: Int): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false
        val cropped = centerCrop(src, w, h)
        customFile(ctx).outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrDefault(false)

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val target = w.toFloat() / h
        val cur = src.width.toFloat() / src.height
        val crop = if (cur > target) {
            val cw = (src.height * target).roundToInt()
            Rect((src.width - cw) / 2, 0, (src.width - cw) / 2 + cw, src.height)
        } else {
            val ch = (src.width / target).roundToInt()
            Rect(0, (src.height - ch) / 2, src.width, (src.height - ch) / 2 + ch)
        }
        val outW = minOf(w, crop.width())
        val outH = (outW / target).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, crop, Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private class Disc(
        val cx: Float, val cy: Float, val r: Float, val c0: Int, val c1: Int,
        val edge: Float = 0.4f, val sy: Float = 1f, val rot: Float = 0f, val shade: Float = 0f, val glow: Float = 0f,
    )

    private class Design(val bg: List<Pair<Float, Int>>, val discs: List<Disc>)

    private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color.argb((a * 255).toInt(), r, g, b)

    /** Each wallpaper is a soft gradient with crisp, lit glass discs — detail for the glass to refract. */
    private val designs: Map<String, Design> = mapOf(
        "aurora" to Design(
            listOf(0f to 0xFF7FA3DE.toInt(), 0.55f to 0xFF4F7DD2.toInt(), 1f to 0xFF86B9CC.toInt()),
            listOf(
                Disc(1.28f, 0.30f, 1.02f, rgba(168, 196, 240, 0.78f), rgba(120, 160, 226, 0.55f), edge = 0.55f, glow = 0.12f),
                Disc(-0.42f, 0.50f, 0.92f, rgba(52, 98, 205, 0.92f), rgba(36, 78, 186, 0.86f), edge = 0.35f, shade = 0.25f),
                Disc(0.80f, 0.86f, 0.80f, rgba(96, 140, 214, 0.85f), rgba(126, 186, 204, 0.8f), edge = 0.5f, sy = 0.52f, rot = -16f, glow = 0.14f),
                Disc(0.10f, 1.12f, 0.70f, rgba(64, 112, 200, 0.6f), rgba(90, 150, 200, 0.5f), edge = 0.3f, shade = 0.15f),
            ),
        ),
        "lavender" to Design(
            listOf(0f to 0xFFB09AD0.toInt(), 0.5f to 0xFF8590D8.toInt(), 1f to 0xFF5B7FCB.toInt()),
            listOf(
                Disc(1.2f, 0.18f, 0.9f, rgba(200, 180, 235, 0.6f), rgba(150, 150, 225, 0.4f), edge = 0.45f, glow = 0.1f),
                Disc(-0.3f, 0.75f, 0.85f, rgba(96, 110, 214, 0.6f), rgba(70, 100, 200, 0.6f), edge = 0.3f, shade = 0.18f),
                Disc(0.85f, 1.02f, 0.7f, rgba(150, 140, 226, 0.5f), rgba(110, 140, 220, 0.5f), edge = 0.4f, sy = 0.55f, rot = -14f, glow = 0.1f),
            ),
        ),
        "midnight" to Design(
            listOf(0f to 0xFF0A1230.toInt(), 0.6f to 0xFF152A66.toInt(), 1f to 0xFF27489C.toInt()),
            listOf(
                Disc(1.25f, 0.35f, 1.0f, rgba(48, 70, 160, 0.75f), rgba(24, 40, 110, 0.6f), edge = 0.35f, glow = 0.08f),
                Disc(-0.4f, 0.55f, 0.9f, rgba(16, 28, 84, 0.92f), rgba(10, 18, 60, 0.9f), edge = 0.22f, shade = 0.3f),
                Disc(0.8f, 0.9f, 0.8f, rgba(70, 60, 190, 0.7f), rgba(40, 90, 190, 0.7f), edge = 0.35f, sy = 0.5f, rot = -16f, glow = 0.1f),
            ),
        ),
        "sunrise" to Design(
            listOf(0f to 0xFFF7B983.toInt(), 0.45f to 0xFFE77C97.toInt(), 1f to 0xFF6F5CC8.toInt()),
            listOf(
                Disc(1.22f, 0.24f, 0.95f, rgba(255, 214, 170, 0.7f), rgba(246, 150, 150, 0.5f), edge = 0.5f, glow = 0.12f),
                Disc(-0.38f, 0.58f, 0.9f, rgba(214, 92, 140, 0.78f), rgba(150, 76, 180, 0.78f), edge = 0.32f, shade = 0.18f),
                Disc(0.82f, 0.9f, 0.8f, rgba(170, 96, 200, 0.7f), rgba(96, 96, 210, 0.7f), edge = 0.42f, sy = 0.5f, rot = -16f, glow = 0.1f),
            ),
        ),
        "mint" to Design(
            listOf(0f to 0xFFBDEEDC.toInt(), 0.5f to 0xFF62B6BA.toInt(), 1f to 0xFF2F628F.toInt()),
            listOf(
                Disc(1.25f, 0.28f, 1.0f, rgba(220, 250, 240, 0.65f), rgba(140, 214, 210, 0.5f), edge = 0.55f, glow = 0.12f),
                Disc(-0.4f, 0.52f, 0.9f, rgba(52, 150, 168, 0.85f), rgba(40, 110, 150, 0.82f), edge = 0.32f, shade = 0.2f),
                Disc(0.8f, 0.88f, 0.8f, rgba(70, 150, 180, 0.75f), rgba(56, 104, 160, 0.75f), edge = 0.42f, sy = 0.5f, rot = -16f, glow = 0.1f),
            ),
        ),
        "graphite" to Design(
            listOf(0f to 0xFF3C4048.toInt(), 0.5f to 0xFF1F2128.toInt(), 1f to 0xFF0E0F13.toInt()),
            listOf(
                Disc(1.25f, 0.32f, 1.0f, rgba(92, 98, 112, 0.7f), rgba(48, 52, 62, 0.6f), edge = 0.4f, glow = 0.08f),
                Disc(-0.4f, 0.55f, 0.9f, rgba(22, 24, 30, 0.92f), rgba(12, 13, 17, 0.9f), edge = 0.2f, shade = 0.2f),
                Disc(0.8f, 0.9f, 0.8f, rgba(56, 76, 130, 0.6f), rgba(36, 44, 70, 0.7f), edge = 0.32f, sy = 0.5f, rot = -16f, glow = 0.08f),
            ),
        ),
    )

    fun generate(id: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val design = designs[id] ?: designs.getValue("aurora")
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, hf, design.bg.map { it.second }.toIntArray(), design.bg.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, p)
        for (d in design.discs) {
            c.save()
            c.translate(d.cx * wf, d.cy * hf)
            c.rotate(d.rot)
            c.scale(1f, d.sy)
            val r = d.r * wf
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(-r, -r, r, r, d.c0, d.c1, Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, r, p)
            if (d.shade > 0f) {
                // Darkening towards the rim reads as the disc's thickness.
                p.shader = RadialGradient(0f, 0f, r, intArrayOf(rgba(10, 30, 90, 0f), rgba(10, 30, 90, 0f), rgba(10, 30, 90, d.shade)), floatArrayOf(0f, 0.86f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(0f, 0f, r, p)
            }
            if (d.glow > 0f) {
                p.shader = RadialGradient(0f, 0f, r, intArrayOf(0x00FFFFFF, 0x00FFFFFF, rgba(255, 255, 255, d.glow)), floatArrayOf(0f, 0.9f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(0f, 0f, r, p)
            }
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.4f * wf / 390f
            p.color = rgba(255, 255, 255, d.edge)
            c.drawCircle(0f, 0f, r - p.strokeWidth / 2f, p)
            c.restore()
        }
        return bmp
    }

    /** Fast frosted copy: progressive downscale then a 3-pass box blur. */
    fun blur(src: Bitmap, screenW: Int): Bitmap {
        val targetW = max(48, screenW / 6)
        var cur = src
        while (cur.width / 2 >= targetW) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, max(1, cur.height / 2), true)
        }
        val tw = targetW.coerceAtMost(cur.width)
        val th = max(1, (cur.height * tw.toFloat() / cur.width).roundToInt())
        val small = Bitmap.createScaledBitmap(cur, tw, th, true).copy(Bitmap.Config.ARGB_8888, true)
        boxBlur(small, 4, 3)
        return small
    }

    private fun boxBlur(bmp: Bitmap, r: Int, passes: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val div = 2 * r + 1
        repeat(passes) {
            // horizontal
            for (y in 0 until h) {
                var sr = 0; var sg = 0; var sb = 0
                val row = y * w
                for (i in -r..r) {
                    val c = px[row + i.coerceIn(0, w - 1)]
                    sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
                }
                for (x in 0 until w) {
                    tmp[row + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                    val a = px[row + (x + r + 1).coerceAtMost(w - 1)]
                    val b = px[row + (x - r).coerceAtLeast(0)]
                    sr += ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
                    sg += ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
                    sb += (a and 0xFF) - (b and 0xFF)
                }
            }
            // vertical
            for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0
                for (i in -r..r) {
                    val c = tmp[i.coerceIn(0, h - 1) * w + x]
                    sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
                }
                for (y in 0 until h) {
                    px[y * w + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                    val a = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                    val b = tmp[(y - r).coerceAtLeast(0) * w + x]
                    sr += ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
                    sg += ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
                    sb += (a and 0xFF) - (b and 0xFF)
                }
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}
