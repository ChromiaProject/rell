#!/usr/bin/env python3
"""Stop hook: run ./gradlew apiCheck when sources of ABI-checked modules changed.

ABI-checked modules are detected from checked-in api/*.api dumps. Results are cached
per working-tree state in .git/claude-apicheck-ok so apiCheck reruns only when the
relevant sources change again. On failure the stop is blocked with instructions.
"""
import hashlib
import json
import os
import subprocess
import sys

REPO = os.environ.get('CLAUDE_PROJECT_DIR') or os.getcwd()


def git(*args, timeout=15):
    r = subprocess.run(['git', *args], capture_output=True, text=True, cwd=REPO, timeout=timeout)
    return r.stdout


def state_path():
    # In a worktree .git is a file, so ask git for the real per-worktree git dir.
    git_dir = git('rev-parse', '--absolute-git-dir').strip() or os.path.join(REPO, '.git')
    return os.path.join(git_dir, 'claude-apicheck-ok')


def relevant_files():
    dumps = git('ls-files', '--', '*.api').splitlines()
    modules = sorted({d.rsplit('/api/', 1)[0] for d in dumps if '/api/' in d})
    changed = git('diff', '--name-only', 'HEAD').splitlines()
    changed += git('ls-files', '--others', '--exclude-standard').splitlines()
    return [
        f for f in changed
        if f.endswith(('.kt', '.kts', '.api')) and any(f.startswith(m + '/') for m in modules)
    ]


def digest_of(files):
    h = hashlib.sha256()
    h.update(git('diff', 'HEAD', '--', *files, timeout=30).encode())
    for f in sorted(files):
        path = os.path.join(REPO, f)
        try:
            with open(path, 'rb') as fh:
                h.update(fh.read())
        except OSError:
            h.update(f.encode())
    return h.hexdigest()


def main():
    try:
        hook_input = json.load(sys.stdin)
    except Exception:
        hook_input = {}

    try:
        files = relevant_files()
    except Exception:
        return
    if not files:
        return

    digest = digest_of(files)
    state = state_path()
    try:
        with open(state) as f:
            if f.read().strip() == digest:
                return
    except OSError:
        pass

    try:
        r = subprocess.run(
            ['./gradlew', 'apiCheck', '-q'],
            capture_output=True, text=True, cwd=REPO, timeout=280,
        )
    except subprocess.TimeoutExpired:
        print(json.dumps({'systemMessage':
            'apiCheck hook timed out. ABI-checked module sources changed: run `./gradlew apiCheck` '
            'yourself and make it pass before finishing (on an intentional API change: `./gradlew apiDump` '
            'and review the *.api diff).'}))
        return

    if r.returncode == 0:
        try:
            with open(state, 'w') as f:
                f.write(digest)
        except OSError:
            pass
        return

    tail = '\n'.join((r.stdout + r.stderr).splitlines()[-30:])
    reason = (
        'ABI check failed: sources of ABI-checked modules changed and `./gradlew apiCheck` does not pass.\n'
        'If the public-API change is intentional, run `./gradlew apiDump`, review the resulting *.api diff, '
        'and include it in the change. Otherwise revert the accidental public-API change '
        '(e.g. make the declaration internal or keep the old signature).\n\n'
        'apiCheck output tail:\n' + tail
    )
    if hook_input.get('stop_hook_active'):
        # Already continuing because of a stop hook - warn instead of blocking to avoid a loop.
        print(json.dumps({'systemMessage': reason}))
    else:
        print(json.dumps({'decision': 'block', 'reason': reason}))


if __name__ == '__main__':
    main()
