/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import com.github.h0tk3y.betterParse.lexer.TokenMatchesSequence
import com.github.h0tk3y.betterParse.lexer.TokenProducer
import com.github.h0tk3y.betterParse.parser.*
import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.ast.S_ReplCommand
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_AttrUtils
import net.postchain.rell.base.compiler.base.def.C_SysAttribute
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.compiler.base.module.C_ModuleKey
import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.compiler.parser.RellTokenizerException
import net.postchain.rell.base.compiler.parser.S_Grammar
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ParameterDefaultValueExpr
import net.postchain.rell.base.lib.type.R_ByteArrayType
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDeclarationProto
import net.postchain.rell.base.utils.doc.DocDeclarationProto_Entity
import net.postchain.rell.base.utils.doc.DocModifiers
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import java.util.*

typealias C_CodeMsgSupplier = () -> C_CodeMsg

class C_CodeMsg(val code: String, val msg: String) {
    override fun toString() = code
}

infix fun String.toCodeMsg(that: String): C_CodeMsg = C_CodeMsg(this, that)

class C_PosCodeMsg(val pos: S_Pos, val code: String, val msg: String) {
    constructor(pos: S_Pos, codeMsg: C_CodeMsg): this(pos, codeMsg.code, codeMsg.msg)
}

class C_CommonError(val code: String, val msg: String): RuntimeException(msg) {
    constructor(codeMsg: C_CodeMsg): this(codeMsg.code, codeMsg.msg)
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

object C_Constants {
    const val LOG_ANNOTATION = "log"
    const val MODULE_ARGS_STRUCT = "module_args"

    const val AT_PLACEHOLDER = "$"

    const val TRANSACTION_ENTITY = "transaction"
    const val BLOCK_ENTITY = "block"

    val TRANSACTION_ENTITY_RNAME = R_Name.of(TRANSACTION_ENTITY)
    val BLOCK_ENTITY_RNAME = R_Name.of(BLOCK_ENTITY)
}

// Operations and queries defined in Postchain (StandardOpsGTXModule). Shall be reserved (not allowed) in Rell.
object C_ReservedMountNames {
    val OPERATIONS: ImmSet<R_MountName> = listOf(
        "__nop",
        "nop",
        "timeb",
    ).map { R_MountName.of(it) }.toImmSet()

