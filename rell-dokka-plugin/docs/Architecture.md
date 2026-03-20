# Technical Architecture & Codebase

## High-Level Architecture Description

Rell Dokka Plugin follows Dokka's pipeline-based architecture, extending multiple stages of the documentation generation pipeline with Rell-specific implementations.

**Architecture Layers:**

1. **CLI Layer** - Command-line interface for invoking documentation generation
2. **Configuration Layer** - Builds and serializes plugin configuration
3. **Dokka Pipeline Layer** - Integrates with Dokka's extension point system
4. **Analysis Layer** - Compiles and analyzes Rell source code using Rell compiler API
5. **Translation Layer** - Converts Rell language constructs to Dokka's Documentable model
6. **Transformation Layer** - Enriches and transforms documentables
7. **Page Generation Layer** - Converts documentables to page models
8. **Rendering Layer** - Renders pages to HTML with custom styling and assets

**Key Architectural Decisions:**

- **Pipeline Extension Pattern** - Plugin extends Dokka's pipeline at multiple extension points rather than replacing entire pipeline
- **Serialization Bypass** - Uses `RellDokkaGlobalState` singleton to bypass Dokka's serialization for non-serializable state
- **Dual Translation Modes** - Supports both user project documentation and system library documentation with different translators
- **Modular Component Design** - Each pipeline stage has dedicated component with clear responsibilities
- **Reflection-Based Extension Detection** - Uses reflection to detect extension functions that aren't directly accessible through Rell compiler API

## Major Components and Responsibilities

### 1. Entry Point (`cli/main.kt`)

**Responsibility:** Command-line interface for invoking documentation generation.

**Key Components:**
- `DokkaCommand`: Clikt-based command-line parser
- `main()`: Application entry point

**Key Functions:**
- Parses command-line arguments (source, target, modules, etc.)
- Builds `RellDokkaPluginConfigurationBuilder` from arguments
- Invokes `RellDokkaGenerator.generate()`

**Why it matters:** Provides user-facing interface for documentation generation. Handles argument parsing and validation.

**Key Dependencies:**
- `RellDokkaGenerator` - Executes documentation generation
- `RellDokkaPluginConfigurationBuilder` - Builds configuration
- Clikt library - Command-line parsing

### 2. Generator (`RellDokkaGenerator.kt`)

**Responsibility:** Initializes and executes documentation generation process.

**Key Functions:**
- `generate()`: Builds Dokka configuration and invokes Dokka generator
- Creates `DokkaConsoleLogger` for logging
- Configures Dokka with plugin configuration

**Why it matters:** Centralizes documentation generation orchestration. Bridges CLI and Dokka framework.

**Key Dependencies:**
- `RellDokkaPluginConfigurationBuilder` - Provides configuration
- `DokkaGenerator` - Dokka framework generator
- `DokkaConsoleLogger` - Logging infrastructure

### 3. Plugin Configuration (`config/`)

**Responsibility:** Manages plugin configuration and global state.

**Key Components:**

**`RellDokkaPluginConfiguration`** - Serializable configuration data class
- Stores module names, filtering options, system library mode
- Serialized to JSON and embedded in Dokka configuration
- Passed between pipeline stages via serialization

**`RellDokkaPluginConfigurationBuilder`** - Builder for configuration
- Fluent API for building configuration
- Handles source links, custom assets, includes
- Supports both user project and system library modes
- Creates `DokkaConfiguration` with plugin configurations

**`RellDokkaGlobalState`** - Singleton for non-serializable state
- Stores hidden packages (modules excluded from navigation)
- Stores `RellCliEnv` for error handling and logging
- Bypasses Dokka's serialization mechanism
- Accessed across pipeline stages

**Why it matters:** Separates serializable configuration from runtime state. Enables state persistence across serialization boundaries.

### 4. Main Plugin (`RellDokkaPlugin.kt`)

**Responsibility:** Registers custom extensions at Dokka pipeline stages.

**Key Extensions Registered:**

