/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import com.github.h0tk3y.betterParse.lexer.TokenMatchesSequence
import com.github.h0tk3y.betterParse.parser.ErrorResult
import com.github.h0tk3y.betterParse.parser.ParseException
import com.github.h0tk3y.betterParse.parser.Parsed
import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.ast.S_ReplCommand
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_AttrUtils
import net.postchain.rell.base.compiler.base.def.C_SysAttribute
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_StmtContext
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.compiler.base.module.C_ModuleKey
import net.postchain.rell.base.compiler.base.module.S_DefinitionContext
import net.postchain.rell.base.compiler.base.utils.C_Parser.parse
import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.compiler.parser.RellTokenizerException
import net.postchain.rell.base.compiler.parser.S_Grammar
import net.postchain.rell.base.compiler.parser.antlr.RellAntlrVisitor
import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ParameterDefaultValueExpr
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDeclarationProto
import net.postchain.rell.base.utils.doc.DocDeclarationProto_Entity
import net.postchain.rell.base.utils.doc.DocModifiers
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.misc.ParseCancellationException
import java.util.*

typealias C_CodeMsgSupplier = () -> C_CodeMsg

class C_PosCodeMsg(val pos: S_Pos, val code: String, val msg: String) {
    constructor(pos: S_Pos, codeMsg: C_CodeMsg): this(pos, codeMsg.code, codeMsg.msg)
}

class C_Error: RuntimeException {
    val pos: S_Pos
    val code: String
    val errMsg: String

    private constructor(pos: S_Pos, code: String, errMsg: String): super("$pos $errMsg") {
        this.pos = pos
        this.code = code
        this.errMsg = errMsg
    }

    private constructor(pos: S_Pos, codeMsg: C_CodeMsg): this(pos, codeMsg.code, codeMsg.msg)

    companion object {
        fun more(pos: S_Pos, code: String, errMsg: String) = C_Error(pos, code, errMsg)
        fun stop(pos: S_Pos, code: String, errMsg: String) = C_Error(pos, code, errMsg)
        fun stop(pos: S_Pos, codeMsg: C_CodeMsg) = C_Error(pos, codeMsg)
        fun stop(err: C_PosCodeMsg) = C_Error(err.pos, err.code, err.msg)
        fun other(pos: S_Pos, code: String, errMsg: String) = C_Error(pos, code, errMsg)
    }
}

sealed class C_ValueOrError<T>
class C_ValueOrError_Value<T>(val value: T): C_ValueOrError<T>()
class C_ValueOrError_Error<T>(val error: C_PosCodeMsg): C_ValueOrError<T>()

// Operations and queries defined in Postchain (StandardOpsGTXModule). Shall be reserved (not allowed) in Rell.
object C_ReservedMountNames {
    val OPERATIONS: ImmSet<MountName> = listOf(
        "__nop",
        "nop",
        "timeb",
    ).map { MountName.of(it) }.toImmSet()

    val QUERIES: ImmSet<MountName> = listOf(
        "last_block_info",
        "tx_confirmation_time",
    ).map { MountName.of(it) }.toImmSet()
}

class C_ParameterDefaultValue(
    private val pos: S_Pos,
    private val paramName: Name,
    val rExprGetter: C_LateGetter<R_Expr>,
    private val initFrameGetter: C_LateGetter<R_CallFrame>,
    val rGetter: C_LateGetter<R_DefaultValue>,
) {
    fun createArgumentExpr(ctx: C_ExprContext, callPos: S_Pos, paramType: R_Type): V_Expr {
        val dbModRes = ctx.getDbModificationRestriction()
        if (dbModRes != null) {
            ctx.executor.onPass(C_CompilerPass.VALIDATION) {
                val rDefaultValue = rGetter.get()
                if (rDefaultValue.isDbModification) {
                    val code = "${dbModRes.code}:param:$paramName"
                    val msg = "${dbModRes.msg} (default value of parameter '$paramName')"
                    ctx.msgCtx.error(callPos, code, msg)
                }
            }
        }

        return V_ParameterDefaultValueExpr(ctx, pos, paramType, callPos.toFilePos(), initFrameGetter, rExprGetter)
    }
}

object C_Utils {
    fun effectiveMemberType(formalType: R_Type, safe: Boolean): R_Type {
        return if (!safe || formalType is R_NullableType || formalType == R_NullType) {
            formalType
        } else {
            R_NullableType(formalType)
        }
    }

    fun checkUnitType(pos: S_Pos, type: R_Type, errSupplier: C_CodeMsgSupplier) {
        C_Errors.check(type != R_UnitType, pos, errSupplier)
    }

