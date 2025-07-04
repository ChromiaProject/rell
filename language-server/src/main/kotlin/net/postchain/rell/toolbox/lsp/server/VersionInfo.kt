package net.postchain.rell.toolbox.lsp.server

object VersionInfo {
    const val UNKNOWN_VERSION = "unknown_version"

    fun getImplementationVersion() = VersionInfo::class.java.`package`.implementationVersion ?: UNKNOWN_VERSION
}
