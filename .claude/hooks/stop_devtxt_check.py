#!/usr/bin/env python3
"""Stop hook: nag about dev.txt + ./gradlew check when user-facing Kotlin source changed."""
import subprocess, json, os

REPO = os.environ.get('CLAUDE_PROJECT_DIR') or os.getcwd()
USER_FACING_DIRS = (
    'rell-base/utils/',
    'rell-base/frontend/',
    'rell-base/rr-tree/',
    'rell-base/rr-serialization/',
    'rell-base/runtime-core/',
    'rell-base/runtime-interpreter/',
    'rell-base/runtime-truffle/',
    'rell-api-base/',
    'rell-api-gtx/',
    'rell-api-native/',
    'rell-api-shell/',
    'rell-gtx/',
    'rell-tools/',
    'rell-toolbox/',
    'rell-codegen/',
)

def main():
    try:
        r = subprocess.run(
            ['git', 'diff', '--name-only', 'HEAD'],
            capture_output=True, text=True, cwd=REPO, timeout=5,
        )
    except Exception:
        return
    files = [f for f in r.stdout.splitlines() if f]
    if not files:
        return
    is_kt = lambda f: f.endswith('.kt') or f.endswith('.kts')
    user_facing = any(any(f.startswith(d) for d in USER_FACING_DIRS) and is_kt(f) for f in files)
    devtxt = 'doc/release-notes/dev.txt' in files
    any_kt = any(is_kt(f) for f in files)

    cheap = ('Prefer cheap checks first: mcp__IntelliJ__get_file_problems on touched files, then '
             '`./gradlew :<module>:compileKotlin :<module>:compileTestKotlin` or '
             '`./gradlew assemble` (skips tests). Run targeted `:<module>:test` for affected modules. '
             'Reserve full `./gradlew check` for final verification or when ABI/coverage matters.')
    if user_facing and not devtxt:
        msg = ('REMINDER: User-facing Kotlin source changed but doc/release-notes/dev.txt was not '
               'updated. Verify whether a release note is needed; if it is, '
               'invoke the `write-release-notes` skill (Skill tool, skill="write-release-notes") to '
               'follow the project conventions in doc/release-notes-guide.md. ' + cheap)
    elif devtxt:
        msg = ('REMINDER: doc/release-notes/dev.txt was edited. Verify the entry follows the project '
               'conventions by invoking the `write-release-notes` skill (Skill tool, '
               'skill="write-release-notes") — formatting, category prefix, and review checklist.')
    elif any_kt:
        msg = 'REMINDER: Kotlin sources modified. ' + cheap
    else:
        return

    print(json.dumps({'systemMessage': msg}))

if __name__ == '__main__':
    main()