    fun checkUnitType(msgMgr: C_MessageManager, pos: S_Pos, type: R_Type, code: String, msg: String): Boolean {
        return checkUnitType(msgMgr, pos, type) { code toCodeMsg msg }
    }

    fun checkUnitType(msgMgr: C_MessageManager, pos: S_Pos, type: R_Type, errSupplier: C_CodeMsgSupplier): Boolean {
        if (type == R_UnitType) {
            val codeMsg = errSupplier()
            msgMgr.error(pos, codeMsg)
            return false
        }
        return true
    }

    fun checkMapKeyType(ctx: C_DefinitionContext, pos: S_Pos, type: R_Type) {
        checkMapKeyType0(ctx.appCtx, pos, type, "expr_map_keytype", "as a map key")
    }

    fun checkGroupValueType(appCtx: C_AppContext, pos: S_Pos, type: R_Type) {
        checkMapKeyType0(appCtx, pos, type, "expr_at_group_type", "for grouping")
    }

    private fun checkMapKeyType0(appCtx: C_AppContext, pos: S_Pos, type: R_Type, errCode: String, errMsg: String) {
        appCtx.executor.onPass(C_CompilerPass.VALIDATION) {
            if (type.completeFlags().mutable) {
                val typeStr = type.strCode()
                appCtx.msgCtx.error(pos, "$errCode:$typeStr", "Mutable type cannot be used $errMsg: $typeStr")
            }
        }
    }

    fun createBlockEntity(appCtx: C_AppContext, chain: R_ExternalChainRef?): R_EntityDefinition {
        val header = C_SysEntityHeader(
            C_Constants.BLOCK_ENTITY_RNAME,
            chain,
            R_EntitySqlMapping_Block(chain),
            appCtx.symCtxProvider.getDocSymbolFactory(),
        )

        val attrs = listOf(
            header.attrMaker.make("block_height", R_IntegerType, isKey = true),
            header.attrMaker.make("block_rid", R_ByteArrayType, isKey = true),
            header.attrMaker.make("timestamp", R_IntegerType),
        )

        return createSysEntity(appCtx, header, attrs)
    }

    fun createTransactionEntity(
        appCtx: C_AppContext,
        chain: R_ExternalChainRef?,
        blockEntity: R_EntityDefinition,
    ): R_EntityDefinition {
        val header = C_SysEntityHeader(
            C_Constants.TRANSACTION_ENTITY_RNAME,
            chain,
            R_EntitySqlMapping_Transaction(chain),
            appCtx.symCtxProvider.getDocSymbolFactory(),
        )

        val attrs = listOf(
            header.attrMaker.make("tx_rid", R_ByteArrayType, isKey = true),
            header.attrMaker.make("tx_hash", R_ByteArrayType),
            header.attrMaker.make("tx_data", R_ByteArrayType),
            header.attrMaker.make("block", blockEntity.type, sqlMapping = "block_iid"),
        )

        return createSysEntity(appCtx, header, attrs)
    }

    private fun createSysEntity(
        appCtx: C_AppContext,
        header: C_SysEntityHeader,
        attrs: List<C_SysAttribute>,
    ): R_EntityDefinition {
        val simpleName = header.simpleName

        val flags = EntityFlags(
            isObject = false,
            canCreate = false,
            canUpdate = false,
            canDelete = false,
            gtv = true,
            log = false,
        )

        val externalEntity = if (header.chain == null) null else R_ExternalEntity(header.chain, false)

        val entity = createEntity(
            appCtx,
            C_DefinitionType.ENTITY,
            header.rDefBase,
            simpleName,
            flags,
            header.sqlMapping,
            externalEntity,
        )

        val rAttrMap = attrs
            .mapIndexed { i, attr ->
                val rAttr = attr.compile(i, true)
                rAttr.rName to rAttr
            }
            .toImmMap()

        appCtx.executor.onPass(C_CompilerPass.MEMBERS) {
            setEntityBody(entity, R_EntityBody(immListOf(), immListOf(), rAttrMap))
        }

        return entity
    }

