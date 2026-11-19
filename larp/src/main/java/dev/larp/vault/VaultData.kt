package dev.larp.vault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** One row in the list. [amount] is free text so it can be shown exactly as typed. */
data class Token(
    val id: Long,
    val name: String,
    val symbol: String,
    val amount: String,
    val value: Double,
    val change: Double,
    val color: Int,
)

/** Header values. A null override means "add up the token list". */
data class Header(
    val account: String,
    val totalOverride: Double?,
    val changeOverride: Double?,
)

val Palette: List<Int> = listOf(
    0xFFF7931A, 0xFF8C9EFF, 0xFF14F1B0, 0xFFE0C35A,
    0xFF3D8BFF, 0xFFFF6B8A, 0xFFB98CFF, 0xFF9BE15D,
).map { it.toInt() }

fun defaultHeader() = Header(account = "Vault 1", totalOverride = null, changeOverride = null)

fun defaultTokens() = listOf(
    Token(1, "Bitcoin", "BTC", "12.5", 1_203_750.00, 18_420.55, Palette[0]),
    Token(2, "Ethereum", "ETH", "84.2", 312_540.10, -4_210.32, Palette[1]),
    Token(3, "Solana", "SOL", "1,250", 182_300.00, 9_115.00, Palette[2]),
    Token(4, "Dogecoin", "DOGE", "250,000", 54_250.00, 1_302.40, Palette[3]),
    Token(5, "USD Coin", "USDC", "25,000", 25_000.00, 0.0, Palette[4]),
)

private class VaultStore(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    fun loadTokens(): List<Token> {
        val raw = prefs.getString("tokens", null) ?: return defaultTokens()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Token(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    symbol = o.getString("symbol"),
                    amount = o.getString("amount"),
                    value = o.getString("value").toDouble(),
                    change = o.getString("change").toDouble(),
                    color = o.getInt("color"),
                )
            }
        }.getOrElse { defaultTokens() }
    }

    fun saveTokens(tokens: List<Token>) {
        val arr = JSONArray()
        tokens.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("symbol", t.symbol)
                    .put("amount", t.amount)
                    // Stored as text so big numbers survive the round trip exactly.
                    .put("value", plain(t.value))
                    .put("change", plain(t.change))
                    .put("color", t.color),
            )
        }
        prefs.edit().putString("tokens", arr.toString()).apply()
    }

    fun loadHeader(): Header {
        val d = defaultHeader()
        return Header(
            account = prefs.getString("account", null) ?: d.account,
            totalOverride = prefs.getString("total", null)?.toDoubleOrNull(),
            changeOverride = prefs.getString("change", null)?.toDoubleOrNull(),
        )
    }

    fun saveHeader(h: Header) {
        prefs.edit()
            .putString("account", h.account)
            .putString("total", h.totalOverride?.let(::plain))
            .putString("change", h.changeOverride?.let(::plain))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

class VaultState(context: Context) {
    private val store = VaultStore(context)

    val tokens = mutableStateListOf<Token>().apply { addAll(store.loadTokens()) }
    var header by mutableStateOf(store.loadHeader())
        private set

    val tokenTotal: Double get() = tokens.sumOf { it.value }
    val tokenChange: Double get() = tokens.sumOf { it.change }
    val total: Double get() = header.totalOverride ?: tokenTotal
    val change: Double get() = header.changeOverride ?: tokenChange

    /** Percent move relative to the value before the change. */
    val percent: Double
        get() {
            val before = total - change
            return if (before == 0.0) 0.0 else change / abs(before) * 100
        }

    fun isNew(token: Token) = tokens.none { it.id == token.id }

    fun upsert(token: Token) {
        val i = tokens.indexOfFirst { it.id == token.id }
        if (i >= 0) tokens[i] = token else tokens.add(token)
        store.saveTokens(tokens)
    }

    fun delete(id: Long) {
        tokens.removeAll { it.id == id }
        store.saveTokens(tokens)
    }

    fun updateHeader(h: Header) {
        header = h
        store.saveHeader(h)
    }

    fun resetAll() {
        store.clear()
        tokens.clear()
        tokens.addAll(defaultTokens())
        header = defaultHeader()
    }
}

// ---- Formatting ----

private val symbols = DecimalFormatSymbols(Locale.US)
private val cents = DecimalFormat("#,##0.00", symbols)
private val whole = DecimalFormat("#,##0", symbols)

fun plain(v: Double): String = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

fun money(v: Double, wholeDollars: Boolean = false): String =
    "$" + (if (wholeDollars) whole else cents).format(BigDecimal.valueOf(abs(v)))

fun signedMoney(v: Double): String = (if (v < 0) "-" else "+") + money(v)

fun signedPercent(v: Double): String = String.format(Locale.US, "%+.2f%%", v)

/** Accepts "1,500,000", "$29.96", "+0.45", "-2.16". */
fun parseNumber(s: String): Double? =
    s.replace(",", "").replace("$", "").replace(" ", "").removePrefix("+").toDoubleOrNull()