**`sourceToDocumentableTranslator`** - Source code to Documentable translation
- Provides `RellSourceToDocumentableTranslator` for user projects
- Provides `RellSystemLibToDocumentableTranslator` for system library
- Selects translator based on `system` configuration flag

**`signatureProvider`** - Function/type signature generation
- Provides `RellSignatureProvider` to override Kotlin signature provider
- Generates Rell-specific signatures for functions, types, etc.

**`documentableToPageTranslator`** - Documentable to Page model translation
- Provides `RellDocumentableToPageTranslator` to override default translator
- Converts Documentables to Page models for rendering

**`aliasProvider`** - Custom tag content provider
- Provides `AliasDocTagProvider` for handling alias documentation tags

**`chromiaAssetsInstaller`** - Custom asset installation
- Provides `ChromiaAssetsInstaller` to inject custom CSS, JavaScript, images
- Runs after styles and assets installation, before scripts

**`rellSearchbarDataInstaller`** - Search functionality customization
- Provides `RellSearchbarDataInstaller` to override default search data installer
- Customizes search indexing for Rell documentation

**`renderer`** - HTML rendering
- Provides `RellHtmlRenderer` to override default HTML renderer
- Customizes HTML output for Rell documentation

**`rellNavigationPageInstaller`** - Navigation structure customization
- Provides `RellNavigationPageInstaller` to override default navigation
- Handles module filtering and hidden packages

**`rellModuleAndPackageDocumentation`** - Module/package documentation transformer
- Provides transformer for module and package-level documentation
- Processes markdown documentation files

**`nullPageTransformer`** - Suppresses Kotlin-specific transformers
- Provides `NullPageTransformer` to override Kotlin samples transformer
- Removes Kotlin-specific functionality not applicable to Rell

**Why it matters:** Central registration point for all plugin extensions. Integrates plugin functionality into Dokka pipeline.

### 5. Analysis (`analysis/RellAnalysis.kt`)

**Responsibility:** Compiles and analyzes Rell source code using Rell compiler API.

**Key Functions:**
- `RellAnalysis()`: Constructor compiles Rell application and extracts information
- `findFunctionReference()`: Finds DRI for function by app-level name
- `getExtensionFunctions()`: Gets extension functions for module
- `modules()`: Returns non-test modules
- `testModules()`: Returns test modules
- `hiddenPackages()`: Computes packages to hide from navigation

**Compilation Process:**
1. Builds Rell compiler `Config` with appropriate settings
2. Calls `RellApiCompile.compileApp()` to compile source code
3. Extracts modules, functions, types, constants, operations, queries
4. Identifies extension functions using reflection
5. Builds maps for efficient lookup (functions by name, extensions by module, etc.)

**Why it matters:** Provides Rell language analysis capabilities. Bridges Rell compiler API and documentation generation.

**Key Dependencies:**
- `RellApiCompile` - Rell compiler API
- `RellCliEnv` - Error handling and logging
- Reflection utilities - Extension function detection

### 6. Source Translation (`translators/RellSourceToDocumentableTranslator.kt`)

**Responsibility:** Translates Rell source code to Dokka Documentables.

**Key Functions:**
- `invoke()`: Main translation function
- Creates `RellAnalysis` to compile and analyze source
- Uses `RellModuleVisitor` to traverse modules
- Separates test and main source sets
- Creates `DModule` with packages

**Translation Process:**
1. Creates `RellAnalysis` instance to compile source
2. Determines source set type (main vs test)
3. Filters modules based on source set
4. Uses `RellModuleVisitor` to visit each module
5. Collects packages from module traversal
6. Marks hidden packages in global state
7. Creates `DModule` with packages and documentation

**Why it matters:** Entry point for user project documentation generation. Converts Rell code to Dokka's internal representation.

**Key Dependencies:**
- `RellAnalysis` - Source code analysis
- `RellModuleVisitor` - Module traversal
- `RellDokkaGlobalState` - Hidden packages registry

### 7. Module Visitor (`translators/RellModuleVisitor.kt`)

**Responsibility:** Traverses Rell modules and creates Documentable objects.

