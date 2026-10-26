#!/usr/bin/env bash
# Patch 5 (26.10.2026, прототип владельца): главная как в макете A1 —
# под кнопкой «НЕ ЗАЩИЩЕНО / ПОДКЛЮЧЕНИЕ… / ЗАЩИЩЕНО», подсказка и чип текущего сервера; таймер сверху убран
# (время подключения — мелко в подсказке); окно «Неофициальная сборка» не показываем (MIT, наш форк).
set -euo pipefail
cd "$(dirname "$0")/../../YPtun"
python3 - <<'PY'
# ---------- 1. без окна «Неофициальная сборка» ----------
p = 'androidApp/src/main/kotlin/org/olcbox/app/AppActivity.kt'
s = open(p, encoding='utf-8').read()
call = '        maybeWarnUnofficialBuild()\n'
assert call in s, 'unofficial-build call not found'
s = s.replace(call, '        // Valhalla: окно «Неофициальная сборка» не показываем — это наш форк (MIT)\n', 1)
open(p, 'w', encoding='utf-8').write(s)
print('unofficial dialog removed')

# ---------- 2. главная: статус и чип сервера под кнопкой ----------
p = 'sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/features/home/HomeScreen.kt'
s = open(p, encoding='utf-8').read()
timer = '''            item(key = "connection-timer") {
                ConnectionTimer(
                    isConnected = state.isVpnConnected,
                    connectedSinceEpochMs = state.connectedSinceEpochMs,
                    onSecretTap = onUnlockExperimental,
                )
            }

'''
assert timer in s, 'timer item not found'
s = s.replace(timer, '', 1)
anchor = '''            extraConnectContent?.let { extra ->'''
assert anchor in s, 'extraConnectContent anchor not found'
status = '''            item(key = "vlh-status") {
                val selName = locations.firstOrNull { it.storageId == locationViewModel.selectedLocationId }?.fullName
                ValhallaStatus(
                    isConnected = state.isVpnConnected,
                    isLoading = state.isVpnLoading,
                    connectedSinceEpochMs = state.connectedSinceEpochMs,
                    serverName = selName,
                    onSecretTap = onUnlockExperimental,
                )
            }

'''
s = s.replace(anchor, status + anchor, 1)
comp = '''

// Valhalla (patch 05): статус под кнопкой, как в макете A1.
@Composable
private fun ValhallaStatus(
    isConnected: Boolean,
    isLoading: Boolean,
    connectedSinceEpochMs: Long,
    serverName: String?,
    onSecretTap: () -> Unit = {},
) {
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(isConnected, connectedSinceEpochMs) {
        if (isConnected && connectedSinceEpochMs > 0L) {
            while (true) {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                elapsed = ((now - connectedSinceEpochMs) / 1000L).coerceAtLeast(0L)
                delay(1000)
            }
        } else {
            elapsed = 0L
        }
    }
    val time = "${(elapsed / 3600).pad()}:${((elapsed % 3600) / 60).pad()}:${(elapsed % 60).pad()}"
    var taps by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                isLoading -> "ПОДКЛЮЧЕНИЕ…"
                isConnected -> "ЗАЩИЩЕНО"
                else -> "НЕ ЗАЩИЩЕНО"
            },
            color = MaterialTheme.colorScheme.primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                taps++
                if (taps >= 5) { taps = 0; onSecretTap() }
            }
        )
        Text(
            text = when {
                isLoading -> "выбираю самый быстрый сервер"
                isConnected -> "нажми на знак, чтобы отключить · $time"
                else -> "нажми на знак, чтобы подключиться"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (serverName != null) {
            Text(
                text = "$serverName  ▾",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
'''
s = s.rstrip('\n') + comp
need = ['androidx.compose.foundation.layout.fillMaxWidth', 'androidx.compose.foundation.layout.padding',
        'androidx.compose.foundation.border', 'androidx.compose.foundation.background', 'androidx.compose.ui.draw.clip',
        'androidx.compose.foundation.layout.Column', 'androidx.compose.foundation.interaction.MutableInteractionSource',
        'androidx.compose.foundation.clickable', 'androidx.compose.ui.text.style.TextAlign', 'androidx.compose.ui.text.font.FontWeight',
        'androidx.compose.runtime.LaunchedEffect', 'androidx.compose.runtime.mutableStateOf', 'androidx.compose.runtime.remember',
        'androidx.compose.runtime.getValue', 'androidx.compose.runtime.setValue', 'kotlinx.coroutines.delay']
lines = s.split('\n')
first_import = next(i for i, l in enumerate(lines) if l.startswith('import '))
add = [f'import {n}' for n in need if f'import {n}\n' not in s and not s.count(f'import {n}\n')]
if add:
    lines[first_import:first_import] = add
s = '\n'.join(lines)
open(p, 'w', encoding='utf-8').write(s)
print('home status added; imports added:', len(add))
PY
echo "patch 5 done"
