#!/usr/bin/env python3
"""Refresh bundled source notices; --check verifies them without writing files."""
import argparse
import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / 'app/src/main/assets/licenses'

def expected_files():
    result = {
        OUT/'NOTICE.txt': (ROOT/'NOTICE').read_text(),
        OUT/'gpl-3.0.txt': (ROOT/'LICENSE').read_text(),
        OUT/'nestopia-gpl-2.0.txt': (ROOT/'third_party/nestopia/COPYING').read_text(),
        OUT/'nes-ntsc-lgpl-2.1.txt': (ROOT/'third_party/nestopia/source/nes_ntsc/license.txt').read_text(),
    }
    notices = {}
    for path in sorted((ROOT/'third_party/nestopia').rglob('*')):
        if path.suffix not in {'.h','.hpp','.c','.cpp','.inl'}:
            continue
        source = path.read_text(errors='replace')[:8000]
        for match in re.finditer(r'/\*.*?\*/',source,re.S):
            block = match.group()
            if not re.search(r'copyright|SPDX-License-Identifier|permission is hereby granted',block,re.I):
                continue
            notices.setdefault(block,[]).append(str(path.relative_to(ROOT)))
    text = 'Vendored native source notices\nOptional source components may also be listed. Original notices are retained below.\n'
    for block,paths in notices.items():
        text += '\n'+'='*72+'\n'+'\n'.join(paths)+'\n\n'+block+'\n'
    result[OUT/'vendored-native-notices.txt'] = text
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if sdk:
        notice = pathlib.Path(sdk)/'ndk/28.2.13676358/NOTICE'
        if notice.is_file(): result[OUT/'ndk-r28-runtime-notices.txt'] = notice.read_text()
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    failed=[]
    for path,text in expected_files().items():
        if args.check:
            if not path.is_file() or path.read_text()!=text: failed.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True); path.write_text(text)
    for path in failed: print('Outdated notice: '+path)
    print('Bundled notices '+('verified.' if args.check and not failed else 'updated.' if not args.check else 'need updating.'))
    return 1 if failed else 0

if __name__=='__main__': sys.exit(main())
