#!/usr/bin/env python3
"""Check release APK contents and 64-bit ELF alignment without third-party packages."""
import argparse
import pathlib
import struct
import subprocess
import sys
import zipfile

ABIS = {'arm64-v8a', 'armeabi-v7a', 'x86_64'}
LICENSES = {'NOTICE.txt', 'apache-2.0.txt', 'gpl-3.0.txt', 'manrope_ofl.txt',
            'nes-ntsc-lgpl-2.1.txt', 'nestopia-gpl-2.0.txt', 'vendored-native-notices.txt',
            'ndk-r28-runtime-notices.txt', 'runtime-dependencies.txt'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=pathlib.Path)
    parser.add_argument('--zipalign', help='Path to Android SDK Build Tools zipalign')
    args = parser.parse_args()
    errors = []
    with zipfile.ZipFile(args.apk) as apk:
        names = set(apk.namelist())
        for abi in sorted(ABIS):
            for library in ['libretro.so', 'libsva_nes.so']:
                name = f'lib/{abi}/{library}'
                if name not in names:
                    errors.append(f'Missing {name}')
        for license_name in LICENSES:
            if f'assets/licenses/{license_name}' not in names:
                errors.append(f'Missing license {license_name}')
        for name in sorted(names):
            if name.lower().endswith(('.nes', '.jks', '.keystore', '.p12', '.pem', '.env')):
                errors.append(f'Unexpected private/game artifact: {name}')
            if not name.endswith('.so') or not name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')):
                continue
            data = apk.read(name)
            if data[:5] != b'\x7fELF\x02':
                errors.append(f'Invalid 64-bit ELF: {name}')
                continue
            order = '<' if data[5] == 1 else '>'
            phoff = struct.unpack_from(order+'Q',data,32)[0]
            size,count = struct.unpack_from(order+'HH',data,54)
            alignments = []
            for i in range(count):
                off = phoff+i*size
                kind = struct.unpack_from(order+'I',data,off)[0]
                if kind == 1:
                    alignment = struct.unpack_from(order+'Q',data,off+48)[0]
                    alignments.append(alignment)
                    if alignment < 16384: errors.append(f'4 KB-only load segment: {name}')
                elif kind == 0x6474e552:
                    address = struct.unpack_from(order+'Q',data,off+16)[0]
                    length = struct.unpack_from(order+'Q',data,off+40)[0]
                    if (address+length)%16384: errors.append(f'Unaligned RELRO: {name}')
            if not alignments: errors.append(f'No load segments: {name}')
            print(f'{name}: load alignment {alignments}')
    if args.zipalign:
        result = subprocess.run([args.zipalign,'-c','-P','16','4',str(args.apk)])
        if result.returncode: errors.append('APK ZIP alignment failed')
    for error in errors:
        print(error, file=sys.stderr)
    print(f'APK checks: {len(errors)} finding(s).')
    return 1 if errors else 0

if __name__ == '__main__':
    sys.exit(main())
