#!/usr/bin/env bash
# Patch 7 (26.10.2026, разбор docs/valhalla-app-razbor.md, сборка №15): главная как в прототипе.
# Шапка без ⚙️➕⋮ и крупнее; знак на тёмно-сером круге (прозрачный фон картинки — branding/neon/sign.png),
# тонкое тусклое кольцо, отклик на нажатие; чип «Авто · страна · мс»; компактный список «СЕРВЕРЫ  все →»
# с первой строкой «Авто · лучший»; без белых всплывашек при «Авто»; тёмные всплывашки с золотой каймой;
# уведомление «VLH»; пинг по умолчанию TCP; шрифт Inter (ровный) вместо Google Sans Flex.
set -euo pipefail
cd "$(dirname "$0")/../../YPtun"

# ---------- шрифт: Inter под теми же именами ресурсов ----------
F=sharedUI/src/commonMain/composeResources/font
for w in regular:Regular medium:Medium semi_bold:SemiBold bold:Bold; do
  cp "../branding/fonts/Inter-${w#*:}.ttf" "$F/google_sans_flex_${w%%:*}.ttf"
done
cp ../branding/fonts/LICENSE.txt "$F/../Inter-LICENSE.txt" 2>/dev/null || true

python3 - <<'PY'
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        assert s.count(old) == 1, f'{p}: not found / not unique: {old[:70]!r}'
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('ok', p.rsplit('/', 1)[-1])

base = 'sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/'

# ---------- пинг по умолчанию TCP ----------
patch('sharedUI/src/commonMain/kotlin/org/olcbox/app/data/model/AppBehaviorSettings.kt', [
    ('    val pingMode: String = PING_PROXY_HEAD,', '    val pingMode: String = PING_TCP, // Valhalla: TCP по умолчанию (владелец 26.10)'),
])

# ---------- уведомление: заголовок VLH ----------
patch('sharedUI/src/androidMain/kotlin/org/olcbox/app/vpn/service/OlcboxVpnService.kt', [
    ('        val title = "YPtun"', '        val title = "VLH"'),
])

# ---------- шапка без значков, крупнее ----------
patch(base + 'features/home/components/HomeScreenAppBar.kt', [
    ('''    var overflowExpanded by remember { mutableStateOf(false) }
''', '''    var overflowExpanded by remember { mutableStateOf(false) }
    // Valhalla (patch 07): на телефоне шапка — только слоган и VALHALLA, без ⚙️ ➕ ⋮ (как в прототипе).
    if (onExitClick == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) { ValhallaHeader() }
        return
    }
'''),
    ('import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.unit.dp\n'
     'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.fillMaxWidth\n'
     'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.statusBarsPadding\n'),
])
patch(base + 'features/home/components/ValhallaHeader.kt', [
    ('            modifier = Modifier.height(14.dp),', '            modifier = Modifier.height(19.dp),'),
    ('            modifier = Modifier.padding(top = 2.dp),', '            modifier = Modifier.padding(top = 6.dp),'),
    ('                modifier = Modifier.height(22.dp),', '                modifier = Modifier.height(44.dp),'),
])

# ---------- знак: тёмно-серый круг, тонкое кольцо, больше, отклик ----------
patch(base + 'components/StartButton.kt', [
    ('    val track = Color(0xFF2C2414)', '    val track = Color(0xFF4A3D22)'),
    ('''    Box(
        modifier = modifier
            .size(196.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },''', '''    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "press")
    Box(
        modifier = modifier
            .size(224.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(enabled = enabled, interactionSource = press, indication = null) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            },'''),
    ('drawCircle(Brush.radialGradient(listOf(Color(0xFF1D170D), Color(0xFF0A0806)), center = center, radius = r), r, center)',
     'drawCircle(Brush.radialGradient(listOf(Color(0xFF34302B), Color(0xFF1A1816), Color(0xFF0C0B0A)), center = center, radius = r), r, center)'),
    ('            drawCircle(track, r, style = Stroke(2.dp.toPx()))', '            drawCircle(track, r, style = Stroke(1.5.dp.toPx()))'),
    ('            modifier = Modifier.size(108.dp)', '            modifier = Modifier.size(132.dp)'),
    ('package org.olcbox.app.ui.components\n', 'package org.olcbox.app.ui.components\n\nimport androidx.compose.foundation.interaction.collectIsPressedAsState\nimport androidx.compose.ui.draw.scale\n'),
])

