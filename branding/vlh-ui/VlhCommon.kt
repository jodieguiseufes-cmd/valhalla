package org.olcbox.app.ui.vlh

// Valhalla (сборка №16, 30.10.2026): общие цвета, помощники и всплывашка для своих экранов «как INCY».
// Файл лежит в репо как есть (branding/vlh-ui) и копируется при сборке патчем 08.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.olcbox.app.data.model.EngineType
import org.olcbox.app.data.model.SubscriptionMetadata
import org.olcbox.app.ui.features.home.components.subscriptionGroupKey
import org.olcbox.app.ui.features.locations.LocationItem
import org.olcbox.app.ui.features.locations.PingsState
import org.olcbox.app.util.parseEmojiAndName

internal object Vlh {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF12100C)
    val CardHi = Color(0xFF1B1710)
    val Stroke = Color(0xFF2E2618)
    val Gold = Color(0xFFE8B04B)
    val GoldDeep = Color(0xFFC8902E)
    val OnGold = Color(0xFF1A1206)
    val Text = Color(0xFFF3EAD7)
    val Muted = Color(0xFF9A8E78)
    val Green = Color(0xFF6CC98A)
    val Yellow = Color(0xFFE3B341)
    val Red = Color(0xFFE5655C)
    const val TG_URL = "https://t.me/valhalla_my_bot"
    const val CABINET_URL = "https://app.vlh-net.com"
    const val CUSTOM_KEY = "vlh:custom"
}

/** Состояние навигации и режимов, которое должно пережить уход на экран конфига и обратно. */
internal object VlhNav {
    var tab by mutableStateOf(0)
    /** Последнее подключение сделано кнопкой «Быстрейший» — под знаком пишем «Авто · …». */
    var auto by mutableStateOf(false)
    var pingedOnStart = false
}

// ---------- пинг: ≤0 — это «нет ответа» (баг «Авто · 0 мс») ----------
internal fun PingsState.vlhRaw(id: String): Int? = when (this) {
    PingsState.Idle -> null
    is PingsState.Loading -> if (currentPings.containsKey(id)) currentPings[id] else lastPings?.get(id)
    is PingsState.Success -> pings[id]
    is PingsState.Error -> lastPings?.get(id)
}

internal fun PingsState.vlhPing(id: String): Int? = vlhRaw(id)?.takeIf { it > 0 }

internal fun PingsState.vlhMeasured(id: String): Boolean = when (this) {
    PingsState.Idle -> false
    is PingsState.Loading -> currentPings.containsKey(id) || (lastPings?.containsKey(id) == true)
    is PingsState.Success -> pings.containsKey(id)
    is PingsState.Error -> lastPings?.containsKey(id) == true
}

internal fun PingsState.vlhPending(id: String): Boolean =
    this is PingsState.Loading && id in pendingLocationIds

internal fun PingsState.vlhAllPings(): Map<String, Int?> = when (this) {
    PingsState.Idle -> emptyMap()
    is PingsState.Loading -> lastPings.orEmpty() + currentPings
    is PingsState.Success -> pings
    is PingsState.Error -> lastPings.orEmpty()
}

internal fun vlhPingColor(ms: Int): Color = when {
    ms < 150 -> Vlh.Green
    ms < 400 -> Vlh.Yellow
    else -> Vlh.Red
}

// ---------- имена, флаги, подписи ----------
internal data class VlhName(val flag: String, val name: String)

internal fun LocationItem.vlhName(): VlhName {
    val raw = (metadata?.name?.takeIf { it.isNotBlank() } ?: fullName).trim()
    val (emoji, rest) = parseEmojiAndName(raw)
    val name = (if (emoji.isEmpty()) raw else rest)
        .ifBlank { config?.displayName().orEmpty() }
        .ifBlank { raw }
    return VlhName(emoji, name)
}

/** Серверы, чьё имя уже начинается с «Авто», не получают второй префикс «Авто · » (баг «Авто · Авто»). */
internal fun vlhAutoTitle(name: String, auto: Boolean): String =
    if (auto && !name.trim().startsWith("Авто", ignoreCase = true)) "Авто · $name" else name