    private class C_SysEntityHeader(
        val simpleName: Name,
        val chain: R_ExternalChainRef?,
        val sqlMapping: R_EntitySqlMapping,
        docFactory: C_DocSymbolFactory,
    ) {
        val moduleKey = ModuleKey(C_LibUtils.DEFAULT_MODULE, chain?.name)

        val rDefBase: R_DefinitionBase = let {
            val mountName = sqlMapping.mountName
            val rQualifiedName = QualifiedName.of(listOf(simpleName))

            val cDefBase = createDefBase(
                C_DefinitionType.ENTITY,
                IdeSymbolKind.DEF_ENTITY,
                moduleKey,
                C_StringQualifiedName.of(rQualifiedName),
                mountName,
                docFactory,
                commentProvider = C_SymbolContext.CommentProvider.NULL,
            )

            val docDeclaration = DocDeclarationProto_Entity(DocModifiers.NONE, rQualifiedName.last)
            val docGetter = cDefBase.docGetter(C_LateGetter.const(docDeclaration))
            cDefBase.rBase(R_CallFrame.NONE_INIT_FRAME_GETTER, null, docGetter)
        }

        val attrMaker = C_SysAttribute.Maker(docFactory, rDefBase.defName)
    }

    fun createEntity(
        appCtx: C_AppContext,
        defType: C_DefinitionType,
        defBase: R_DefinitionBase,
        name: Name,
        flags: EntityFlags,
        sqlMapping: R_EntitySqlMapping,
        externalEntity: R_ExternalEntity?,
    ): R_EntityDefinition {
        val rEntity = R_EntityDefinition(appCtx.executor, defBase, defType, name, flags, sqlMapping, externalEntity)
        appCtx.defsAdder.addStruct(rEntity.mirrorStructs.immutable)
        appCtx.defsAdder.addStruct(rEntity.mirrorStructs.mutable)
        return rEntity
    }

    fun setEntityBody(entity: R_EntityDefinition, body: R_EntityBody) {
        entity.setBody(body)
        setEntityMirrorStructAttrs(body, entity, false)
        setEntityMirrorStructAttrs(body, entity, true)
    }

    private fun setEntityMirrorStructAttrs(body: R_EntityBody, entity: R_EntityDefinition, mutable: Boolean) {
        val struct = entity.mirrorStructs.getStruct(mutable)

        val structAttrs = body.attributes.mapValuesToImmMap { (_, attr) ->
            val ideKind = C_AttrUtils.getIdeSymbolKind(false, mutable, attr.keyIndexKind)
            val ideInfo = attr.ideInfo.update(kind = ideKind, defId = null)
            attr.copy(mutable = mutable, ideInfo = ideInfo)
        }

        struct.setAttributes(structAttrs)
    }

    fun createSysStruct(name: String): R_Struct {
        return R_Struct(
            name,
            rDefBase = null,
            mirrorStructs = null,
        )
    }

    fun createSysEnum(name: String, values: List<String>): R_EnumDefinition {
        val moduleName = RELL_MODULE_NAME
        val moduleKey = ModuleKey(moduleName, null)
        val qName = C_StringQualifiedName.of(name)

        val cDefBase = createDefBase(
            C_DefinitionType.ENUM,
            IdeSymbolKind.DEF_ENUM,
            moduleKey,
            qName,
            mountName = null,
            C_DocSymbolFactory.NONE,
            commentProvider = C_SymbolContext.CommentProvider.NULL,
        )

        val docGetter = cDefBase.docGetter(C_LateGetter.const(DocDeclarationProto.NONE))
        val defBase = cDefBase.rBase(R_CallFrame.NONE_INIT_FRAME_GETTER, null, docGetter)

        val attrs = values.mapIndexed { index, valueName ->
            val rName = Name.of(valueName)
            val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.MEM_ENUM_VALUE)
            R_EnumAttr(rName, index, ideInfo, null)
        }.toImmList()

