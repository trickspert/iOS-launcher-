package dev.liquidglass.launcher.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.liquidglass.launcher.data.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Liquid glass shader. Each glass shape is a rounded-rect signed distance field. Near the rim the
 * surface curves like a lens, bending (refracting) the blurred wallpaper behind it with a little
 * chromatic dispersion; the rim catches a specular highlight from a light whose direction follows
 * how the phone is tilted, and the far edge gets a weaker internal reflection.
 */
private const val GLASS_AGSL = """
uniform shader wall;
uniform float2 size;
uniform float2 origin;
uniform float radius;
uniform float2 light;
uniform float press;
uniform float2 touch;
uniform float refr;
uniform float4 tint;
uniform float frost;
uniform float rimAmt;

float sdRR(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - hs;
    float d = sdRR(p, hs, radius);
    if (d > 1.0) { return half4(0.0); }
    float2 e = float2(0.5, 0.0);
    float2 g = float2(
        sdRR(p + e.xy, hs, radius) - sdRR(p - e.xy, hs, radius),
        sdRR(p + e.yx, hs, radius) - sdRR(p - e.yx, hs, radius));
    float2 n = g / max(length(g), 0.0001);

    float depth = -d;
    float bevel = clamp(min(size.x, size.y) * 0.24, 6.0, 30.0);
    float t = clamp(depth / bevel, 0.0, 1.0);
    float edge = pow(1.0 - t, 2.2);
    float k = refr * (1.0 + press * 0.55);

    float2 off = -n * k * edge;
    float2 mag = -p * (0.06 + 0.07 * press);
    float2 sc = origin + coord + off + mag;
    float2 ca = n * edge * k * 0.18;
    float3 col = float3(wall.eval(sc + ca).r, wall.eval(sc).g, wall.eval(sc - ca).b);

    col = mix(col, tint.rgb, tint.a);
    col = col * (1.0 + frost * 0.08) + frost * 0.035;
    col += 0.055 * (1.0 - coord.y / size.y);

    float rim = 1.0 - smoothstep(0.0, 1.6, depth);
    float glow = 1.0 - smoothstep(0.0, bevel * 0.9, depth);
    float s1 = pow(max(dot(n, light), 0.0), 2.5);
    float s2 = pow(max(dot(n, -light), 0.0), 3.0);
    col += rimAmt * (rim * (0.22 + 0.78 * s1) * 0.9 + rim * s2 * 0.45 + glow * (s1 * 0.22 + s2 * 0.10));
    col *= 1.0 - glow * 0.10 * max(dot(n, -light), 0.0);

    float2 dt = coord - touch;
    float tg = exp(-dot(dt, dt) / (size.x * size.x * 0.18 + 1.0)) * press * 0.30;
    col += tg;

    float a = 1.0 - smoothstep(-0.75, 0.75, d);
    return half4(half3(col * a), half(a));
}
"""

/** Light direction (unit vector pointing from the glass towards the light), driven by tilt. */
@Stable
class GlassLight {
    var dir by mutableStateOf(Offset(-0.55f, -0.83f))
}

class LightSensor(context: Context, private val light: GlassLight) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var fx = 0f
    private var fy = 9.8f
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = true
        sm?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        sm?.unregisterListener(this)
    }

    fun reset() { light.dir = Offset(-0.55f, -0.83f) }

    override fun onSensorChanged(event: SensorEvent) {
        fx += (event.values[0] - fx) * 0.18f
        fy += (event.values[1] - fy) * 0.18f
        // World "up" projected on the screen, exaggerated sideways so small tilts read clearly.
        var x = fx / 9.81f * 2.6f - 0.35f
        var y = -fy / 9.81f - 0.45f
        val len = sqrt(x * x + y * y).coerceAtLeast(0.001f)
        x /= len; y /= len
        val cur = light.dir
        if (abs(cur.x - x) + abs(cur.y - y) > 0.012f) light.dir = Offset(x, y)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/** Everything glass needs to know about what is behind it. */
@Stable
class GlassEnv(
    val blurred: Bitmap,
    val screen: Size,
    val light: GlassLight,
    val settings: LauncherSettings,
) {
    val wallShader: BitmapShader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(screen.width / blurred.width, screen.height / blurred.height) })
    }
}

