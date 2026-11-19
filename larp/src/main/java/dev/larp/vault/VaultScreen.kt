package dev.larp.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min

// "Midnight glass": pure black, frosted cards, icy blue/teal glow.
private val Black = Color(0xFF000000)
private val GlowBlue = Color(0xFF2F5BFF)
private val GlowTeal = Color(0xFF00D1C1)
private val Ice = Color(0xFF7FE7FF)
private val Up = Color(0xFF34F5C5)
private val Down = Color(0xFFFF4D6D)
private val Muted = Color(0xFF8A93A3)
private val DialogBg = Color(0xFF101318)

private val CardShape = RoundedCornerShape(22.dp)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ice,
            onPrimary = Black,
            background = Black,
            surface = DialogBg,
            onSurface = Color.White,
            onSurfaceVariant = Muted,
            outline = Color(0xFF2C3340),
        ),
        content = content,
    )
}

/** Frosted glass: a faint white wash with a light top edge. */
private fun Modifier.glass(shape: Shape = CardShape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.085f), Color.White.copy(alpha = 0.035f))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.03f))), shape)

@Composable
fun VaultScreen(state: VaultState) {
    var editingToken by remember { mutableStateOf<Token?>(null) }
    var editingHeader by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Black)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(GlowBlue.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.02f),
                        radius = size.width * 1.0f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(GlowTeal.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.2f),
                        radius = size.width * 0.8f,
                    ),
                )
            },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TopBar(account = state.header.account, onAccount = { editingHeader = true })
            }
            item {
                Balance(
                    total = state.total,
                    change = state.change,
                    percent = state.percent,
                    tokens = state.tokens.sortedByDescending { it.value },
                    onClick = { editingHeader = true },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                ) {
                    Text("Crypto", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${state.tokens.size}", color = Muted, fontSize = 15.sp)
                }
            }
            items(state.tokens.sortedByDescending { it.value }, key = { it.id }) { token ->
                TokenRow(token) { editingToken = token }
            }
            item {
                AddTokenCard {
                    editingToken = Token(
                        id = System.currentTimeMillis(),
                        name = "",
                        symbol = "",
                        amount = "",
                        value = 0.0,
                        change = 0.0,
                        color = Palette[state.tokens.size % Palette.size],
                    )
                }
            }
            item {
                Text(
                    "Tap the balance or any coin to change it.\nDemo display only. Not a real wallet.",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp),
                )
            }
        }
    }

    editingToken?.let { token ->
        TokenDialog(
            state = state,
            initial = token,
            isNew = state.isNew(token),
            onSave = { state.upsert(it); editingToken = null },
            onDelete = { state.delete(token.id); editingToken = null },
            onDismiss = { editingToken = null },
        )
    }
    if (editingHeader) {
        HeaderDialog(state = state, onDismiss = { editingHeader = false })
    }
}

@Composable
private fun TopBar(account: String, onAccount: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).clickable(onClick = onAccount),
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GlowBlue, GlowTeal))),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(account), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                account,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Ice.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text("DEMO", color = Ice, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
    }
}

/** Animates from the previous value to [target]; counts up from zero on first show. */
@Composable
private fun countUp(target: Double): Double {
    var from by remember { mutableStateOf(0.0) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (target != to) {
            from += (to - from) * progress.value
            to = target
            progress.snapTo(0f)
        }
        progress.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }
    return from + (to - from) * progress.value
}

