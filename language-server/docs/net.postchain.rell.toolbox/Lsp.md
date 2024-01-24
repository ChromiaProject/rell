## Overview `net.postchain.rell.toolbox.lsp`

### `.caching`

* The RellIndexSerializer class in the caching package provides functionality for serializing and deserializing a
  WorkspaceIndexer object. It leverages the Fury library for serialization purposes, using the default serializer
  except for the type IdeSymbolId were the custom serializer IdeSymbolIdSerializer is used. Key functionalities include
  converting Resource and IdeSymbolInfo instances into their serializable counterparts
  (SerializableResource and SerializableSymbolInfo)
* Read [Caching.md](Caching.md)

### `.diagnostics`

* The DiagnosticsConverter Kotlin object converts Rell issues into LSP diagnostics using LSP4J. It maps severity, code,
  message, and range from Rell issues to LSP diagnostic attributes. The toDiagnostics function processes a list of Rell
  issues, while the toDiagnostic function handles individual issue conversions. The severity mapping ensures
  compatibility with LSP diagnostic severities.

### `.editing`

* The Document class in the editing package manages a text document's state for language server interactions. It stores
  file-related information, such as URI, version, and contents. The class provides methods to get the offset and
  position
  in the document based on line and character indices. Additionally, it includes functionality to apply text document
  changes, adjusting the document's version and content accordingly.

### `.editorconfig`

* RellFormatterOptionsResolver class fetches formatting options for Rell code from the `.rellformat` file if it exists
  in
  the users' workspace. It utilizes RellWorkspaceManager to locate and parse the file, extracting properties
  like max_line_width, insert_spaces, and tab_size, that are used by the formatter.

### `.launcher`

* The launcher package encompasses functionality for launching the Rell Language Server. It features two launcher
  classes: SocketServerLauncher and StdioServerLauncher. Both launchers extend the AbstractServerLauncher class. The
  SocketServerLauncher initiates the server on a specified socket port, used for development. The StdioServerLauncher
  launches the server on the standard input/output streams, used in production.

### `.references`

* This Kotlin class, ReferenceIndexer, manages indexing and retrieval of references for symbols within Rell code. The
  class maintains maps for global, module, and local references, and provides methods to find references based on the
  type of symbol (global, module, or local). It also handles the updating of the reference index when the underlying
  code changes. The class is designed to work with URI-based representations of files and symbols, utilizing LSP4J for
  defining locations and ranges within the code.

### `.server`

* Read [LanguageServer.md](LanguageServer.md)

### `.symbols`

* The RellSymbolService in the symbols package efficiently handles go-to-definition requests within the Rell Language
  Server. It facilitates the retrieval of symbol locations and information, supporting various symbol types such as
  global, module, and local links.
* The OutlineTreeBuilder and related classes in the symbols package are utilized for constructing and managing outline
  trees within the Rell Language Server. It assists in the representation of document symbols, their relationships, and
  the hierarchy of code entities.