internal fun LocationItem.vlhSubtitle(): String {
    val c = config ?: return ""
    val desc = c.description.trim()
    val p = c.proxy
    val tech = if (p != null) {
        val proto = when (p.type.lowercase()) {
            "vless" -> "VLESS"
            "vmess" -> "VMess"
            "trojan" -> "Trojan"
            "shadowsocks" -> "Shadowsocks"
            "hysteria2" -> "Hysteria2"
            "amneziawg" -> "AmneziaWG"
            "" -> null
            else -> p.type.uppercase()
        }
        val sec = if (p.security.equals("reality", ignoreCase = true)) "Reality" else null
        val net = when (p.network.lowercase()) {
            "xhttp" -> "XHTTP"
            "grpc" -> "gRPC"
            "ws" -> "WS"
            "httpupgrade" -> "HTTPUpgrade"
            else -> null
        }
        listOfNotNull(proto, sec, net).joinToString(" · ")
    } else {
        when (c.engine) {
            EngineType.VkTurn -> "VK-TURN"
            EngineType.MasterDns -> "MasterDNS"
            EngineType.OpenFlux -> "OpenFlux"
            else -> c.engine.name
        }
    }
    return listOf(desc, tech).filter { it.isNotBlank() }.joinToString(" · ")
}

internal fun LocationItem.vlhReady(): Boolean = config?.isComplete() == true

// ---------- подписки (группы) ----------
internal data class VlhGroup(
    val key: String,
    val url: String?,
    val sub: SubscriptionMetadata?,
    val items: List<LocationItem>,
) {
    val isCustom: Boolean get() = key == Vlh.CUSTOM_KEY
    val title: String
        get() = if (isCustom) "Свои серверы"
        else sub?.customName?.takeIf { it.isNotBlank() } ?: sub?.name?.takeIf { it.isNotBlank() } ?: "Подписка"
}

internal fun vlhGroups(locations: List<LocationItem>): List<VlhGroup> {
    val subs = locations.filter { !it.subscriptionUrl.isNullOrBlank() }
        .groupBy { it.subscriptionGroupKey() }
        .map { (key, list) ->
            VlhGroup(
                key = key,
                url = list.first().subscriptionUrl,
                sub = list.firstNotNullOfOrNull { it.metadata?.subscription },
                items = list,
            )
        }
    val custom = locations.filter { it.subscriptionUrl.isNullOrBlank() }
    return if (custom.isEmpty()) subs else subs + VlhGroup(Vlh.CUSTOM_KEY, null, null, custom)
}

// ---------- трафик и сроки ----------
internal fun vlhBytes(text: String?): Double? {
    val m = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([KMGTP]?)i?B""", RegexOption.IGNORE_CASE)
        .find(text ?: return null) ?: return null
    val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val pow = "BKMGTP".indexOf(m.groupValues[2].uppercase().ifEmpty { "B" })
    var r = v
    repeat(pow.coerceAtLeast(0)) { r *= 1024.0 }
    return r
}

internal fun vlhRuUnits(text: String): String = text
    .replace("TiB", "ТБ").replace("GiB", "ГБ").replace("MiB", "МБ").replace("KiB", "КБ")
    .replace("TB", "ТБ").replace("GB", "ГБ").replace("MB", "МБ").replace("KB", "КБ")
    .replace(" B", " Б").replace('.', ',')

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun vlhNowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

internal fun SubscriptionMetadata.vlhDaysLeft(): Long? =
    expiresAtEpochMs?.let { ((it - vlhNowMs()) + 86_399_999L).floorDiv(86_400_000L).coerceAtLeast(0L) }

internal fun vlhSpeed(bytesPerSec: Long): String {
    val b = bytesPerSec.coerceAtLeast(0L)
    return when {
        b >= 1024L * 1024L -> {
            val t = b * 10L / (1024L * 1024L)
            "${t / 10},${t % 10} MB/s"
        }
        b >= 1024L -> "${b / 1024L} KB/s"
        else -> "$b B/s"
    }
}

internal fun vlhPlural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

// ---------- всплывашка сверху (тёмная плашка с золотой каймой и галочкой, как INCY) ----------
internal class VlhToastState {
    var message by mutableStateOf<String?>(null)
    var isError by mutableStateOf(false)
    var seq by mutableStateOf(0)

    fun show(text: String, error: Boolean = false) {
        message = text
        isError = error
        seq++
    }
}

@Composable
internal fun VlhToastHost(state: VlhToastState, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf("") }
    var shownError by remember { mutableStateOf(false) }
    LaunchedEffect(state.seq) {
        val m = state.message ?: return@LaunchedEffect
        shown = m
        shownError = state.isError
        delay(if (state.isError) 3500 else 2200)
        state.message = null
    }
    AnimatedVisibility(
        visible = state.message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Vlh.CardHi)
                .border(1.dp, (if (shownError) Vlh.Red else Vlh.Gold).copy(alpha = 0.55f), shape)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (shownError) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (shownError) Vlh.Red else Vlh.Gold,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(shown, color = Vlh.Text, fontSize = 15.sp, maxLines = 3)
        }
    }
}