@Composable
private fun Balance(total: Double, change: Double, percent: Double, tokens: List<Token>, onClick: () -> Unit) {
    val tint = if (change < 0) Down else Up
    val shownTotal = countUp(total)
    val shownChange = countUp(change)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .pressClick(onClick = onClick)
            .background(
                Brush.linearGradient(
                    listOf(GlowBlue.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), GlowTeal.copy(alpha = 0.16f)),
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.04f), Ice.copy(alpha = 0.25f))),
                CardShape,
            )
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
    ) {
        Text("Total balance", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        val text = (if (shownTotal < 0) "-" else "") + money(shownTotal, wholeDollars = abs(total) >= 1_000_000)
        FitText(text, maxSizeSp = 50f, style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(signedMoney(shownChange), color = tint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            ChangePill(percent, tint)
            Spacer(Modifier.width(8.dp))
            Text("today", color = Muted, fontSize = 14.sp)
        }
        AllocationBar(tokens)
    }
}

@Composable
private fun ChangePill(percent: Double, tint: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(signedPercent(percent), color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** A thin bar split by how much of the total each coin is worth. */
@Composable
private fun AllocationBar(tokens: List<Token>) {
    val parts = tokens.filter { it.value > 0 }
    val sum = parts.sumOf { it.value }
    if (parts.isEmpty() || sum <= 0) return
    Spacer(Modifier.height(18.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth().height(6.dp),
    ) {
        parts.forEach { t ->
            Box(
                Modifier
                    .weight((t.value / sum).toFloat().coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(t.color)),
            )
        }
    }
}

/** Clickable card that shrinks slightly while pressed. */
@Composable
private fun Modifier.pressClick(shape: Shape = CardShape, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}

/** One line of text that shrinks to fit the width, for very long balances. */
@Composable
private fun FitText(text: String, maxSizeSp: Float, style: TextStyle) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val widthSp = with(LocalDensity.current) { maxWidth.toSp().value }
        val size = min(maxSizeSp, widthSp / (text.length * 0.6f))
        Text(text, style = style.copy(fontSize = size.sp), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun TokenRow(token: Token, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressClick(onClick = onClick)
            .glass()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        CoinIcon(token, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                token.name.ifBlank { "Untitled" },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${token.amount} ${token.symbol}".trim(),
                color = Muted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                money(token.value),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = if (token.change < 0) Down else Up
                Text(signedMoney(token.change), color = tint, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                ChangePill(tokenPercent(token), tint)
            }
        }
    }
}

@Composable
private fun AddTokenCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .pressClick(onClick = onClick)
            .glass()
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+  Add crypto", color = Ice, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---- Dialogs ----

@Composable
private fun TokenDialog(
    state: VaultState,
    initial: Token,
    isNew: Boolean,
    onSave: (Token) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var symbol by remember { mutableStateOf(initial.symbol) }
    var amount by remember { mutableStateOf(initial.amount) }
    var value by remember { mutableStateOf(if (isNew) "" else plain(initial.value)) }
    var change by remember { mutableStateOf(if (isNew) "" else plain(abs(initial.change))) }
    var changeUp by remember { mutableStateOf(initial.change >= 0) }
    var color by remember { mutableIntStateOf(initial.color) }
    var icon by remember { mutableStateOf(initial.icon) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) state.importIcon(uri)?.let { icon = it }
    }
    val preview = initial.copy(name = name, symbol = symbol, color = color, icon = icon)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        title = { Text(if (isNew) "Add crypto" else "Edit crypto") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(preview, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Logo", icon == "" || icon == "auto") { icon = "" }
                            Chip("Letter", icon == "letter") { icon = "letter" }
                        }
                        Chip("Photo…", icon.startsWith("file:")) {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                }
                Text(
                    "Logo works for BTC, ETH, SOL, USDC and DOGE. Others show a letter unless you pick a photo.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Field("Name", name, { name = it })
                Field("Symbol", symbol, { symbol = it.uppercase() }, KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                Field("Amount held (e.g. 3,000)", amount, { amount = it })
                Field("Value in $", value, { value = it }, KeyboardOptions(keyboardType = KeyboardType.Decimal))
                ChangeField(change, { change = it }, changeUp, { changeUp = it })
                Text("Color", color = Muted, fontSize = 13.sp)
                Palette.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (c == color) 3.dp else 0.dp,
                                        color = if (c == color) Color.White else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { color = c },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val magnitude = abs(parseNumber(change) ?: 0.0)
                onSave(
                    initial.copy(
                        name = name.trim(),
                        symbol = symbol.trim(),
                        amount = amount.trim(),
                        value = parseNumber(value) ?: 0.0,
                        change = if (changeUp) magnitude else -magnitude,
                        color = color,
                        icon = icon,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = Down) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun HeaderDialog(state: VaultState, onDismiss: () -> Unit) {
    val h = state.header
    var account by remember { mutableStateOf(h.account) }
    var total by remember { mutableStateOf(h.totalOverride?.let(::plain) ?: "") }
    var change by remember { mutableStateOf(h.changeOverride?.let { plain(abs(it)) } ?: "") }
    var changeUp by remember { mutableStateOf((h.changeOverride ?: state.tokenChange) >= 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        title = { Text("Edit balance") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Field("Account name", account, { account = it })
                Field(
                    "Total balance in $",
                    total,
                    { total = it },
                    KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = "Blank = sum of coins (${money(state.tokenTotal)})",
                )
                ChangeField(
                    change, { change = it }, changeUp, { changeUp = it },
                    placeholder = "Blank = sum of coins",
                )
                TextButton(onClick = { state.resetAll(); onDismiss() }) {
                    Text("Reset everything to the sample", color = Down)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = parseNumber(change)?.let { if (changeUp) abs(it) else -abs(it) }
                state.updateHeader(
                    Header(
                        account = account.trim().ifBlank { "Vault 1" },
                        totalOverride = parseNumber(total),
                        changeOverride = c,
                    ),
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, fontSize = 13.sp) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = keyboard,
        modifier = modifier,
    )
}

/** Change amount plus an Up/Down toggle, since number keyboards often lack a minus key. */
@Composable
private fun ChangeField(
    value: String,
    onChange: (String) -> Unit,
    up: Boolean,
    onUp: (Boolean) -> Unit,
    placeholder: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(
            "Change in $",
            value,
            onChange,
            KeyboardOptions(keyboardType = KeyboardType.Decimal),
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        val tint = if (up) Up else Down
        Box(
            Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.15f))
                .clickable { onUp(!up) }
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Text(if (up) "▲ Up" else "▼ Down", color = tint, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Ice.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) Ice.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (selected) Ice else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun tokenPercent(t: Token): Double {
    val before = t.value - t.change
    return if (before == 0.0) 0.0 else t.change / abs(before) * 100
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "V"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