**Key Functions:**
- `visitRellModule()`: Main visitor function for modules
- `genericVisitor()`: Generic visitor for collections of definitions
- `namespace()`: Creates namespace packages
- Individual visit methods for each Rell construct type

**Visitor Pattern:**
- Visits functions, operations, queries
- Visits entities, structs, objects, enums
- Visits constants
- Visits extension functions
- Organizes definitions by namespace
- Creates `DPackage`, `DFunction`, `DClass`, etc. objects
- Attaches documentation from doc comments

**Why it matters:** Core translation logic. Converts Rell language constructs to Dokka Documentables.

**Key Dependencies:**
- `RellAnalysis` - Function lookup and extension detection
- `RellDocumentableSource` - Source location tracking
- Doc comment parsing utilities

### 8. System Library Translation (`translators/RellSystemLibToDocumentableTranslator.kt`)

**Responsibility:** Translates Rell system library to Documentables.

**Key Functions:**
- `invoke()`: Main translation function for system library
- Uses `RellModule.find()` to locate system module
- Uses `SystemLibVisitor` to traverse system library structure

**Translation Process:**
1. Finds system module from source set
2. Uses `SystemLibVisitor` to visit system library
3. Creates `DModule` with system library packages
4. System library structure is predefined, not compiled from source but from C_LibModule

**Why it matters:** Enables system library documentation generation. Uses different approach than user project documentation.

**Key Dependencies:**
- `RellModule` - System module definitions
- `SystemLibVisitor` - System library traversal
- `RellDocumentableSource.NULL` - Null source for system library

### 9. System Library Visitor (`systemlib/SystemLibVisitor.kt`)

**Responsibility:** Traverses Rell system library structure and creates Documentables.

**Key Functions:**
- `visitRellModule()`: Main visitor function
- Individual visit methods for system library constructs
- Filters blacklisted types, aliases, namespaces

**Visitor Pattern:**
- Visits types, structs, functions, special functions
- Visits properties, aliases, namespaces
- Filters blacklisted items (guid, signer, tuid, etc.)
- Creates Documentables from system library definitions
- Uses `TypeDefMemberVisitor` for type definition members

**Why it matters:** Handles system library-specific documentation generation. Works with predefined system library structure.

**Key Dependencies:**
- `RellModule` - System module definitions
- `TypeDefMemberVisitor` - Type member traversal
- System library type definitions

### 10. Page Translation (`translators/documentables/RellDocumentableToPageTranslator.kt`)

**Responsibility:** Converts Documentables to Page models for rendering.

**Key Functions:**
- `invoke()`: Main translation function
- Creates `RellPageCreator` with configuration and dependencies
- Calls `pageForModule()` to generate page structure

**Translation Process:**
1. Extracts configuration and dependencies from context
2. Creates `RellPageCreator` instance
3. Calls `pageForModule()` to generate `ModulePageNode`
4. Page structure includes navigation, content, and metadata

**Why it matters:** Converts documentation model to renderable page structure. Prepares documentation for HTML rendering.

**Key Dependencies:**
- `RellPageCreator` - Page structure creation
- `DokkaBase` plugins - Comments converter, signature provider, tag providers

### 11. Page Creator (`page/RellPageCreator.kt`)

**Responsibility:** Creates page models from Documentables.

**Key Functions:**
- `pageForModule()`: Creates module page structure
- Creates pages for packages, classes, functions, etc.
- Organizes pages hierarchically

**Page Structure:**
- Module page contains package pages
- Package pages contain class/function pages
- Pages include content, navigation, and metadata
- Structure mirrors Documentable hierarchy

**Why it matters:** Builds renderable page structure. Organizes documentation into navigable hierarchy.

### 12. HTML Rendering (`renderers/html/RellHtmlRenderer.kt`)

**Responsibility:** Renders page models to HTML with Rell-specific customizations.

**Key Customizations:**
- Rell-specific tabbed content types (OPERATION, QUERY, FUNCTION)
- Custom signature rendering for Rell constructs
- Rell-specific HTML structure and styling
- Custom handling for operations, queries, functions

