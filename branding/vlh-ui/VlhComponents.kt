package org.olcbox.app.ui.vlh

// Valhalla (сборка №16): карточка подписки («текущий провайдер» INCY), компактные строки серверов,
// меню долгого нажатия, нижняя плавающая панель, окно «Добавить».

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olcbox.app.ui.features.locations.LocationItem
import org.olcbox.app.ui.features.locations.PingsState

/** Данные, общие для главной и вкладки «Серверы». */
internal class VlhData(
    val pings: PingsState,
    val selectedId: String?,
    val favorites: List<String>,
    val collapsed: Set<String>,
    val pingSorted: Set<String>,
    val refreshingKey: String?,
)

/** Действия, общие для главной и вкладки «Серверы». */
internal class VlhActions(
    val select: (String) -> Unit,
    val toggleFavorite: (String) -> Unit,
    val ping: (ids: List<String>?, single: String?) -> Unit,
    val openConfig: (String) -> Unit,
    val share: (LocationItem) -> Unit,
    val refresh: (VlhGroup?) -> Unit,
    val toggleCollapsed: (String) -> Unit,
    val fastest: (List<String>?) -> Unit,
    val setAutoUpdate: (VlhGroup, Boolean) -> Unit,
    val delete: (VlhGroup) -> Unit,
    val openLogs: () -> Unit,
    val openUrl: (String) -> Unit,
    val add: () -> Unit,
    val paste: () -> Unit,
    val scanQr: () -> Unit,
)

// ---------- маленькая квадратная кнопка с иконкой (↻ ⏱ ⋯ в заголовке подписки) ----------
@Composable
internal fun VlhIconSquare(
    icon: ImageVector,
    onClick: () -> Unit,
    busy: Boolean = false,
    filled: Boolean = false,
    size: Dp = 34.dp,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (filled) Vlh.Gold else Vlh.CardHi)
            .border(1.dp, if (filled) Vlh.Gold else Vlh.Stroke, shape)
            .clickable(enabled = !busy) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Vlh.Gold, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = if (filled) Vlh.OnGold else Vlh.Gold, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VlhChip(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = text,
        color = if (filled) Vlh.OnGold else Vlh.Gold,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (filled) Vlh.Gold else Color.Transparent)
            .border(1.dp, Vlh.Gold.copy(alpha = if (filled) 1f else 0.6f), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// ---------- карточка подписки + её серверы ----------
@Composable
internal fun VlhProviderCard(
    group: VlhGroup,
    data: VlhData,
    act: VlhActions,
    filter: String = "",
    autoMode: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    val searching = filter.isNotBlank()
    val collapsed = !searching && group.key in data.collapsed
    var menuOpen by remember { mutableStateOf(false) }
    val sub = group.sub
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Vlh.Card)
            .border(1.dp, Vlh.Stroke, shape)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
    ) {
        // Заголовок: ˅ имя  … ↻ ⏱ ⋯
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { act.toggleCollapsed(group.key) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (collapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = Vlh.Muted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = group.title,
                    color = Vlh.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub?.autoUpdateEnabled == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Sync, contentDescription = null, tint = Vlh.Muted, modifier = Modifier.size(14.dp))
                }
            }
            if (!group.isCustom) {
                VlhIconSquare(Icons.Rounded.Refresh, onClick = { act.refresh(group) }, busy = data.refreshingKey == group.key)
                Spacer(Modifier.width(8.dp))
            }
            VlhIconSquare(Icons.Rounded.Speed, onClick = { act.ping(group.items.map { it.storageId }, null) })
            Spacer(Modifier.width(8.dp))
            Box {
                VlhIconSquare(Icons.Rounded.MoreHoriz, onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    VlhMenuItem("Подключить быстрейший", Icons.Rounded.Bolt) {
                        menuOpen = false; act.fastest(group.items.map { it.storageId })
                    }
                    if (!group.isCustom && sub != null) {
                        VlhMenuItem(
                            if (sub.autoUpdateEnabled) "Выключить автообновление" else "Включить автообновление",
                            if (sub.autoUpdateEnabled) Icons.Rounded.SyncDisabled else Icons.Rounded.Sync,
                        ) { menuOpen = false; act.setAutoUpdate(group, !sub.autoUpdateEnabled) }
                    }
                    VlhMenuItem("Журнал подключения", Icons.Rounded.Description) { menuOpen = false; act.openLogs() }
                    if (!group.isCustom) {
                        VlhMenuItem("Удалить подписку", Icons.Rounded.Delete, danger = true) { menuOpen = false; act.delete(group) }
                    }
                }
            }
        }

        if (!group.isCustom) {
            // «Осталось N дн.» + полоса + «X / Y»
            val days = sub?.vlhDaysLeft()
            val used = vlhBytes(sub?.used)
            val total = vlhBytes(sub?.available)
            val progress = if (used != null && total != null && total > 0.0) (used / total).toFloat().coerceIn(0f, 1f) else 0f
            val usedText: String? = sub?.used
            val availText: String? = sub?.available
            val traffic: String? = when {
                usedText == null -> null
                availText == null || availText == "∞" -> "${vlhRuUnits(usedText)} · без лимита"
                else -> "${vlhRuUnits(usedText)} / ${vlhRuUnits(availText)}"
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (days != null) "Осталось $days дн." else "Без срока",
                    color = if (days != null && days <= 2L) Vlh.Red else Vlh.Muted,
                    fontSize = 12.sp,
                    fontWeight = if (days != null && days <= 2L) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Vlh.Gold,
                    trackColor = Vlh.CardHi,
                )
                if (traffic != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(traffic, color = Vlh.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
            // Кнопки «✈ Telegram», «Кабинет» — всегда (ссылки из подписки, иначе наши)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tg = sub?.supportUrl?.takeIf { it.isNotBlank() } ?: Vlh.TG_URL
                val web = sub?.webPageUrl?.takeIf { it.isNotBlank() } ?: Vlh.CABINET_URL
                VlhChip("✈ Telegram", filled = false) { act.openUrl(tg) }
                VlhChip("Кабинет", filled = true) { act.openUrl(web) }
                Spacer(Modifier.weight(1f))
                val upd = sub?.lastRefreshAtEpochMs
                if (upd != null) {
                    Text(
                        text = org.olcbox.app.util.IsoTime.formatLocalDateTime(upd),
                        color = Vlh.Muted.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        if (!collapsed) {
            val q = filter.trim()
            var items = group.items
            if (q.isNotEmpty()) {
                items = items.filter { it.vlhName().name.contains(q, ignoreCase = true) || it.fullName.contains(q, ignoreCase = true) }
            }
            if (group.key in data.pingSorted) {
                items = items.sortedBy { data.pings.vlhPing(it.storageId) ?: Int.MAX_VALUE }
            }
            // избранные — сверху
            items = items.sortedBy { if (it.storageId in data.favorites) 0 else 1 }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text(
                    text = if (q.isNotEmpty()) "Ничего не найдено" else "Серверов нет — обновите подписку",
                    color = Vlh.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(8.dp),
                )
            }
            items.forEach { item ->
                VlhServerRow(
                    item = item,
                    selected = item.storageId == data.selectedId,
                    data = data,
                    act = act,
                    autoMode = autoMode,
                )
            }
        }
    }
}

@Composable
private fun VlhMenuItem(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (danger) Vlh.Red else Vlh.Text, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (danger) Vlh.Red else Vlh.Gold) },
        onClick = onClick,
    )
}

