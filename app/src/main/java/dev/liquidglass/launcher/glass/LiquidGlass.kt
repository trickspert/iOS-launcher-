package dev.liquidglass.launcher.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.liquidglass.launcher.data.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Liquid glass optics, shared by both shaders below.
 *
 * Every glass shape is a rounded-rect signed distance field. Its rim is a convex bevel with a
 * circular cross-section: the steeper the surface, the further the light passing through it is
 * bent, so whatever is behind the glass is squeezed towards the centre along the rim (with a touch
 * of chromatic dispersion) while the flat body only magnifies slightly. A hair-thin specular line
 * catches a light whose direction follows how the phone is tilted, the opposite rim shows the
 * weaker internal reflection, and the slab casts a soft contact shadow.
 */
private const val GLASS_UNIFORMS = """
uniform float2 size;
uniform float radius;
uniform float2 light;
uniform float press;
uniform float2 touch;
uniform float refr;
uniform float4 tint;
uniform float rimAmt;
uniform float shadowAmt;
uniform float dp;

float sdRR(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}
"""

private const val GLASS_OPTICS = """
    float2 hs = size * 0.5;
    float2 p = coord - hs;
    float d = sdRR(p, hs, radius);
    float sd = max(sdRR(p - float2(0.0, 2.5 * dp), hs, radius), 0.0);
    float sh = shadowAmt * 0.15 * exp(-sd * sd / (72.0 * dp * dp));
    if (d > 0.75) { return half4(0.0, 0.0, 0.0, half(sh)); }
    float2 q = abs(p) - hs + radius;
    float2 sg = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float2 n = (q.x > 0.0 && q.y > 0.0) ? normalize(q) * sg : (q.x > q.y ? float2(sg.x, 0.0) : float2(0.0, sg.y));
    float depth = -d;
    float bevel = min(clamp(min(size.x, size.y) * 0.22, 5.0 * dp, 20.0 * dp), max(radius, 1.0));
    float x = 1.0 - clamp(depth / bevel, 0.0, 1.0);
    float slope = min(x / sqrt(1.03 - x * x), 3.2) * x;
    float k = refr * (1.0 + press * 0.6);
    float2 off = -n * k * slope * 0.42 - p * (0.035 + 0.07 * press);
    float2 ca = n * slope * k * 0.07;
"""

private const val GLASS_FINISH = """
    float lum = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(lum), col, 1.18);
    col = mix(col, tint.rgb, tint.a);
    col += 0.03 * dot(p / hs, light) + 0.012;
    float rim = 1.0 - smoothstep(0.0, 1.3 * dp, depth);
    float s1 = pow(max(dot(n, light), 0.0), 2.2);
    float s2 = pow(max(dot(n, -light), 0.0), 2.6);
    float band = x * x * x;
    col += rimAmt * (rim * (0.10 + 0.80 * s1 + 0.42 * s2) + band * (0.11 * s1 + 0.05 * s2));
    col *= 1.0 - band * 0.07 * max(dot(n, -light), 0.0) * (1.0 - s2);
    float2 dt = coord - touch;
    col += exp(-dot(dt, dt) / (size.x * size.x * 0.16 + 1.0)) * press * 0.22;
    col = clamp(col, 0.0, 1.0);
    float a = 1.0 - smoothstep(-0.75, 0.75, d);
    return half4(half3(col * a), half(a + sh * (1.0 - a)));
"""

/** Glass over the wallpaper: samples a sharp and a frosted copy and mixes them. */
private const val WALL_AGSL = """
uniform shader wall;
uniform shader sharp;
uniform float2 origin;
uniform float frost;
uniform float2 wallT;
uniform float2 wallC;
uniform float wallS;
""" + GLASS_UNIFORMS + """
half4 main(float2 coord) {
""" + GLASS_OPTICS + """
    float2 wc = (origin + coord + off - wallT - wallC) / wallS + wallC;
    float2 cw = ca / wallS;
    float3 cs = float3(sharp.eval(wc + cw).r, sharp.eval(wc).g, sharp.eval(wc - cw).b);
    float3 col = mix(cs, wall.eval(wc).rgb, frost);
""" + GLASS_FINISH + """
}
"""

/** Glass over live content: runs as a RenderEffect on a layer holding whatever is behind it. */
private const val BACKDROP_AGSL = """
uniform shader content;
uniform float pad;
""" + GLASS_UNIFORMS + """
half4 main(float2 xy) {
    float2 coord = xy - pad;
""" + GLASS_OPTICS + """
    float2 bc = xy + off;
    float3 col = float3(content.eval(bc + ca).r, content.eval(bc).g, content.eval(bc - ca).b);
""" + GLASS_FINISH + """
}
"""

