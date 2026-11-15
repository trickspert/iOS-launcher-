package dev.liquidglass.launcher.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
        // Gradients look identical at half resolution and save a lot of memory.
        return generate(id, max(1, w / 2), max(1, h / 2))
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

    fun generate(id: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun vertical(vararg stops: Pair<Float, Int>) {
            p.shader = LinearGradient(0f, 0f, 0f, hf, stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf, p)
            p.shader = null
        }

        fun diagonal(vararg stops: Pair<Float, Int>) {
            p.shader = LinearGradient(0f, 0f, wf, hf, stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf, p)
            p.shader = null
        }

        /** Soft translucent blob: an ellipse filled with a radial falloff, optionally rotated. */
        fun blob(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, color: Int, fadeTo: Int = color and 0x00FFFFFF) {
            c.save()
            c.rotate(deg, cx * wf, cy * hf)
            p.shader = RadialGradient(cx * wf, cy * hf, max(rx * wf, ry * hf), color, fadeTo, Shader.TileMode.CLAMP)
            c.drawOval(RectF(cx * wf - rx * wf, cy * hf - ry * hf, cx * wf + rx * wf, cy * hf + ry * hf), p)
            p.shader = null
            c.restore()
        }

        /** Thin light edge along an ellipse arc, like the glass ribbons on iOS wallpapers. */
        fun arcLine(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, start: Float, sweep: Float, alpha: Int, width: Float) {
            c.save()
            c.rotate(deg, cx * wf, cy * hf)
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = width * wf / 390f
                color = (alpha shl 24) or 0xFFFFFF
            }
            c.drawArc(RectF(cx * wf - rx * wf, cy * hf - ry * hf, cx * wf + rx * wf, cy * hf + ry * hf), start, sweep, false, sp)
            c.restore()
        }

        /** A solid ribbon (ellipse) with a highlighted rim — the main iOS 26 wallpaper motif. */
        fun ribbon(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, inner: Int, outer: Int) {
            c.save()
            c.rotate(deg, cx * wf, cy * hf)
            val r = RectF(cx * wf - rx * wf, cy * hf - ry * hf, cx * wf + rx * wf, cy * hf + ry * hf)
            p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, inner, outer, Shader.TileMode.CLAMP)
            c.drawOval(r, p)
            p.shader = null
            c.restore()
        }

        when (id) {
            "lavender" -> {
                vertical(0f to 0xFFA895C8.toInt(), 0.45f to 0xFF7D8ED3.toInt(), 1f to 0xFF5B7FCB.toInt())
                blob(0.85f, 0.15f, 0.7f, 0.25f, -20f, 0x66C9B6E8)
                blob(0.1f, 0.85f, 0.8f, 0.3f, 15f, 0x553D63C2)
            }
            "midnight" -> {
                diagonal(0f to 0xFF0B1433.toInt(), 0.6f to 0xFF1A2E6E.toInt(), 1f to 0xFF2B4FA8.toInt())
                ribbon(-0.1f, 0.62f, 0.95f, 0.3f, -28f, 0xFF1F3C8F.toInt(), 0xFF0D1A44.toInt())
                blob(0.9f, 0.3f, 0.7f, 0.28f, -30f, 0x885E4BD6.toInt())
                arcLine(-0.1f, 0.62f, 0.95f, 0.3f, -28f, 200f, 120f, 70, 1.5f)
            }
            "sunrise" -> {
                vertical(0f to 0xFFF6B07A.toInt(), 0.45f to 0xFFE27399.toInt(), 1f to 0xFF6B5BC9.toInt())
                blob(0.15f, 0.25f, 0.8f, 0.3f, 20f, 0x88FFD3A5.toInt())
                ribbon(1.05f, 0.7f, 0.8f, 0.28f, -35f, 0x99B35FD0.toInt(), 0x33E27399)
                arcLine(1.05f, 0.7f, 0.8f, 0.28f, -35f, 140f, 110f, 80, 1.5f)
            }
            "mint" -> {
                vertical(0f to 0xFFB5EAD7.toInt(), 0.5f to 0xFF5FB3B8.toInt(), 1f to 0xFF2E5E8C.toInt())
                blob(0.85f, 0.3f, 0.7f, 0.26f, -25f, 0x77E8FFF6)
                ribbon(-0.15f, 0.7f, 0.9f, 0.27f, -30f, 0xAA3A8FA6.toInt(), 0x2234617E)
                arcLine(-0.15f, 0.7f, 0.9f, 0.27f, -30f, 250f, 120f, 80, 1.5f)
            }
            "graphite" -> {
                vertical(0f to 0xFF3A3D44.toInt(), 0.5f to 0xFF1E2026.toInt(), 1f to 0xFF0E0F12.toInt())
                ribbon(1.0f, 0.45f, 0.8f, 0.3f, -32f, 0xFF4B5060.toInt(), 0xFF1A1C22.toInt())
                arcLine(1.0f, 0.45f, 0.8f, 0.3f, -32f, 120f, 120f, 70, 1.5f)
                blob(0.1f, 0.85f, 0.7f, 0.3f, 0f, 0x553E6BD6)
            }
            else -> { // aurora
                vertical(0f to 0xFF86A9E3.toInt(), 0.5f to 0xFF5D88D6.toInt(), 1f to 0xFF7FB3C9.toInt())
                blob(-0.15f, 0.62f, 0.95f, 0.30f, -28f, 0xD92855BE.toInt())
                blob(0.95f, 0.42f, 0.75f, 0.26f, -34f, 0xE6BED7FA.toInt(), 0x0096B9F0)
                arcLine(-0.15f, 0.62f, 0.95f, 0.30f, -28f, -69f, 103f, 90, 1.5f)
                arcLine(0.95f, 0.42f, 0.75f, 0.26f, -34f, 126f, 97f, 64, 1.5f)
            }
        }
        return bmp
    }

    /** Fast frosted copy: progressive downscale then a 3-pass box blur. */
    fun blur(src: Bitmap, frost: Float, screenW: Int): Bitmap {
        val targetW = max(48, screenW / 6)
        var cur = src
        while (cur.width / 2 >= targetW) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, max(1, cur.height / 2), true)
        }
        val tw = targetW.coerceAtMost(cur.width)
        val th = max(1, (cur.height * tw.toFloat() / cur.width).roundToInt())
        val small = Bitmap.createScaledBitmap(cur, tw, th, true).copy(Bitmap.Config.ARGB_8888, true)
        val radius = (1 + frost * 5f).roundToInt()
        boxBlur(small, radius, 3)
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