        return R_EnumDefinition(defBase, attrs)
    }

    private val RELL_MODULE_NAME = ModuleName.of("rell")

    fun createSysQuery(
        executor: C_CompilerExecutor,
        simpleName: String,
        type: R_Type,
        fn: Any,
        params: ImmList<R_FunctionParam> = immListOf(),
    ): R_QueryDefinition {
        val moduleName = RELL_MODULE_NAME
        val moduleKey = ModuleKey(moduleName, null)
        val qName = C_StringQualifiedName.of(simpleName)
        val mountName = MountName(moduleName.parts + Name.of(simpleName))

        val cDefBase = createDefBase(
            C_DefinitionType.QUERY,
            IdeSymbolKind.DEF_QUERY,
            moduleKey,
            qName,
            mountName,
            C_DocSymbolFactory.NONE,
            commentProvider = C_SymbolContext.CommentProvider.NULL,
        )

        val docGetter = cDefBase.docGetter(C_LateGetter.const(DocDeclarationProto.NONE))
        val defBase = cDefBase.rBase(R_CallFrame.NONE_INIT_FRAME_GETTER, null, docGetter)

        val query = R_QueryDefinition(executor, defBase, mountName)

        executor.onPass(C_CompilerPass.EXPRESSIONS) {
            // Key the sys query by its mount name — unique per query and stable across JVMs,
            // unlike the opaque `fn` object's class name.
            val body = R_SysQueryBody(type, params, fn, key = "sys_query:${mountName.str()}")
            query.setBody(body)
        }

        return query
    }

    fun createDefBase(
        defType: C_DefinitionType,
        ideKind: IdeSymbolKind,
        moduleKey: ModuleKey,
        qualifiedName: C_StringQualifiedName,
        mountName: MountName?,
        docFactory: C_DocSymbolFactory,
        commentProvider: C_SymbolContext.CommentProvider,
    ): C_CommonDefinitionBase {
        val cDefName = createDefName(moduleKey, qualifiedName)
        val defName = cDefName.toRDefName()
        val defId = DefinitionId(defName.module, defName.qualifiedName)
        return C_CommonDefinitionBase(defType, ideKind, defId, cDefName, defName, mountName, docFactory, commentProvider)
    }

    private fun createDefName(module: ModuleKey, qualifiedName: C_StringQualifiedName): C_DefinitionName {
        val defModule = C_DefinitionModuleName(module.name.str(), module.externalChain)
        return C_DefinitionName(defModule, qualifiedName)
    }

    fun fullName(namespacePath: String?, name: String): String {
        return if (namespacePath == null) name else "$namespacePath.$name"
    }

    fun appLevelName(module: C_ModuleKey, name: C_QualifiedName): String {
        val nameStr = name.str()
        return DefinitionName.appLevelName(module.keyStr(), nameStr)
    }

    fun checkGtvCompatibility(
            msgCtx: C_MessageContext,
            pos: S_Pos,
            type: R_Type,
            from: Boolean,
            errCode: String,
            errMsg: String
    ) {
        // Skip the check if the type is already an error — avoids cascading "result_nogtv" errors.
        if (type.isError()) return
        val flags = type.completeFlags()
        val flag = if (from) flags.gtv.fromGtv else flags.gtv.toGtv
        if (!flag) {
            val fullMsg = "$errMsg is not Gtv-compatible: ${type.strCode()}"
            msgCtx.error(pos, "$errCode:${type.strCode()}", fullMsg)
        }
    }

}

sealed class C_ParserResult<out T> {
    abstract fun getAst(): T
    abstract val tokenStream: CommonTokenStream?
}

private class C_SuccessParserResult<T>(
    private val ast: T,
    override val tokenStream: CommonTokenStream,
): C_ParserResult<T>() {
    override fun getAst() = ast
}

private class C_ErrorParserResult<T>(
    val errors: List<C_Error>,
    val eof: Boolean,
    override val tokenStream: CommonTokenStream? = null,
    private val partialAst: T? = null,
): C_ParserResult<T>() {
    val error: C_Error get() = errors.first()
    override fun getAst() = throw errors.first()
    fun getPartialAst(): T? = partialAst
}

data class C_ParserFilePath(val sourcePath: C_SourcePath, val idePath: IdeFilePath) {
    override fun toString() = sourcePath.toString()
}

object C_Parser {
    private val REPL_PARSER_PATH: C_ParserFilePath = let {
        val path = C_SourcePath.parse("<console>")
        C_ParserFilePath(path, IdeSourcePathFilePath(path))
    }

    val REPL_NULL_POS: S_Pos = S_BasicPos(REPL_PARSER_PATH, 0, 1, 1)

    fun parse(
        filePath: C_SourcePath,
        idePath: IdeFilePath,
        sourceCode: String,
        version: R_LangVersion = RellVersions.VERSION,
    ): S_RellFile {
        val parserPath = C_ParserFilePath(filePath, idePath)
        val res = parseAntlr(parserPath, sourceCode) { p -> p.file() }
        val tree = res.getAst()
        return try {
            RellAntlrVisitor(parserPath, tokenStream = res.tokenStream).toFile(tree)
        } catch (e: RellTokenizerException) {
            throw e.toCError()
        }
    }

