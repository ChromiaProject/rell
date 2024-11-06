package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.findRellFilesInWorkspace
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.editing.CodeActionService
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.hover.formatDocSymbol
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.toPath
import kotlin.time.Duration.Companion.minutes

val TYPE_DEFINITIONS =
    setOf(IdeSymbolKind.DEF_ENTITY, IdeSymbolKind.DEF_STRUCT, IdeSymbolKind.DEF_ENUM, IdeSymbolKind.DEF_TYPE)

class RellWorkspaceManager(
    private val rellSymbolService: RellSymbolService,
    private val rellReferenceService: RellReferenceService,
    private val indexCachingService: RellIndexCachingService,
    private val rellLinter: RellLinter,
    private val formattingStyleLinter: FormattingStyleLinter,
    private val formatterOptionsResolver: RellFormatterOptionsResolver,
    private val linterOptionsResolver: RellLinterOptionsResolver,
    private val completionService: RellCompletionService,
    private val documentManager: RellDocumentManager,
) {

    var indexCachingEnabled: Boolean = false

    private lateinit var workspaceFolders: List<WorkspaceFolder>
    private lateinit var diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit
    val indexers: MutableMap<URI, WorkspaceIndexer> = ConcurrentHashMap()

    private val workspaceFolderUris get() = workspaceFolders.map {
        parseFileUri(it.uri) ?: throw IllegalArgumentException("Invalid workspace folder ${it.uri}")
    }

    fun initialize(workspaceFolders: List<WorkspaceFolder>, diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit) {
        this.workspaceFolders = workspaceFolders
        this.diagnosticsPublisher = diagnosticsPublisher

        runIndexers()
    }

    fun runIndexers() {
        val newIndexers = mutableMapOf<URI, WorkspaceIndexer>()
        workspaceFolderUris.forEach { workspaceFolder ->
            val indexer = doIndex(workspaceFolder)
            newIndexers[indexer.workspaceUri] = indexer
            reportDiagnostics(indexer)
        }
        indexers.clear()
        indexers.putAll(newIndexers)

        if (indexCachingEnabled) {
            indexCachingService.persistOnDiskPeriodically(indexers.values, 1.minutes)
        }
        indexCachingService.cleanupOldCaches()
    }

    private fun doIndex(workspaceFolderUri: URI): WorkspaceIndexer {
        val resolvedSourceDirUri = findSourceDirURI(workspaceFolderUri)
        val cachedIndexer =
            if (indexCachingEnabled) indexCachingService.getWorkspaceIndexer(resolvedSourceDirUri) else null

        val (linterOptions, formatterOptions) = getLinterAndFormatterOptions(workspaceFolderUri)

        val indexer =
            WorkspaceIndexer(
                resolvedSourceDirUri,
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                workspaceFolderUri
            )
        indexer.initialFileIndexBuild(cachedIndexer)
        return indexer
    }

    private fun getLinterAndFormatterOptions(workspaceFolderUri: URI): Pair<LinterOptions, FormatterOptions> {
        val formatterOptions = formatterOptionsResolver.getWorkspaceFormattingOptions(workspaceFolderUri)
        val linterOptions = linterOptionsResolver.getLinterConfig(workspaceFolderUri)
        return Pair(linterOptions, formatterOptions)
    }

    fun findSourceDirURI(workspaceUri: URI): URI {
        val workspaceFolder = File(workspaceUri)

        val rellSrcFolder = workspaceFolder.resolve("rell/src")
        val rellFolder = workspaceFolder.resolve("rell")
        val srcFolder = workspaceFolder.resolve("src")
        val parentSrcFolder = findSrcParentDirectory(workspaceUri)

        val sourceFolder = when {
            rellSrcFolder.exists() && rellFolder.isDirectory -> rellSrcFolder
            rellFolder.exists() && rellFolder.isDirectory -> rellFolder
            srcFolder.exists() && srcFolder.isDirectory -> srcFolder
            parentSrcFolder != null -> parentSrcFolder
            else -> null
        }
        return sourceFolder?.toURI() ?: workspaceFolder.toURI()
    }

    private fun findSrcParentDirectory(uri: URI): File? {
        if (!uri.path.endsWith(".rell")) return null

        val path = Paths.get(uri)
        var depth = 0
        var currentPath = path
        while (currentPath.parent != null && depth < MAX_DEPTH) {
            depth++
            val srcDirectory = currentPath.resolveSibling("src")
            if (Files.exists(srcDirectory) && Files.isDirectory(srcDirectory)) {
                return srcDirectory.toFile()
            }
            currentPath = currentPath.parent
        }
        return null
    }

    fun reportDiagnostics(indexer: WorkspaceIndexer, fileUris: List<URI> = listOf()) {
        var issues = indexer.getAllIssues()
        if (fileUris.isNotEmpty()) {
            issues = issues.filter { (uri, _) -> fileUris.contains(uri) }
        }
        issues.forEach { (uri, issues) ->
            diagnosticsPublisher(uri, issues)
        }
    }

    fun getHoverDocumentation(params: HoverParams): MarkupContent {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return MarkupContent("plaintext", "")
        val indexer = getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return MarkupContent("plaintext", "")

        val symbolLocation = rellSymbolService.getSymbolLocationsWithSymbol(document, indexer, params.position)
        return MarkupContent("markdown", formatDocSymbol(symbolLocation?.second?.doc))
    }

    private fun reportDiagnostics(fileUris: List<URI>) {
        if (fileUris.isNotEmpty()) {
            for (indexer in indexers.values) {
                reportDiagnostics(indexer, fileUris)
            }
        }
    }

    fun didOpen(fileUri: URI, version: Int, content: String) {
        documentManager.openDocument(fileUri, version, content)
        val indexer = getIndexerFor(fileUri)
        indexer.updateFileUriResourceMap(fileUri, content)
        reportDiagnostics(indexer, listOf(fileUri))
    }

    // TODO: Revisit how we get the indexer. Would this approach work if the have two indexer active where one
    // is indexed from from a child folder from the other indexer.
    fun getIndexerFor(fileUri: URI): WorkspaceIndexer = getIndexerForOrNull(fileUri) ?: doSingleFileIndex(fileUri)

    fun getIndexerForOrNull(fileUri: URI): WorkspaceIndexer? {
        for (indexer in indexers.entries) {
            if (fileUri.path.startsWith(indexer.key.path)) {
                return indexer.value
            }
        }
        return null
    }

    private fun doSingleFileIndex(fileUri: URI): WorkspaceIndexer {
        val sourceDirUri = findSourceDirURI(fileUri)
        val projectRootUri = findProjectRootURI(sourceDirUri)

        val (linterOptions, formatterOptions) = getLinterAndFormatterOptions(sourceDirUri)

        val indexer = WorkspaceIndexer(
            sourceDirUri,
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions,
            projectRootUri
        )
        val cachedIndexer = if (indexCachingEnabled) indexCachingService.getWorkspaceIndexer(sourceDirUri) else null

        indexer.initialFileIndexBuild(cachedIndexer)
        indexers[sourceDirUri] = indexer
        reportDiagnostics(indexer)
        return indexer
    }

    private fun findProjectRootURI(sourceDirUri: URI): URI? {
        val parentDir = File(sourceDirUri).parentFile ?: return null
        val grandParentDir = parentDir.parentFile ?: return null

        return when {
            parentDir.resolve(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME).exists() -> parentDir.toURI()
            grandParentDir.resolve(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME)
                .exists() -> grandParentDir.toURI()

            else -> null
        }
    }

    fun didClose(fileUri: URI) {
        documentManager.closeDocument(fileUri)
    }

    fun didChangeTextDocumentContent(fileUri: URI, contentChanges: List<TextDocumentContentChangeEvent>) {
        val updatedDocument = documentManager.applyTextDocumentChanges(fileUri, contentChanges)
        val indexer = getIndexerFor(fileUri)
        indexer.updateFileUriResourceMap(fileUri, updatedDocument.content)
        reportDiagnostics(indexer, listOf(fileUri))
    }

    fun didChangeFiles(dirtyFiles: List<URI>, deletedFiles: List<URI>, updateAffectedFiles: Boolean = false) {
        val affectedUris = mutableSetOf<URI>()

        deletedFiles.forEach { uri ->
            getIndexerFor(uri).let { indexer ->
                if (updateAffectedFiles) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
                indexer.removeFileUriResourceMap(uri)
                diagnosticsPublisher(uri, listOf())
            }
        }

        dirtyFiles.forEach { uri ->
            getIndexerFor(uri).let { indexer ->
                indexer.updateFileUriResourceMap(uri)
                if (updateAffectedFiles) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
            }
        }
        affectedUris.removeAll((deletedFiles + dirtyFiles).toSet())
        affectedUris.forEach { uri ->
            getIndexerFor(uri).updateFileUriResourceMap(uri)
        }

        val allUris = listOf(dirtyFiles, deletedFiles, affectedUris).flatten()
        reportDiagnostics(allUris)
    }

    fun didChangeFolders(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        val deletedFiles = mutableListOf<URI>()
        val dirtyFiles = mutableListOf<URI>()
        deletedFolders.forEach { uri ->
            getIndexerForOrNull(uri)?.let { indexer ->
                deletedFiles.addAll(indexer.getFileUrisWithPrefix(uri))
            }
        }

        dirtyFolders.forEach { uri ->
            findRellFilesInWorkspace(File(uri), dirtyFiles)
        }

        // Need to do two passes of the files to guarantee that imports and modules are resolved correctly
        dirtyFiles.addAll(dirtyFiles)
        didChangeFiles(dirtyFiles, deletedFiles, true)
    }

    fun didSave(fileUri: URI) {
        val contents = documentManager.getOpenDocument(fileUri)
        if (contents == null) {
            logger.error { "The document $fileUri has not been opened." }
            return
        }
        val indexer = getIndexerFor(fileUri)
        indexer.let {
            val affectedUris = indexer.findAffectedFiles(fileUri)
            didChangeFiles(affectedUris.toList() + fileUri, listOf())
        }
    }

    fun getResource(fileUri: URI): Resource? {
        val indexer = getIndexerFor(fileUri)
        return indexer.getResource(fileUri)
    }

    fun getDefinitionLocations(
        fileUri: URI,
        position: Position
    ): Either<MutableList<out Location>, MutableList<out LocationLink>> {
        val indexer = getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return Either.forLeft(mutableListOf())
        return Either.forLeft(rellSymbolService.getSymbolLocations(document, indexer, position))
    }

    private fun getDefinitionLocationsAndSymbolInfo(
        fileUri: URI,
        position: Position
    ): Pair<Location, IdeSymbolInfo>? {
        val indexer = getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return null

        return rellSymbolService.getSymbolLocationsWithSymbol(document, indexer, position)
    }

    fun getDocumentSymbols(fileUri: URI): List<Either<SymbolInformation, DocumentSymbol>> {
        val resource = getResource(fileUri) ?: return listOf()
        val document = documentManager.getOpenDocument(fileUri) ?: return listOf()
        return rellSymbolService.getDocumentSymbols(fileUri, document, resource)
    }

    fun getReferenceLocations(fileUri: URI, position: Position?, includeDefinition: Boolean = true): List<Location> {
        val indexer = getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return listOf()

        val result: MutableList<Location> = mutableListOf()
        if (includeDefinition && position != null) {
            val locationIdeSymbolInfoPair = getDefinitionLocationsAndSymbolInfo(fileUri, position)
            if (locationIdeSymbolInfoPair != null &&
                locationIdeSymbolInfoPair.second.kind != IdeSymbolKind.DEF_IMPORT_MODULE
            ) {
                result.add(locationIdeSymbolInfoPair.first)
            }
        }

        result.addAll(rellReferenceService.getReferenceLocations(fileUri, document, indexer, position))
        return result
    }

    fun prepareRename(
        fileUri: URI,
        position: Position
    ): Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> {
        val indexer = getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri)
            ?: return Either3.forThird(PrepareRenameDefaultBehavior())

        val location = rellSymbolService.getSymbolLocationForRenaming(document, indexer, position)
            ?: return Either3.forThird(PrepareRenameDefaultBehavior())

        val placeholder = getPlaceholderText(document, indexer, fileUri, position)
        return if (placeholder.isNotEmpty()) {
            Either3.forSecond(PrepareRenameResult(location.range, placeholder))
        } else {
            Either3.forFirst(location.range)
        }
    }

    private fun getPlaceholderText(
        document: Document,
        indexer: WorkspaceIndexer,
        fileUri: URI,
        position: Position
    ): String {
        val resource = indexer.getResource(fileUri) ?: return ""
        val clickedSymbol = rellSymbolService.getSymbolForDocument(document, resource, position) ?: return ""
        return document.getTextIn(clickedSymbol.interval)
    }

    fun getCodeActions(fileUri: URI, range: Range): List<Either<Command, CodeAction>> {
        val indexer = getIndexerFor(fileUri)
        return CodeActionService.getCodeActions(fileUri, range, indexer)
    }

    fun getCodeActionForFile(fileUri: URI): CodeAction {
        val indexer = getIndexerFor(fileUri)
        return CodeActionService.getCodeActionForFile(fileUri, indexer)
    }

    fun rename(fileUri: URI, position: Position, newName: String): WorkspaceEdit {
        val indexer = getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return WorkspaceEdit()
        val (renamingTriggerSymbol, interval) = rellSymbolService.getSymbolInfoWithInterval(document, indexer, position)
            ?: return WorkspaceEdit()

        val oldName = document.getTextIn(interval)
        val locations = getReferenceLocations(fileUri, position, true)
        val changes = mutableMapOf<String, MutableList<TextEdit>>()

        locations.forEach { location ->
            val change = renameLocation(
                indexer,
                location,
                renamingTriggerSymbol,
                oldName,
                newName,
            )
            changes[location.uri]?.add(change) ?: changes.put(location.uri, mutableListOf(change))
        }
        return WorkspaceEdit(changes)
    }

    private fun renameLocation(
        indexer: WorkspaceIndexer,
        location: Location,
        renamingTriggerSymbol: IdeSymbolInfo,
        oldName: String,
        newName: String,
    ): TextEdit {
        val locationFileUri = URI(location.uri)
        val symbolToRename = rellSymbolService.getSymbolInfoWithInterval(
            documentManager.getDocument(locationFileUri),
            indexer,
            location.range.start
        )?.ideSymbolInfo ?: return TextEdit(location.range, newName)

        return if (symbolToRename.isTypeDefinition()) {
            val resource = indexer.getResource(locationFileUri)
            val fullNameWithRange = resource?.let { findAnonAttrFullName(it, location) }
            val text = determineNewText(renamingTriggerSymbol, symbolToRename, oldName, newName, fullNameWithRange)
            TextEdit(fullNameWithRange?.range ?: location.range, text)
        } else {
            TextEdit(location.range, newName)
        }
    }

    private fun determineNewText(
        renamingTriggerSymbol: IdeSymbolInfo,
        symbolToRename: IdeSymbolInfo,
        oldName: String,
        newName: String,
        fullNameWithRange: FullNameWithRange?
    ): String {
        return when {
            renamingTriggerSymbol.isTypeReference() -> {
                "$oldName: ${updateNewName(newName, fullNameWithRange)}"
            }

            renamingTriggerSymbol.isNotTypeReference() -> {
                when {
                    symbolToRename.containsParameter(oldName) -> {
                        if (renamingTriggerSymbol.isLocalParam()) {
                            "$newName: ${updateOldName(oldName, fullNameWithRange)}"
                        } else {
                            "$oldName: ${updateNewName(newName, fullNameWithRange)}"
                        }
                    }

                    symbolToRename.containsAttribute(oldName) -> {
                        "$oldName: ${updateNewName(newName, fullNameWithRange)}"
                    }

                    else -> newName
                }
            }

            else -> {
                "$newName: ${updateOldName(oldName, fullNameWithRange)}"
            }
        }
    }

    private fun IdeSymbolInfo.isTypeDefinition(): Boolean =
        this.kind in TYPE_DEFINITIONS && this.defId != null && this.link != null

    private fun IdeSymbolInfo.isReference(): Boolean = this.link != null

    private fun IdeSymbolInfo.isExplicitTypeReference(): Boolean =
        this.kind in TYPE_DEFINITIONS && this.defId == null

    private fun IdeSymbolInfo.isTypeReference(): Boolean = this.isReference() && this.isExplicitTypeReference()

    private fun IdeSymbolInfo.isNotTypeReference(): Boolean = !this.isReference() && !this.isExplicitTypeReference()

    private fun IdeSymbolInfo.containsParameter(name: String): Boolean =
        this.defId?.encode()?.contains("param[$name]") == true

    private fun IdeSymbolInfo.containsAttribute(name: String): Boolean =
        this.defId?.encode()?.contains("attr[$name]") == true

    private fun IdeSymbolInfo.isLocalParam(): Boolean = this.kind == IdeSymbolKind.LOC_PARAMETER

    private fun updateNewName(newName: String, fullNameWithRange: FullNameWithRange?): String {
        return if (fullNameWithRange?.isQualifiedName() == true) {
            val partialFullName = fullNameWithRange.fullName.dropLast(1).joinToString(separator = ".")
            "$partialFullName.$newName"
        } else {
            newName
        }
    }

    private fun updateOldName(oldName: String, fullNameWithRange: FullNameWithRange?): String {
        return if (fullNameWithRange?.isQualifiedName() == true) {
            fullNameWithRange.fullName.joinToString(separator = ".")
        } else {
            oldName
        }
    }

    data class FullNameWithRange(val fullName: List<String>, val range: Range) {
        fun isQualifiedName(): Boolean = fullName.size > 1
    }

    private fun findAnonAttrFullName(resource: Resource, location: Location): FullNameWithRange? {
        val visitor = object : RellBaseVisitor<Unit>() {
            var result: FullNameWithRange? = null
            override fun visitRuleX_AnonAttrHeader(ctx: RellParser.RuleX_AnonAttrHeaderContext) {
                val startPos = ctx.start.line
                if (startPos == location.range.start.line + 1 &&
                    ctx.stop.charPositionInLine == location.range.start.character
                ) {
                    val range = Range(
                        Position(
                            ctx.ruleX_QualifiedNameNode().start.line - 1,
                            ctx.ruleX_QualifiedNameNode().start.charPositionInLine
                        ),
                        Position(
                            ctx.ruleX_QualifiedNameNode().stop.line - 1,
                            ctx.ruleX_QualifiedNameNode().stop.charPositionInLine +
                                ctx.ruleX_QualifiedNameNode().stop.text.length
                        )
                    )
                    result = FullNameWithRange(
                        ctx.ruleX_QualifiedNameNode().ruleX_NameNode().map { it.text },
                        range
                    )
                }
            }
        }
        visitor.visit(resource.parseTree)
        return visitor.result
    }

    fun getIndexerForConfigFile(uri: URI): WorkspaceIndexer? {
        val configFolder = uri.toPath().parent
        return indexers.values.find {
            it.workspaceUri.toPath().startsWith(configFolder)
        }
    }

    fun getCompletions(fileUri: URI, position: Position): List<CompletionItem> {
        val document = documentManager.getOpenDocument(fileUri) ?: return listOf()
        val indexer = getIndexerForOrNull(fileUri) ?: return listOf()
        val offset = document.getOffSet(position)
        val trimPrefixDot = shouldTrimPrefixDot(document, offset)

        return completionService.getCompletions(fileUri, offset, indexer, trimPrefixDot)
    }

    private fun shouldTrimPrefixDot(doc: Document, offset: Int): Boolean {
        return doc.previousNonLetterChar(offset) == '.'
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val MAX_DEPTH = 5
    }
}