// ---------- строка сервера ----------
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VlhServerRow(
    item: LocationItem,
    selected: Boolean,
    data: VlhData,
    act: VlhActions,
    autoMode: Boolean = false,
) {
    val id = item.storageId
    val (flag, name) = item.vlhName()
    val subtitle = item.vlhSubtitle()
    val fav = id in data.favorites
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(shape)
                .background(if (selected) Vlh.Gold.copy(alpha = 0.11f) else Color.Transparent)
                .border(1.dp, if (selected) Vlh.Gold.copy(alpha = 0.75f) else Color.Transparent, shape)
                .combinedClickable(onClick = { act.select(id) }, onLongClick = { menu = true })
                .heightIn(min = 50.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                Text(text = flag.ifEmpty { "🌐" }, fontSize = 19.sp, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selected) vlhAutoTitle(name, autoMode) else name,
                        color = Vlh.Text,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fav) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Vlh.Gold, modifier = Modifier.size(12.dp))
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Vlh.Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            VlhPingBadge(data.pings, id, item.vlhReady())
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            VlhMenuItem(if (fav) "Убрать из избранного" else "В избранное", if (fav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) {
                menu = false; act.toggleFavorite(id)
            }
            VlhMenuItem("Пинг сервера", Icons.Rounded.Speed) { menu = false; act.ping(listOf(id), id) }
            VlhMenuItem("Просмотр конфига", Icons.Rounded.Info) { menu = false; act.openConfig(id) }
            VlhMenuItem("Поделиться", Icons.Rounded.Share) { menu = false; act.share(item) }
        }
    }
}