    /**
     * Legacy `better-parse`-based parser, used only by differential tests that compare the
     * ANTLR pipeline against the original parser output. Production callers must use [parse].
     * Throws [C_Error] on syntax errors (matches the legacy single-error contract).
     */
    fun parseLegacy(
        filePath: C_SourcePath,
        idePath: IdeFilePath,
        sourceCode: String,
        version: R_LangVersion = RellVersions.VERSION,
    ): S_RellFile {
        val parserPath = C_ParserFilePath(filePath, idePath)
        val tokenProd = RellTokenizer(version).tokenProducer(parserPath, sourceCode)
        return try {
            val seq = TokenMatchesSequence(tokenProd)
            when (val result = S_Grammar.rootParser.tryParse(seq, 0)) {
                is ErrorResult -> throw ParseException(result)
                is Parsed -> result.value
            }
        } catch (e: RellTokenizerException) {
            throw e.toCError()
        } catch (_: ParseException) {
            val pos = tokenProd.getEndPos()
            throw C_Error.other(pos, "syntax", "Syntax error")
        }
    }

    /**
     * Parse a Rell source file and report all syntax errors via [errorReporter] instead of throwing
     * on the first one. The SLL fast path is tried first; on prediction conflict the LL fallback
     * runs with a full error-recovery strategy that collects every error in the file.
     *
     * Returns a [S_RellFile] AST built from the (possibly partial) parse tree, or `null` if parsing
     * fails so catastrophically that no usable tree is produced. Callers can use this for IDE
     * diagnostics where reporting all errors at once is required.
     */
    fun parseWithErrors(
        filePath: C_SourcePath,
        idePath: IdeFilePath,
        sourceCode: String,
        errorReporter: (C_Error) -> Unit,
    ): S_RellFile? {
        val parserPath = C_ParserFilePath(filePath, idePath)
        val res = parseAntlr(parserPath, sourceCode) { p -> p.file() }
        val (tree, tokenStream) = when (res) {
            is C_SuccessParserResult -> res.getAst() to res.tokenStream
            is C_ErrorParserResult -> {
                res.errors.forEach(errorReporter)
                (res.getPartialAst() ?: return null) to (res.tokenStream ?: return null)
            }
        }
        return try {
            RellAntlrVisitor(parserPath, tokenStream = tokenStream).toFile(tree)
        } catch (e: RellTokenizerException) {
            errorReporter(e.toCError())
            null
        } catch (e: RuntimeException) {
            // Visitor methods use `!!`, `as TerminalNode`, `children[i]`, `ctx.children.first { ... }`.
            // On deeply degenerate partial trees these can throw NPE / ClassCastException /
            // NoSuchElementException / IndexOutOfBoundsException. Catch only those — internal-invariant
            // violations (`error(...)` / `require(...)` → IllegalStateException / IllegalArgumentException)
            // must propagate as real bugs.
            if (e !is NullPointerException
                && e !is ClassCastException
                && e !is NoSuchElementException
                && e !is IndexOutOfBoundsException
            ) {
                throw e
            }
            val pos = tree.start?.let { S_BasicPos(parserPath, it.startIndex, it.line, it.charPositionInLine + 1) }
                ?: S_BasicPos(parserPath, 0, 1, 1)
            errorReporter(C_Error.other(pos, "syntax:visitor", "Syntax error (parse tree recovery failed: ${e.javaClass.simpleName})"))
            null
        }
    }

    fun parseRepl(code: String): S_ReplCommand {
        val res = parseAntlr(REPL_PARSER_PATH, code) { p -> p.replCommand() }
        val tree = res.getAst()
        return try {
            RellAntlrVisitor(REPL_PARSER_PATH, tokenStream = res.tokenStream).toReplCommand(tree)
        } catch (e: RellTokenizerException) {
            throw e.toCError()
        }
    }

    fun checkEofErrorRepl(code: String): C_Error? =
        when (val res = parseAntlr(REPL_PARSER_PATH, code) { p -> p.replCommand() }) {
            is C_SuccessParserResult -> null
            is C_ErrorParserResult -> if (res.eof) res.error else null
        }

    private fun <R : ParserRuleContext> parseAntlr(
        filePath: C_ParserFilePath,
        sourceCode: String,
        ruleEntry: (RellParser) -> R,
    ): C_ParserResult<R> {
        // First pass: SLL prediction with BailErrorStrategy. This is the fast path; for valid input
        // it is 3–4× faster than LL. On any prediction conflict it bails immediately and we fall
        // back to LL with full error reporting.
        val sllResult = tryParseSll(filePath, sourceCode, ruleEntry)
        if (sllResult != null) return C_SuccessParserResult(sllResult.first, sllResult.second)

        // Second pass: LL prediction with an error-collecting listener. This produces user-visible
        // syntax errors and EOF detection for the REPL continuation prompt.
        return parseLl(filePath, sourceCode, ruleEntry)
    }

