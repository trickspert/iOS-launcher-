package dev.larp.vault

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Which picture a token shows: a built-in coin logo, its first letter, or a photo file. */
fun iconKey(token: Token): String {
    if (token.icon.isNotBlank() && token.icon != "auto") return token.icon
    return when (token.symbol.trim().uppercase()) {
        "BTC", "WBTC" -> "btc"
        "ETH", "WETH" -> "eth"
        "SOL" -> "sol"
        "USDC" -> "usdc"
        "DOGE" -> "doge"
        else -> "letter"
    }
}

@Composable
fun CoinIcon(token: Token, size: Dp, modifier: Modifier = Modifier) {
    val key = iconKey(token)
    val photo = remember(key) {
        if (key.startsWith("file:")) BitmapFactory.decodeFile(key.removePrefix("file:"))?.asImageBitmap() else null
    }
    val base = modifier.size(size).clip(CircleShape)
    val fontPx = size.value

    when {
        photo != null -> Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = base)

        key == "btc" -> Box(base.background(Color(0xFFF7931A)), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().rotate(14f), contentAlignment = Alignment.Center) {
                // The two little bars that poke out above and below the B.
                Canvas(Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val stroke = w * 0.065f
                    for (x in listOf(0.43f, 0.56f)) {
                        drawLine(Color.White, Offset(w * x, w * 0.17f), Offset(w * x, w * 0.29f), stroke, StrokeCap.Butt)
                        drawLine(Color.White, Offset(w * x, w * 0.71f), Offset(w * x, w * 0.83f), stroke, StrokeCap.Butt)
                    }
                }
                Text("B", color = Color.White, fontSize = (fontPx * 0.56f).sp, fontWeight = FontWeight.Black)
            }
        }

        key == "eth" -> Canvas(base.background(Color(0xFF627EEA))) { drawEth() }

        key == "sol" -> Canvas(base.background(Color(0xFF0B0B10))) { drawSol() }

        key == "usdc" -> Box(base.background(Color(0xFF2775CA)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = this.size.width
                val r = w * 0.34f
                val tl = Offset(w / 2 - r, w / 2 - r)
                val stroke = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                drawArc(Color.White, 115f, 130f, false, tl, Size(r * 2, r * 2), style = stroke)
                drawArc(Color.White, -65f, 130f, false, tl, Size(r * 2, r * 2), style = stroke)
            }
            Text("$", color = Color.White, fontSize = (fontPx * 0.42f).sp, fontWeight = FontWeight.Black)
        }

        key == "doge" -> Box(
            base.background(Brush.linearGradient(listOf(Color(0xFFF0CD56), Color(0xFFBA9F33)))),
            contentAlignment = Alignment.Center,
        ) {
            Text("Ð", color = Color.White, fontSize = (fontPx * 0.5f).sp, fontWeight = FontWeight.Black)
        }

        else -> {
            val c = Color(token.color)
            Box(
                base
                    .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f))))
                    .border(scaledBorder(size), Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    token.symbol.ifBlank { token.name }.take(1).uppercase().ifBlank { "?" },
                    color = Color.Black,
                    fontSize = (fontPx * 0.42f).sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/** Border width that scales a little with the icon. */
private fun scaledBorder(size: Dp): Dp = Dp((1.5f * size.value / 46f).coerceAtLeast(1f))

/** Polygon in a 32×32 design grid, scaled to the canvas. */
private fun DrawScope.poly(vararg pts: Float): Path {
    val s = size.width / 32f
    return Path().apply {
        moveTo(pts[0] * s, pts[1] * s)
        var i = 2
        while (i < pts.size) {
            lineTo(pts[i] * s, pts[i + 1] * s)
            i += 2
        }
        close()
    }
}

private fun DrawScope.drawEth() {
    val w = Color.White
    drawPath(poly(16f, 4f, 16f, 12.87f, 23.5f, 16.22f), w.copy(alpha = 0.6f))
    drawPath(poly(16f, 4f, 8.5f, 16.22f, 16f, 12.87f), w)
    drawPath(poly(16f, 21.97f, 16f, 28f, 23.5f, 17.62f), w.copy(alpha = 0.6f))
    drawPath(poly(16f, 28f, 16f, 21.97f, 8.5f, 17.62f), w)
    drawPath(poly(16f, 20.57f, 23.5f, 16.22f, 16f, 12.87f), w.copy(alpha = 0.2f))
    drawPath(poly(8.5f, 16.22f, 16f, 20.57f, 16f, 12.87f), w.copy(alpha = 0.6f))
}

private fun DrawScope.drawSol() {
    val brush = Brush.linearGradient(
        listOf(Color(0xFF00FFA3), Color(0xFFDC1FFF)),
        start = Offset(size.width * 0.2f, size.height * 0.8f),
        end = Offset(size.width * 0.8f, size.height * 0.2f),
    )
    drawPath(poly(11f, 9.5f, 24.5f, 9.5f, 21f, 12.6f, 7.5f, 12.6f), brush)
    drawPath(poly(7.5f, 14.45f, 21f, 14.45f, 24.5f, 17.55f, 11f, 17.55f), brush)
    drawPath(poly(11f, 19.4f, 24.5f, 19.4f, 21f, 22.5f, 7.5f, 22.5f), brush)
}
