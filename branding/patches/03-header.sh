#!/usr/bin/env bash
# Patch 3: pinned brand header (slogan + logo + VALHALLA word) in the top bar of the home screen.
set -euo pipefail
cd "$(dirname "$0")/../../YPtun"
python3 - <<'PY'
import glob, os
from PIL import Image

DRAW = 'sharedUI/src/commonMain/composeResources/drawable'
os.makedirs(DRAW, exist_ok=True)

# pick the images in branding/ by their shape: logo ~ 1.0-1.7 wide, slogan > 3.5 wide
logo = slogan = None
for f in sorted(glob.glob('../branding/*.jpg') + glob.glob('../branding/*.jpeg') + glob.glob('../branding/*.png')):
    w, h = Image.open(f).size
    r = w / h
    if logo is None and 1.0 <= r <= 1.7:
        logo = f
    elif slogan is None and r > 3.5:
        slogan = f

def bright_rows(im, thr=45):
    m = im.convert('L').point(lambda p: 255 if p > thr else 0)
    return [m.crop((0, y, im.width, y + 1)).getbbox() is not None for y in range(im.height)], m

def segments(flags, min_len=4):
    segs, start = [], None
    for y, on in enumerate(flags + [False]):
        if on and start is None: start = y
        if not on and start is not None:
            if y - start >= min_len: segs.append((start, y))
            start = None
    return segs

def save_scaled(im, name, height):
    w = max(1, round(im.width * height / im.height))
    im.resize((w, height), Image.LANCZOS).save(f'{DRAW}/{name}.png', optimize=True)

if logo:
    im = Image.open(logo).convert('RGB')
    flags, mask = bright_rows(im)
    segs = segments(flags)
    assert len(segs) >= 2, 'could not split the logo into triangle and word'
    gaps = [(segs[i + 1][0] - segs[i][1], i) for i in range(len(segs) - 1)]
    _, gi = max(gaps)
    tri_rows = (segs[0][0], segs[gi][1])
    word_rows = (segs[gi + 1][0], segs[-1][1])
    def part(rows, extra_bottom=0, pad=14):
        sub = im.crop((0, rows[0], im.width, min(im.height, rows[1] + extra_bottom)))
        bb = mask.crop((0, rows[0], im.width, rows[1])).getbbox()
        x0, x1 = max(0, bb[0] - pad), min(im.width, bb[2] + pad)
        return sub.crop((x0, 0, x1, sub.height))
    save_scaled(part(tri_rows), 'vh_tri', 150)
    save_scaled(part(word_rows, extra_bottom=40), 'vh_word', 84)
    print('logo split from', logo)
else:
    for n in ('vh_tri', 'vh_word'):
        Image.new('RGB', (2, 2), (0, 0, 0)).save(f'{DRAW}/{n}.png')
    print('no logo image found, empty header images')

if slogan:
    im = Image.open(slogan).convert('RGB')
    bb = im.convert('L').point(lambda p: 255 if p > 45 else 0).getbbox()
    im = im.crop((max(0, bb[0] - 8), max(0, bb[1] - 8), min(im.width, bb[2] + 8), min(im.height, bb[3] + 8)))
    save_scaled(im, 'vh_slogan', 56)
    print('slogan from', slogan)
else:
    Image.new('RGB', (2, 2), (0, 0, 0)).save(f'{DRAW}/vh_slogan.png')
    print('no slogan image found, empty slogan')

# the composable
kt = '''package org.olcbox.app.ui.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import multiplatform_app.sharedui.generated.resources.Res
import multiplatform_app.sharedui.generated.resources.vh_slogan
import multiplatform_app.sharedui.generated.resources.vh_tri
import multiplatform_app.sharedui.generated.resources.vh_word
import org.jetbrains.compose.resources.painterResource

/** Pinned brand header: slogan on top, logo and the VALHALLA word below (images on a black background). */
@Composable
fun ValhallaHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(Res.drawable.vh_slogan),
            contentDescription = null,
            modifier = Modifier.height(14.dp),
            contentScale = ContentScale.Fit
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.vh_tri),
                contentDescription = null,
                modifier = Modifier.height(32.dp),
                contentScale = ContentScale.Fit
            )
            Image(
                painter = painterResource(Res.drawable.vh_word),
                contentDescription = "Valhalla",
                modifier = Modifier.height(22.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
'''
open('sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/features/home/components/ValhallaHeader.kt', 'w', encoding='utf-8').write(kt)

# put it into the top bar title
p = 'sharedUI/src/commonMain/kotlin/org/olcbox/app/ui/features/home/components/HomeScreenAppBar.kt'
s = open(p, encoding='utf-8').read()
if 'ValhallaHeader()' not in s:
    done = False
    for name in ('Valhalla', 'YPtun'):
        old = ('        title = {\n            Text(\n                text = "' + name + '",\n'
               '                style = MaterialTheme.typography.titleLarge,\n'
               '                color = MaterialTheme.colorScheme.onSurface\n            )\n        },\n')
        if old in s:
            s = s.replace(old, '        title = { ValhallaHeader() },\n'); done = True; break
    assert done, 'top bar title block not found'
    open(p, 'w', encoding='utf-8').write(s)
    print('header placed in the top bar')
PY
echo "patch 3 done"
