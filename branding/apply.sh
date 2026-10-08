#!/usr/bin/env bash
# Valhalla branding: name, version, package id, APK file names and launcher icon.
# Applied by CI before every build, so the upstream YPtun code stays untouched.
set -euo pipefail
cd "$(dirname "$0")/.."
cd YPtun

# Version, package id
sed -i 's/^olcbox.version=.*/olcbox.version=1.0.0/; s/^olcbox.versionCode=.*/olcbox.versionCode=1/; s/^olcbox.applicationId=.*/olcbox.applicationId=com.vlhnet.valhalla/' gradle.properties

# App name on the home screen, quick-settings tile and widget
sed -i 's/android:label="YPtun"/android:label="Valhalla"/' androidApp/src/main/AndroidManifest.xml
for f in androidApp/src/main/res/values*/strings.xml; do
  sed -i -E 's#(name="(qs_tile_label|widget_toggle_label)">)YPtun(<)#\1Valhalla\3#' "$f"
done

# APK file names: Valhalla-<version>-<abi>-release.apk
python3 - <<'PY'
p = 'androidApp/build.gradle.kts'
s = open(p, encoding='utf-8').read()
if 'BasePluginExtension' not in s:
    marker = 'android {\n    namespace = "org.olcbox.app"'
    assert marker in s, 'marker not found in build.gradle.kts'
    add = ('extensions.configure<org.gradle.api.plugins.BasePluginExtension> {\n'
           '    archivesName.set("Valhalla-${olcboxVersion.get()}")\n}\n\n')
    open(p, 'w', encoding='utf-8').write(s.replace(marker, add + marker, 1))
PY

# Launcher icon from the logo image placed in branding/
python3 - <<'PY'
import glob, os, sys
from PIL import Image, ImageDraw
files = sorted(glob.glob('../branding/*.jpg') + glob.glob('../branding/*.jpeg') + glob.glob('../branding/*.png'))
if not files:
    sys.exit('branding: put the logo image (jpg/png) into the branding folder')
src = Image.open(files[0]).convert('RGB')
x0, y0, x1, y1 = src.convert('L').point(lambda p: 255 if p > 60 else 0).getbbox()
crop = src.crop((max(0, x0 - 40), max(0, y0 - 40), min(src.width, x1 + 40), min(src.height, y1 + 80)))
RATIO = 0.61                       # logo width as a share of the visible icon area
S = 432
sc = RATIO * S * 72 / 108 / (x1 - x0)
c = crop.resize((round(crop.width * sc), round(crop.height * sc)), Image.LANCZOS)
M = Image.new('RGB', (S, S), (0, 0, 0))
bx, by, bw, bh = (x0 - max(0, x0 - 40)) * sc, (y0 - max(0, y0 - 40)) * sc, (x1 - x0) * sc, (y1 - y0) * sc
M.paste(c, (round(S / 2 - bx - bw / 2), round(S / 2 - by - bh / 2)))
fg = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
leg = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for d, s in fg.items():
    p = f'androidApp/src/main/res/mipmap-{d}'
    os.makedirs(p, exist_ok=True)
    M.resize((s, s), Image.LANCZOS).convert('RGBA').save(f'{p}/ic_launcher_foreground.png')
    Image.new('RGBA', (s, s), (0, 0, 0, 255)).save(f'{p}/ic_launcher_background.png')
    win = M.crop((72, 72, 360, 360)).resize((leg[d], leg[d]), Image.LANCZOS)
    m = Image.new('L', (leg[d] * 4, leg[d] * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, leg[d] * 4 - 1, leg[d] * 4 - 1), radius=int(leg[d] * 4 * 0.3), fill=255)
    m = m.resize((leg[d], leg[d]), Image.LANCZOS)
    o = Image.new('RGBA', (leg[d], leg[d]), (0, 0, 0, 0)); o.paste(win.convert('RGBA'), (0, 0), m)
    o.save(f'{p}/ic_launcher.png')
print('icons generated from', files[0])
PY
echo "Valhalla branding applied"
