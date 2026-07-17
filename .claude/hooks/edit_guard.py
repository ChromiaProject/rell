#!/usr/bin/env python3
"""PreToolUse hook for Edit/Write/MultiEdit: route .kt edits through IntelliJ MCP."""
import sys, json

def main():
    try:
        d = json.load(sys.stdin)
    except Exception:
        return
    ti = d.get('tool_input', {})
    p = ti.get('file_path', '')
    is_kt = p.endswith('.kt') or p.endswith('.kts')
    if not is_kt:
        return
    if ti.get('replace_all', False):
        msg = ('CLAUDE.md: renames on .kt should use mcp__IntelliJ__rename_refactoring '
               '(or LSP rename in worktrees / when IntelliJ MCP is unavailable). '
               'Edit replace_all is a last resort — verify call sites manually.')
    else:
        msg = ('CLAUDE.md: prefer mcp__IntelliJ__replace_text_in_file for .kt edits to keep '
               'the IDE index fresh. If IntelliJ MCP is unavailable (worktree, no IDE running, '
               'plugin down), use Kotlin LSP or fall back to built-in Edit/Write — do not block on this.')
    print(json.dumps({
        'hookSpecificOutput': {
            'hookEventName': 'PreToolUse',
            'additionalContext': msg,
        }
    }))

if __name__ == '__main__':
    main()
