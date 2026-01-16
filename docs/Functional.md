# Functional Overview

## High-Level Features

Rell Dokka Plugin provides documentation generation capabilities through a pipeline-based architecture:

### 1. Rell Source Code Analysis

Analyzes Rell source code to extract documentation:

- **Module Analysis** - Identifies and processes Rell modules, including entry point modules and additional modules
- **Function Documentation** - Extracts function definitions, parameters, return types, and doc comments
- **Type Documentation** - Documents type definitions, structs, enums, and objects
- **Operation and Query Documentation** - Documents blockchain operations and queries
- **Constant Documentation** - Documents module-level constants
- **Extension Function Detection** - Identifies and documents extension functions using reflection
- **Module Hierarchy** - Builds package/module structure from Rell module organization

### 2. System Library Documentation

Generates complete API reference for Rell standard library:

- **System Module Documentation** - Documents all standard library modules (rell, rell.test, etc.)
- **Type Definitions** - Documents system types, structs, and aliases
- **Function Reference** - Documents all system library functions
- **Special Functions** - Documents language-level special functions
- **Property Documentation** - Documents system properties
- **Namespace Organization** - Organizes system library by namespace/package

### 3. Documentation Generation

Produces HTML documentation sites with:

- **HTML Output** - Generates complete HTML documentation site
- **Navigation Structure** - Creates hierarchical navigation based on module/package structure
- **Search Functionality** - Provides searchable documentation with symbol indexing
- **Code Examples** - Renders code examples from doc comments
- **Cross-References** - Generates links between related documentation elements
- **Custom Styling** - Applies Chromia branding and custom CSS
- **Source Links** - Optional links to source code repositories

### 4. Module Filtering and Configuration

Provides flexible documentation generation options:

- **Module Filtering** - Excludes specific modules from navigation (while keeping them in documentation)
- **Additional Modules** - Includes additional modules that would otherwise be filtered
- **Entry Point Selection** - Specifies which modules to document as entry points
- **Test Module Handling** - Separates test modules from main documentation
- **Custom Assets** - Allows injection of custom CSS and JavaScript files

## Primary User/System Flows

### Standard Documentation Generation Flow

**If a user wants to generate documentation for a Rell project:**

1. User invokes CLI with source directory and target output directory
2. CLI parses arguments and builds `RellDokkaPluginConfigurationBuilder`
3. Configuration is created with module names, source paths, and options
4. `RellDokkaGenerator` initializes Dokka with configuration
5. Dokka framework initializes plugin system
6. `RellDokkaPlugin` registers custom extensions at pipeline stages
7. `RellSourceToDocumentableTranslator` is invoked for source analysis
8. `RellAnalysis` compiles Rell source code using Rell compiler API
9. `RellModuleVisitor` traverses compiled modules and creates Documentables
10. Documentables are processed through transformation stages
11. `RellDocumentableToPageTranslator` converts Documentables to Page models
12. `RellHtmlRenderer` renders pages to HTML
13. Custom assets (CSS, JS, images) are installed
14. Navigation structure is generated
15. HTML files are written to target directory

**If source code has compilation errors:**
- Rell compiler API reports errors
- Errors are logged through `RellCliEnv`
- Documentation generation may fail or produce incomplete documentation depending on error severity
- User must fix compilation errors before generating complete documentation

**If modules are not found:**
- Plugin attempts to compile specified modules
- If modules don't exist or can't be found, compilation fails
- Error messages indicate which modules are missing

### System Library Documentation Generation Flow

**If a user wants to generate system library documentation:**

1. User invokes CLI with `--system` flag
2. Configuration is set to system library mode (`system: true`)
3. `RellSystemLibToDocumentableTranslator` is used instead of source translator
4. System library modules are loaded from predefined definitions
5. `SystemLibVisitor` traverses system library structure
6. System library types, functions, and modules are converted to Documentables
7. Documentation is generated following same pipeline as user projects
8. Output is titled "Rell System Library API Reference"

**System library modules included:**
- `rell` - Core Rell standard library
- `rell.test` - Test library (only in test modules)

### Module Filtering Flow

**If a user wants to hide certain modules from navigation:**

1. User specifies `--filtered-modules` with comma-separated module names
2. Configuration stores filtered modules list
3. `RellDokkaPlugin` initializes hidden packages registry
4. Modules in filtered list (but not in additional modules) are marked as hidden
5. `RellNavigationPageInstaller` uses hidden packages list
6. Hidden modules are excluded from navigation menu
7. Documentation for hidden modules is still generated but not shown in navigation

**If a user wants to include filtered modules:**
- User can specify `--additional-modules` to override filtering
- Modules in additional modules list are shown in navigation even if filtered
- This allows fine-grained control over navigation visibility

### Doc Comment Processing Flow

**If Rell code contains doc comments:**

1. Doc comments are extracted during module traversal
2. `RellMarkdownParser` parses markdown-formatted doc comments
3. Doc comments are converted to `DocumentationNode` structures
4. Documentation nodes are attached to corresponding Documentables
5. During rendering, doc comments are converted to HTML
6. Code examples in doc comments are syntax-highlighted
7. Cross-references in doc comments are resolved to documentation links

**Doc comment format:**
- Doc comments use markdown syntax
- Code blocks are supported for examples
- Cross-references use DRI (Documentable Resource Identifier) format

### Extension Function Detection Flow

