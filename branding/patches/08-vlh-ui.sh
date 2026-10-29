#!/usr/bin/env bash
# Patch 8 (30.10.2026, сборка №16): свои экраны «как INCY» — Главная и Серверы, плавающая панель на 3 вкладки.
# Готовые файлы branding/vlh-ui/*.kt копируются в пакет org.olcbox.app.ui.vlh (не python-заменами).
# Точечные врезки (без них нельзя):
#   1) OlcboxAppContent: на телефоне вместо HomeScreen(...) — VlhApp(...) (на ПК остаётся HomeScreen);
#   2) скорость ↑↓ для строки над знаком публикуется всегда, когда подключено (без настройки «скорость на главной»);
#   3) порт SOCKS 10808 занят другим приложением (Happ, v2RayTun…) — в режиме TUN берём свободный, без ошибки.
set -euo pipefail
cd "$(dirname "$0")/../../YPtun"

DST=sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/vlh
mkdir -p "$DST"
cp ../branding/vlh-ui/*.kt "$DST"/
echo "vlh-ui: $(ls "$DST" | wc -l) files copied"

python3 - <<'PY'
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        assert s.count(old) == 1, f'{p}: not found / not unique: {old[:80]!r}'
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('ok', p.rsplit('/', 1)[-1])

# ---------- 1. подключение VlhApp ----------
patch('sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/OlcboxAppContent.kt', [
    ('''            AppScreen.Home -> {
                HomeScreen(
''', '''            AppScreen.Home -> {
                // Valhalla (patch 08): на телефоне — свои экраны «как INCY»; на ПК (wide / exit) — прежний HomeScreen.
                if (onExitClick == null && !wideLayout) org.olcbox.app.ui.vlh.VlhApp(
                    viewModel = homeViewModel,
                    locationViewModel = locationViewModel,
                    onToggleClick = onToggleClick,
                    onImportFileRequested = onImportFileRequested,
                    onImportFromClipboardRequested = onImportFromClipboardRequested,
                    onScanQrRequested = onScanQrRequested,
                    onShareLocationRequested = onShareLocationRequested,
                    onSaveLogsRequested = onSaveLogsRequested,
                    onAppSettingsClick = onAppSettingsClick,
                    onOpenLocationSettings = { id ->
                        locationViewModel.startEditing(id)
                        onNavigate(AppScreen.LocationSettings(id))
                    },
                    onAddLocation = {
                        locationViewModel.startEditing(null)
                        onNavigate(AppScreen.LocationSettings(null))
                    },
                    confirmBeforeDelete = confirmBeforeDelete,
                    pingParallelism = pingParallelism,
                    updateAvailable = updateAvailable,
                    onUpdateClick = onUpdateClick,
                    onUnlockExperimental = onUnlockExperimental,
                    collapsedGroups = collapsedGroups,
                    pingSortedGroups = pingSortedGroups,
                    favorites = pinnedCustomLocations,
                    onToggleGroupCollapsed = onToggleGroupCollapsed,
                    onToggleFavorite = onToggleCustomLocationPinned,
                ) else HomeScreen(
'''),
])

# ---------- 2. скорость для строки «↑ · ⏱ · ↓» ----------
patch('sharedUI/src/androidMain/kotlin/org/olcbox/app/ui/activities/AndroidMainScreen.kt', [
    ('(if (appBehavior.showSpeedOnHome && isVpnConnected) liveSpeed else null)',
     '(if (isVpnConnected) liveSpeed else null) // Valhalla: строка скорости на главной всегда'),
])
patch('sharedUI/src/androidMain/kotlin/org/olcbox/app/vpn/service/OlcboxVpnService.kt', [
    ('                    showSpeedOnHome = speedHome\n',
     '                    showSpeedOnHome = true // Valhalla: скорость на главной всегда (сборка №16)\n'),
    ('            showSpeedOnHome = it.showSpeedOnHome\n',
     '            showSpeedOnHome = true // Valhalla: скорость на главной всегда (сборка №16)\n'),
    # ---------- 3. занятый порт SOCKS в режиме TUN → свободный ----------
    ('''                if (options.socksUsername.isBlank() || options.socksPassword.isBlank()) {
                    socksUsername = randomSocksToken()
                    socksPassword = randomSocksToken()
                }
''', '''                if (options.socksUsername.isBlank() || options.socksPassword.isBlank()) {
                    socksUsername = randomSocksToken()
                    socksPassword = randomSocksToken()
                }
                // Valhalla (сборка №16): в TUN локальный SOCKS внутренний. Если порт уже занят другим
                // приложением (Happ, v2RayTun, INCY…) — берём свободный вместо ошибки «port is still in use».
                val vlhBindHost = AndroidSocksProxySettings.connectHost(socksListenHost)
                val vlhBusy: (Int) -> Boolean = { p ->
                    runCatching {
                        java.net.ServerSocket().use { ss ->
                            ss.reuseAddress = true
                            ss.bind(java.net.InetSocketAddress(vlhBindHost, p))
                        }
                    }.isFailure
                }
                if (vlhBusy(socksListenPort)) {
                    val vlhAlt = (listOf(10818, 10828, 10838) + List(24) { 20000 + kotlin.random.Random.nextInt(20000) })
                        .firstOrNull { it != socksListenPort && !vlhBusy(it) }
                    if (vlhAlt != null) {
                        addLog("SOCKS port $socksListenPort is busy (another app?) -> using free port $vlhAlt")
                        socksListenPort = vlhAlt
                    }
                }
'''),
])
PY
echo "patch 8 done"