/** Light direction (unit vector pointing from the glass towards the light) and tilt, driven by the gravity sensor. */
@Stable
class GlassLight {
    var dir by mutableStateOf(DEFAULT_LIGHT)
    /** Recent tilt away from how the phone is usually held, each axis in -1..1. */
    var tilt by mutableStateOf(Offset.Zero)
}

val DEFAULT_LIGHT = Offset(-0.55f, -0.83f)

class LightSensor(context: Context, private val light: GlassLight) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var fx = 0f
    private var fy = 9.8f
    private var bx = 0f
    private var by = 6.5f
    private var primed = false
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = true
        primed = false
        sm?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        running = false
        sm?.unregisterListener(this)
    }

    fun reset() { light.dir = DEFAULT_LIGHT; light.tilt = Offset.Zero }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        if (!primed) { primed = true; fx = ax; fy = ay; bx = ax; by = ay }
        fx += (ax - fx) * 0.14f
        fy += (ay - fy) * 0.14f
        // The resting pose drifts slowly towards the current one, so parallax re-centres like iOS.
        bx += (fx - bx) * 0.012f
        by += (fy - by) * 0.012f
        // World "up" projected on the screen, exaggerated sideways so small tilts read clearly.
        var x = fx / 9.81f * 2.6f - 0.35f
        var y = -fy / 9.81f - 0.45f
        val len = sqrt(x * x + y * y).coerceAtLeast(0.001f)
        x /= len; y /= len
        val cur = light.dir
        // Thresholds keep a phone resting on a table (or held still) from redrawing the screen at all.
        if (abs(cur.x - x) + abs(cur.y - y) > 0.018f) light.dir = Offset(x, y)
        val tx = ((fx - bx) / 3.2f).coerceIn(-1f, 1f)
        val ty = (-(fy - by) / 3.2f).coerceIn(-1f, 1f)
        val t = light.tilt
        if (abs(t.x - tx) + abs(t.y - ty) > 0.015f) light.tilt = Offset(tx, ty)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/** Everything glass needs to know about the wallpaper behind it. */
@Stable
class GlassEnv(
    val sharp: Bitmap,
    val blurred: Bitmap,
    val screen: Size,
    val light: GlassLight,
    val settings: LauncherSettings,
    private val zoom: () -> Float,
) {
    val wallShader: BitmapShader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(screen.width / blurred.width, screen.height / blurred.height) })
        if (Build.VERSION.SDK_INT >= 33) filterMode = BitmapShader.FILTER_MODE_LINEAR
    }
    val sharpShader: BitmapShader = BitmapShader(sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(screen.width / sharp.width, screen.height / sharp.height) })
        if (Build.VERSION.SDK_INT >= 33) filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    /** The wallpaper is drawn slightly enlarged so it can slide with tilt (parallax) and zoom with app launches. */
    val wallScale: Float get() = 1.05f + 0.07f * zoom()
    val wallShift: Offset
        get() = if (settings.motionLight) light.tilt * (screen.width * 0.02f) else Offset.Zero
    val lightDir: Offset get() = if (settings.motionLight) light.dir else DEFAULT_LIGHT
}

val LocalGlass = staticCompositionLocalOf<GlassEnv?> { null }

/** How a glass surface gets what is behind it. */
enum class GlassMode {
    /** Sits on the wallpaper: refracts the wallpaper itself (cheapest; used by the home screen). */
    WALL,
    /** Floats over live content: refracts a real-time capture of everything behind it. */
    BACKDROP,
    /** Nested inside another glass panel: a thin clear layer with rim light only. */
    FLAT,
}

val LocalGlassMode = compositionLocalOf { GlassMode.WALL }

/** A live recording of the content that [GlassMode.BACKDROP] surfaces refract. */
@Stable
class Backdrop(val layer: GraphicsLayer)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Records this element's drawing into [backdrop] (and still draws it normally). */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = drawWithContent {
    backdrop.layer.record { this@drawWithContent.drawContent() }
    drawLayer(backdrop.layer)
}

/** Press state shared between a gesture handler and the glass it deforms. */
@Stable
class GlassTouch {
    val press = Animatable(0f)
    var point by mutableStateOf(Offset.Unspecified)

    fun down(scope: CoroutineScope, at: Offset) {
        point = at
        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
    }

