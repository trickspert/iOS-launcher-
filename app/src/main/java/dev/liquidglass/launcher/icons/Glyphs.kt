package dev.liquidglass.launcher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import dev.liquidglass.launcher.data.IconStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Custom glass-style glyphs drawn for well known system apps. */
enum class Glyph {
    PHONE, MESSAGES, CAMERA, PHOTOS, SETTINGS, MAIL, BROWSER, MUSIC, CALENDAR, CLOCK, CALCULATOR,
    MAPS, CONTACTS, FILES, STORE, NOTES, WEATHER, HEALTH, VIDEO, LAUNCHER;

    /** Glyphs whose look depends on the current time. */
    val dynamic get() = this == CALENDAR || this == CLOCK
}

object GlyphPainter {

    fun render(glyph: Glyph, style: IconStyle, accent: Int, sizePx: Int, now: Calendar = Calendar.getInstance()): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        G(c, sizePx.toFloat(), style, accent).draw(glyph, now)
        return glassFinish(bmp)
    }

    /**
     * Gives a flat glyph the layered-glass look of iOS 26 icons: a thin light edge where it faces
     * the light (top-left) and a faint shade on the opposite edge, so it reads as a raised piece of glass.
     */
    fun glassFinish(src: Bitmap, light: Int = 150, shade: Int = 46): Bitmap {
        val w = src.width
        val h = src.height
        val d = (w * 0.013f).coerceAtLeast(1f)
        val edge = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val ec = Canvas(edge)
        val cut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        oc.drawBitmap(src, 0f, 0f, null)
        // shade: silhouette minus itself shifted up-left -> bottom-right sliver
        ec.drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = android.graphics.PorterDuffColorFilter(0xFF0A1430.toInt(), PorterDuff.Mode.SRC_IN) })
        ec.drawBitmap(src, -d, -d, cut)
        oc.drawBitmap(edge, 0f, 0f, Paint().apply { alpha = shade })
        // light: silhouette minus itself shifted down-right -> top-left sliver
        edge.eraseColor(0)
        ec.drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = android.graphics.PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN) })
        ec.drawBitmap(src, d, d, cut)
        oc.drawBitmap(edge, 0f, 0f, Paint().apply { alpha = light })
        return out
    }

    /** Small monochrome symbol used in menus / search fields. */
    fun symbol(glyph: Glyph, sizePx: Int): Bitmap = render(glyph, IconStyle.CLEAR, 0xFFFFFFFF.toInt(), sizePx)

    private class G(val c: Canvas, val s: Float, val style: IconStyle, val accent: Int) {
        val colorful = style.colorful
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun S(v: Float) = v * s
        fun S(v: Double) = (v * s).toFloat()

        fun shadow(on: Boolean = true) {
            if (on) p.setShadowLayer(S(0.045f), 0f, S(0.014f), 0x30000000) else p.clearShadowLayer()
        }

        fun brush(top: Int, bottom: Int = top): Shader {
            val (a, b) = when (style) {
                IconStyle.CLEAR -> 0xFAFFFFFF.toInt() to 0xC8FFFFFF.toInt()
                IconStyle.TINTED -> ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.72f) to ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.38f)
                else -> top to bottom
            }
            return LinearGradient(0f, S(0.18f), 0f, S(0.82f), a, b, Shader.TileMode.CLAMP)
        }

        fun fill(shader: Shader) { p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.shader = shader }
        fun fill(color: Int) { p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = color }
        fun stroke(w: Float, color: Int) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = S(w); p.color = color
            p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        }
        fun rect(l: Float, t: Float, w: Float, h: Float) = RectF(S(l), S(t), S(l + w), S(t + h))
        fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = RectF(S(cx - rx), S(cy - ry), S(cx + rx), S(cy + ry))
        fun circle(cx: Float, cy: Float, r: Float) = c.drawCircle(S(cx), S(cy), S(r), p)
        fun rrect(l: Float, t: Float, w: Float, h: Float, r: Float) = c.drawRoundRect(rect(l, t, w, h), S(r), S(r), p)
        fun cut() { p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }

        /** Ink for small details drawn on top of a glyph (dark in colour modes, translucent otherwise). */
        fun ink(colorfulInk: Int, soft: Int = 0x8C7890BE.toInt()) = if (colorful) colorfulInk else soft

        fun draw(glyph: Glyph, now: Calendar) {
            when (glyph) {
                Glyph.PHONE -> phone()
                Glyph.MESSAGES -> messages()
                Glyph.CAMERA -> camera()
                Glyph.PHOTOS -> photos()
                Glyph.SETTINGS -> settings()
                Glyph.MAIL -> mail()
                Glyph.BROWSER -> browser()
                Glyph.MUSIC -> music()
                Glyph.CALENDAR -> calendar(now)
                Glyph.CLOCK -> clock(now)
                Glyph.CALCULATOR -> calculator()
                Glyph.MAPS -> maps()
                Glyph.CONTACTS -> contacts()
                Glyph.FILES -> files()
                Glyph.STORE -> store()
                Glyph.NOTES -> notes()
                Glyph.WEATHER -> weather()
                Glyph.HEALTH -> health()
                Glyph.VIDEO -> video()
                Glyph.LAUNCHER -> launcher()
            }
        }

        private fun phone() {
            fill(brush(0xFF6EF07F.toInt(), 0xFF22B43A.toInt())); shadow()
            val path = Path().apply {
                moveTo(S(.285f), S(.215f))
                cubicTo(S(.33f), S(.20f), S(.375f), S(.215f), S(.395f), S(.26f))
                lineTo(S(.44f), S(.36f))
                cubicTo(S(.455f), S(.40f), S(.445f), S(.43f), S(.415f), S(.455f))
                lineTo(S(.385f), S(.48f))
                cubicTo(S(.42f), S(.56f), S(.47f), S(.61f), S(.535f), S(.645f))
                lineTo(S(.56f), S(.615f))
                cubicTo(S(.585f), S(.585f), S(.62f), S(.578f), S(.655f), S(.595f))
                lineTo(S(.755f), S(.64f))
                cubicTo(S(.80f), S(.66f), S(.81f), S(.70f), S(.795f), S(.735f))
                lineTo(S(.765f), S(.775f))
                cubicTo(S(.72f), S(.825f), S(.64f), S(.835f), S(.56f), S(.80f))
                cubicTo(S(.41f), S(.735f), S(.27f), S(.595f), S(.205f), S(.445f))
                cubicTo(S(.17f), S(.36f), S(.18f), S(.28f), S(.235f), S(.24f))
                close()
            }
            c.drawPath(path, p)
        }

        private fun messages() {
            fill(brush(0xFF6EF07F.toInt(), 0xFF22B43A.toInt())); shadow()
            val path = Path().apply {
                addOval(oval(.5f, .47f, .31f, .255f), Path.Direction.CW)
                val tail = Path().apply {
                    moveTo(S(.27f), S(.6f)); quadTo(S(.27f), S(.73f), S(.18f), S(.77f))
                    quadTo(S(.33f), S(.77f), S(.4f), S(.69f)); close()
                }
                op(tail, Path.Op.UNION)
            }
            c.drawPath(path, p)
        }

        private fun camera() {
            if (!colorful) {
                stroke(.05f, 0xFFFFFFFF.toInt()); p.shader = brush(0); shadow()
                circle(.5f, .5f, .27f)
                fill(brush(0)); p.alpha = 140; circle(.5f, .5f, .17f)
            } else {
                fill(RadialGradient(S(.5f), S(.5f), S(.3f), intArrayOf(0xFF2B3A55.toInt(), 0xFF0D1220.toInt(), 0xFF596276.toInt()), floatArrayOf(0f, .75f, 1f), Shader.TileMode.CLAMP))
                shadow(); circle(.5f, .5f, .3f)
                fill(RadialGradient(S(.5f), S(.5f), S(.17f), 0xFF3F6FD0.toInt(), 0xFF101A33.toInt(), Shader.TileMode.CLAMP))
                circle(.5f, .5f, .17f)
            }
            fill(0xD9FFFFFF.toInt())
            c.save(); c.rotate(-34f, S(.44f), S(.43f)); c.drawOval(oval(.44f, .43f, .05f, .035f), p); c.restore()
        }

        private fun photos() {
            val cols = intArrayOf(0xFFF9A23C.toInt(), 0xFFFBD13D.toInt(), 0xFF9ED84E.toInt(), 0xFF4FC48B.toInt(), 0xFF45AEE3.toInt(), 0xFF7C6FE6.toInt(), 0xFFC46ADB.toInt(), 0xFFF2617A.toInt())
            for (i in 0 until 8) {
                c.save(); c.rotate(i * 45f, S(.5f), S(.5f))
                when (style) {
                    IconStyle.CLEAR -> { fill(0xFFFFFFFF.toInt()); p.alpha = 140 }
                    IconStyle.TINTED -> { fill(ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.6f)); p.alpha = 150 }
                    else -> { fill(cols[i]); p.alpha = 215 }
                }
                c.drawOval(oval(.5f, .33f, .085f, .15f), p)
                c.restore()
            }
        }

        private fun settings() {
            val outer = brush(0xFFE9EBEF.toInt(), 0xFF9A9FA8.toInt())
            fill(outer); shadow()
            val n = 24
            val gear = Path().apply {
                for (i in 0 until n * 2) {
                    val a = i * PI / n
                    val r = if (i % 2 == 1) S(.27f) else S(.31f)
                    val x = S(.5f) + (cos(a) * r).toFloat()
                    val y = S(.5f) + (sin(a) * r).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
                addCircle(S(.5f), S(.5f), S(.22f), Path.Direction.CCW)
                fillType = Path.FillType.EVEN_ODD
            }
            c.drawPath(gear, p)
            val inner = brush(0xFFD4D7DD.toInt(), 0xFF8B9099.toInt())
            stroke(.045f, 0xFFFFFFFF.toInt()); p.shader = inner
            circle(.5f, .5f, .155f)
            for (k in 0 until 3) {
                val a = -PI / 2 + k * 2 * PI / 3
                c.drawLine(S(.5f), S(.5f), S(.5f) + (cos(a) * S(.17f)).toFloat(), S(.5f) + (sin(a) * S(.17f)).toFloat(), p)
            }
            fill(inner); circle(.5f, .5f, .05f)
        }

        private fun mail() {
            fill(brush(0xFFFFFFFF.toInt(), 0xFFCFE0FA.toInt())); shadow()
            rrect(.2f, .29f, .6f, .42f, .07f)
            stroke(.03f, if (colorful) 0x8C4678C8.toInt() else 0xFF000000.toInt())
            if (!colorful) cut()
            val v = Path().apply { moveTo(S(.22f), S(.32f)); lineTo(S(.5f), S(.53f)); lineTo(S(.78f), S(.32f)) }
            c.drawPath(v, p)
        }

        private fun browser() {
            fill(brush(0xFFFFFFFF.toInt(), 0xFFE6EEF8.toInt())); shadow()
            circle(.5f, .5f, .3f)
            if (colorful) {
                fill(LinearGradient(0f, S(.22f), 0f, S(.78f), 0xFF3FB5F7.toInt(), 0xFF1D5FE0.toInt(), Shader.TileMode.CLAMP))
            } else {
                fill(0xFF000000.toInt()); cut()
            }
            circle(.5f, .5f, .265f)
            stroke(.012f, if (colorful) 0xE6FFFFFF.toInt() else 0x99FFFFFF.toInt()); p.strokeCap = Paint.Cap.BUTT
            for (i in 0 until 36) {
                val a = i * PI / 18
                val r1 = S(if (i % 3 != 0) .235f else .215f)
                val r2 = S(.255f)
                c.drawLine(
                    S(.5f) + (cos(a) * r1).toFloat(), S(.5f) + (sin(a) * r1).toFloat(),
                    S(.5f) + (cos(a) * r2).toFloat(), S(.5f) + (sin(a) * r2).toFloat(), p,
                )
            }
            c.save(); c.rotate(45f, S(.5f), S(.5f))
            fill(if (colorful) 0xFFFF3B30.toInt() else 0xF2FFFFFF.toInt())
            c.drawPath(Path().apply { moveTo(S(.5f), S(.3f)); lineTo(S(.54f), S(.5f)); lineTo(S(.46f), S(.5f)); close() }, p)
            fill(0xF2FFFFFF.toInt()); if (!colorful) p.alpha = 150
            c.drawPath(Path().apply { moveTo(S(.5f), S(.7f)); lineTo(S(.54f), S(.5f)); lineTo(S(.46f), S(.5f)); close() }, p)
            c.restore()
        }

        private fun music() {
            fill(brush(0xFFFF6B81.toInt(), 0xFFF2263F.toInt())); shadow()
            c.save(); c.translate(S(.5f), S(.5f))
            val path = Path().apply {
                moveTo(-S(.10f), -S(.12f)); lineTo(S(.17f), -S(.19f)); lineTo(S(.17f), -S(.10f)); lineTo(-S(.10f), -S(.03f)); close()
                addRoundRect(RectF(-S(.10f), -S(.12f), -S(.055f), S(.18f)), S(.02f), S(.02f), Path.Direction.CW)
                addRoundRect(RectF(S(.125f), -S(.19f), S(.17f), S(.11f)), S(.02f), S(.02f), Path.Direction.CW)
                val e1 = Path().apply { addOval(RectF(-S(.21f), S(.12f), -S(.06f), S(.24f)), Path.Direction.CW) }
                val m1 = android.graphics.Matrix().apply { setRotate(-20f, -S(.135f), S(.18f)) }
                e1.transform(m1)
                val e2 = Path().apply { addOval(RectF(S(.015f), S(.05f), S(.165f), S(.17f)), Path.Direction.CW) }
                val m2 = android.graphics.Matrix().apply { setRotate(-20f, S(.09f), S(.11f)) }
                e2.transform(m2)
                op(e1, Path.Op.UNION); op(e2, Path.Op.UNION)
            }
            c.drawPath(path, p)
            c.restore()
        }

        private fun calendar(now: Calendar) {
            val day = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())?.uppercase(Locale.getDefault()) ?: ""
            fill(if (colorful) 0xFFFF3B30.toInt() else 0xD9FFFFFF.toInt())
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.textSize = S(.15f)
            c.drawText(day.take(3), S(.5f), S(.32f), p)
            fill(brush(0xFFFFFFFF.toInt(), 0xFFE8E8EE.toInt())); shadow()
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            p.textSize = S(.42f)
            c.drawText(now.get(Calendar.DAY_OF_MONTH).toString(), S(.5f), S(.745f), p)
        }

        private fun clock(now: Calendar) {
            fill(brush(0xFFFFFFFF.toInt(), 0xFFE4E6EC.toInt())); shadow()
            circle(.5f, .5f, .3f)
            val inkC = ink(0xFF1C1C1E.toInt(), 0x8C283C6E.toInt())
            stroke(.012f, inkC); p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 12) {
                val a = i * PI / 6
                c.drawLine(
                    S(.5f) + (cos(a) * S(.24f)).toFloat(), S(.5f) + (sin(a) * S(.24f)).toFloat(),
                    S(.5f) + (cos(a) * S(.27f)).toFloat(), S(.5f) + (sin(a) * S(.27f)).toFloat(), p,
                )
            }
            val min = now.get(Calendar.MINUTE)
            val hr = now.get(Calendar.HOUR) + min / 60f
            fun hand(angle: Double, len: Float, w: Float, color: Int) {
                stroke(w, color)
                c.drawLine(S(.5f), S(.5f), S(.5f) + (cos(angle) * S(len)).toFloat(), S(.5f) + (sin(angle) * S(len)).toFloat(), p)
            }
            hand(-PI / 2 + hr / 12.0 * 2 * PI, .15f, .035f, inkC)
            hand(-PI / 2 + min / 60.0 * 2 * PI, .22f, .025f, inkC)
            hand(-PI / 2 + (min * 7 % 60) / 60.0 * 2 * PI, .24f, .01f, 0xFFFF9500.toInt())
            fill(0xFFFF9500.toInt()); circle(.5f, .5f, .022f)
        }

        private fun calculator() {
            fill(brush(0xFFFFFFFF.toInt(), 0xFFDADDE4.toInt())); shadow()
            rrect(.30f, .2f, .40f, .6f, .07f)
            fill(ink(0xFF7C8BA6.toInt())); rrect(.35f, .25f, .30f, .11f, .025f)
            for (r in 0 until 4) for (k in 0 until 3) {
                fill(if (k == 2 && colorful) 0xFFFF9F0A.toInt() else ink(0xFF8C96A8.toInt()))
                circle(.385f + k * .115f, .44f + r * .09f, .032f)
            }
        }

        private fun maps() {
            fill(brush(0xFF57D17A.toInt(), 0xFF2E9E57.toInt())); shadow()
            val path = Path().apply {
                arcTo(oval(.5f, .42f, .2f, .2f), 144f, 252f)
                lineTo(S(.5f), S(.8f)); close()
                addCircle(S(.5f), S(.42f), S(.08f), Path.Direction.CCW)
                fillType = Path.FillType.EVEN_ODD
            }
            c.drawPath(path, p)
        }

        private fun contacts() {
            fill(brush(0xFFC7CCD6.toInt(), 0xFF8D94A3.toInt())); shadow()
            circle(.5f, .38f, .13f)
            c.drawArc(oval(.5f, .78f, .27f, .2f), 180f, 180f, true, p)
        }

        private fun files() {
            fill(brush(0xFF6CC3FF.toInt(), 0xFF1E8BF0.toInt())); shadow()
            val tab = Path().apply { addRoundRect(rect(.2f, .26f, .27f, .14f), S(.04f), S(.04f), Path.Direction.CW) }
            val body = Path().apply { addRoundRect(rect(.2f, .32f, .6f, .42f), S(.05f), S(.05f), Path.Direction.CW) }
            body.op(tab, Path.Op.UNION)
            c.drawPath(body, p)
            fill(0x47FFFFFF); rrect(.2f, .38f, .6f, .36f, .05f)
        }

        private fun store() {
            fill(brush(0xFF5AC8FA.toInt(), 0xFF1A7CF0.toInt())); shadow()
            rrect(.24f, .36f, .52f, .42f, .08f)
            stroke(.045f, 0xFFFFFFFF.toInt()); p.shader = brush(0xFF5AC8FA.toInt(), 0xFF1A7CF0.toInt())
            c.drawArc(oval(.5f, .36f, .12f, .12f), 180f, 180f, false, p)
            fill(if (colorful) 0xE6FFFFFF.toInt() else 0xFF000000.toInt()); if (!colorful) cut()
            circle(.5f, .56f, .065f)
        }

        private fun notes() {
            fill(brush(0xFFFFFFFF.toInt(), 0xFFE9E9EE.toInt())); shadow()
            rrect(.24f, .2f, .52f, .6f, .07f)
            c.save()
            c.clipPath(Path().apply { addRoundRect(rect(.24f, .2f, .52f, .6f), S(.07f), S(.07f), Path.Direction.CW) })
            fill(if (colorful) 0xFFFFCC00.toInt() else 0x66FFFFFF)
            c.drawRect(rect(.24f, .2f, .52f, .13f), p)
            c.restore()
            stroke(.015f, if (colorful) 0x99969AA0.toInt() else 0x667890BE)
            for (i in 0 until 4) c.drawLine(S(.3f), S(.43f + i * .09f), S(.7f), S(.43f + i * .09f), p)
        }

        private fun weather() {
            fill(if (colorful) 0xFFFFD60A.toInt() else 0xBFFFFFFF.toInt()); shadow()
            circle(.6f, .38f, .15f)
            fill(brush(0xFFFFFFFF.toInt(), 0xFFDDE6F2.toInt())); shadow()
            val cloud = Path().apply {
                addCircle(S(.36f), S(.6f), S(.1f), Path.Direction.CW)
                op(Path().apply { addCircle(S(.5f), S(.52f), S(.14f), Path.Direction.CW) }, Path.Op.UNION)
                op(Path().apply { addCircle(S(.64f), S(.62f), S(.1f), Path.Direction.CW) }, Path.Op.UNION)
                op(Path().apply { addRoundRect(rect(.26f, .6f, .48f, .12f), S(.06f), S(.06f), Path.Direction.CW) }, Path.Op.UNION)
            }
            c.drawPath(cloud, p)
        }

        private fun health() {
            fill(brush(0xFFFF6B8A.toInt(), 0xFFFF2D55.toInt())); shadow()
            val path = Path().apply {
                moveTo(S(.5f), S(.74f))
                cubicTo(S(.16f), S(.52f), S(.24f), S(.23f), S(.5f), S(.38f))
                cubicTo(S(.76f), S(.23f), S(.84f), S(.52f), S(.5f), S(.74f))
                close()
            }
            c.drawPath(path, p)
        }

        private fun video() {
            fill(brush(0xFFFF5A5F.toInt(), 0xFFE0242B.toInt())); shadow()
            rrect(.2f, .3f, .6f, .4f, .11f)
            fill(if (colorful) 0xF2FFFFFF.toInt() else 0xFF000000.toInt()); if (!colorful) cut()
            c.drawPath(Path().apply { moveTo(S(.44f), S(.41f)); lineTo(S(.6f), S(.5f)); lineTo(S(.44f), S(.59f)); close() }, p)
        }

        private fun launcher() {
            val knobs = floatArrayOf(.35f, .15f, .42f)
            for (i in 0 until 3) {
                fill(brush(0xFFFFFFFF.toInt(), 0xFFC9D6F5.toInt())); p.alpha = 215
                rrect(.24f, .28f + i * .17f, .52f, .06f, .03f)
                fill(brush(0xFFFFFFFF.toInt(), 0xFFC9D6F5.toInt())); shadow()
                circle(.24f + knobs[i], .31f + i * .17f, .065f)
            }
        }
    }
}
