package net.postchain.rell.toolbox.lsp.caching

import io.fury.Fury
import io.fury.config.Language
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.core.indexer.calculateChecksum
import net.postchain.rell.toolbox.core.indexer.createLocationInfo
import java.net.URI

class RellIndexSerializer {
    private val fury = Fury.builder().withLanguage(Language.JAVA)
        .requireClassRegistration(false)
        .withRefTracking(true)
        .suppressClassRegistrationWarnings(true)
        .withCodegen(false)
        .buildThreadSafeFury()

    fun deserializeAsResourceMap(indexAsBytes: ByteArray): Map<URI, Resource> =
        fromSerializableResources(deserialize(indexAsBytes))

    fun serializeAsBytes(indexer: WorkspaceIndexer): ByteArray = serialize(toSerializableResources(indexer))

    private fun deserialize(indexAsBytes: ByteArray) = fury.deserialize(indexAsBytes) as List<SerializableResource>

    private fun serialize(serializableData: List<SerializableResource>): ByteArray = fury.serialize(serializableData)

    private fun toSerializableResources(indexer: WorkspaceIndexer): List<SerializableResource> {
        val serializableData = indexer.fileUriResourceMap.map { (_, resource) ->
            val checksum = calculateChecksum(resource.fileUri)
            SerializableResource(
                resource.parseTree,
                resource.moduleInfo,
                resource.fileUri,
                resource.workspaceUri,
                resource.ast,
                resource.syntaxErrors,
                resource.semanticErrors,
                toSerializableSymbolInfos(resource.symbolInfos),
                checksum
            )
        }
        return serializableData
    }

    private fun toSerializableSymbolInfos(symbolInfos: Map<S_Pos, IdeSymbolInfo>): Map<S_Pos, SerializableSymbolInfo> {
        return symbolInfos.mapValues {
            SerializableSymbolInfo(
                kind = it.value.kind,
                defId = it.value.defId,
                link = it.value.link
            )
        }
    }

    private fun fromSerializableResources(serializedResources: List<SerializableResource>) =
        serializedResources.associate {
            val symbolInfos = fromSerializableSymbolInfos(it.symbolInfos)
            val resource = Resource(
                it.parseTree,
                it.moduleInfo,
                it.fileUri,
                it.workspaceUri,
                it.ast,
                it.syntaxErrors,
                it.semanticErrors,
                symbolInfos,
                createLocationInfo(symbolInfos),
                it.checksum
            )
            it.fileUri to resource
        }

    private fun fromSerializableSymbolInfos(symbolInfos: Map<S_Pos, SerializableSymbolInfo>): Map<S_Pos, IdeSymbolInfo> {
        return symbolInfos.mapValues {
            IdeSymbolInfo.make(
                kind = it.value.kind,
                defId = it.value.defId,
                link = it.value.link,
                null
            )
        }
    }
}
