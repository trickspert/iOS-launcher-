package dev.larp.vault

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val Bg = Color(0xFF0D1117)
private val TopGlow = Color(0xFF0F3A40)
private val CardBg = Color(0xFF181F28)
private val Mint = Color(0xFF3DDBB4)
private val Up = Color(0xFF2EE6A0)
private val Down = Color(0xFFFF5C6C)
private val Muted = Color(0xFF8B96A5)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Color(0xFF062A22),
            background = Bg,
            surface = CardBg,
            onSurface = Color.White,
            onSurfaceVariant = Muted,
            outline = Color(0xFF3A4452),
        ),
        content = content,
    )
}

@Composable
fun VaultScreen(state: VaultState) {
    var editingToken by remember { mutableStateOf<Token?>(null) }
    var editingHeader by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to TopGlow, 0.38f to Bg, 1f to Bg)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TopBar(state.header.account) { editingHeader = true } }
            item {
                Balance(
                    total = state.total,
                    change = state.change,
                    percent = state.percent,
                    onClick = { editingHeader = true },
                )
            }
            item {
                Text(
                    "Tokens",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(state.tokens, key = { it.id }) { token ->
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
                    "Tap the balance or any token to change it.\nDemo display only. Not a real wallet.",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp),
                )
            }
        }
    }

    editingToken?.let { token ->
        val isNew = state.isNew(token)
        TokenDialog(
            initial = token,
            isNew = isNew,
            onSave = { state.upsert(it); editingToken = null },
            onDelete = { state.delete(token.id); editingToken = null },
            onDismiss = { editingToken = null },
        )
    }
    if (editingHeader) {
        HeaderDialog(
            state = state,
            onDismiss = { editingHeader = false },
        )
    }
}

@Composable
private fun TopBar(account: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Mint),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(account), color = Color(0xFF062A22), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "$account  ▾",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Mint.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text("DEMO", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun Balance(total: Double, change: Double, percent: Double, onClick: () -> Unit) {
    val tint = if (change < 0) Down else Up
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 28.dp),
    ) {
        val text = (if (total < 0) "-" else "") + money(total, wholeDollars = abs(total) >= 1_000_000)
        FitText(text, maxSizeSp = 56f, style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(signedMoney(change), color = tint, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(signedPercent(percent), color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
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
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        TokenIcon(token, 48)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                token.name.ifBlank { "Untitled" },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${token.amount} ${token.symbol}".trim(),
                color = Muted,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(money(token.value), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                signedMoney(token.change),
                color = if (token.change < 0) Down else Up,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TokenIcon(token: Token, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(Color(token.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (token.symbol.ifBlank { token.name }).take(1).uppercase().ifBlank { "?" },
            color = Color(0xFF0D1117),
            fontSize = (sizeDp * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AddTokenCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Mint.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+  Add token", color = Mint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---- Dialogs ----

@Composable
private fun TokenDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(if (isNew) "Add token" else "Edit token") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
        containerColor = CardBg,
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
                    placeholder = "Blank = sum of tokens (${money(state.tokenTotal)})",
                )
                ChangeField(
                    change, { change = it }, changeUp, { changeUp = it },
                    placeholder = "Blank = sum of tokens",
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

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "V"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
