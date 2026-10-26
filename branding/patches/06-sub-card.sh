#!/usr/bin/env bash
# Patch 6 (26.10.2026, прототип владельца FINAL-off): под статусом — карточка подписки
# «Подписка <имя> · автообновление | N дн.», полоса трафика «X из Y ГБ», кнопки Telegram / Кабинет
# (ссылки из заголовков подписки support-url / profile-web-page-url). Круглая кнопка «Авто» справа
# от знака убрана (в прототипе её нет); чип сервера листает к списку серверов.
set -euo pipefail
cd "$(dirname "$0")/../../YPtun"
python3 - <<'PY'
p = 'sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/features/home/HomeScreen.kt'
s = open(p, encoding='utf-8').read()

# ---------- 1. без круглой «Авто» рядом со знаком ----------
old = '                    if (canAutoConnect) {\n'
assert s.count(old) == 1, 'auto satellite not found'
s = s.replace(old, '                    if (canAutoConnect && false) { // Valhalla: «Авто» нет в прототипе\n', 1)

# ---------- 2. чип сервера кликабелен + карточка подписки ----------
old = '''                    serverName = selName,
                    onSecretTap = onUnlockExperimental,
                )
            }
'''
assert s.count(old) == 1, 'vlh-status item not found'
new = '''                    serverName = selName,
                    onSecretTap = onUnlockExperimental,
                    onServerClick = { scope.launch { scrollState.animateScrollToItem(3) } },
                )
            }

            item(key = "vlh-sub") {
                val selected = locations.firstOrNull { it.storageId == locationViewModel.selectedLocationId }
                val sub = selected?.metadata?.subscription
                    ?: locations.firstNotNullOfOrNull { it.metadata?.subscription }
                if (sub != null) {
                    ValhallaSubscriptionCard(sub = sub, isConnected = state.isVpnConnected)
                }
            }
'''
s = s.replace(old, new, 1)

old = '''    serverName: String?,
    onSecretTap: () -> Unit = {},
) {
    var elapsed by remember { mutableStateOf(0L) }'''
assert s.count(old) == 1, 'ValhallaStatus signature not found'
s = s.replace(old, '''    serverName: String?,
    onSecretTap: () -> Unit = {},
    onServerClick: () -> Unit = {},
) {
    var elapsed by remember { mutableStateOf(0L) }''', 1)

old = '''                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)'''
assert s.count(old) == 1, 'server chip not found'
s = s.replace(old, '''                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable { onServerClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)''', 1)

s += '''

// Valhalla (patch 06): карточка подписки, как в прототипе FINAL-off.
private fun vlhBytes(text: String?): Double? {
    val m = Regex("""([0-9]+(?:[.,][0-9]+)?)\\s*([KMGTP]?)i?B""", RegexOption.IGNORE_CASE)
        .find(text ?: return null) ?: return null
    val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val pow = "BKMGTP".indexOf(m.groupValues[2].uppercase().ifEmpty { "B" })
    var r = v
    repeat(pow) { r *= 1024.0 }
    return r
}

private fun vlhRu(text: String): String = text
    .replace("TB", "ТБ").replace("GB", "ГБ").replace("MB", "МБ").replace("KB", "КБ").replace(" B", " Б")

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun ValhallaSubscriptionCard(
    sub: org.olcbox.app.data.model.SubscriptionMetadata,
    isConnected: Boolean,
) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val gold = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val days = sub.expiresAtEpochMs?.let { ((it - now) + 86_399_999L).floorDiv(86_400_000L).coerceAtLeast(0L) }
    val used = vlhBytes(sub.used)
    val total = vlhBytes(sub.available)
    val progress = if (used != null && total != null && total > 0.0) (used / total).toFloat().coerceIn(0f, 1f) else 0f
    val trafficText = when {
        sub.used == null -> null
        sub.available == null || sub.available == "∞" -> "${vlhRu(sub.used!!)} · без лимита"
        else -> "${vlhRu(sub.used!!)} из ${vlhRu(sub.available!!)}"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, gold.copy(alpha = 0.35f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Подписка ",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Text(
                text = sub.name ?: "VLH",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (sub.autoUpdateEnabled) {
                Text(
                    text = " · ♻ автообновление",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = if (days != null) "$days дн." else "∞",
                color = if (days != null && days <= 2L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (total != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = gold,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(
                text = trafficText ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (isConnected) "подключено" else "не подключено",
                color = if (isConnected) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        val tg = sub.supportUrl
        val web = sub.webPageUrl
        if (tg != null || web != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tg != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { runCatching { uri.openUri(tg) } },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gold),
                    ) {
                        Text("✈ Telegram", color = gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                if (web != null) {
                    Button(
                        onClick = { runCatching { uri.openUri(web) } },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Кабинет", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
'''
open(p, 'w', encoding='utf-8').write(s)
print('subscription card added')
PY
