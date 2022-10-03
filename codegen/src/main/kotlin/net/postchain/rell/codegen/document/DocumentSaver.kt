package net.postchain.rell.codegen.document

import java.io.File

class DocumentSaver(private val targetFolder: File) {

    fun saveDocuments(documentFiles: Map<String, Document>) {
        documentFiles.forEach { (path, document) -> saveDocument(path, document) }
    }

    fun saveDocument(path: String, document: Document) {
        val f = File(targetFolder, path)
        f.parentFile.mkdirs()
        f.createNewFile()
        f.writeText(document.format())
    }
}
