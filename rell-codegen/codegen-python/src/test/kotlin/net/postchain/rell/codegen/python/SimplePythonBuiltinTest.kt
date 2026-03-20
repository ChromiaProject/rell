package net.postchain.rell.codegen.python

import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.python.util.PythonBuiltinType
import org.junit.jupiter.api.Test

internal class SimplePythonBuiltinTest {
    @Test
    fun builtInTransactionTest() {
        val entity = PythonBuiltinType.Transaction.createBuiltin()
        assertThat(entity.format()).contains("class Transaction:")
    }

    @Test
    fun builtInBlockTest() {
        val entity = PythonBuiltinType.Block.createBuiltin()
        assertThat(entity.format()).contains("class Block:")
    }
} 