package net.postchain.rell.toolbox.lsp.caching

import io.fury.Fury
import io.fury.memory.MemoryBuffer
import io.fury.serializer.Serializer
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolId


class IdeSymbolIdSerializer(fury: Fury?) : Serializer<IdeSymbolId>(fury, IdeSymbolId::class.java) {
    private val regex = Regex("""(\w+)\[([^]]+)]""")
    private val ideSymbolCategoryMap = IdeSymbolCategory.entries.associateBy { it.code }

    override fun write(buffer: MemoryBuffer, value: IdeSymbolId?) {
        if (value == null) {
            buffer.writeBytesWithSizeEmbedded(byteArrayOf())
            return
        }
        val encodedAsBytes = value.encode().toByteArray()
        buffer.writeBytesWithSizeEmbedded(encodedAsBytes)
    }

    override fun read(buffer: MemoryBuffer): IdeSymbolId? {
        val bytes = buffer.readBytesWithSizeEmbedded()
        if (bytes.isEmpty()) return null
        val encodedAsString = String(bytes)
        return decodeSymId(encodedAsString)
    }

    private fun decodeSymId(defIdEncoded: String): IdeSymbolId? {
        val parts = extractParts(defIdEncoded)
        if (parts.isEmpty()) return null

        val (category, name) = decodeCategoryAndName(parts)
        val members = decodeMembers(parts)

        return IdeSymbolId(category, name, members)
    }

    private fun extractParts(defIdEncoded: String): List<Pair<String, String>> {
        val matches = regex.findAll(defIdEncoded)
        val parts = matches.map {
            val category = it.groupValues[1]
            val name = it.groupValues[2]
            Pair(category, name)
        }.toList()
        return parts
    }

    private fun decodeCategoryAndName(parts: List<Pair<String, String>>): Pair<IdeSymbolCategory, String> {
        val nameAndCategoryPart = parts.first()
        val category = ideSymbolCategoryMap[nameAndCategoryPart.first]!!
        val name = nameAndCategoryPart.second
        return Pair(category, name)
    }

    private fun decodeMembers(parts: List<Pair<String, String>>): List<Pair<IdeSymbolCategory, R_Name>> {
        val members = parts.drop(1).map {
            val category = ideSymbolCategoryMap[it.first]!!
            val name = it.second
            Pair(category, R_Name.of(name))
        }
        return members
    }
}