package org.olcbox.app.ui.vlh

// Valhalla (сборка №16): «Главная» и «Серверы» по раскладке INCY, стиль Valhalla (чёрный + золото, неон).

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.olcbox.app.ui.components.StartButton
import org.olcbox.app.ui.features.home.components.ValhallaHeader
import org.olcbox.app.ui.features.locations.LocationItem
import org.olcbox.app.ui.features.locations.components.SpeedSample

// =====================================================================================
// Главная
// =====================================================================================
@Composable
internal fun VlhHomeScreen(
    locations: List<LocationItem>,
    hasLoaded: Boolean,
    data: VlhData,
    act: VlhActions,
    isConnected: Boolean,
    isLoading: Boolean,
    connectedSinceEpochMs: Long,
    speed: SpeedSample?,
    autoMode: Boolean,
    bottomPad: Dp,
    onToggle: () -> Unit,
    onSecretTap: () -> Unit,
    onOpenServers: () -> Unit,
) {
    val groups = vlhGroups(locations)
    val selected = locations.firstOrNull { it.storageId == data.selectedId }
    // Действующая подписка: та, где выбранный сервер; иначе первая подписка; иначе «свои серверы».
    val active = groups.firstOrNull { g -> g.items.any { it.storageId == data.selectedId } }
        ?: groups.firstOrNull { !it.isCustom }
        ?: groups.firstOrNull()

    Column(modifier = Modifier.fillMaxSize().background(Vlh.Bg).statusBarsPadding()) {
        // Шапка: слоган + VALHALLA (закреплена)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            ValhallaHeader()
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "speed") {
                VlhSpeedLine(isConnected, connectedSinceEpochMs, speed, onSecretTap)
            }
            item(key = "sign") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    StartButton(
                        isActive = isConnected,
                        isLoading = isLoading,
                        label = if (isConnected || isLoading) "Отключить" else "Подключить",
                        enabled = true,
                        onClick = onToggle,
                    )
                }
            }
            item(key = "status") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        text = when {
                            isLoading -> "ПОДКЛЮЧЕНИЕ…"
                            isConnected -> "ЗАЩИЩЕНО"
                            else -> "НЕ ЗАЩИЩЕНО"
                        },
                        color = Vlh.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    )
                    if (selected != null) {
                        val (flag, name) = selected.vlhName()
                        Row(
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenServers() }.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (flag.isNotEmpty()) {
                                Text(flag, fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = vlhAutoTitle(name, autoMode),
                                color = Vlh.Gold,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (selected != null) {
                item(key = "check") {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
                        Text(
                            text = "Проверить",
                            color = Vlh.Gold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { act.ping(listOf(selected.storageId), selected.storageId) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (active != null) {
                item(key = "provider-label") {
                    Text(
                        text = if (active.isCustom) "СВОИ СЕРВЕРЫ" else "ТЕКУЩАЯ ПОДПИСКА",
                        color = Vlh.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 8.dp),
                    )
                }
                item(key = "provider-" + active.key) {
                    VlhProviderCard(group = active, data = data, act = act, autoMode = autoMode)
                }
                if (groups.size > 1) {
                    item(key = "others") {
                        Text(
                            text = "Другие подписки — во вкладке «Серверы» →",
                            color = Vlh.Gold,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenServers() }.padding(6.dp),
                        )
                    }
                }
            } else if (hasLoaded) {
                item(key = "empty") {
                    VlhEmptyState(act = act, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

/** «↑ 0 B/s · ⏱ 00:00 · ↓ 0 B/s» над знаком, как в INCY. */
@Composable
private fun VlhSpeedLine(isConnected: Boolean, sinceMs: Long, speed: SpeedSample?, onSecretTap: () -> Unit) {
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(isConnected, sinceMs) {
        if (isConnected && sinceMs > 0L) {
            while (true) {
                elapsed = ((vlhNowMs() - sinceMs) / 1000L).coerceAtLeast(0L)
                delay(1000)
            }
        } else {
            elapsed = 0L
        }
    }
    val h = elapsed / 3600
    val m = (elapsed % 3600) / 60
    val s = elapsed % 60
    fun p(v: Long) = v.toString().padStart(2, '0')
    val time = if (h > 0) "$h:${p(m)}:${p(s)}" else "${p(m)}:${p(s)}"
    var taps by remember { mutableStateOf(0) }
    val up = if (isConnected && speed != null) vlhSpeed(speed.upBytesPerSec) else "0 B/s"
    val down = if (isConnected && speed != null) vlhSpeed(speed.downBytesPerSec) else "0 B/s"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = Vlh.Gold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(up, color = Vlh.Text, fontSize = 12.sp, maxLines = 1)
        }
        Row(
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                // 5 нажатий по таймеру — скрытые «экспериментальные» настройки (как было у YPtun)
                taps++
                if (taps >= 5) { taps = 0; onSecretTap() }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Vlh.Gold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(time, color = Vlh.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = Vlh.Gold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(down, color = Vlh.Text, fontSize = 12.sp, maxLines = 1)
        }
    }
}

// =====================================================================================
// Серверы
// =====================================================================================
@Composable
internal fun VlhServersScreen(
    locations: List<LocationItem>,
    hasLoaded: Boolean,
    data: VlhData,
    act: VlhActions,
    autoMode: Boolean,
    bottomPad: Dp,
    refreshingAll: Boolean,
    onCollapseAll: (collapse: Boolean) -> Unit,
) {
    val groups = vlhGroups(locations)
    var query by remember { mutableStateOf("") }
    val subCount = groups.count { !it.isCustom }
    val serverCount = locations.size
    val allCollapsed = groups.isNotEmpty() && groups.all { it.key in data.collapsed }

    Column(modifier = Modifier.fillMaxSize().background(Vlh.Bg).statusBarsPadding()) {
        // Заголовок «Серверы» + «N серверов · N подписка» и кнопки ˄ ↻ ⏱ ＋
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Серверы", color = Vlh.Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "$serverCount ${vlhPlural(serverCount, "сервер", "сервера", "серверов")} · " +
                        "$subCount ${vlhPlural(subCount, "подписка", "подписки", "подписок")}",
                    color = Vlh.Muted,
                    fontSize = 12.sp,
                )
            }
            if (groups.isNotEmpty()) {
                VlhIconSquare(
                    if (allCollapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                    onClick = { onCollapseAll(!allCollapsed) },
                )
                Spacer(Modifier.width(8.dp))
                VlhIconSquare(Icons.Rounded.Refresh, onClick = { act.refresh(null) }, busy = refreshingAll)
                Spacer(Modifier.width(8.dp))
                VlhIconSquare(Icons.Rounded.Speed, onClick = { act.ping(null, null) })
                Spacer(Modifier.width(8.dp))
            }
            VlhIconSquare(Icons.Rounded.Add, onClick = { act.add() }, filled = true)
        }

        // Поиск
        if (groups.isNotEmpty()) {
            val shape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    .clip(shape)
                    .background(Vlh.Card)
                    .border(1.dp, Vlh.Stroke, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Vlh.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Поиск серверов…", color = Vlh.Muted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Vlh.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(Vlh.Gold),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Vlh.Muted,
                        modifier = Modifier.size(18.dp).clickable { query = "" },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (groups.isEmpty() && hasLoaded) {
                item(key = "empty") { VlhEmptyState(act = act, title = "Нет серверов", modifier = Modifier.padding(top = 40.dp)) }
            }
            val q = query.trim()
            groups.forEach { g ->
                val visible = q.isEmpty() || g.items.any {
                    it.vlhName().name.contains(q, ignoreCase = true) || it.fullName.contains(q, ignoreCase = true)
                }
                if (visible) {
                    item(key = "group-" + g.key) {
                        VlhProviderCard(group = g, data = data, act = act, filter = q, autoMode = autoMode)
                    }
                }
            }
        }

        // Внизу: «＋ Добавить», «Вставить», QR (над панелью вкладок)
        if (groups.isNotEmpty()) {
            VlhAddRow(act = act, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad))
        } else {
            Spacer(Modifier.height(bottomPad))
        }
    }
}