**If Rell code contains extension functions:**
2. Extension functions are identified by their target function
3. Extension functions are grouped by target function and module
4. Extension functions are documented alongside their target functions
5. Navigation shows extension functions in appropriate contexts
6. Extension function signatures show target function information

### Source Link Generation Flow

**If a user specifies source links:**
1. User provides `--source-link` with format `<path>=<url>[#lineSuffix]`
2. Configuration stores source link mappings
3. During page generation, source links are attached to documentables
4. HTML rendering includes "View Source" links
5. Links point to specified URL with optional line number suffix
6. Source links allow users to view original source code

**Source link format:**
- Local directory path maps to remote URL
- Optional line suffix (e.g., `#L123`) is appended to URLs
- Multiple source links can be specified for different directories

## Important Assumptions

### Rell Compilation Assumptions

- **Source code must compile** - Plugin requires successful Rell compilation to extract documentation. Code with compilation errors may produce incomplete or failed documentation.
- **Module structure must be valid** - Rell modules must follow valid Rell module structure. Invalid module organization may cause documentation generation to fail.

### Documentation Generation Assumptions

- **Doc comments are optional** - Code without doc comments will still generate documentation with signatures but no descriptions.
- **Markdown in doc comments** - Doc comments are expected to use markdown format. Non-markdown content may not render correctly.
- **File structure** - Plugin expects standard Rell project structure with source files in specified directory.

### System Library Assumptions

- **System library structure is fixed** - System library documentation is generated from predefined `RellModule` enum definitions (`MAIN` and `TEST`) that map to `C_LibModule` objects from the Rell compiler API (`Lib_Rell.MODULE` and `Lib_RellTest.MODULE`). Changes to system library structure require plugin updates to the `RellModule` enum and `SystemLibVisitor` implementation.

- **No source code compilation** - System library documentation does not compile Rell source files. Instead, it directly accesses the precompiled library model (`C_LibModule`) provided by the Rell compiler API. The plugin creates fake source sets to satisfy Dokka's pipeline requirements, but no actual source files are processed.

- **System library version dependency** - System library documentation reflects the version of Rell compiler API used by the plugin. The `C_LibModule` structure and contents are determined by the Rell compiler API version, not by any source files. Updating the Rell compiler API version will change the system library documentation without modifying the plugin code.

- **Blacklisted items** - Certain system library items are explicitly excluded from documentation: types (`guid`, `signer`), aliases (`tuid`), and empty namespaces. This filtering is hardcoded in `SystemLibVisitor` and requires code changes to modify.

- **Module documentation files** - System library module and package-level documentation can be provided via markdown files (e.g., `rell.md`) included through the `--includes` parameter. These files are processed separately from the library model structure.

## Non-Obvious Behavior

### Hidden Packages vs Filtered Modules

**Navigation filtering:**
- `filteredModules` excludes modules from navigation menu
- Filtered modules are still documented and searchable
- `additionalModules` overrides filtering for specific sub/modules

### Test Module Separation

**Test modules are handled separately:**
- Test modules (marked with `@test module`) are processed in separate source sets
- Test modules are excluded from main documentation by default
- Test module documentation can be generated separately if needed
- System library test modules (`rell.test`) are included in system library documentation

### Module Name Resolution

**App-level names vs module names:**
- Rell uses app-level names (qualified names) for symbols
- Plugin converts app-level names to package/module structure
- Module names are derived from symbol qualifiers
- Package hierarchy is built from module organization

### Extension Function Detection

**Reflection-based detection:**
- Extension functions are detected using reflection on the compiled Rell model because the Rell compiler API exposes extension function information through private/internal properties (e.g., `R_FunctionExtensionsTable.list`, `R_ExtendableFunctionUid.name`) that are not publicly accessible
- Extension functions are grouped by target function for documentation

### System Library Blacklisting

**Certain system types are excluded:**
- Types like `guid`, `signer` are blacklisted from system library documentation
- Aliases like `tuid` are blacklisted
- Empty namespaces are excluded
- This filtering is hardcoded in `SystemLibVisitor`

### Documentation Serialization

**Configuration serialization:**
- Plugin configuration is serialized to JSON and passed between pipeline stages
- Non-serializable state (hidden packages, CLI environment) is stored in `RellDokkaGlobalState` singleton
- This bypasses Dokka's serialization mechanism for state that can't be serialized

### Source Set Handling

**Multiple source sets:**
- Plugin supports main and test source sets
- Each source set is processed separately
- Main source set processes non-test modules
- Test source set processes test modules

## Error Handling Behavior

### Compilation Errors

**If Rell code fails to compile:**
- Compilation errors are logged through `RellCliEnv`
- Error messages indicate which files/modules have errors
- Documentation generation may fail or produce incomplete output
- User must fix compilation errors before generating complete documentation

### Missing Modules

**If specified modules don't exist:**
- Compilation fails with module not found errors
- Error messages indicate which modules are missing
- Documentation generation cannot proceed without valid modules

### Invalid Configuration

**If configuration is invalid:**
- Invalid source directories cause file not found errors
- Invalid target directories may cause write permission errors
- Invalid module names cause compilation errors
- Configuration validation happens during builder construction

## Known Functional Limitations

### Extension Function Limitations

- Extension function detection relies on reflection 

### System Library Limitations

- System library documentation is generated from predefined definitions
- Changes to system library require plugin updates
- Some system library elements are blacklisted and not documented

### Doc Comment Limitations

- Doc comment parsing may not support all markdown features
- Complex markdown structures may not render correctly

### Module Filtering Limitations

- Complex filtering scenarios may not be fully supported
