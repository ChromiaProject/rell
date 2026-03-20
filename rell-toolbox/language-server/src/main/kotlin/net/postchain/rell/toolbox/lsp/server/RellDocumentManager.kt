package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.lsp.editing.Document
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class RellDocumentManager {

    private val openDocuments = ConcurrentHashMap<URI, Document>()

    fun getOpenDocuments(): Map<URI, Document> = openDocuments

    fun openDocument(fileUri: URI, version: Int, content: String) {
        openDocuments[fileUri] = Document(fileUri, version, content)
    }

    fun closeDocument(fileUri: URI) {
        openDocuments.remove(fileUri)
    }

    fun getDocument(uri: URI): Document = getOpenDocument(uri) ?: Document(
        uri,
        version = 0,
        content = File(uri).readText()
    )

    fun getOpenDocument(uri: URI): Document? = openDocuments[uri]

    fun applyTextDocumentChanges(fileUri: URI, contentChanges: List<TextDocumentContentChangeEvent>): Document {
        val document = getOpenDocument(fileUri)
        check(document != null) { "Document $fileUri not opened" }
        val updatedDocument = document.applyTextDocumentChanges(contentChanges)
        openDocuments[fileUri] = updatedDocument
        return updatedDocument
    }
}
