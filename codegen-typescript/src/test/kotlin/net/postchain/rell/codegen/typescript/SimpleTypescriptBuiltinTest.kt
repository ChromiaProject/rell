package net.postchain.rell.codegen.typescript

import assertk.assertThat
import net.postchain.rell.codegen.typescript.util.BlockEntity
import net.postchain.rell.codegen.typescript.util.TransactionEntity
import net.postchain.rell.codegen.typescript.util.TypescriptBuiltinType
import org.junit.jupiter.api.Test

internal class SimpleTypescriptBuiltinTest {
    @Test
    fun builtInTransactionTest() {
        val entity = TypescriptBuiltinType.Transaction.createBuiltin()
        assertThat { entity == TransactionEntity }
    }

    @Test
    fun builtInBlockTest() {
        val entity = TypescriptBuiltinType.Block.createBuiltin()
        assertThat { entity == BlockEntity }
    }
}
