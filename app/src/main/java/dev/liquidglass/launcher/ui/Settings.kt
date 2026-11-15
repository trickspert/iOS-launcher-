package dev.liquidglass.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquidglass.launcher.LauncherState
import dev.liquidglass.launcher.data.IconStyle
import dev.liquidglass.launcher.glass.Wallpapers
import dev.liquidglass.launcher.glass.darkGlassTint
import dev.liquidglass.launcher.glass.liquidGlass
import dev.liquidglass.launcher.icons.Glyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsSheet(state: LauncherState) {
    val s = state.settings
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().plainClick { state.overlay = null }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = 28.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = drag.value }
                .liquidGlass(40.dp, tint = darkGlassTint(s), refraction = 1.5f)
                .plainClick { },
        ) { InGlass {
            // Grabber + header (drag down to dismiss)
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { c, dy -> c.consume(); scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } },
                            onDragEnd = {
                                scope.launch {
                                    if (drag.value > 220f) { state.overlay = null; drag.snapTo(0f) }
                                    else drag.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                                }
                            },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(38.dp).height(5.dp).background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(50)))
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Customize", style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    GlassButton(onClick = { state.overlay = null }, modifier = Modifier.height(36.dp), tint = Color(0xFF0A84FF).copy(alpha = 0.55f)) {
                        BasicText("Done", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            ) {
                Spacer(Modifier.height(14.dp))
                Preview(state)

                Section("Icons")
                Group {
                    GlassSegmented(IconStyle.entries.map { it.label }, s.iconStyle.ordinal, { s.iconStyle = IconStyle.entries[it]; state.icons.clear() })
                    if (s.iconStyle == IconStyle.TINTED) {
                        Spacer(Modifier.height(14.dp))
                        Caption("Tint colour")
                        GlassSlider(
                            s.tintHue / 360f, { s.tintHue = it * 360f },
                            track = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.6f, 1f) }),
                        )
                    }
                    Divider()
                    ToggleRow("Show app names", s.showLabels) { s.showLabels = it }
                    Divider()
                    Caption("Home Screen grid")
                    GlassSegmented(listOf("4 × 6", "5 × 7"), if (s.columns == 4) 0 else 1, { s.columns = if (it == 0) 4 else 5 })
                }

                Section("Wallpaper")
                Group { WallpaperPicker(state) }

                Section("Liquid Glass")
                Group {
                    Caption("Refraction")
                    GlassSlider(s.refraction, { s.refraction = it })
                    Spacer(Modifier.height(10.dp))
                    Caption("Frost")
                    GlassSlider(s.frost, { s.frost = it })
                    Spacer(Modifier.height(10.dp))
                    Caption("Tint")
                    GlassSlider(s.glassTint, { s.glassTint = it })
                    Divider()
                    ToggleRow("Motion-reactive light", s.motionLight, "Highlights move as you tilt your phone") { s.motionLight = it }
                    Divider()
                    ActionRow("Reset glass", null) { s.resetGlass() }
                }

                Section("Home")
                Group {
                    ActionRow("Default Home app", if (state.isDefaultHome) "Glass" else "Set up") { state.requestDefaultHome() }
                    Divider()
                    ActionRow("Notification badges", if (state.badgesEnabled) "On" else "Off") {
                        state.openPackageIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    Divider()
                    ActionRow("Edit Home Screen", null) { state.enterEdit() }
                    Divider()
                    ActionRow("Reset Home Screen layout", null, destructive = true) { state.resetLayout() }
                }
                Spacer(Modifier.height(18.dp))
                BasicText("Glass Launcher", style = CaptionStyle, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(24.dp))
            }
        } }
    }
}

@Composable
private fun Preview(state: LauncherState) {
    val sample = listOf(Glyph.PHONE, Glyph.MESSAGES, Glyph.PHOTOS, Glyph.SETTINGS)
    val apps = state.repo.apps
    val shown = sample.mapNotNull { g -> apps.firstOrNull { it.glyph == g } }.ifEmpty { apps.take(4) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        shown.take(4).forEach { app -> AppIcon(app, 60.dp, true, dev.liquidglass.launcher.Source.Library) }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    BasicText(title.uppercase(), style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp), modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Caption(t: String) {
    BasicText(t, style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp), modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
}

@Composable
private fun ToggleRow(title: String, value: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = BodyStyle)
            if (subtitle != null) BasicText(subtitle, style = CaptionStyle)
        }
        GlassSwitch(value, onChange)
    }
}

@Composable
private fun ActionRow(title: String, value: String?, destructive: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().plainClick(onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = BodyStyle.copy(color = if (destructive) Color(0xFFFF6961) else Color.White), modifier = Modifier.weight(1f))
        if (value != null) {
            BasicText(value, style = BodyStyle.copy(color = Color.White.copy(alpha = 0.55f)))
            Spacer(Modifier.width(6.dp))
        }
        if (!destructive) Symbol(Sym.CHEVRON, 14.dp, Color.White.copy(alpha = 0.4f))
    }
}

@Composable
private fun WallpaperPicker(state: LauncherState) {
    val s = state.settings
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Wallpapers.builtIns.forEach { (id, name) ->
            val thumb by produceState<ImageBitmap?>(null, id) {
                value = withContext(Dispatchers.Default) { Wallpapers.generate(id, 120, 260).asImageBitmap() }
            }
            WallThumb(name, thumb, s.wallpaper == id) { s.wallpaper = id }
        }
        val customThumb by produceState<ImageBitmap?>(null, s.wallpaperVersion) {
            value = withContext(Dispatchers.IO) {
                if (Wallpapers.hasCustom(state.app)) runCatching { Wallpapers.load(state.app, "custom", 120, 260).asImageBitmap() }.getOrNull() else null
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(64.dp, 138.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(if (s.wallpaper == "custom") 2.5.dp else 0.5.dp, if (s.wallpaper == "custom") Color.White else Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .plainClick { state.pickWallpaper() },
                contentAlignment = Alignment.Center,
            ) {
                customThumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.size(30.dp).liquidGlass(15.dp), contentAlignment = Alignment.Center) { Symbol(Sym.PLUS, 13.dp, Color.White) }
            }
            Spacer(Modifier.height(6.dp))
            BasicText("Photos", style = TextStyle(color = Color.White, fontSize = 12.sp))
        }
    }
}

@Composable
private fun WallThumb(name: String, thumb: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp, 138.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(if (selected) 2.5.dp else 0.5.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .plainClick(onClick),
        ) {
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(20.dp).background(Color.White, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) { Symbol(Sym.CHECK, 11.dp, Color(0xFF0A84FF)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(name, style = TextStyle(color = Color.White, fontSize = 12.sp))
    }
}
