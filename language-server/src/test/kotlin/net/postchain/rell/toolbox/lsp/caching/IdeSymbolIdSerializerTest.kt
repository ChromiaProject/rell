package net.postchain.rell.toolbox.lsp.caching

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolId
import org.junit.jupiter.api.Test

class IdeSymbolIdSerializerTest {

    @Test
    fun `IdeSymbolId instance is serialized correctly`() {
        val fury = RellIndexSerializer.getFury()
        val ideSymbolId = IdeSymbolId(IdeSymbolCategory.FUNCTION, "dummyFunction", listOf(
            Pair(IdeSymbolCategory.PARAMETER, R_Name.of("dummyParameter")),
            Pair(IdeSymbolCategory.ATTRIBUTE, R_Name.of("dummyAttribute"))
        ))

        val symIdAsBytes = fury.serialize(ideSymbolId)

        assertThat(symIdAsBytes).isNotNull()
        assertThat(symIdAsBytes).isNotEmpty()

        val deserializedSymbolId = fury.deserialize(symIdAsBytes)

        assertThat(deserializedSymbolId).isNotNull()
        assertThat(deserializedSymbolId).isEqualTo(ideSymbolId)
    }
}