**Rendering Process:**
1. Extends `HtmlRenderer` from DokkaBase
2. Overrides rendering methods for Rell-specific constructs
3. Applies custom HTML structure and styling
4. Generates HTML files in output directory

**Why it matters:** Produces final HTML output. Applies Rell-specific rendering customizations.

**Key Dependencies:**
- `HtmlRenderer` - Base HTML rendering from DokkaBase
- Kotlinx HTML - HTML generation utilities

### 13. Asset Installation (`renderers/html/ChromiaAssetsInstaller.kt`)

**Responsibility:** Installs custom CSS, JavaScript, and image assets.

**Key Functions:**
- Installs Chromia-branded assets
- Copies custom CSS and JavaScript files
- Installs images and icons
- Runs as page transformer in pipeline

**Why it matters:** Provides custom styling and branding. Enhances documentation appearance and functionality.

### 14. Navigation (`navigation/RellNavigationPageInstaller.kt`)

**Responsibility:** Customizes navigation structure with module filtering.

**Key Functions:**
- Builds navigation menu structure
- Filters hidden packages from navigation
- Organizes navigation by module/package hierarchy
- Overrides default navigation installer

**Why it matters:** Controls navigation visibility. Enables hiding implementation details while keeping documentation.

**Key Dependencies:**
- `RellDokkaGlobalState` - Hidden packages list

### 15. Search (`renderers/html/RellSearchbarDataInstaller.kt`)

**Responsibility:** Customizes search functionality for Rell documentation.

**Key Functions:**
- Builds search index from Documentables
- Customizes search data format
- Overrides default search data installer

**Why it matters:** Enables search functionality. Provides symbol search in documentation.

### 16. Signature Provider (`signature/RellSignatureProvider.kt`)

**Responsibility:** Generates Rell-specific function and type signatures.

**Key Functions:**
- Generates signatures for functions, operations, queries
- Generates signatures for types, structs, enums
- Formats Rell syntax correctly
- Overrides Kotlin signature provider

**Why it matters:** Produces correct Rell syntax in documentation. Ensures signatures match Rell language syntax.

### 17. Doc Comment Processing (`doc/`)

**Responsibility:** Parses and processes Rell doc comments.

**Key Components:**

**`RellMarkdownParser`** - Parses markdown in doc comments
- Converts markdown to `DocumentationNode` structures
- Handles code blocks, links, formatting

**`AliasDocTagProvider`** - Handles alias documentation tags
- Processes custom documentation tags
- Provides content for alias tags

**`doc_comment.kt`** - Doc comment utilities
- Extracts doc comments from Rell definitions
- Converts doc comments to documentation nodes

**Why it matters:** Processes documentation content. Enables rich documentation with markdown support.

### 18. DRI (Documentable Resource Identifier) (`dri/`)

**Responsibility:** Creates and manages DRI references for cross-referencing.

**Key Components:**
- `dri.kt` - DRI creation utilities
- `callable.kt` - Callable DRI creation
- `bound.kt` - Bound type DRI creation
- `extra.kt` - Extra DRI properties

**Why it matters:** Enables cross-references in documentation. Links related documentation elements.

### 19. Module Documentation (`moduledocs/`)

**Responsibility:** Processes module and package-level documentation files.

**Key Components:**
- `ModuleAndPackageDocumentationReader` - Reads documentation files
- `ModuleAndPackageDocumentationParser` - Parses documentation content
- `ModuleAndPackageDocumentationTransformer` - Transforms documentables with module docs

**Why it matters:** Enables module-level documentation. Supports documentation files separate from source code.

## How Components Communicate

### Pipeline Flow

```
CLI (main.kt)
    ↓
RellDokkaGenerator
    ↓
DokkaGenerator (Framework)
    ↓
RellDokkaPlugin (Extension Registration)
    ↓
┌─────────────────────────────────────┐
│ Source Analysis Stage               │
│ RellSourceToDocumentableTranslator  │
│   → RellAnalysis (compilation)      │
│   → RellModuleVisitor (traversal)    │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ Transformation Stage                │
│ ModuleAndPackageDocumentation       │
│ Documentable Transformers           │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ Page Generation Stage               │
│ RellDocumentableToPageTranslator    │
│   → RellPageCreator                 │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ Rendering Stage                     │
│ RellHtmlRenderer                    │
│ ChromiaAssetsInstaller              │
│ RellNavigationPageInstaller         │
│ RellSearchbarDataInstaller          │
└─────────────────────────────────────┘
    ↓
HTML Output
```

