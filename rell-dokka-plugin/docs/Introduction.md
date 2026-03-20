# Introduction

**Project Name:** Rell Dokka Plugin

**Repository URL:** https://gitlab.com/chromaway/core-tools/rell-dokka-plugin

## Project Summary

Rell Dokka Plugin is a **Dokka plugin** that generates HTML documentation for Rell programming language code. It extends Dokka's documentation generation framework to support Rell source code analysis, translation, and HTML rendering. The plugin can generate documentation for both user Rell projects and the Rell system library.

The plugin works by:

1. **Analyzing Rell source code** - Uses the Rell compiler API to compile and analyze Rell modules, extracting functions, types, constants, operations, queries, and their relationships
2. **Translating to Dokka's internal model** - Converts Rell language constructs into Dokka's Documentable representation
3. **Generating HTML documentation** - Renders documentation pages with navigation, search, and custom Chromia branding

The plugin provides a command-line interface for generating documentation sites and can be integrated into build systems via Gradle or Maven.

## Value Creation

Rell Dokka Plugin enables developers to:

- **Generate API documentation automatically** - Creates comprehensive documentation from Rell source code and doc comments without manual maintenance
- **Document Rell system library** - Provides complete API reference for the Rell standard library, including types, functions, and modules
- **Maintain documentation consistency** - Ensures documentation stays synchronized with code changes through automated generation
- **Provide developer-friendly documentation** - Generates HTML documentation with navigation, search, and code examples similar to Kotlin's Dokka documentation
- **Support multiple documentation modes** - Can generate documentation for user projects or system library, with different filtering and inclusion options

## Upstream and Downstream Projects

### Upstream Dependencies (What Rell Dokka Plugin Consumes)

**1. Dokka Framework**
- **Role:** Core documentation generation framework providing pipeline architecture, extension points, and HTML rendering infrastructure
- **Source:** Maven Central (`org.jetbrains.dokka:dokka-core`, `org.jetbrains.dokka:dokka-base`)
- **Version:** 1.9.20
- **Relationship:** Rell Dokka Plugin extends Dokka's plugin system by implementing custom translators, renderers, and transformers at various pipeline stages
- **Critical Dependency:** If Dokka framework is unavailable or incompatible, the plugin cannot function. The plugin depends on Dokka's extension point system for integration.

**2. Rell Compiler API**
- **Role:** Provides Rell language analysis capabilities, including compilation, module parsing, and symbol resolution
- **Source:** GitLab Maven registry (`net.postchain.rell:rell-api-base`, `net.postchain.rell:rell-base`)
- **Version:** 0.15.0
- **Relationship:** Plugin uses `RellApiCompile` to compile Rell source code and extract module structures, function definitions, and type information
- **Critical Dependency:** If Rell compiler API is unavailable or fails to compile source code, documentation generation will fail. The plugin requires successful compilation to extract documentation.

**4. Additional Libraries**
- **Kotlinx HTML** - HTML generation utilities
- **Kotlinx Serialization** - JSON serialization for configuration
- **Clikt** - Command-line interface parsing
- **Jackson** - JSON processing

### Downstream Projects (What Consumes Rell Dokka Plugin)

**1. Chromia CLI**
- **Role:** Command-line tool for Chromia development that includes documentation generation functionality
- **Source:** https://gitlab.com/chromaway/core-tools/chromia-cli
- **Relationship:** Chromia CLI uses `RellDokkaGenerator` and `RellDokkaPluginConfigurationBuilder` to generate documentation for Rell projects via the `generate docs-site` command
- **Usage Pattern:** Plugin is invoked when users run documentation generation commands in Chromia CLI
- **Critical Dependency:** Chromia CLI depends on rell-dokka-plugin for its documentation generation feature

**2. Rell Gradle Plugin**
- **Role:** Gradle plugin for Rell projects that provides build tasks including documentation generation
- **Source:** https://gitlab.com/chromaway/core-tools/rell-gradle-plugin
- **Relationship:** Rell Gradle Plugin uses `RellDokkaGenerator` and `RellDokkaPluginConfigurationBuilder` in its `RellDocsTask` to generate documentation as part of Gradle builds
- **Usage Pattern:** Plugin is invoked when users run documentation generation tasks (e.g., `rellDocs`) in Gradle builds
- **Critical Dependency:** Rell Gradle Plugin depends on rell-dokka-plugin for its documentation generation feature