    fun up(scope: CoroutineScope) {
        scope.launch { press.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    }

    fun flash(scope: CoroutineScope, at: Offset) {
        point = at
        scope.launch {
            press.animateTo(1f, tween(90))
            press.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
}

/**
 * Draws a liquid glass slab behind the content.
 * @param tint colour mixed into the glass (alpha = amount).
 * @param refraction multiplier on the global refraction setting.
 * @param shadow strength of the contact shadow (0 = none).
 * @param frosted true for panels that carry text: what is behind them is blurred heavily so it stays readable.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    tint: Color = Color.Unspecified,
    refraction: Float = 1f,
    touch: GlassTouch? = null,
    rim: Float = 1f,
    shadow: Float = 1f,
    frosted: Boolean = false,
): Modifier = composed {
    val env = LocalGlass.current ?: return@composed this
    val mode = LocalGlassMode.current
    val backdrop = LocalBackdrop.current
    if (mode == GlassMode.FLAT) {
        return@composed this.drawBehind {
            val r = min(cornerRadius.toPx(), size.minDimension / 2f)
            val t = if (tint.isSpecified) tint else Color.White.copy(alpha = 0.10f + 0.10f * env.settings.glassTint)
            drawFlatGlass(r, t, env.lightDir, touch?.press?.value ?: 0f, rim)
        }
    }
    val pos = remember { mutableStateOf(Offset.Zero) }
    val tracked = this.onGloballyPositioned { pos.value = it.positionInWindow() }
    val shaders = Build.VERSION.SDK_INT >= 33 && GlassSupport.shaderOk
    if (shaders && mode == GlassMode.BACKDROP && backdrop != null) {
        val layer = rememberGraphicsLayer()
        val glass = remember { BackdropGlass() }
        tracked.drawBehind {
            val s = env.settings
            val t = if (tint.isSpecified) tint else defaultTint(s)
            val r = min(cornerRadius.toPx(), size.minDimension / 2f)
            val press = touch?.press?.value ?: 0f
            val tp = touch?.point?.takeIf { it.isSpecified } ?: Offset(size.width / 2f, size.height / 2f)
            val refr = (3.dp.toPx() + 15.dp.toPx() * s.refraction) * refraction
            val pad = ceil(16.dp.toPx())
            val o = pos.value
            // Small lenses stay clear; large panels frost what is behind them.
            val scale = (size.minDimension / 200.dp.toPx()).coerceIn(0.2f, 1f)
            val blur = if (frosted) 18.dp.toPx() + 14.dp.toPx() * s.frost else (2.dp.toPx() + 22.dp.toPx() * s.frost) * scale
            try {
                layer.record(IntSize(ceil(size.width + pad * 2).toInt(), ceil(size.height + pad * 2).toInt())) {
                    translate(pad - o.x, pad - o.y) { drawLayer(backdrop.layer) }
                }
                glass.update(size, pad, r, env.lightDir, press, tp, refr, t, rim, shadow, density)
                layer.renderEffect = glass.effect(blur).asComposeRenderEffect()
                translate(-pad, -pad) { drawLayer(layer) }
            } catch (e: Exception) {
                GlassSupport.shaderOk = false
                drawFlatGlass(r, t, env.lightDir, press, rim)
            }
        }
    } else if (shaders) {
        val painter = remember(env) { WallGlass(env) }
        tracked.drawBehind {
            val s = env.settings
            val t = if (tint.isSpecified) tint else defaultTint(s)
            val r = min(cornerRadius.toPx(), size.minDimension / 2f)
            val press = touch?.press?.value ?: 0f
            val tp = touch?.point?.takeIf { it.isSpecified } ?: Offset(size.width / 2f, size.height / 2f)
            val refr = (3.dp.toPx() + 15.dp.toPx() * s.refraction) * refraction
            val m = if (shadow > 0f) 12.dp.toPx() else 1f
            try {
                painter.update(size, pos.value, r, env.lightDir, press, tp, refr, t, 0.1f + s.frost * 0.8f, rim, shadow, density, env)
                drawIntoCanvas { it.nativeCanvas.drawRect(-m, -m, size.width + m, size.height + m, painter.paint) }
            } catch (e: Exception) {
                GlassSupport.shaderOk = false
                drawFlatGlass(r, t, env.lightDir, press, rim)
            }
        }
    } else {
        val fb = remember(env) { FallbackGlass(env) }
        tracked.drawBehind {
            val t = if (tint.isSpecified) tint else defaultTint(env.settings)
            val r = min(cornerRadius.toPx(), size.minDimension / 2f)
            fb.update(pos.value)
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, fb.paint) }
            drawFlatGlass(r, t, env.lightDir, touch?.press?.value ?: 0f, rim)
        }
    }
}

/** Tint, sheen and a light-direction-aware rim; the whole look on old devices and inside panels. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlatGlass(r: Float, tint: Color, l: Offset, press: Float, rim: Float) {
    val cr = CornerRadius(r, r)
    drawRoundRect(tint, cornerRadius = cr)
    if (press > 0.01f) drawRoundRect(Color.White.copy(alpha = 0.14f * press), cornerRadius = cr)
    val c = Offset(size.width / 2f, size.height / 2f)
    val reach = max(size.width, size.height) / 2f
    val w = 1.1.dp.toPx()
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.75f * rim),
            0.45f to Color.White.copy(alpha = 0.10f * rim),
            1f to Color.White.copy(alpha = 0.42f * rim),
            start = c + l * reach, end = c - l * reach,
        ),
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius((r - w / 2).coerceAtLeast(0f)),
        style = Stroke(width = w),
    )
}

/** Checks once that this device's GPU driver accepts the glass shaders. */
object GlassSupport {
    @Volatile
    var shaderOk: Boolean = Build.VERSION.SDK_INT >= 33 && compiles()

    private fun compiles(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(WALL_AGSL); RuntimeShader(BACKDROP_AGSL); true }.getOrDefault(false) else false
}

fun defaultTint(s: LauncherSettings): Color = Color.White.copy(alpha = 0.03f + 0.20f * s.glassTint)

/** Darker glass used by menus and sheets so white text stays readable. */
fun darkGlassTint(s: LauncherSettings): Color = Color(0xFF1B1D25).copy(alpha = 0.38f + 0.22f * s.glassTint)

@RequiresApi(33)
private fun RuntimeShader.common(
    size: Size, radius: Float, light: Offset, press: Float, touch: Offset,
    refr: Float, tint: Color, rim: Float, shadow: Float, density: Float,
) {
    setFloatUniform("size", size.width, size.height)
    setFloatUniform("radius", radius)
    setFloatUniform("light", light.x, light.y)
    setFloatUniform("press", press)
    setFloatUniform("touch", touch.x, touch.y)
    setFloatUniform("refr", refr)
    setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    setFloatUniform("rimAmt", rim)
    setFloatUniform("shadowAmt", shadow)
    setFloatUniform("dp", density)
}

@RequiresApi(33)
private class WallGlass(env: GlassEnv) {
    val shader = RuntimeShader(WALL_AGSL).apply {
        setInputShader("wall", env.wallShader)
        setInputShader("sharp", env.sharpShader)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@WallGlass.shader }

    fun update(
        size: Size, origin: Offset, radius: Float, light: Offset, press: Float, touch: Offset,
        refr: Float, tint: Color, frost: Float, rim: Float, shadow: Float, density: Float, env: GlassEnv,
    ) {
        shader.common(size, radius, light, press, touch, refr, tint, rim, shadow, density)
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("frost", frost)
        val shift = env.wallShift
        shader.setFloatUniform("wallT", shift.x, shift.y)
        shader.setFloatUniform("wallC", env.screen.width / 2f, env.screen.height / 2f)
        shader.setFloatUniform("wallS", env.wallScale)
        paint.shader = shader
    }
}

@RequiresApi(33)
private class BackdropGlass {
    val shader = RuntimeShader(BACKDROP_AGSL)

    fun update(
        size: Size, pad: Float, radius: Float, light: Offset, press: Float, touch: Offset,
        refr: Float, tint: Color, rim: Float, shadow: Float, density: Float,
    ) {
        shader.common(size, radius, light, press, touch, refr, tint, rim, shadow, density)
        shader.setFloatUniform("pad", pad)
    }

    fun effect(blur: Float): RenderEffect {
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        return if (blur > 0.5f) RenderEffect.createChainEffect(lens, RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)) else lens
    }
}

private class FallbackGlass(private val env: GlassEnv) {
    private val bs = BitmapShader(env.blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val m = Matrix()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = bs }

    fun update(origin: Offset) {
        val s = env.wallScale
        val t = env.wallShift
        m.setScale(env.screen.width / env.blurred.width, env.screen.height / env.blurred.height)
        m.postScale(s, s, env.screen.width / 2f, env.screen.height / 2f)
        m.postTranslate(t.x - origin.x, t.y - origin.y)
        bs.setLocalMatrix(m)
        paint.shader = bs
    }
}
