from pathlib import Path

readme = Path('README.md').read_text(encoding='utf-8')
title = next(line for line in readme.splitlines() if line.strip())
if 'NeoFabric' not in title or 'An Installer for Fabric' in readme[:500]:
    raise SystemExit('README title and introduction must use NeoFabric branding before release.')
print('NeoFabric README branding verified.')
