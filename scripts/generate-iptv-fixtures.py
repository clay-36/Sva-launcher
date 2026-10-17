#!/usr/bin/env python3
"""Generate synthetic IPTV test assets. Requires FFmpeg and Shaka Packager; never runs during app builds."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--packager', required=True)
args = parser.parse_args()
packager = str(Path(args.packager).resolve())
target = Path(__file__).resolve().parents[1] / 'app/src/androidTest/assets/iptv'
target.mkdir(parents=True, exist_ok=True)


def ffmpeg(*options):
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', *map(str, options)], check=True)


with tempfile.TemporaryDirectory(prefix='sva-iptv-') as workspace:
    base = target / 'sample.mp4'
    ffmpeg('-f', 'lavfi', '-i', 'testsrc2=size=160x90:rate=15', '-t', '4', '-an',
           '-c:v', 'libx264', '-profile:v', 'baseline', '-level:v', '3.0', '-pix_fmt', 'yuv420p',
           '-g', '15', '-keyint_min', '15', '-sc_threshold', '0', '-map_metadata', '-1',
           '-fflags', '+bitexact', '-flags:v', '+bitexact', '-movflags', '+faststart', base)
    ffmpeg('-i', base, '-c', 'copy', '-f', 'mpegts', target / 'sample.ts')
    ffmpeg('-i', base, '-c', 'copy', '-hls_time', '2', '-hls_list_size', '0',
           '-hls_segment_filename', target / 'plain-%d.ts', target / 'plain.m3u8')
    key = target / 'key.bin'
    key.write_bytes(bytes.fromhex('00112233445566778899aabbccddeeff'))
    key_info = Path(workspace) / 'key-info.txt'
    key_info.write_text(f'key.bin\n{key}\n00000000000000000000000000000000\n')
    ffmpeg('-i', base, '-c', 'copy', '-hls_time', '2', '-hls_list_size', '0',
           '-hls_key_info_file', key_info, '-hls_segment_filename', target / 'encrypted-%d.ts',
           target / 'encrypted.m3u8')
    for encrypted in [False, True]:
        prefix = 'ck' if encrypted else 'dash'
        command = [packager,
                   f'in={base},stream=video,drm_label=TEST,init_segment={prefix}-init.mp4,segment_template={prefix}-$Number$.m4s',
                   '--segment_duration', '2', '--mpd_output', f'{prefix}.mpd', '--generate_static_live_mpd']
        if encrypted:
            command += ['--enable_raw_key_encryption', '--clear_lead', '0',
                        '--keys', 'label=TEST:key_id=000102030405060708090a0b0c0d0e0f:key=00112233445566778899aabbccddeeff',
                        '--iv', '000102030405060708090a0b0c0d0e0f', '--protection_systems', 'CommonSystem']
        subprocess.run(command, cwd=target, check=True)
print('Generated synthetic test media in', target)
