#!/usr/bin/env bash
# Patch 4 (26.10.2026, макет A1 владельца): золотая тёмная тема, кнопка подключения «неон» со знаком в круге,
# в шапке — только слоган и слово VALHALLA (знак уже в кнопке).
set -euo pipefail
cd "$(dirname "$0")/../../YPtun"
python3 - <<'PY'
import base64, re
# ---------- 1. Золотая тёмная тема (OlcboxDarkColorScheme) ----------
p = 'sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/theme/Color.kt'
s = open(p, encoding='utf-8').read()
gold = {
    'primary': 'FFE8B04B', 'onPrimary': 'FF1A1206', 'primaryContainer': 'FF3A2F1C', 'onPrimaryContainer': 'FFFFE7B8',
    'inversePrimary': 'FFB8862E', 'secondary': 'FFB9A77F', 'onSecondary': 'FF1A1206', 'secondaryContainer': 'FF2A2214',
    'onSecondaryContainer': 'FFF3EAD7', 'tertiary': 'FFFFC766', 'onTertiary': 'FF1A1206', 'tertiaryContainer': 'FF33280F',
    'onTertiaryContainer': 'FFFFE7B8', 'onBackground': 'FFF3EAD7', 'onSurface': 'FFF3EAD7', 'surfaceVariant': 'FF2A2418',
    'onSurfaceVariant': 'FFB9A77F', 'surfaceContainerLow': 'FF0E0B07', 'surfaceContainer': 'FF15110A',
    'surfaceContainerHigh': 'FF1E1810', 'surfaceContainerHighest': 'FF2A2216', 'outline': 'FF4A3D24', 'outlineVariant': 'FF2A2216',
}
start = s.index('internal val OlcboxDarkColorScheme = darkColorScheme(')
end = s.index('\n)', start)
block = s[start:end]
for k, v in gold.items():
    block, n = re.subn(r'(\n    ' + k + r' = Color\(0x)[0-9A-Fa-f]{8}(\))', r'\g<1>' + v + r'\2', block)
    assert n == 1, 'color not found: ' + k
s = s[:start] + block + s[end:]
open(p, 'w', encoding='utf-8').write(s)
print('gold theme applied')

# ---------- 2. Кнопка «неон» ----------
sign = base64.b64encode(open('../branding/neon/sign.png', 'rb').read()).decode()
parts = [sign[i:i + 16000] for i in range(0, len(sign), 16000)]
kt_sign = 'listOf(\n' + ',\n'.join('        "' + x + '"' for x in parts) + '\n    )'
btn = '''package org.olcbox.app.ui.components

// Valhalla (patch 04): кнопка подключения «неон» — знак в круге, золотая неоновая дуга.
// Выключено — короткая дуга со светящейся «головой»; подключение — дуга бежит по кругу; включено — полный круг.
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.decodeToImageBitmap

sealed class StartButtonState {
    object Idle : StartButtonState()
    object Loading : StartButtonState()
    object Success : StartButtonState()
}

internal object ValhallaNeonSign {
    val png = ''' + kt_sign + '''
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeNeonSign(): ImageBitmap = Base64.decode(ValhallaNeonSign.png.joinToString("")).decodeToImageBitmap()

@Composable
fun StartButton(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    isLoading: Boolean,
    requiresSetup: Boolean = false,
    label: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val gold = Color(0xFFFFB21F)
    val core = Color(0xFFFFF3CC)
    val glow = Color(0xFFFF9D00)
    val track = Color(0xFF2C2414)
    val sign = remember { decodeNeonSign() }
    val spin = rememberInfiniteTransition(label = "neonSpin")
    val rot by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rot")
    val sweepTarget by animateFloatAsState(if (isActive) 360f else 65f, tween(600), label = "sweep")
    val lit by animateFloatAsState(if (isActive) 1f else 0f, tween(600), label = "lit")

    Box(
        modifier = modifier
            .size(196.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f - 14.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2f, r * 2f)
            drawCircle(track, r, style = Stroke(2.dp.toPx()))
            val start = if (isLoading) -90f + rot else -90f
            val sweep = if (isLoading) 110f else sweepTarget
            drawArc(glow.copy(alpha = 0.18f + 0.17f * lit), start, sweep, false, tl, sz,
                style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawArc(gold, start, sweep, false, tl, sz, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(core.copy(alpha = 0.9f), start, sweep, false, tl, sz, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            if (sweep < 359f) {
                val a = (start + sweep) * PI.toFloat() / 180f
                val p = Offset(center.x + r * cos(a), center.y + r * sin(a))
                val hr = 11.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(core, gold, Color.Transparent), center = p, radius = hr), hr, p)
            }
        }
        Image(
            bitmap = sign,
            contentDescription = label ?: "Toggle",
            modifier = Modifier.size(108.dp).alpha(if (enabled) 0.72f + 0.28f * lit else 0.4f),
            contentScale = ContentScale.Fit
        )
        if (requiresSetup && !isActive && !isLoading) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = gold, modifier = Modifier.align(Alignment.BottomCenter).size(26.dp))
        }
    }
}
'''
open('sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/components/StartButton.kt', 'w', encoding='utf-8').write(btn)
print('neon button applied')

# ---------- 3. Шапка: без второго знака (знак уже в кнопке) ----------
hp = 'sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/features/home/components/ValhallaHeader.kt'
h = open(hp, encoding='utf-8').read()
tri = '''            Image(
                bitmap = triangle,
                contentDescription = null,
                modifier = Modifier.height(32.dp),
                contentScale = ContentScale.Fit
            )
'''
if tri in h:
    h = h.replace(tri, '')
    open(hp, 'w', encoding='utf-8').write(h)
    print('header: triangle removed')
else:
    print('header: triangle block not found, left as is')
PY
echo "patch 4 done"
