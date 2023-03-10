package net.postchain.rell.codegen.typescript

import assertk.assertThat
import net.postchain.rell.codegen.typescript.util.BlockEntity
import net.postchain.rell.codegen.typescript.util.TransactionEntity
import net.postchain.rell.codegen.typescript.util.builtin
import net.postchain.rell.codegen.util.BuiltinType
import org.junit.jupiter.api.Test

internal class SimpleTypescriptBuiltinTest {
    @Test
    fun builtInTransactionTest() {
        val entity = builtin(BuiltinType.Transaction)
        assertThat { entity == TransactionEntity }
    }

    @Test
    fun builtInBlockTest() {
        val entity = builtin(BuiltinType.Block)
        assertThat { entity == BlockEntity }
    }
}