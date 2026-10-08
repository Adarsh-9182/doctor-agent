#!/usr/bin/env python3
"""Acquire the pinned Apache-licensed starter weights; never run inference."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'models/starter-model.json').read_text())
folder = root / 'dist/models'
folder.mkdir(parents=True, exist_ok=True)
target = folder / manifest['filename']

def verified(path):
    if not path.is_file() or path.stat().st_size != manifest['bytes']:
        return False
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest() == manifest['sha256']

if verified(target):
    print(f'Pinned starter model already available: {target}')
else:
    if shutil.disk_usage(folder).free < manifest['bytes'] + 64 * 1024 * 1024:
        raise SystemExit('Not enough free disk space for the starter model.')
    print(f"Downloading {manifest['repository']} ({manifest['license']}); see {manifest['model_card']}")
    partial = target.with_suffix('.litertlm.download')
    try:
        subprocess.run(['curl', '-sS', '--fail', '--location', '--retry', '2', manifest['url'], '-o', str(partial)], check=True)
        if not verified(partial):
            raise SystemExit('Starter model size/checksum mismatch; previous file was kept.')
        partial.replace(target)
    finally:
        partial.unlink(missing_ok=True)
    print(f'Verified starter model: {target}')