### Component Interaction Details

**Configuration Flow:**
1. CLI parses arguments and builds `RellDokkaPluginConfigurationBuilder`
2. Builder creates `RellDokkaPluginConfiguration` (serializable)
3. Builder stores non-serializable state in `RellDokkaGlobalState`
4. Configuration is serialized to JSON and embedded in `DokkaConfiguration`
5. `DokkaGenerator` deserializes configuration and passes to plugins

**Source Analysis Flow:**
1. `RellSourceToDocumentableTranslator.invoke()` is called by Dokka
2. Translator creates `RellAnalysis` instance
3. `RellAnalysis` compiles source using `RellApiCompile.compileApp()`
4. Translator creates `RellModuleVisitor` with analysis results
5. Visitor traverses modules and creates Documentables
6. Hidden packages are registered in `RellDokkaGlobalState`

**Translation Flow:**
1. `RellModuleVisitor` visits each Rell module
2. For each module, visitor processes functions, types, constants, etc.
3. Visitor creates `DPackage`, `DFunction`, `DClass` objects
4. Doc comments are parsed and attached to Documentables
5. DRI references are created for cross-referencing

**Page Generation Flow:**
1. `RellDocumentableToPageTranslator.invoke()` is called
2. Translator creates `RellPageCreator` with dependencies
3. Page creator traverses Documentables and creates Page models
4. Pages are organized hierarchically (module → package → class/function)

**Rendering Flow:**
1. `RellHtmlRenderer` receives Page models
2. Renderer converts pages to HTML using templates
3. `ChromiaAssetsInstaller` adds custom assets
4. `RellNavigationPageInstaller` builds navigation structure
5. `RellSearchbarDataInstaller` builds search index
6. HTML files are written to output directory

**State Management:**
- Configuration is serialized and passed between stages
- `RellDokkaGlobalState` singleton is accessed directly (bypasses serialization)
- Hidden packages and CLI environment persist across pipeline stages

## Key Frameworks, Libraries, and Versions

### Core Dependencies

- **Dokka Core:** 1.9.20 (`org.jetbrains.dokka:dokka-core`)
- **Dokka Base:** 1.9.20 (`org.jetbrains.dokka:dokka-base`)
- **Rell API Base:** 0.15.0 (`net.postchain.rell:rell-api-base`)
- **Rell Base:** 0.15.0 (`net.postchain.rell:rell-base`)

### Supporting Libraries

- **Kotlinx HTML:** 0.8.0 (`org.jetbrains.kotlinx:kotlinx-html-jvm`) - HTML generation
- **Kotlinx Serialization:** 1.6.0 (`org.jetbrains.kotlinx:kotlinx-serialization-json`) - JSON serialization
- **Clikt:** 3.5.2 (`com.github.ajalt.clikt:clikt`) - Command-line parsing
- **Jackson:** 2.15.3 (`com.fasterxml.jackson.module:jackson-module-kotlin`) - JSON processing

### Test Dependencies

- **Dokka Test API:** 1.9.20 (`org.jetbrains.dokka:dokka-test-api`)
- **Dokka Base Test Utils:** 1.9.20 (`org.jetbrains.dokka:dokka-base-test-utils`)
- **JSoup:** 1.17.2 (`org.jsoup:jsoup`) - HTML parsing for tests

### Key Libraries Purpose

- **Dokka Framework:** Core documentation generation infrastructure, pipeline architecture, extension points
- **Rell Compiler API:** Rell language analysis, compilation, symbol resolution
- **Kotlinx HTML:** HTML generation utilities for rendering
- **Kotlinx Serialization:** JSON serialization for configuration
- **Clikt:** Command-line interface parsing and validation
