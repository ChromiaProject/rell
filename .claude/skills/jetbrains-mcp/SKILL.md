---
name: jetbrains-mcp
description: Use when working with Kotlin code in this repo (rell-base, rell-toolbox, rell-codegen, rell-gtx, rell-tools, etc.) — symbol lookup, references, diagnostics, refactors, build/run, debugger. Lists the IntelliJ MCP tool catalog and the workflow patterns that replace grep/glob for `.kt` work. Skip for worktrees — the IDE indexes only the main checkout, so use Kotlin LSP there instead.
---

# JetBrains IntelliJ MCP — Tool Selection for Rell

The IntelliJ index gives symbol identity, inheritance graph, type resolution, module boundaries (excludes `build/` and generated sources), live diagnostics, and safe refactoring. Grep is unreliable at this repo's scale — pick the IntelliJ MCP tool that matches the question.

**Precondition:** an IDEA instance with the `rell` project open and the JetBrains MCP plugin reachable. If not — for example in a worktree, on CI, or when no IDE is running — fall back to the Kotlin LSP tool (`LSP` against `kotlin-lsp` on PATH). Never fall back to grep/glob for Kotlin symbol work.

## Tool catalog

**Symbol search** (replaces grep for code elements):
- `search_symbol` — find class/function/field by name. First choice.
- `get_symbol_info` — full info at file offset. Follow-up to `search_symbol`.

**Text search** (string literals in source):
- `search_in_files_by_text` / `search_in_files_by_regex`

**File discovery** (replaces glob):
- `find_files_by_glob`, `find_files_by_name_keyword`, `search_file`, `list_directory_tree`

**File read/edit** (keeps IDE index fresh):
- `get_file_text_by_path` / `read_file` — read (sees unsaved buffers)
- `replace_text_in_file` — edit with immediate reindex
- `create_new_file`, `reformat_file`, `open_file_in_editor`, `get_all_open_file_paths`

**Diagnostics:**
- `get_file_problems` — errors/warnings/inspections, faster than `./gradlew`

**Refactoring:**
- `rename_refactoring` — semantic rename (the only correct way to rename Kotlin symbols)

**Build & run:**
- `build_project` — quick sanity (not CI-authoritative)
- `get_run_configurations` / `execute_run_configuration`

**Project structure:**
- `get_project_modules` — Gradle module list
- `get_project_dependencies` — dep graph (check before cross-module refactors)

**Database:**
- `list_database_connections`, `list_database_schemas`, `list_schema_objects`, `execute_sql_query`, etc.

Built-in `Read`/`Edit`/`Write` are fine for markdown, scripts, SQL, and `doc/release-notes/` — the `edit_guard.py` hook only nudges on `.kt`/`.kts` paths (prefer `replace_text_in_file`; fall back to LSP or built-in Edit when IntelliJ MCP is unavailable).

## Workflow patterns

1. **Find definition** → `search_symbol` + `get_symbol_info` (not grep)
2. **Find implementors** → `search_symbol` on interface, then `get_symbol_info` or `search_in_files_by_text` for `: InterfaceName`
3. **Find callers** → `search_symbol` for decl + `search_in_files_by_text` for call name
4. **Verify edit** → `get_file_problems` on touched files (Stop hook also runs this automatically)
5. **Read open file** → `get_file_text_by_path` for live buffer state
6. **Orient** → `get_all_open_file_paths` + `get_project_modules`

## What stays with Bash/Grep/Glob

Git ops, plain-text trees (`doc/`, `work/`, `*.md`, `*.sh`, `*.sql`), files outside project root, shell pipelines, image/PDF reads.
