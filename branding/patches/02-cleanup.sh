#!/usr/bin/env bash
# Patch 2: update source = our own repo, no foreign donation/community blocks, no theme pickers.
set -euo pipefail
cd "$(dirname "$0")/../../YPtun"
export REPO="${GITHUB_REPOSITORY:-}"
python3 - <<'PY'
import os, re
base = 'sharedUI/src/'

# 1) Where the app looks for updates: our own repository (the original YPtun repo would offer its own app)
repo = os.environ.get('REPO', '')
p = base + 'commonMain/kotlin/org/olcbox/app/update/AppUpdateService.kt'
s = open(p, encoding='utf-8').read()
old = 'repositoryUrl = "https://github.com/yanisplugg/yptun"'
if repo and old in s:
    open(p, 'w', encoding='utf-8').write(s.replace(old, 'repositoryUrl = "https://github.com/' + repo + '"'))
    print('update source ->', repo)

# 2) Settings sheet
p = base + 'androidMain/kotlin/org/olcbox/app/ui/activities/AndroidAppSettingsSheets.kt'
s = open(p, encoding='utf-8').read()

# 2a) dynamic / light theme switches and the colour pickers
a = s.find('        SettingsSwitchRow(\n            title = s.dynamicTheme,')
end_marker = 'onBackgroundColorSelected = onBackgroundColorSelected\n            )\n        }\n'
b = s.find(end_marker, a) if a != -1 else -1
if a != -1 and b != -1:
    s = s[:a] + s[b + len(end_marker):]
    print('theme switches and pickers removed')

# 2b) foreign community links and the donation block -> one support row
a = s.find('        val communityUriHandler = LocalUriHandler.current\n        SettingsGroupCard {')
end_marker = '            onOpenUrl = { it -> communityUriHandler.openUri(it) }\n        )\n'
b = s.find(end_marker, a) if a != -1 else -1
if a != -1 and b != -1:
    support = (
        '        val communityUriHandler = LocalUriHandler.current\n'
        '        SettingsGroupCard {\n'
        '            SettingsGroupRow(\n'
        '                title = "Поддержка",\n'
        '                subtitle = "Telegram",\n'
        '                icon = Icons.Rounded.Person,\n'
        '                enabled = true,\n'
        '                onClick = { communityUriHandler.openUri("https://t.me/valhalla_my_bot") }\n'
        '            )\n'
        '        }\n'
    )
    s = s[:a] + support + s[b + len(end_marker):]
    print('community and donation blocks replaced')
open(p, 'w', encoding='utf-8').write(s)
PY
echo "patch 2 done"