    private fun <R : ParserRuleContext> tryParseSll(
        filePath: C_ParserFilePath,
        sourceCode: String,
        ruleEntry: (RellParser) -> R,
    ): Pair<R, CommonTokenStream>? {
        val (lexer, parser) = newParser(filePath, sourceCode)
        lexer.removeErrorListeners()
        lexer.addErrorListener(BailLexerErrorListener)
        parser.removeErrorListeners()
        parser.errorHandler = BailErrorStrategy()
        parser.interpreter.predictionMode = PredictionMode.SLL
        val tree = try {
            ruleEntry(parser)
        } catch (_: ParseCancellationException) {
            return null
        } catch (_: RecognitionException) {
            return null
        }
        return tree to (parser.tokenStream as CommonTokenStream)
    }

    private fun <R : ParserRuleContext> parseLl(
        filePath: C_ParserFilePath,
        sourceCode: String,
        ruleEntry: (RellParser) -> R,
    ): C_ParserResult<R> {
        val (lexer, parser) = newParser(filePath, sourceCode)
        val collector = AntlrErrorCollector(filePath, sourceCode)
        lexer.removeErrorListeners()
        lexer.addErrorListener(collector)
        parser.removeErrorListeners()
        parser.addErrorListener(collector)
        parser.interpreter.predictionMode = PredictionMode.LL
        val tree = try {
            ruleEntry(parser)
        } catch (e: RecognitionException) {
            // The default error strategy reports via the listener and recovers; but if no listener
            // fired (shouldn't happen), surface the exception position directly.
            if (collector.errors.isEmpty()) {
                val tok = e.offendingToken
                val pos = if (tok != null) {
                    S_BasicPos(filePath, tok.startIndex, tok.line, tok.charPositionInLine + 1)
                } else {
                    S_BasicPos(filePath, sourceCode.length, 1, 1)
                }
                val eof = tok != null && tok.type == Token.EOF
                return C_ErrorParserResult(listOf(C_Error.other(pos, "syntax", "Syntax error")), eof)
            }
            null
        }
        val tokens = parser.tokenStream as CommonTokenStream
        return if (collector.errors.isNotEmpty()) {
            val errs = collector.errors.map { it.error }
            C_ErrorParserResult(errs, collector.errors.first().eof, tokens, partialAst = tree)
        } else {
            C_SuccessParserResult(tree!!, tokens)
        }
    }

    private fun newParser(filePath: C_ParserFilePath, sourceCode: String): Pair<RellLexer, RellParser> {
        val cs = CharStreams.fromString(sourceCode, filePath.sourcePath.toString())
        val lexer = RellLexer(cs)
        val tokens = CommonTokenStream(lexer)
        return lexer to RellParser(tokens)
    }
}

/**
 * Lexer listener used during the SLL fast path: any lexer-level error throws
 * `ParseCancellationException`, which `tryParseSll` catches to fall back to LL. The grammar's
 * `ERROR : . ;` catch-all means most malformed input flows through the parser's listener instead,
 * but this guard ensures any future grammar change that drops the catch-all does not silently
 * leak lexer errors to stderr via ANTLR's default `ConsoleErrorListener`.
 */
private object BailLexerErrorListener : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?,
    ) {
        throw ParseCancellationException(msg)
    }
}

private class AntlrErrorEntry(val error: C_Error, val eof: Boolean)

/**
 * Collects every syntax error reported by ANTLR's default error strategy during the LL pass. The
 * full list is preserved (one entry per `syntaxError` callback) so callers can surface every error
 * in a file at once — the better-parse path used to bail on the first error and only reported one.
 */
private class AntlrErrorCollector(
    private val filePath: C_ParserFilePath,
    private val sourceCode: String,
) : BaseErrorListener() {
    private val _errors = mutableListOf<AntlrErrorEntry>()
    val errors: List<AntlrErrorEntry> get() = _errors

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?,
    ) {
        val token = offendingSymbol as? Token
        // Lexer errors deliver `offendingSymbol == null`; recover the offset from the lexer-level
        // exception or the recognizer's input stream position so IDE diagnostics get a real char
        // offset, not end-of-file. `line` / `charPositionInLine` are valid in either case.
        val offset = when {
            token != null -> token.startIndex
            e is LexerNoViableAltException -> e.startIndex
            recognizer is Lexer -> recognizer.inputStream?.index() ?: sourceCode.length
            else -> sourceCode.length
        }
        val pos = S_BasicPos(filePath, offset.coerceAtLeast(0), line, charPositionInLine + 1)
        val eof = token != null && token.type == Token.EOF
        val error = C_Error.other(pos, "syntax", "Syntax error: $msg")
        _errors.add(AntlrErrorEntry(error, eof))
    }
}