# ---------- главная ----------
p = base + 'features/home/HomeScreen.kt'
patch(p, [
    # «Авто» без белых окон: поиск показываем под знаком, итог — на чипе
    ('''        autoRunning = true
        scope.launch { snackbarHostState.showSnackbar(s.autoConnectSearching) }''', '''        autoRunning = true
        vlhAuto = true'''),
    ('''                    autoRunning = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (connectedName != null) s.autoConnectConnected(connectedName)
                            else s.autoConnectFailed
                        )
                    }''', '''                    autoRunning = false
                    if (connectedName == null) scope.launch { snackbarHostState.showSnackbar(s.autoConnectFailed) }'''),
    ('    var autoRunning by remember { mutableStateOf(false) }\n',
     '    var autoRunning by remember { mutableStateOf(false) }\n'
     '    var vlhAuto by remember { mutableStateOf(false) }      // Valhalla: выбран «Авто · лучший»\n'
     '    var vlhShowAll by remember { mutableStateOf(false) }   // Valhalla: «все →» — полный список\n'),
    # тёмные всплывашки с золотой каймой
    ('''            SnackbarHost(snackbarHostState)
''', '''            SnackbarHost(snackbarHostState) { data ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✓  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(data.visuals.message, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
            }
'''),
    # чип: «Авто · страна · мс»
    ('''                val selName = locations.firstOrNull { it.storageId == locationViewModel.selectedLocationId }?.fullName
                ValhallaStatus(
                    isConnected = state.isVpnConnected,
                    isLoading = state.isVpnLoading,''', '''                val selId = locationViewModel.selectedLocationId
                val selName = locations.firstOrNull { it.storageId == selId }?.fullName?.let { n ->
                    val ms = vlhPings(pingsState)[selId]
                    (if (vlhAuto) "Авто · $n" else n) + (if (ms != null) " · $ms мс" else "")
                }
                ValhallaStatus(
                    isConnected = state.isVpnConnected,
                    isLoading = state.isVpnLoading || autoRunning,'''),
    # компактный список вместо полного (узкий экран)
    ('''                connectItems()
                locationItems()
            }''', '''                connectItems()
                if (vlhShowAll || selectionMode) {
                    item(key = "vlh-all-head") {
                        VlhListHeader(title = "ВСЕ СЕРВЕРЫ", action = "свернуть ↑") { vlhShowAll = false }
                    }
                    locationItems()
                } else {
                    item(key = "vlh-compact") {
                        val ready = locations.filter { it.config?.isComplete() == true }
                        if (ready.isNotEmpty()) {
                            val pings = vlhPings(pingsState)
                            VlhListHeader(title = "СЕРВЕРЫ", action = "все →") { vlhShowAll = true }
                            VlhCompactList(
                                items = ready.take(6).map { Triple(it.storageId, it.fullName, pings[it.storageId]) },
                                selectedId = if (vlhAuto) null else locationViewModel.selectedLocationId,
                                autoSelected = vlhAuto,
                                autoPing = ready.mapNotNull { pings[it.storageId] }.minOrNull(),
                                onAuto = { autoConnectFastest() },
                                onSelect = { id ->
                                    vlhAuto = false
                                    locationViewModel.selectLocation(id) {
                                        viewModel.loadCurrentConfig()
                                        viewModel.restartVpnIfRunning()
                                    }
                                },
                            )
                        }
                    }
                    item(key = "vlh-bottom") { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }'''),
    # ручной выбор в полном списке снимает «Авто»
    ('''                onLocationSelected = { id ->
                    locationViewModel.selectLocation(id) {''', '''                onLocationSelected = { id ->
                    vlhAuto = false
                    locationViewModel.selectLocation(id) {'''),
])

# пинги один раз при открытии, если их ещё нет
s = open(p, encoding='utf-8').read()
anchor = '    fun afterDeletion(message: String) {'
assert s.count(anchor) == 1
s = s.replace(anchor, '''    // Valhalla (patch 07): пинг серверов при открытии главной, чтобы в списке и на чипе сразу были «мс».
    LaunchedEffect(locations.size) {
        if (locations.isNotEmpty() && vlhPings(locationViewModel.pingsState).isEmpty()) refreshHttpPings()
    }

''' + anchor, 1)
s += '''

// Valhalla (patch 07): компактный список серверов на главной, как в прототипе.
private fun vlhPings(st: org.olcbox.app.ui.features.locations.PingsState): Map<String, Int?> = when (st) {
    is org.olcbox.app.ui.features.locations.PingsState.Success -> st.pings
    is org.olcbox.app.ui.features.locations.PingsState.Loading -> (st.lastPings.orEmpty() + st.currentPings)
    is org.olcbox.app.ui.features.locations.PingsState.Error -> st.lastPings.orEmpty()
    else -> emptyMap()
}

@Composable
private fun VlhListHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = action,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAction() }.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun VlhCompactList(
    items: List<Triple<String, String, Int?>>,
    selectedId: String?,
    autoSelected: Boolean,
    autoPing: Int?,
    onAuto: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
    ) {
        VlhRow(lead = "⚡", name = "Авто · лучший", sub = "сам выбирает быстрый", ping = autoPing, selected = autoSelected, onClick = onAuto)
        items.forEach { (id, name, ms) ->
            VlhRow(lead = null, name = name, sub = null, ping = ms, selected = id == selectedId, onClick = { onSelect(id) })
        }
    }
}

@Composable
private fun VlhRow(lead: String?, name: String, sub: String?, ping: Int?, selected: Boolean, onClick: () -> Unit) {
    val gold = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) gold.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier.width(3.dp).fillMaxHeight()
                .background(if (selected) gold else androidx.compose.ui.graphics.Color.Transparent)
        )
        Row(
            modifier = Modifier.weight(1f).padding(start = 13.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (lead != null) {
                Text(
                    text = lead,
                    color = gold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(top = 3.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (sub != null) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
            Text(
                text = if (ping != null) "●  $ping мс" else "—",
                color = when {
                    ping == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    ping < 150 -> androidx.compose.ui.graphics.Color(0xFF5FCB7A)
                    ping < 400 -> androidx.compose.ui.graphics.Color(0xFFE3B341)
                    else -> androidx.compose.ui.graphics.Color(0xFFE5584F)
                },
                fontSize = 12.sp,
            )
        }
    }
}
'''
for imp in ['import androidx.compose.foundation.layout.width', 'import androidx.compose.foundation.layout.size',
            'import androidx.compose.foundation.layout.fillMaxHeight']:
    if imp + '\n' not in s:
        s = s.replace('import androidx.compose.foundation.layout.fillMaxWidth\n',
                      'import androidx.compose.foundation.layout.fillMaxWidth\n' + imp + '\n', 1)
open(p, 'w', encoding='utf-8').write(s)
print('home proto applied')
PY
