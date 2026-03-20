# Contributing to Rell Dokka Plugin
> This document provides information about the main components of the Rell Dokka Plugin and how they work together during the documentation generation process.
 
## Dokka Developer Guide
[Dokka plugin developement](https://kotlin.github.io/dokka/2.0.0/developer_guide/plugin-development/introduction/)
[Dokka developer guide](https://kotlin.github.io/dokka/2.0.0/developer_guide/introduction/)
[Simple plugin tutorial](https://kotlin.github.io/dokka/2.0.0/developer_guide/plugin-development/sample-plugin-tutorial/)
<!-- This is very useful since it shows how we can modify the main stages of generating documentation -->
[Core extension points](https://kotlin.github.io/dokka/2.0.0/developer_guide/architecture/extension_points/core_extension_points/) 

> All core extensions
```kotlin
object CoreExtensions {
    val preGenerationCheck by coreExtensionPoint<PreGenerationChecker>()
    val generation by coreExtensionPoint<Generation>()
    val sourceToDocumentableTranslator by coreExtensionPoint<SourceToDocumentableTranslator>()
    val documentableMerger by coreExtensionPoint<DocumentableMerger>()
    val documentableTransformer by coreExtensionPoint<DocumentableTransformer>()
    val documentableToPageTranslator by coreExtensionPoint<DocumentableToPageTranslator>()
    val pageTransformer by coreExtensionPoint<PageTransformer>()
    val renderer by coreExtensionPoint<Renderer>()
    val postActions by coreExtensionPoint<PostAction>()
}
```

## Architecture Overview

The Rell Dokka Plugin extends Dokka to generate documentation for Rell programming language code. It works by analyzing Rell source code, translating it into Dokka's internal representation, and then generating HTML documentation.

## Dokka Pipelines

Dokka uses a pipeline architecture to process documentation. The pipeline consists of several stages:

1. **Source Analysis**: Source code is analyzed and parsed into language-specific models
2. **Translation**: Language-specific models are translated into Dokka's internal representation (Documentables)
3. **Merging**: Information from different sources/modules is merged 
4. **Transformation**: Documentables are transformed and enriched with additional information
5. **Page Generation**: Documentables are translated into page models
6. **Rendering**: Page models are rendered into the final output format (HTML)

Each stage in the pipeline can be extended or overridden by plugins.

## Main Classes and Their Functions

### Plugin Configuration

- **RellDokkaPluginConfiguration**: Configures the plugin with settings such as module names, filtering options, and system library documentation mode. The configuration is serialized and passed between pipeline stages.

- **RellDokkaGlobalState**: A global registry that bypasses Dokka's serialization process. It maintains state that needs to persist across the entire documentation generation process, including hidden packages (modules excluded from UI navigation) and logging/error handling through RellCliEnv.

### Core Plugin Components

- **RellDokkaPlugin**: The main entry point for the plugin. Extends Dokka's core functionality by providing custom implementations for various pipeline stages through extension points. It configures:
  - Source to documentable translation
  - Signature providers
  - Page translation
  - HTML rendering
  - Navigation and search functionality

- **RellDokkaGenerator**: Handles the initialization and execution of the documentation generation process.

### Analysis and Translation

- **RellAnalysis**: Analyzes Rell source code using the Rell compiler API. It compiles the application, extracts functions, modules, and their relationships, and provides utilities for working with the compiled model.

- **RellSourceToDocumentableTranslator**: Translates Rell source code into Dokka's internal representation (Documentables). It uses RellAnalysis to compile the code and RellModuleVisitor to traverse the module structure.

- **RellSystemLibToDocumentableTranslator**: Similar to RellSourceToDocumentableTranslator but focused on the Rell system library documentation.

- **RellModuleVisitor**: Traverses the Rell module structure and creates Dokka documentable objects for each module, package, class, function, etc.

### Documentation Generation

- **RellDocumentableToPageTranslator**: Translates documentable objects into page models that can be rendered by Dokka.

- **RellPageCreator**: Creates page models for Rell documentation, handling the structure of the documentation pages.

### HTML Rendering

- **RellHtmlRenderer**: Customizes the HTML rendering process for Rell documentation.

- **ChromiaAssetsInstaller**: Installs custom assets (CSS, JavaScript, images) for the documentation.

- **RellSearchbarDataInstaller**: Customizes the search functionality for Rell documentation.

- **RellNavigationPageInstaller**: Customizes the navigation structure for Rell documentation.

## Pipeline Flow

1. The process begins with `RellDokkaGenerator` initializing the configuration and starting the Dokka generator

2. `RellDokkaPlugin` extends various pipeline stages with Rell-specific implementations

3. `RellSourceToDocumentableTranslator` or `RellSystemLibToDocumentableTranslator` (depending on mode) analyzes the source code and creates documentable objects

4. These documentables are processed through various transformers that enrich and modify them

5. `RellDocumentableToPageTranslator` transforms documentables into page models

6. `RellHtmlRenderer` and associated components render the page models into HTML documentation

## Extending the Plugin

To extend the plugin with new functionality:

1. Identify the appropriate pipeline stage to extend
2. Create a custom implementation of the relevant interface
3. Register it in the `RellDokkaPlugin` using the `extending` mechanism
4. Test your changes with both regular Rell code and system library documentation

## Development Setup

1. Clone the repository
2. Build the project using Gradle: `./gradlew build`
3. Run tests: `./gradlew test` 
4. Generate docs for a rell project: `./gradlew run --args="--source /path/to/rell/project --target ./docs/output --modules module1,module2"`