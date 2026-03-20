package net.postchain.rell.codegen.kotlin

import assertk.assertThat
import net.postchain.rell.codegen.kotlin.util.BlockEntity
import net.postchain.rell.codegen.kotlin.util.KotlinBuiltinType
import net.postchain.rell.codegen.kotlin.util.TransactionEntity
import org.junit.jupiter.api.Test

internal class SimpleKotlinBuiltinTest {
    @Test
    fun builtInTransactionTest() {
        val entity = KotlinBuiltinType.Transaction.createBuiltin()
        assertThat { entity == TransactionEntity }
    }

    @Test
    fun builtInBlockTest() {
        val entity = KotlinBuiltinType.Block.createBuiltin()
        assertThat { entity == BlockEntity }
    }
}
