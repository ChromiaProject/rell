package net.postchain.rell.codegen.document

import net.postchain.rell.codegen.StringSerializable
import java.io.File

class DocumentSaver(private val targetFolder: File) {

    fun saveDocuments(documentFiles: Map<String, StringSerializable>) {
        documentFiles.forEach { (path, document) -> saveDocument(path, document) }
    }

    fun saveDocument(path: String, document: StringSerializable) {
        val formattedDocument = document.format()
        //if (formattedDocument.isBlank()) return
        val f = File(targetFolder, path)
        f.parentFile.mkdirs()
        f.createNewFile()
        f.writeText(formattedDocument)
    }
}