val LocalGlass = staticCompositionLocalOf<GlassEnv?> { null }

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
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    tint: Color = Color.Unspecified,
    refraction: Float = 1f,
    touch: GlassTouch? = null,
    rim: Float = 1f,
): Modifier = composed {
    val env = LocalGlass.current ?: return@composed this
    val pos = remember { mutableStateOf(Offset.Zero) }
    val tracked = this.onGloballyPositioned { pos.value = it.positionInWindow() }
    if (Build.VERSION.SDK_INT >= 33 && GlassSupport.shaderOk) {
        val painter = remember(env) { ShaderGlass(env) }
        tracked.drawBehind {
            val s = env.settings
            val t = if (tint.isSpecified) tint else defaultTint(s)
            val r = min(cornerRadius.toPx(), size.minDimension / 2f)
            val press = touch?.press?.value ?: 0f
            val tp = touch?.point?.takeIf { it.isSpecified } ?: Offset(size.width / 2f, size.height / 2f)
            val refr = (3.dp.toPx() + 15.dp.toPx() * s.refraction) * refraction
            val l = if (s.motionLight) env.light.dir else Offset(-0.55f, -0.83f)
            try {
                painter.update(size, pos.value, r, l, press, tp, refr, t, 0.4f + s.frost * 0.8f, rim)
                drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, painter.paint) }
            } catch (e: Exception) {
                GlassSupport.shaderOk = false
                drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = CornerRadius(r, r))
            }
        }
    } else {
        val fb = remember(env) { FallbackGlass(env) }
        tracked.drawBehind {
            val s = env.settings
            val t = if (tint.isSpecified) tint else defaultTint(s)
            val r = min(cornerRadius.toPx(), size.minDimension / 2f)
            val press = touch?.press?.value ?: 0f
            val l = if (s.motionLight) env.light.dir else Offset(-0.55f, -0.83f)
            fb.update(pos.value)
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, fb.paint) }
            val cr = CornerRadius(r, r)
            drawRoundRect(t, cornerRadius = cr)
            drawRoundRect(Color.White.copy(alpha = 0.06f + 0.12f * press), cornerRadius = cr)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), 0f, size.height * 0.55f),
                cornerRadius = cr,
            )
            val c = Offset(size.width / 2f, size.height / 2f)
            val reach = max(size.width, size.height) / 2f
            drawRoundRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.95f * rim),
                    0.5f to Color.White.copy(alpha = 0.12f * rim),
                    1f to Color.White.copy(alpha = 0.5f * rim),
                    start = c + l * reach, end = c - l * reach,
                ),
                cornerRadius = cr,
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
    }
}

/** Checks once that this device's GPU driver accepts the glass shader. */
object GlassSupport {
    @Volatile
    var shaderOk: Boolean = Build.VERSION.SDK_INT >= 33 && compiles()

    private fun compiles(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(GLASS_AGSL); true }.getOrDefault(false) else false
}

fun defaultTint(s: LauncherSettings): Color = Color.White.copy(alpha = 0.04f + 0.22f * s.glassTint)

/** Darker glass used by menus and sheets so white text stays readable. */
fun darkGlassTint(s: LauncherSettings): Color = Color(0xFF15171C).copy(alpha = 0.22f + 0.3f * s.glassTint)

@RequiresApi(33)
private class ShaderGlass(env: GlassEnv) {
    val shader = RuntimeShader(GLASS_AGSL).apply { setInputShader("wall", env.wallShader) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@ShaderGlass.shader }

    fun update(
        size: Size, origin: Offset, radius: Float, light: Offset, press: Float, touch: Offset,
        refr: Float, tint: Color, frost: Float, rim: Float,
    ) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("light", light.x, light.y)
        shader.setFloatUniform("press", press)
        shader.setFloatUniform("touch", touch.x, touch.y)
        shader.setFloatUniform("refr", refr)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("frost", frost)
        shader.setFloatUniform("rimAmt", rim)
        paint.shader = shader
    }
}

private class FallbackGlass(private val env: GlassEnv) {
    private val bs = BitmapShader(env.blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val m = Matrix()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = bs }

    fun update(origin: Offset) {
        m.setScale(env.screen.width / env.blurred.width, env.screen.height / env.blurred.height)
        m.postTranslate(-origin.x, -origin.y)
        bs.setLocalMatrix(m)
        paint.shader = bs
    }
}
