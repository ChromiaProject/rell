# Release Notes Guide

**Note**: The formatting conventions described below are guidelines, not strict rules. Use judgment to choose the most appropriate formatting for the content. The goal is clarity and consistency within each release notes file.

## File Naming and Header Conventions

### File Naming

Release notes files are stored in `doc/release-notes/` and follow this naming pattern:

- **Major/Minor releases**: `{MAJOR}.{MINOR}.{PATCH}.txt` (e.g., `0.14.0.txt`, `0.13.5.txt`)
- **Development version**: `dev.txt` (for unreleased changes)

### Header Format

Every release notes file starts with a standard header:

```
RELEASE NOTES {VERSION} ({YYYY-MM-DD})
UNRELEASED NOTES (for dev.txt)
```

## Section Organization and Categories

Each section is separated by a line of @ symbols:

```
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
```

### Standard Categories

Sections are numbered and categorized with standard prefixes:

- **Language**: Core language features, syntax changes, new constructs
    - Example: `1. Language: Null analysis of complex expressions`

- **Library**: Standard library additions, changes to built-in functions
    - Example: `2. Library: function try_call()`

- **Tools**: CLI tools, build system, development utilities
    - Example: `3. Tools: multirun.sh --sqllog`

- **Runtime**: Runtime behavior, execution engine changes
    - Example: `4. Runtime: Default parameter values in operations`

- **Compiler**: Compiler improvements, error messages, performance
    - Example: `5. Compiler: Not stopping on first compilation error`

- **Docs**: Documentation system, doc comments, help text
    - Example: `6. Docs: New comment tags`

- **Tests**: Unit testing framework, test utilities
    - Example: `7. Tests: Function-level @test annotation`

- **API**: Public API changes, integration points
    - Example: `8. API: Functions to check the validity of Rell tokens`

## Formatting Conventions

### Code Examples

**Indentation**: Use 4 spaces for code blocks

```
    val v: my_type? = get_v();
    if (v != null) {
        // compiler knows that "v" is not nullable here
    }
```

### Tables

Use Unicode box drawing for tables:

```
    ┌───────────┬───────────────────────────────┐
    │ Specifier │ Meaning                       │
    ├───────────┼───────────────────────────────┤
    │ y         │ Year                          │
    │ M         │ Month in the year             │
    └───────────┴───────────────────────────────┘
```

### Lists

**Bulleted lists**: Use `-` for main points:

```
- A value can have up to 131072 decimal digits
- Literals have suffix "L": 123L, 0x123L
- Uses Java class java.math.BigInteger internally
```

**Numbered lists**: Use Arabic numerals for sequential points or detailed explanations:

```
1. If a new key is added and the values of its attributes in the database aren't unique, database initialization fails.
2. Adding a key or index may be slow for big tables.
3. Adding new key or index attributes to an entity is supported too.
```

**Sub-items**: Use letters for sub-categorization:

```
(a) Constants:

    big_integer.PRECISION: integer

        Maximum number of decimal digits (131072).

(b) Constructors:

    big_integer(integer): big_integer

        Creates a big_integer from integer.
```

### Breaking Changes

Always include compatibility warnings:

```
Note. This is a breaking change - it may break compilation of existing code, because types of some expressions may change from nullable to not nullable. Applications deployed with older versions of Rell will not be affected (thanks to the backward compatibility mode).
```

### Deprecation Notices

Format deprecated features clearly:

```
New @return tag is to be used instead of the old @returns tag, which is now deprecated.
```

### Bug Fixes

For significant bug fixes, explain the impact:

```
14. Bug fixes

(1) False "Wrong operand types..." compilation error on expression: T in list<T?>.
(2) Conversion from gtv big integer value to decimal.
```

## Review Checklist

Before publishing new release's notes, verify:

- [ ] Header format is correct with proper date
- [ ] Categories are appropriate and consistently formatted
- [ ] Code examples are properly indented with 4 spaces
- [ ] Breaking changes include compatibility warnings
- [ ] Examples are tested and work correctly
- [ ] Grammar and spelling are correct
- [ ] Cross-references to other sections are accurate
