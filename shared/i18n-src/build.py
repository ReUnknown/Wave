#!/usr/bin/env python3
"""Turns shared/i18n-src/<lang>.txt ("English ⟶ Translation" per line) into the app's tables
(shared/src/commonMain/kotlin/app/wave/music/i18n/<Lang>.kt). Run it after editing a .txt file:

    python3 shared/i18n-src/build.py

It warns when a line's placeholders ({0}, {1}...) don't match, and lists English text that isn't
translated yet (from keys.txt, if present)."""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, '..', 'src', 'commonMain', 'kotlin', 'app', 'wave', 'music', 'i18n')
names = {'es': 'Es', 'pt': 'Pt', 'fr': 'Fr', 'de': 'De'}
keys = set()
kp = os.path.join(here, 'keys.txt')
if os.path.exists(kp):
    keys = {l for l in open(kp, encoding='utf-8').read().split('\n') if l.strip()}
for code, obj in names.items():
    path = os.path.join(here, code + '.txt')
    if not os.path.exists(path):
        continue
    rows = []
    for n, line in enumerate(open(path, encoding='utf-8').read().split('\n'), 1):
        if not line.strip() or line.startswith('#'):
            continue
        if ' ⟶ ' not in line:
            print(f'{code}:{n}: no ⟶'); continue
        en, tr = line.split(' ⟶ ', 1)
        if sorted(re.findall(r'\{\d+\}', en)) != sorted(re.findall(r'\{\d+\}', tr)):
            print(f'{code}:{n}: placeholders differ: {en!r}')
        rows.append(en.replace('\t', ' ') + '\t' + tr.replace('\t', ' '))
    if keys:
        have = {r.split('\t')[0] for r in rows}
        missing = [k for k in keys if k.strip() not in {h.strip() for h in have}]
        print(f'{code}: {len(rows)} lines, {len(missing)} app strings without a line')
    # Raw strings, in pieces small enough for a class file's constants; $ can't appear raw.
    chunks, cur = [], ''
    for r in rows:
        r = r.replace('$', "${'$'}")
        if len(cur) + len(r) > 20000:
            chunks.append(cur); cur = ''
        cur += r + '\n'
    if cur:
        chunks.append(cur)
    body = ',\n'.join('        """' + c + '"""' for c in chunks)
    open(os.path.join(out, obj + '.kt'), 'w', encoding='utf-8').write(
        f'// Made by shared/i18n-src/build.py from {code}.txt. Edit that file, not this one.\n'
        f'package app.wave.music.i18n\n\ninternal object {obj} {{\n    val text: String get() = listOf(\n{body},\n    ).joinToString("")\n}}\n')