    val QUERIES: ImmSet<R_MountName> = listOf(
        "last_block_info",
        "tx_confirmation_time",
    ).map { R_MountName.of(it) }.toImmSet()
}

internal class C_ParameterDefaultValue(
    private val pos: S_Pos,
    private val paramName: R_Name,
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

internal object C_Utils {
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

        val flags = R_EntityFlags(
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
        val simpleName: R_Name,
        val chain: R_ExternalChainRef?,
        val sqlMapping: R_EntitySqlMapping,
        docFactory: C_DocSymbolFactory,
    ) {
        val moduleKey = R_ModuleKey(C_LibUtils.DEFAULT_MODULE, chain?.name)

        val rDefBase: R_DefinitionBase = let {
            val mountName = sqlMapping.mountName
            val rQualifiedName = R_QualifiedName.of(simpleName)

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
        name: R_Name,
        flags: R_EntityFlags,
        sqlMapping: R_EntitySqlMapping,
        externalEntity: R_ExternalEntity?,
    ): R_EntityDefinition {
        val rEntity = R_EntityDefinition(defBase, defType, name, flags, sqlMapping, externalEntity)
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
            name.toGtv(),
            rDefBase = null,
            mirrorStructs = null,
        )
    }

    private val RELL_MODULE_NAME = R_ModuleName.of("rell")

    fun createSysQuery(
        executor: C_CompilerExecutor,
        simpleName: String,
        type: R_Type,
        fn: R_SysFunction,
        params: ImmList<R_FunctionParam> = immListOf(),
    ): R_QueryDefinition {
        val moduleName = RELL_MODULE_NAME
        val moduleKey = R_ModuleKey(moduleName, null)
        val qName = C_StringQualifiedName.of(simpleName)
        val mountName = R_MountName(moduleName.parts + R_Name.of(simpleName))

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

        val query = R_QueryDefinition(defBase, mountName)

        executor.onPass(C_CompilerPass.EXPRESSIONS) {
            val body = R_SysQueryBody(type, params, fn)
            query.setBody(body)
        }

        return query
    }

    internal fun createDefBase(
        defType: C_DefinitionType,
        ideKind: IdeSymbolKind,
        moduleKey: R_ModuleKey,
        qualifiedName: C_StringQualifiedName,
        mountName: R_MountName?,
        docFactory: C_DocSymbolFactory,
        commentProvider: C_SymbolContext.CommentProvider,
    ): C_CommonDefinitionBase {
        val cDefName = createDefName(moduleKey, qualifiedName)
        val defName = cDefName.toRDefName()
        val defId = R_DefinitionId(defName.module, defName.qualifiedName)
        return C_CommonDefinitionBase(defType, ideKind, defId, cDefName, defName, mountName, docFactory, commentProvider)
    }

    private fun createDefName(module: R_ModuleKey, qualifiedName: C_StringQualifiedName): C_DefinitionName {
        val defModule = C_DefinitionModuleName(module.name.str(), module.externalChain)
        return C_DefinitionName(defModule, qualifiedName)
    }

    fun fullName(namespacePath: String?, name: String): String {
        return if (namespacePath == null) name else "$namespacePath.$name"
    }

    fun appLevelName(module: C_ModuleKey, name: C_QualifiedName): String {
        val nameStr = name.str()
        return R_DefinitionName.appLevelName(module.keyStr(), nameStr)
    }

    fun checkGtvCompatibility(
            msgCtx: C_MessageContext,
            pos: S_Pos,
            type: R_Type,
            from: Boolean,
            errCode: String,
            errMsg: String
    ) {
        val flags = type.completeFlags()
        val flag = if (from) flags.gtv.fromGtv else flags.gtv.toGtv
        if (!flag) {
            val fullMsg = "$errMsg is not Gtv-compatible: ${type.strCode()}"
            msgCtx.error(pos, "$errCode:${type.strCode()}", fullMsg)
        }
    }

    fun getFullNameLazy(type: R_Type, name: R_Name): LazyString {
        return LazyString.of {
            val baseType = C_Types.removeNullable(type)
            "${baseType.strCode()}.$name"
        }
    }
}

sealed class C_ParserResult<T> {
    abstract fun getAst(): T
}

private class C_SuccessParserResult<T>(private val ast: T): C_ParserResult<T>() {
    override fun getAst() = ast
}

private class C_ErrorParserResult<T>(val error: C_Error, val eof: Boolean): C_ParserResult<T>() {
    override fun getAst() = throw error
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
        val res = parse0(parserPath, sourceCode, version, S_Grammar.rootParser)
        val ast = res.getAst()
        return ast
    }

    internal fun parseRepl(code: String): S_ReplCommand {
        val res = parse0(REPL_PARSER_PATH, code, RellVersions.VERSION, S_Grammar.replParser)
        val ast = res.getAst()
        return ast
    }

    fun checkEofErrorRepl(code: String): C_Error? {
        val res = parse0(REPL_PARSER_PATH, code, RellVersions.VERSION, S_Grammar.replParser)
        return when (res) {
            is C_SuccessParserResult -> null
            is C_ErrorParserResult -> if (res.eof) res.error else null
        }
    }

    private fun <T> parse0(
        filePath: C_ParserFilePath,
        sourceCode: String,
        version: R_LangVersion,
        parser: Parser<T>,
    ): C_ParserResult<T> {
        // The syntax error position returned by the parser library is misleading: if there is an error in the middle
        // of an operation, it returns the position of the beginning of the operation.
        // Following workaround handles this by tracking the position of the farthest reached token (seems to work fine).

        val tokenProd = RellTokenizer(version).tokenProducer(filePath, sourceCode)

        return try {
            val ast = parseToEnd(tokenProd, parser)
            C_SuccessParserResult(ast)
        } catch (e: RellTokenizerException) {
            val error = e.toCError()
            C_ErrorParserResult(error, e.eof)
        } catch (e: ParseException) {
            val pos = tokenProd.getEndPos()
            val eof = pos.offset() >= sourceCode.length
            val error = C_Error.other(pos, "syntax", "Syntax error")
            C_ErrorParserResult(error, eof)
        }
    }

    private fun <T> parseToEnd(tokenProducer: TokenProducer, parser: Parser<T>): T {
        val seq = TokenMatchesSequence(tokenProducer)
        val result = parser.tryParse(seq, 0)

        return when (result) {
            is ErrorResult -> throw ParseException(result)
            is Parsed -> {
                checkUnparsedRemainder(seq, result)
                result.value
            }
        }
    }

    private fun checkUnparsedRemainder(seq: TokenMatchesSequence, result: Parsed<*>) {
        var pos = result.nextPosition
        while (true) {
            val match = seq[pos]
            match ?: break
            if (!match.type.ignored) {
                throw throw ParseException(UnparsedRemainder(match))
            }
            ++pos
        }
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
                    check(cycleEntry != null)
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

    fun <T: Any> findCyclicVertices(graph: Map<T, Collection<T>>): List<T> {
        val cycles = findCycles(graph)
        val cyclicVertices = mutableSetOf<T>()
        for (cycle in cycles) {
            cyclicVertices.addAll(cycle)
        }
        return cyclicVertices.toList()
    }

    fun <T> transpose(graph: Map<T, Collection<T>>): Map<T, Collection<T>> {
        val mut = mutableMapOf<T, MutableCollection<T>>()

        for (vert in graph.keys) {
            mut[vert] = mutableSetOf()
        }

        for (vert in graph.keys) {
            for (adjVert in graph.getValue(vert)) {
                val set = mut.computeIfAbsent(adjVert) { mutableSetOf() }
                set.add(vert)
            }
        }

        return mut.mapValues { (_, v) -> v.toList() }.toMap()
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

private class C_LateInitContext(executor: C_CompilerExecutor) {
    private var internals: Internals? = Internals(executor)

    fun onDestroy(code: () -> Unit) {
        val ints = internals
        check(ints != null)
        ints.uninits.add(code)
    }

    fun checkPass(minPass: C_CompilerPass?, maxPass: C_CompilerPass?) {
        val ints = internals
        if (ints != null) {
            ints.executor.checkPass(minPass, maxPass)
        } else {
            C_CompilerExecutor.checkPass(C_CompilerPass.LAST, minPass, maxPass)
        }
    }

    private fun destroy() {
        val ints = internals
        check(ints != null)
        internals = null

        for (code in ints.uninits) {
            code()
        }
        ints.uninits.clear()
    }

    private class Internals(val executor: C_CompilerExecutor) {
        val uninits = mutableListOf<() -> Unit>()
    }

    companion object {
        private val LOCAL_CTX = ThreadLocalContext<C_LateInitContext>()

        fun inContext() = LOCAL_CTX.getOpt() != null
        fun getContext() = LOCAL_CTX.get()

        fun <T> runInContext(executor: C_CompilerExecutor, code: () -> T): T {
            val ctx = C_LateInitContext(executor)
            try {
                val res = LOCAL_CTX.set(ctx, code)
                return res
            } finally {
                ctx.destroy()
            }
        }
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

private class C_TransformingLateGetter<T, R>(
    private val getter: C_LateGetter<T>,
    private val transformer: (T) -> R,
): C_LateGetter<R>() {
    private val lazyValue: R by lazy {
        val value = getter.get()
        transformer(value)
    }

    override fun get(): R = lazyValue
}

class C_LateInit<T>(val pass: C_CompilerPass, fallback: T) {
    private val ctx = C_LateInitContext.getContext()

    private var value: T = noValue()

    @Suppress("CanBePrimaryConstructorProperty")
    private var fallback: T = fallback

    init {
        ctx.checkPass(null, pass.prev())
        ctx.onDestroy {
            if (value === NOVALUE) value = this.fallback
            this.fallback = noValue()
        }
    }

    val getter: C_LateGetter<T> = C_DirectLateGetter(this)

    fun set(value: T, allowEarly: Boolean = false) {
        val minPass = if (allowEarly) null else pass
        ctx.checkPass(minPass, pass)
        check(this.value === NOVALUE) { "value already set" }
        this.value = value
        fallback = noValue()
    }

    fun get(): T {
        ctx.checkPass(pass.next(), null)
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

        fun <T> context(executor: C_CompilerExecutor, code: () -> T): T {
            return C_LateInitContext.runInContext(executor, code)
        }

        private fun checkPass(minPass: C_CompilerPass?, maxPass: C_CompilerPass?) {
            val ctx = C_LateInitContext.getContext()
            ctx.checkPass(minPass, maxPass)
        }

        fun checkPass(pass: C_CompilerPass) {
            checkPass(pass, pass)
        }
    }
}

class C_ListBuilder<T>(proto: List<T> = immListOf()) {
    private val list = proto.toMutableList()
    private var commit: ImmList<T>? = null

    val size: Int get() = list.size

    fun add(value: T) {
        check(commit == null)
        list.add(value)
    }

    fun commit(): ImmList<T> {
        if (commit == null) {
            commit = list.toImmList()
        }
        return commit!!
    }
}

class C_MapBuilder<K: Any, V: Any>(proto: Map<K, V> = immMapOf()) {
    private val map = proto.toMutableMap()
    private var commit: Map<K, V>? = null

    fun put(key: K, value: V) {
        check(commit == null)
        check(key !in map)
        map[key] = value
    }

    fun commit(): Map<K, V> {
        if (commit == null) {
            commit = map.toImmMap()
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

class C_Symbol_Name(private val name: R_Name): C_Symbol(name.str) {
    override fun msgNormal() = "name '$name'"
}

object C_Symbol_Placeholder: C_Symbol(C_Constants.AT_PLACEHOLDER) {
    override fun msgNormal() = "symbol '$code'"
}
