import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

reports = Path(sys.argv[1])
index = json.loads(Path('src/main/resources/META-INF/neofabric/apis/index.json').read_text(encoding='utf-8'))
total = 0
rows = []
for group in ['core', *index['apis']]:
    files = list((reports / ('NeoFabric-tests-' + group)).rglob('TEST-*.xml'))
    counts = [0, 0, 0, 0]
    for report in files:
        suite = ET.parse(report).getroot()
        counts = [a + int(suite.get(key, 0)) for a, key in zip(counts, ('tests', 'failures', 'errors', 'skipped'))]
    if counts[0] == 0 or any(counts[1:]):
        raise SystemExit(f'Missing or failing regression group {group}: {counts}')
    total += counts[0]
    java = '-' if group == 'core' else (17 if group in ('1.20.1', '1.20.2', '1.20.3', '1.20.4') else 21)
    rows.append(f'| {group} | {java} | {counts[0]} |')
summary = '\n'.join(['| Minecraft / unit group | Java | Tests |', '| --- | --- | --- |', *rows,
                     '', f'Total: {total} tests, 0 failures, 0 errors, 0 skipped.'])
print(summary)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as out:
        out.write(summary + '\n')
