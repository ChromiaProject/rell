/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.*

fun findRellFilesInWorkspace(file: File, uris: MutableList<URI>) {
    if (file.isDirectory) {
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
        result.append(String.format(Locale.US, "%02x", byte))
    }

    return result.toString()
}

fun calculateChecksum(fileUri: URI) = calculateChecksum(File(fileUri))

fun calculateChecksum(file: File) = sha256(file.readBytes())

fun calculateChecksum(fileContent: String) = sha256(fileContent)
