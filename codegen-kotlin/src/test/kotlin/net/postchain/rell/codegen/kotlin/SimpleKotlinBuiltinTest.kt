package net.postchain.rell.codegen.kotlin

import assertk.assertThat
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.kotlin.util.BlockEntity
import net.postchain.rell.codegen.kotlin.util.TransactionEntity
import net.postchain.rell.codegen.kotlin.util.builtin
import net.postchain.rell.codegen.util.BuiltinType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SimpleKotlinBuiltinTest {

    companion object : SingleFileRellApp("structures") {

        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @ParameterizedTest(name = "Dependency {0} creates {1} builtin objects")
    @CsvSource(
            "Block, 1",
            "Transaction, 2",
    )
    fun builtinTypes(neededObject: String, numberOfObjects: Int) {
        val entities = builtin(BuiltinType.valueOf(neededObject))
        assertThat { entities.size == numberOfObjects }
    }

    @Test
    fun builtInTransactionTest() {
        val entities = builtin(BuiltinType.Transaction)
        assertThat { entities.containsAll(listOf(BlockEntity, TransactionEntity)) }
    }

    @Test
    fun builtInBlockTest() {
        val entities = builtin(BuiltinType.Transaction)
        assertThat { entities.containsAll(listOf(BlockEntity)) }
    }
}
