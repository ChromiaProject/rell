package net.postchain.rell.toolbox.core.indexer

import java.io.File
import java.net.URI
import java.security.MessageDigest

fun findRellFilesInWorkspace(file: File, uris: MutableList<URI>) {

    if (file.isDirectory()) {
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                findRellFilesInWorkspace(f, uris)
            }
        }
    } else {
        if (file.extension == "rell") {
            uris.add(file.toURI())
        }
    }
}

fun sha256(input: String) = sha256(input.toByteArray())

fun sha256(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)

    val result = StringBuilder()
    for (byte in digest) {
        result.append(String.format("%02x", byte))
    }

    return result.toString()
}

fun calculateChecksum(fileUri: URI) = sha256(File(fileUri).readBytes())

fun calculateChecksum(fileContent: String) = sha256(fileContent)