object C_GraphUtils {
    fun <T: Any> findCycles(graph: Map<T, Collection<T>>): List<List<T>> {
        val graphEx = graph.mapValues { vert ->
            vert.value.mapToImmList { 0 to it }
        }

        val cyclesEx = findCyclesEx(graphEx)

        return cyclesEx.mapToImmList { cycle ->
            cycle.mapToImmList { it.second }
        }
    }

    /** Returns some, not all cycles (at least one cycle for each cyclic vertex). */
    fun <V, E> findCyclesEx(graph: Map<V, Collection<Pair<E, V>>>): List<List<Pair<E, V>>> {
        class VertEntry<E, V>(val vert: V, val edge: E?, val enter: Boolean, val parent: VertEntry<E, V>?)

        val queue = LinkedList<VertEntry<E, V>>()
        val visiting = mutableSetOf<V>()
        val visited = mutableSetOf<V>()
        val cycles = mutableListOf<List<Pair<E, V>>>()

        for (vert in graph.keys) {
            queue.add(VertEntry(vert, null, true, null))
        }

        while (!queue.isEmpty()) {
            val entry = queue.remove()

            if (!entry.enter) {
                check(visiting.remove(entry.vert))
                check(visited.add(entry.vert))
                continue
            } else if (entry.vert in visited) {
                check(entry.vert !in visiting)
                continue
            } else if (entry.vert in visiting) {
                var cycleEntry = entry
                val cycle = mutableListOf<Pair<E, V>>()
                while (true) {
                    cycle.add(cycleEntry.edge!! to cycleEntry.vert)
                    cycleEntry = cycleEntry.parent
                    checkNotNull(cycleEntry)
                    if (cycleEntry.vert == entry.vert) break
                }
                cycles.add(cycle.toList())
                continue
            }

            queue.addFirst(VertEntry(entry.vert, entry.edge, false, entry.parent))
            visiting.add(entry.vert)

            for ((adjEdge, adjVert) in graph.getValue(entry.vert)) {
                queue.addFirst(VertEntry(adjVert, adjEdge, true, entry))
            }
        }

        return cycles.toImmList()
    }

    fun <T> topologicalSort(graph: Map<T, Collection<T>>): List<T> {
        class VertEntry<T>(val vert: T, val enter: Boolean, val parent: VertEntry<T>?)

        val queue = LinkedList<VertEntry<T>>()
        val visiting = mutableSetOf<T>()
        val visited = mutableSetOf<T>()
        val result = mutableListOf<T>()

        for (vert in graph.keys) {
            queue.add(VertEntry(vert, true, null))
        }

        while (!queue.isEmpty()) {
            val entry = queue.remove()

            if (!entry.enter) {
                check(visiting.remove(entry.vert))
                check(visited.add(entry.vert))
                result.add(entry.vert)
                continue
            } else if (entry.vert in visited) {
                check(entry.vert !in visiting)
                continue
            }

            check(entry.vert !in visiting) // Cycle
            queue.addFirst(VertEntry(entry.vert, false, entry.parent))
            visiting.add(entry.vert)

            for (adjVert in graph.getValue(entry.vert)) {
                queue.addFirst(VertEntry(adjVert, true, entry))
            }
        }

        return result.toList()
    }

    fun <T: Any> findCyclicVertices(graph: Map<T, Collection<T>>): Set<T> =
        findCycles(graph).flatMapTo(mutableSetOf()) { it }

    fun <T> transpose(graph: Map<T, Collection<T>>): Map<T, Collection<T>> = buildMap<T, MutableCollection<T>> {
        for (vert in graph.keys) {
            this[vert] = mutableSetOf()
        }

        for (vert in graph.keys) {
            for (adjVert in graph.getValue<T, Collection<T>>(vert)) {
                val set = getOrPut(adjVert) { mutableSetOf() }
                set.add(vert)
            }
        }
    }

    fun <T> closure(vertices: Collection<T>, graph: (T) -> Collection<T>): Collection<T> {
        val queue = LinkedList(vertices)
        val visited = mutableSetOf<T>()

        while (!queue.isEmpty()) {
            val vert = queue.remove()
            if (visited.add(vert)) {
                val adjVerts = graph(vert)
                for (adjVert in adjVerts) {
                    queue.add(adjVert)
                }
            }
        }

        return visited.toList()
    }
}

