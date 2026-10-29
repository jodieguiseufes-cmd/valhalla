package org.olcbox.app.ui.vlh

// Valhalla (сборка №16, 30.10.2026): корень своих экранов — три вкладки (Главная / Серверы / Настройки)
// с плавающей нижней панелью. Подключается в OlcboxAppContent вместо HomeScreen (только телефон).
// Движок YPtun не трогаем: HomeScreenViewModel, LocationViewModel, VPN-служба, импорт.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olcbox.app.data.model.LocationConfig
import org.olcbox.app.ui.features.home.HomeScreenViewModel
import org.olcbox.app.ui.features.home.components.LogsSheet
import org.olcbox.app.ui.features.locations.LocationViewModel
import org.olcbox.app.ui.features.locations.components.LocalConnectedSpeed

@Composable
fun VlhApp(
    viewModel: HomeScreenViewModel,
    locationViewModel: LocationViewModel,
    onToggleClick: () -> Unit,
    onImportFileRequested: () -> Unit,
    onImportFromClipboardRequested: (onImported: () -> Unit, onError: (String) -> Unit) -> Unit,
    onScanQrRequested: () -> Unit,
    onShareLocationRequested: (LocationConfig) -> Unit,
    onSaveLogsRequested: (onSaved: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onAppSettingsClick: () -> Unit,
    onOpenLocationSettings: (String) -> Unit,
    onAddLocation: () -> Unit,
    confirmBeforeDelete: Boolean,
    pingParallelism: Int,
    updateAvailable: Boolean,
    onUpdateClick: () -> Unit,
    onUnlockExperimental: () -> Unit,
    collapsedGroups: Set<String>,
    pingSortedGroups: Set<String>,
    favorites: List<String>,
    onToggleGroupCollapsed: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val toast = remember { VlhToastState() }
    val locations = locationViewModel.locations.toList()
    val pingsState = locationViewModel.pingsState
    val speed = LocalConnectedSpeed.current
    var addOpen by remember { mutableStateOf(false) }
    var logsOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<VlhGroup?>(null) }
    var refreshingKey by remember { mutableStateOf<String?>(null) }
    var autoRunning by remember { mutableStateOf(false) }
    val hasSubscriptions = locations.any { !it.subscriptionUrl.isNullOrBlank() }

    // Ошибка подключения → красная плашка сверху (вместо белого snackbar)
    LaunchedEffect(state.connectError) {
        val error = state.connectError
        if (!error.isNullOrBlank()) {
            toast.show(error, error = true)
            viewModel.clearConnectError()
        }
    }

    fun openUrl(url: String) {
        runCatching { uri.openUri(url) }.onFailure { toast.show("Не удалось открыть ссылку", error = true) }
    }

    fun ping(ids: List<String>?, single: String?) {
        locationViewModel.refreshPings(
            targetLocationIds = ids,
            parallelism = pingParallelism,
            performPing = { config -> viewModel.performPingFor(config) },
            onComplete = { _, _ ->
                if (single != null) {
                    val ms = locationViewModel.pingsState.vlhPing(single)
                    val name = locations.firstOrNull { it.storageId == single }?.vlhName()?.name ?: "Сервер"
                    toast.show(if (ms != null) "$name: $ms мс" else "$name: нет ответа", error = ms == null)
                } else {
                    toast.show("Пинг обновлён")
                }
            },
        )
    }

    fun afterRefresh(count: Int) {
        locationViewModel.loadLocations {
            viewModel.restartVpnIfRunning()
            refreshingKey = null
            toast.show(if (count > 0) "Подписки обновлены" else "Не удалось обновить подписку", error = count <= 0)
        }
    }

    fun refresh(group: VlhGroup?) {
        if (refreshingKey != null) return
        val url = group?.url
        if (group == null || url == null) {
            refreshingKey = "*"
            viewModel.refreshSubscriptions { count -> afterRefresh(count) }
        } else {
            refreshingKey = group.key
            viewModel.refreshSubscription(url) { count -> afterRefresh(count) }
        }
    }

    // «Быстрейший»: пинг всех готовых серверов → подключение к самому быстрому, при неудаче — к следующему.
    // Пинги ≤0 считаем «нет ответа» (баг «Авто · 0 мс»).
    fun fastest(ids: List<String>?) {
        if (autoRunning) return
        val candidates = locations.filter { it.vlhReady() && (ids == null || it.storageId in ids) }
        if (candidates.isEmpty()) {
            toast.show("Нет серверов для подключения", error = true)
            return
        }
        autoRunning = true
        toast.show("Ищу самый быстрый сервер…")
        locationViewModel.refreshPings(
            targetLocationIds = candidates.map { it.storageId },
            parallelism = pingParallelism,
            performPing = { config -> viewModel.performPingFor(config) },
            onComplete = { _, _ ->
                val p = locationViewModel.pingsState
                val reachable = candidates
                    .mapNotNull { c -> p.vlhPing(c.storageId)?.let { c.storageId to it } }
                    .sortedBy { it.second }
                    .map { it.first }
                val order = reachable.ifEmpty { candidates.map { it.storageId } }
                viewModel.autoConnectInOrder(order) { connectedName ->
                    autoRunning = false
                    VlhNav.auto = connectedName != null
                    locationViewModel.loadLocations()
                    if (connectedName != null) toast.show("Подключено: $connectedName")
                    else toast.show("Не удалось подключиться ни к одному серверу", error = true)
                }
            },
        )
    }

    // Пинг серверов при первом открытии (как «Пинг при запуске» в INCY)
    LaunchedEffect(locations.size) {
        if (!VlhNav.pingedOnStart && locations.any { it.vlhReady() }) {
            VlhNav.pingedOnStart = true
            if (pingsState.vlhAllPings().isEmpty()) {
                locationViewModel.refreshPings(
                    parallelism = pingParallelism,
                    performPing = { config -> viewModel.performPingFor(config) },
                )
            }
        }
    }

    // Кнопка «Авто» на виджете рабочего стола → поиск быстрейшего
    val autoSignalPending by org.olcbox.app.widget.WidgetAutoSignal.pending.collectAsState()
    LaunchedEffect(autoSignalPending, locations.size) {
        if (autoSignalPending && locations.any { it.vlhReady() }) {
            org.olcbox.app.widget.WidgetAutoSignal.consume()
            fastest(null)
        }
    }

    val act = VlhActions(
        select = { id ->
            VlhNav.auto = false
            locationViewModel.selectLocation(id) {
                viewModel.loadCurrentConfig()
                viewModel.restartVpnIfRunning()
            }
        },
        toggleFavorite = { id -> onToggleFavorite(id) },
        ping = { ids, single -> ping(ids, single) },
        openConfig = { id -> onOpenLocationSettings(id) },
        share = { item ->
            val cfg = item.config
            if (cfg != null) onShareLocationRequested(cfg) else toast.show("Нечем поделиться", error = true)
        },
        refresh = { g -> refresh(g) },
        toggleCollapsed = { key -> onToggleGroupCollapsed(key) },
        fastest = { ids -> fastest(ids) },
        setAutoUpdate = { g, enabled ->
            val url = g.url
            if (url != null) {
                locationViewModel.setSubscriptionAutoUpdate(url, enabled)
                toast.show(if (enabled) "Автообновление включено" else "Автообновление выключено")
            }
        },
        delete = { g ->
            if (confirmBeforeDelete) {
                pendingDelete = g
            } else {
                deleteGroup(g, locationViewModel, viewModel, toast)
            }
        },
        openLogs = { logsOpen = true },
        openUrl = { u -> openUrl(u) },
        add = { addOpen = true },
        paste = {
            onImportFromClipboardRequested(
                { toast.show("Подписка добавлена") },
                { msg -> toast.show(msg, error = true) },
            )
        },
        scanQr = { onScanQrRequested() },
    )

    val data = VlhData(
        pings = pingsState,
        selectedId = locationViewModel.selectedLocationId,
        favorites = favorites,
        collapsed = collapsedGroups,
        pingSorted = pingSortedGroups,
        refreshingKey = refreshingKey,
    )

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = navInset + 92.dp

    // Проба шрифта (разд. 4 п. 5 плана): явный Inter без наклона на всех своих экранах.
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Normal)

    ProvideTextStyle(baseStyle) {
        Box(modifier = Modifier.fillMaxSize().background(Vlh.Bg)) {
            when (VlhNav.tab) {
                1 -> VlhServersScreen(
                    locations = locations,
                    hasLoaded = locationViewModel.hasLoadedLocations,
                    data = data,
                    act = act,
                    autoMode = VlhNav.auto,
                    bottomPad = bottomPad,
                    refreshingAll = refreshingKey == "*",
                    onCollapseAll = { collapse ->
                        vlhGroups(locations).forEach { g ->
                            val isCollapsed = g.key in collapsedGroups
                            if (isCollapsed != collapse) onToggleGroupCollapsed(g.key)
                        }
                    },
                )
                else -> VlhHomeScreen(
                    locations = locations,
                    hasLoaded = locationViewModel.hasLoadedLocations,
                    data = data,
                    act = act,
                    isConnected = state.isVpnConnected,
                    isLoading = state.isVpnLoading || autoRunning,
                    connectedSinceEpochMs = state.connectedSinceEpochMs,
                    speed = speed,
                    autoMode = VlhNav.auto,
                    bottomPad = bottomPad,
                    onToggle = {
                        val requiresSetup = !state.canStartVpn && !state.isVpnConnected && !state.isVpnLoading
                        if (requiresSetup) addOpen = true else onToggleClick()
                    },
                    onSecretTap = onUnlockExperimental,
                    onOpenServers = { VlhNav.tab = 1 },
                )
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (updateAvailable) {
                    Text(
                        text = "Доступно обновление приложения — обновить",
                        color = Vlh.OnGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(Vlh.Gold, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { onUpdateClick() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                VlhBottomBar(
                    tab = VlhNav.tab,
                    onTab = { t ->
                        // ⚙ пока открывает прежние настройки (свой экран — сборка №17)
                        if (t == 2) {
                            onAppSettingsClick()
                        } else {
                            VlhNav.tab = t
                        }
                    },
                )
            }

            VlhToastHost(toast, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
        }

        if (addOpen) {
            VlhAddSheet(
                hasSubscriptions = hasSubscriptions,
                onDismiss = { addOpen = false },
                onPaste = { addOpen = false; act.paste() },
                onQr = { addOpen = false; onScanQrRequested() },
                onFile = { addOpen = false; onImportFileRequested() },
                onManual = { addOpen = false; onAddLocation() },
                onRefreshAll = { addOpen = false; refresh(null) },
                onWhere = { openUrl(Vlh.CABINET_URL) },
            )
        }

        if (logsOpen) {
            val logs by viewModel.logs.collectAsState()
            LogsSheet(
                logs = logs,
                onSaveClick = {
                    onSaveLogsRequested({ m -> toast.show(m) }, { m -> toast.show(m, error = true) })
                },
                onShareClick = {
                    viewModel.onShareLogs(onShared = { m -> toast.show(m) }, onError = { m -> toast.show(m, error = true) })
                },
                onDismiss = { logsOpen = false },
            )
        }

        pendingDelete?.let { g ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = Vlh.Card,
                title = { Text("Удалить подписку?", color = Vlh.Text) },
                text = { Text("«${g.title}» и все её серверы (${g.items.size}) будут удалены из приложения.", color = Vlh.Muted) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        deleteGroup(g, locationViewModel, viewModel, toast)
                    }) { Text("Удалить", color = Vlh.Red) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Отмена", color = Vlh.Gold) }
                },
            )
        }
    }
}

private fun deleteGroup(g: VlhGroup, lvm: LocationViewModel, vm: HomeScreenViewModel, toast: VlhToastState) {
    lvm.deleteLocations(g.items.map { it.storageId }) {
        vm.loadCurrentConfig()
        vm.restartVpnIfRunning()
        toast.show("Подписка удалена")
    }
}