@Composable
internal fun VlhPingBadge(pings: PingsState, id: String, ready: Boolean) {
    val ms = pings.vlhPing(id)
    when {
        pings.vlhPending(id) -> CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Vlh.Muted, strokeWidth = 1.5.dp)
        ms != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(vlhPingColor(ms)))
                Spacer(Modifier.width(6.dp))
                Text("$ms мс", color = Vlh.Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        pings.vlhMeasured(id) || !ready -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Vlh.Red))
                Spacer(Modifier.width(6.dp))
                Text("нет ответа", color = Vlh.Muted, fontSize = 11.sp, maxLines = 1)
            }
        }
        else -> Text("—", color = Vlh.Muted, fontSize = 12.sp)
    }
}

// ---------- нижняя плавающая панель: ⌂ / ☰ / ⚙ ----------
@Composable
internal fun VlhBottomBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Vlh.Card.copy(alpha = 0.97f))
            .border(1.dp, Vlh.Stroke, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VlhTabItem(Icons.Rounded.Home, "Главная", tab == 0, Modifier.weight(1f)) { onTab(0) }
        VlhTabItem(Icons.Rounded.Storage, "Серверы", tab == 1, Modifier.weight(1f)) { onTab(1) }
        VlhTabItem(Icons.Rounded.Settings, "Настройки", tab == 2, Modifier.weight(1f)) { onTab(2) }
    }
}

@Composable
private fun VlhTabItem(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (active) Vlh.Gold.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Vlh.Gold else Vlh.Muted, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            color = if (active) Vlh.Gold else Vlh.Muted,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

// ---------- кнопки «＋ Добавить», «Вставить», QR ----------
@Composable
internal fun VlhAddRow(act: VlhActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VlhWideButton("＋  Добавить", filled = true, modifier = Modifier.weight(1f)) { act.add() }
        VlhWideButton("Вставить", filled = false, icon = Icons.Rounded.ContentPaste, modifier = Modifier.weight(1f)) { act.paste() }
        VlhIconSquare(Icons.Rounded.QrCodeScanner, onClick = { act.scanQr() }, size = 48.dp)
    }
}

@Composable
internal fun VlhWideButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (filled) Vlh.Gold else Vlh.CardHi)
            .border(1.dp, if (filled) Vlh.Gold else Vlh.Stroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (filled) Vlh.OnGold else Vlh.Gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (filled) Vlh.OnGold else Vlh.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

// ---------- пусто: нет подписки ----------
@Composable
internal fun VlhEmptyState(act: VlhActions, title: String = "Подписки пока нет", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Vlh.Muted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = Vlh.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Вставьте ссылку на подписку из кабинета Valhalla",
            color = Vlh.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        VlhWideButton("＋  Добавить подписку", filled = true, modifier = Modifier.fillMaxWidth(), height = 56.dp) { act.add() }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { act.openUrl(Vlh.CABINET_URL) }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Где взять ссылку?", color = Vlh.Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Vlh.Gold, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------- окно «Добавить» (наш стиль) ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VlhAddSheet(
    hasSubscriptions: Boolean,
    onDismiss: () -> Unit,
    onPaste: () -> Unit,
    onQr: () -> Unit,
    onFile: () -> Unit,
    onManual: () -> Unit,
    onRefreshAll: () -> Unit,
    onWhere: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Vlh.Card,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Добавить", color = Vlh.Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Скопируйте ссылку на подписку в кабинете и нажмите «Вставить»",
                color = Vlh.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            VlhWideButton("Вставить ссылку из буфера", filled = true, icon = Icons.Rounded.ContentPaste,
                modifier = Modifier.fillMaxWidth(), height = 54.dp) { onPaste() }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VlhWideButton("QR-код", filled = false, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.weight(1f)) { onQr() }
                VlhWideButton("Из файла", filled = false, icon = Icons.Rounded.Description, modifier = Modifier.weight(1f)) { onFile() }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VlhWideButton("Свой сервер", filled = false, icon = Icons.Rounded.Edit, modifier = Modifier.weight(1f)) { onManual() }
                if (hasSubscriptions) {
                    VlhWideButton("Обновить всё", filled = false, icon = Icons.Rounded.Refresh, modifier = Modifier.weight(1f)) { onRefreshAll() }
                }
            }
            Row(
                modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(10.dp)).clickable { onWhere() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Где взять ссылку? — кабинет Valhalla", color = Vlh.Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Vlh.Gold, modifier = Modifier.size(16.dp))
            }
        }
    }
}
