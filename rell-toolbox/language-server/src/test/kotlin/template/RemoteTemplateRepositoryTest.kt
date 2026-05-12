/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class RemoteTemplateRepositoryTest {

    @Test
    fun `templatesRoot returns cached directory without re-downloading when ready marker present`(
        @TempDir cacheRoot: File,
    ) {
        val ref = "test-ref"
        val cached = cacheRoot.toPath().resolve(ref).also { it.createDirectories() }
        cached.resolve(".ready").writeText(ref)
        cached.resolve("plain").createDirectories().resolve("chromia.yml").writeText("noop\n")

        // Pointing at an unreachable host: if the implementation tried to download we'd get an
        // IOException. The cache-hit path must short-circuit before the network call.
        val repo = RemoteTemplateRepository(
            cacheDir = cacheRoot.toPath(),
            projectId = "0",
            ref = ref,
            subPath = "anything",
        )

        val resolved: Path = repo.templatesRoot()
        assertThat(resolved).isEqualTo(cached)
    }

}
