/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.caching

import net.postchain.rell.base.model.Name
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.toImmList
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

internal class IdeSymbolIdSerializer(config: Config) : Serializer<IdeSymbolId>(config, IdeSymbolId::class.java) {
    private val regex = Regex("""(\w+)\[([^]]+)]""")
    private val ideSymbolCategoryMap = IdeSymbolCategory.entries.associateBy { it.code }

    override fun write(context: WriteContext, value: IdeSymbolId?) {
        if (value == null) {
            context.buffer.writeBytesWithSize(byteArrayOf())
            return
        }
        val encodedAsBytes = value.encode().toByteArray()
        context.buffer.writeBytesWithSize(encodedAsBytes)
    }

    override fun read(context: ReadContext): IdeSymbolId? {
        val bytes = context.buffer.readBytesAndSize()
        if (bytes.isEmpty()) return null
        val encodedAsString = String(bytes)
        return decodeSymId(encodedAsString)
    }

    private fun decodeSymId(defIdEncoded: String): IdeSymbolId? {
        val parts = extractParts(defIdEncoded)
        if (parts.isEmpty()) return null

        val (category, name) = decodeCategoryAndName(parts)
        val members = decodeMembers(parts)

        return IdeSymbolId(category, name, members.toImmList())
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

    private fun decodeMembers(parts: List<Pair<String, String>>): List<Pair<IdeSymbolCategory, Name>> {
        val members = parts.drop(1).map {
            val category = ideSymbolCategoryMap[it.first]!!
            val name = it.second
            Pair(category, Name.of(name))
        }
        return members
    }
}