sealed class C_LateGetter<out T> {
    abstract fun get(): T

    fun <R> transform(transformer: (T) -> R): C_LateGetter<R> = C_TransformingLateGetter(this, transformer)

    companion object {
        private val NULL: C_LateGetter<Any?> = C_ConstLateGetter(null)

        @Suppress("UNCHECKED_CAST")
        fun <T> const(value: T): C_LateGetter<T> {
            return when (value) {
                null -> NULL as C_LateGetter<T>
                else -> C_ConstLateGetter(value)
            }
        }

        fun <T> list(getters: ImmList<C_LateGetter<T>>): C_LateGetter<ImmList<T>> = C_ListLateGetter(getters)
    }
}

fun <T, R> ImmList<C_LateGetter<T>>.transform(transformer: (ImmList<T>) -> R): C_LateGetter<R> {
    val listGetter = C_LateGetter.list(this)
    return listGetter.transform(transformer)
}

private class C_ConstLateGetter<T>(private val value: T): C_LateGetter<T>() {
    override fun get() = value
}

private class C_DirectLateGetter<T>(private val init: C_LateInit<T>): C_LateGetter<T>() {
    override fun get() = init.get()
}

private class C_ListLateGetter<T>(private val getters: ImmList<C_LateGetter<T>>): C_LateGetter<ImmList<T>>() {
    private val lazyValue: ImmList<T> by lazy {
        getters.mapToImmList { it.get() }
    }

    override fun get() = lazyValue
}

private class C_TransformingLateGetter<in T, R>(
    private val getter: C_LateGetter<T>,
    private val transformer: (T) -> R,
): C_LateGetter<R>() {
    private val lazyValue: R by lazy {
        val value = getter.get()
        transformer(value)
    }

    override fun get(): R = lazyValue
}

class C_LateInit<T>(private val executor: C_CompilerExecutor, val pass: C_CompilerPass, fallback: T) {
    private var value: T = noValue()

    @Suppress("CanBePrimaryConstructorProperty")
    private var fallback: T = fallback

    init {
        executor.checkPass(null, pass.prev())
        executor.onClose {
            if (value === NOVALUE) value = this.fallback
            this.fallback = noValue()
        }
    }

    val getter: C_LateGetter<T> = C_DirectLateGetter(this)

    fun set(value: T, allowEarly: Boolean = false) {
        val minPass = if (allowEarly) null else pass
        executor.checkPass(minPass, pass)
        check(this.value === NOVALUE) { "value already set" }
        this.value = value
        fallback = noValue()
    }

    fun get(): T {
        executor.checkPass(pass.next(), null)
        if (value === NOVALUE) {
            check(fallback !== NOVALUE)
            value = fallback
        }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    private fun noValue(): T = NOVALUE as T

    companion object {
        private val NOVALUE: Any = Any()
    }
}

fun <T> C_CompilerExecutor.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    C_LateInit(this, pass, fallback)

fun <T> C_MountContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

fun <T> C_DefinitionContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

fun <T> C_ExprContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

fun <T> C_FunctionContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

fun <T> C_BlockContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

fun <T> C_FrameContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

fun <T> C_StmtContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

fun <T> S_DefinitionContext.lateInit(pass: C_CompilerPass, fallback: T): C_LateInit<T> =
    executor.lateInit(pass, fallback)

class C_ListBuilder<T>(proto: List<T> = immListOf()) {
    private val list = proto.toMutableList()
    private var commit: ImmList<T>? = null

    val size: Int get() = list.size

    fun add(value: T) {
        checkNull(commit)
        list.add(value)
    }

    fun commit(): ImmList<T> {
        if (commit == null) {
            commit = list.toImmList()
        }
        return commit!!
    }
}

class C_UidGen<T>(private val factory: (Long, String) -> T) {
    private var nextUid = 0L

    fun next(name: String): T {
        val uid = nextUid++
        return factory(uid, name)
    }
}

sealed class C_Symbol(val code: String) {
    abstract fun msgNormal(): String

    fun msgCapital(): String = msgNormal().capitalizeEx()

    final override fun toString() = msgNormal()
}

class C_Symbol_Name(private val name: Name): C_Symbol(name.str) {
    override fun msgNormal() = "name '$name'"
}

object C_Symbol_Placeholder: C_Symbol(C_Constants.AT_PLACEHOLDER) {
    override fun msgNormal() = "symbol '$code'"
}
