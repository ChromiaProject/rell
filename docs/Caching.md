# Caching

### Initialization

- If the `indexCaching` option is set to true in the client initialization event, the Language Server caches the
  workspace on disk through
  the [RellIndexCachingService](../language-server/src/main/kotlin/net/postchain/rell/toolbox/lsp/caching/RellIndexCachingService.kt)
- During subsequent openings of the workspace, the Language Server reads the document resources from the cache,
  avoiding the need to rebuild them from file content on disk.
- The `RellIndexCachingService` is invoked by the `RellWorkspaceManager` during initialization.

### Background Job for Persistence

- The `RellIndexCachingService` initiates a background job, `persistOnDiskPeriodically`, which saves the current state
  to the cache every minute.
- This periodic persistence ensures that the cache remains up-to-date with the latest workspace information.

### Cache Cleanup

- During initialization, the `RellIndexCachingService` clears old caches from disk that are older than 30 minutes
  with the method `cleanupOldCaches(...)`.
- This cleanup mechanism helps maintain the integrity of the cache and prevents outdated information from persisting.
- Client can also send a request `invalidateCaches` which clears the whole cache folder

### Serialization

- Serialization of document resource data is handled using the `io.fury.Fury` library.
- The [RellIndexSerializer](../language-server/src/main/kotlin/net/postchain/rell/toolbox/lsp/caching/RellIndexSerializer.kt) class
  manages the serialization and deserialization process.
- What gets cached is a serializable representation of document
  resource, [SerializableResource](../language-server/src/main/kotlin/net/postchain/rell/toolbox/lsp/caching/SerializableResource.kt).
  The document resource is a data class that encapsulates essential data from the Rell file required to support our
  features and is defined in the core
  module, [Resource](../../core/src/main/kotlin/net/postchain/rell/toolbox/core/indexer/Resource.kt).

#### IdeSymbolIdSerializer

The default serializer from Fury was not able to serialize `net.postchain.rell.base.utils.ide.IdeSymbolId` so the custom
serializer [IdeSymbolIdSerializer](../language-server/src/main/kotlin/net/postchain/rell/toolbox/lsp/caching/IdeSymbolIdSerializer.kt)
handle the serialization and deserialization of the type `IdeSymbolId`.
