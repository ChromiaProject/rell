package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test


class RellRequestManagerTest {

    @Test
    fun `Task submitted using runRead gets executed`() {
        val requestManager = RellRequestManager()
        val result = requestManager.runRead { 2 + 2 }
        assertThat(result.get()).isEqualTo(4)
    }

    @Test
    fun `Task submitted using runWrite gets executed`() {
        val requestManager = RellRequestManager()
        val result = requestManager.runWrite { 2 + 2 }
        assertThat(result.get()).isEqualTo(4)
    }
}