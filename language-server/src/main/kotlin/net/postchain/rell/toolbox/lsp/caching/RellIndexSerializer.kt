package net.postchain.rell.toolbox.lsp.caching

import io.fury.Fury
import io.fury.config.Language
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.core.indexer.calculateChecksum
import net.postchain.rell.toolbox.core.indexer.createLocationInfo
import java.util.concurrent.ConcurrentHashMap

class RellIndexSerializer {

    fun deserializeAsWorkspaceIndexer(indexAsBytes: ByteArray): WorkspaceIndexer =
        fromSerializableWorkspaceIndexer(deserialize(indexAsBytes))

    fun serializeAsBytes(indexer: WorkspaceIndexer): ByteArray = serialize(toSerializableWorkspaceIndexer(indexer))

    private fun deserialize(indexAsBytes: ByteArray) = getFury().deserialize(indexAsBytes) as SerializableWorkspaceIndexer

    private fun serialize(serializableData: SerializableWorkspaceIndexer): ByteArray = getFury().serialize(serializableData)

    private fun toSerializableWorkspaceIndexer(indexer: WorkspaceIndexer): SerializableWorkspaceIndexer {
        return SerializableWorkspaceIndexer(indexer.workspaceUri, toSerializableResources(indexer))
    }

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

    private fun fromSerializableWorkspaceIndexer(serializableWorkspaceIndexer: SerializableWorkspaceIndexer): WorkspaceIndexer {
        val indexer = WorkspaceIndexer(serializableWorkspaceIndexer.workspaceUri)
        indexer.fileUriResourceMap = ConcurrentHashMap(fromSerializableResources(serializableWorkspaceIndexer.serializableResources))
        return indexer
    }

    private fun fromSerializableResources(serializedResources: List<SerializableResource>) =
        serializedResources.associate { res ->
            val symbolInfos = fromSerializableSymbolInfos(res.symbolInfos)
            val resource = Resource(
                res.parseTree,
                res.moduleInfo,
                res.fileUri,
                res.workspaceUri,
                res.ast,
                res.syntaxErrors,
                res.semanticErrors,
                symbolInfos,
                symbolInfos.asSequence().filter { it.value.defId != null }.associate { it.value.defId!! to it.key },
                createLocationInfo(symbolInfos),
                res.checksum
            )
            resource.fileUri to resource
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

    companion object {
        fun getFury(): Fury {
            val fury = Fury.builder().withLanguage(Language.JAVA)
                .requireClassRegistration(false)
                .withRefTracking(true)
                .suppressClassRegistrationWarnings(true)
                .withCodegen(false)
                .build()
            fury.registerSerializer(IdeSymbolId::class.java, IdeSymbolIdSerializer(fury))
            return fury
        }
    }
}
