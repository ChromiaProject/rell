package net.postchain.rell.toolbox.lsp.caching

import io.fury.Fury
import io.fury.config.Language
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.calculateChecksum
import net.postchain.rell.toolbox.indexer.createLocationInfo
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.server.VersionInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RellIndexSerializer(
    private val rellLinter: RellLinter,
    private val formattingStyleLinter: FormattingStyleLinter,
    private val formatterOptionsResolver: RellFormatterOptionsResolver,
    private val linterOptionsResolver: RellLinterOptionsResolver
) {

    fun deserializeAsWorkspaceIndexer(indexAsBytes: ByteArray): WorkspaceIndexer =
        fromSerializableWorkspaceIndexer(deserialize(indexAsBytes))

    fun serializeAsBytes(indexer: WorkspaceIndexer): ByteArray = serialize(toSerializableWorkspaceIndexer(indexer))

    private fun deserialize(indexAsBytes: ByteArray) =
        getFury().deserialize(indexAsBytes) as SerializableWorkspaceIndexer

    private fun serialize(serializableData: SerializableWorkspaceIndexer): ByteArray =
        getFury().serialize(serializableData)

    private fun toSerializableWorkspaceIndexer(indexer: WorkspaceIndexer): SerializableWorkspaceIndexer {
        val linterOptions = linterOptionsResolver.getLinterConfig(indexer.workspaceUri)
        val formatterOptions = formatterOptionsResolver.getWorkspaceFormattingOptions(indexer.workspaceUri)
        val metaData = SerializableMetaData(
            languageServerVersion = VersionInfo.getImplementationVersion(),
        )
        return SerializableWorkspaceIndexer(
            indexer.workspaceUri,
            toSerializableResources(indexer),
            linterOptions,
            formatterOptions,
            indexer.projectRootUri,
            metaData
        )
    }

    private fun toSerializableResources(indexer: WorkspaceIndexer): List<SerializableResource> {
        val serializableData = indexer.fileUriResourceMap.mapNotNull { (_, resource) ->
            val file = File(resource.fileUri)
            if (!file.exists()) {
                return@mapNotNull null
            }
            val checksum = calculateChecksum(file)
            SerializableResource(
                resource.parseTree,
                resource.moduleInfo,
                resource.fileUri,
                resource.workspaceUri,
                resource.ast,
                resource.syntaxErrors,
                resource.semanticErrors,
                toSerializableSymbolInfos(resource.symbolInfos),
                checksum,
                resource.tokenStream,
                resource.linterIssues,
                resource.formatterIssues,
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

    private fun fromSerializableWorkspaceIndexer(
        serializableWorkspaceIndexer: SerializableWorkspaceIndexer
    ): WorkspaceIndexer {
        val indexer =
            WorkspaceIndexer(
                serializableWorkspaceIndexer.workspaceUri,
                rellLinter,
                serializableWorkspaceIndexer.linterOptions,
                formattingStyleLinter,
                serializableWorkspaceIndexer.formatterOptions,
                serializableWorkspaceIndexer.projectRootUri
            )
        val metaData = serializableWorkspaceIndexer.metaData
        val currentVersion = VersionInfo.getImplementationVersion()
        if (metaData?.languageServerVersion != currentVersion) {
            throw IllegalStateException(
                "The serialized indexer was created with a different version of the language server. " +
                    "Expected: $currentVersion, Found: ${metaData?.languageServerVersion}"
            )
        }
        indexer.fileUriResourceMap = ConcurrentHashMap(
            fromSerializableResources(serializableWorkspaceIndexer.serializableResources)
        )
        return indexer
    }

    private fun fromSerializableResources(
        serializedResources: List<SerializableResource>
    ) =
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
                res.linterIssues,
                res.formatterIssues,
                symbolInfos,
                symbolInfos.asSequence().filter { it.value.defId != null }.associate { it.value.defId!! to it.key },
                createLocationInfo(symbolInfos),
                res.checksum,
                res.tokenStream,
            )
            resource.fileUri to resource
        }

    private fun fromSerializableSymbolInfos(
        symbolInfos: Map<S_Pos, SerializableSymbolInfo>
    ): Map<S_Pos, IdeSymbolInfo> {
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
