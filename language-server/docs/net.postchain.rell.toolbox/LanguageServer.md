## Language Server

This module serves as the server-side implementation of
the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) (LSP), leveraging the capabilities
provided by the [eclipse-lsp4j](https://github.com/eclipse-lsp4j/lsp4j/) library.

### Server-Client Communication

The communication between the server and client is facilitated through
the [JSON RPC Protocol](https://www.jsonrpc.org/specification). Key aspects of this
interaction include:

- Bi-directional Communication: The server and client act as both servers and clients for each other. Meaning they can
  both send and retrieve requests from each other.

[RellLanguageServer](../../src/main/kotlin/net/postchain/rell/toolbox/lsp/server/RellLanguageServer.kt) contains the
entry point for all the supported requests that are sent from the client.

### Server Setup

This project supports starting the server on two different streams to accommodate following censorious:

- Socket for Development: This setup is conducive to debugging, allowing the use of a debugger during development. Entry
  point is [SocketMain](../../src/main/kotlin/net/postchain/rell/toolbox/lsp/SocketMain.kt)
- Standard Input/Output Stream for Production: In a production environment, the server is configured to use the
  standard input/output stream. Entry
  point is [StdioMain](../../src/main/kotlin/net/postchain/rell/toolbox/lsp/StdioMain.kt)

To enable tracing for the communication between client and server one needs to pass in the argument '-trace' to the
method `server.launch()`. This method call exists inside both of StdioMain.kt and SocketMain.kt.

### Dependency Injection with Koin

Regardless of the chosen setup, the server utilizes [Koin](https://insert-koin.io) for managing
dependency injections. The classes required for server functionality are defined in
the [ServerModule](../../src/main/kotlin/net/postchain/rell/toolbox/lsp/server/ServerModule.kt).

### Language Server Initialization

1. Client Sends Initialization Request
   The initialization process begins when the client sends an initialization request to the language server.
   This request includes information about the client's capabilities and preferences, and also the path of the active
   workspace folder.


2. Server Responds with Initialization Result upon receiving the initialization request and are finish with processing
   the
   client's capabilities and preferences and indexing the workspace the client has opened.
   The server then responds with an initialization result, which includes the server's own capabilities. The
   capabilities we support on the server side are set
   in [CapabilitiesProvider](../../src/main/kotlin/net/postchain/rell/toolbox/lsp/server/CapabilitiesProvider.kt)


3. Client Sends Initialization Completion Notification Following the receipt of the initialization result,
   the client sends an initialized notification to inform the server that the client has completed its initialization
   process and that communication of other requests can now be sent.

### Custom Endpoints

The Language Server interface that we implement in RellLanguageServer has the endpoints for the default requests from
Language Server Protocol. If one wish to implement a custom endpoint one does it so by using the annotation modifier
`@JsonRequest` above the method that shall handle the request.

```kotlin
@JsonRequest(useSegment = false, value = "rell/about")
fun about(): CompletableFuture<RellAbout> {
    return CompletableFuture.completedFuture(RellVersionInfo.getAbout())
}
```