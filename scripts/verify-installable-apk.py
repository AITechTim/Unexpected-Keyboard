#!/usr/bin/env python3
"""Reject test-only/split artifacts before distributing a signed debug update."""
import argparse
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--build-tools', type=Path, required=True)
parser.add_argument('--version-name', required=True)
parser.add_argument('--version-code', required=True)
parser.add_argument('--certificate', required=True)
a = parser.parse_args()

def run(tool, *args):
    return subprocess.check_output([str(a.build_tools / tool), *map(str, args)], text=True)

manifest = run('aapt', 'dump', 'xmltree', a.apk, 'AndroidManifest.xml')
assert not re.search(r'android:testOnly[^\n]*=\(type 0x12\)0x(?:ffffffff|1)\b', manifest), 'testOnly APK cannot be installed through the phone installer'
assert not re.search(r'^\s+A: split=', manifest, re.M), 'Distribute a standalone APK'
badging = run('aapt', 'dump', 'badging', a.apk)
assert "package: name='juloo.keyboard2.debug'" in badging
assert f"versionCode='{a.version_code}'" in badging
assert f"versionName='{a.version_name}'" in badging
assert "native-code: 'arm64-v8a'" in badging
signature = run('apksigner', 'verify', '--print-certs', a.apk)
assert f'certificate SHA-256 digest: {a.certificate}' in signature
run('zipalign', '-c', '-P', '16', '4', a.apk)
print(json.dumps({'test_only': False, 'standalone': True, 'signature_verified': True,
                  'alignment_verified': True, 'version_name': a.version_name,
                  'version_code': int(a.version_code)}))