**3. Rell System Library Documentation**
- **Role:** Official Rell system library API reference documentation
- **Relationship:** Plugin generates public-facing documentation for the Rell standard library
- **Usage Pattern:** System library documentation is generated separately using the `--system` flag, typically invoked directly via the plugin's CLI

## Data and Control Flow

### Documentation Generation Flow

```
1. User/CLI Invocation
   ↓
   Command-line arguments or build configuration
   ↓
2. RellDokkaGenerator
   ↓
   Builds DokkaConfiguration with RellDokkaPluginConfiguration
   ↓
3. DokkaGenerator (Dokka Framework)
   ↓
   Initializes plugin system and pipeline
   ↓
4. RellDokkaPlugin
   ↓
   Registers custom extensions at pipeline stages
   ↓
5. Source Analysis Stage
   ↓
   RellSourceToDocumentableTranslator or RellSystemLibToDocumentableTranslator
   ↓
   RellAnalysis compiles Rell code using RellApiCompile
   ↓
   RellModuleVisitor traverses compiled modules
   ↓
6. Translation Stage
   ↓
   Rell language constructs → Dokka Documentables
   ↓
7. Merging & Transformation Stage
   ↓
   Documentables merged and enriched with additional information
   ↓
8. Page Generation Stage
   ↓
   RellDocumentableToPageTranslator converts Documentables to Page models
   ↓
9. Rendering Stage
   ↓
   RellHtmlRenderer renders pages to HTML
   ↓
   ChromiaAssetsInstaller adds custom CSS/JS/assets
   ↓
   RellNavigationPageInstaller creates navigation structure
   ↓
10. Output
   ↓
   HTML documentation site in target directory
```

### Control Flow Details

**Configuration:**
- CLI parses arguments and builds `RellDokkaPluginConfigurationBuilder`
- Configuration is serialized to JSON and embedded in Dokka configuration
- `RellDokkaGlobalState` maintains non-serializable state (hidden packages, CLI environment)

**Source Analysis:**
- `RellAnalysis` uses Rell compiler API to compile source code
- Compilation extracts modules, functions, types, constants, operations, queries
- Extension functions are identified using reflection
- Module structure is analyzed to determine package hierarchy

**Translation:**
- `RellModuleVisitor` traverses compiled Rell modules
- Each Rell construct (function, type, etc.) is converted to Dokka `Documentable`
- Doc comments are parsed and attached to documentables
- DRI (Documentable Resource Identifier) references are created for cross-references

**Rendering:**
- `RellDocumentableToPageTranslator` converts documentables to page models
- `RellHtmlRenderer` renders pages using Dokka's HTML template system
- Custom assets (CSS, JavaScript, images) are installed
- Navigation structure is generated with module filtering support

**System Library Mode:**
- When `system: true` is set, `RellSystemLibToDocumentableTranslator` is used
- System library documentation is generated from predefined Rell module definitions
- Uses `SystemLibVisitor` to traverse system library structure
- Generates documentation for standard library modules (rell, rell.test, etc.)

### Data Persistence

Rell Dokka Plugin maintains minimal state:

- **Configuration** - Serialized in Dokka configuration, passed between pipeline stages
- **Global State** - `RellDokkaGlobalState` singleton maintains hidden packages and CLI environment (bypasses serialization)
- **Compiled Rell Model** - In-memory representation during generation, not persisted
- **Generated HTML** - Output files written to target directory

The only persistent data is:
- **Generated documentation files** - HTML, CSS, JavaScript, images written to output directory
- **No database or configuration files** - All configuration is provided at generation time

## External References and Resources

### Dokka Documentation
- **Dokka Developer Guide:** https://kotlin.github.io/dokka/2.0.0/developer_guide/introduction/
- **Dokka Plugin Development:** https://kotlin.github.io/dokka/2.0.0/developer_guide/plugin-development/introduction/
- **Dokka Core Extension Points:** https://kotlin.github.io/dokka/2.0.0/developer_guide/architecture/extension_points/core_extension_points/
- **Dokka Architecture:** https://kotlin.github.io/dokka/2.0.0/developer_guide/architecture/architecture_overview/

### Rell Language Documentation
- **Rell Documentation:** https://docs.chromia.com/rell/
