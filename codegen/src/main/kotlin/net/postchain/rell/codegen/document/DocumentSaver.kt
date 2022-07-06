package net.postchain.rell.codegen.document

import java.io.File

class DocumentSaver(private val targetFolder: File) {

    fun saveDocuments(documentFiles: Collection<DocumentFile>) {
        documentFiles.forEach { saveDocument(it) }
    }

    fun saveDocument(documentFile: DocumentFile) {
        val f = File(targetFolder, documentFile.path)
        f.parentFile.mkdirs()
        f.createNewFile()
        f.writeText(documentFile.document.format())
    }
}
