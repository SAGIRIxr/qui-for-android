"""Validate release identity before signing or publishing (no credentials needed)."""
import argparse
import re
import subprocess
from pathlib import Path


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def validate(root, tag):
    if not re.fullmatch(r'v\d+\.\d+\.\d+', tag):
        raise ValueError('Release tag must be vMAJOR.MINOR.PATCH')
    config = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
    version = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
    if tag != f'v{version}':
        raise ValueError('Tag and APK versionName differ')
    if git(root, 'rev-parse', f'{tag}^{{commit}}') != git(root, 'rev-parse', 'HEAD'):
        raise ValueError('Checkout does not match release tag')
    for name in ('CHANGELOG.md', 'CHANGELOG.zh-CN.md'):
        text = (root / name).read_text(encoding='utf-8')
        section = re.search(rf'^## {re.escape(version)}\s*\n(.*?)(?=^## |\Z)', text, re.M | re.S)
        if not section or not re.search(r'^- \S', section.group(1), re.M):
            raise ValueError(f'{name} has no release notes for {version}')
    # All older reachable releases must have a lower Android versionCode.
    for previous in git(root, 'tag', '--merged', 'HEAD').splitlines():
        if previous == tag or not re.fullmatch(r'v\d+\.\d+\.\d+', previous):
            continue
        old = git(root, 'show', f'{previous}:app/build.gradle.kts')
        old_code = int(re.search(r'versionCode\s*=\s*(\d+)', old).group(1))
        if code <= old_code:
            raise ValueError(f'versionCode must exceed {previous} ({old_code})')
    return version, code


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--tag', required=True)
    args = parser.parse_args()
    version, code = validate(Path(__file__).resolve().parent.parent, args.tag)
    print(f'Release verified: {version} ({code})')
