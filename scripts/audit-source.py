#!/usr/bin/env python3
"""High-confidence source checks; prints locations, never matched secret values.

This is a publication hygiene check, not a replacement for a full secret scanner.
Use --history before first publication; it examines reachable local Git history.
"""
import argparse
import hashlib
import pathlib
import re
import subprocess
import sys
from urllib.parse import parse_qsl, urlsplit

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEXT = {'.java', '.cpp', '.h', '.gradle', '.properties', '.xml', '.json', '.md', '.txt', '.yml', '.yaml', '.py', '.sh'}
SECRET = re.compile(r'-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[A-Z0-9]{16}|AIza[0-9A-Za-z_-]{35}')
URL = re.compile(r'https?://[^\s<>\"\x27]+')
PRIVATE_NAMES = {'.env', 'local.properties'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.p12', '.pem', '.apk', '.aab'}

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

def inspect(path, data, label):
    if path.startswith('third_party/') or pathlib.Path(path).suffix.lower() not in TEXT:
        return []
    try:
        content = data.decode('utf-8')
    except UnicodeDecodeError:
        return []
    problems = []
    for line_number, line in enumerate(content.splitlines(), 1):
        reason = None
        if SECRET.search(line):
            reason = 'possible secret/private key'
        for match in URL.finditer(line):
            try:
                url = urlsplit(match.group())
                host = (url.hostname or '').lower()
                if host in {'localhost', '127.0.0.1', 'fixture'} or host.endswith(('.test', '.invalid')) or host in {'example.com', 'example.org', 'example.net'} or host.endswith(('.example.com', '.example.org', '.example.net')):
                    continue
                if url.password or any(k.lower() in {'password', 'passwd', 'api_key', 'access_token'}
                                       and v and not any(c in v for c in '${}') for k, v in parse_qsl(url.query)):
                    reason = 'possible credentials in URL'
            except ValueError:
                continue
        if reason:
            problems.append(f'{label}:{line_number}: {reason}')
    return problems

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--history', action='store_true')
    args = parser.parse_args()
    problems = []
    # Official Gradle 8.13 wrapper JAR checksum, verified against services.gradle.org.
    wrapper = ROOT / 'gradle/wrapper/gradle-wrapper.jar'
    if hashlib.sha256(wrapper.read_bytes()).hexdigest() != '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f':
        problems.append('gradle/wrapper/gradle-wrapper.jar: checksum differs from pinned Gradle 8.13')
    names = git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0')
    for name in filter(None, names):
        path = ROOT / name
        if pathlib.Path(name).name in PRIVATE_NAMES or path.suffix.lower() in PRIVATE_SUFFIXES:
            problems.append(f'{name}: private/local artifact must not be published')
        if path.is_file() and path.stat().st_size < 2_000_000:
            problems.extend(inspect(name, path.read_bytes(), name))
    if args.history:
        objects = git('rev-list', '--objects', '--all').decode().splitlines()
        for entry in objects:
            fields = entry.split(' ', 1)
            if len(fields) != 2:
                continue
            oid, name = fields
            if pathlib.Path(name).name in PRIVATE_NAMES or pathlib.Path(name).suffix.lower() in PRIVATE_SUFFIXES:
                problems.append(f'history {oid[:12]} {name}: private/local artifact')
            if name.startswith('third_party/') or pathlib.Path(name).suffix.lower() not in TEXT:
                continue
            if git('cat-file', '-t', oid).strip() != b'blob' or int(git('cat-file', '-s', oid)) > 2_000_000:
                continue
            problems.extend(inspect(name, git('cat-file', 'blob', oid), f'history {oid[:12]} {name}'))
    for problem in sorted(set(problems)):
        print(problem)
    print(f'Source hygiene: {len(set(problems))} finding(s)' + (' including reachable history.' if args.history else '.'))
    return 1 if problems else 0

if __name__ == '__main__':
    sys.exit(main())
