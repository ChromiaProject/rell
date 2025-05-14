/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.C_EntityAttrRef
import net.postchain.rell.base.compiler.base.expr.C_ExprHint
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolKind

private class C_EntityAttributeClause(
    private val defCtx: C_DefinitionContext,
    private val sysAttr: C_SysAttribute?,
    private val persistent: Boolean,
) {
    private val msgCtx = defCtx.msgCtx

    private val defs = mutableListOf<AttrDef>()

    fun addDefinition(def: AttrDef) {
        defs.add(def)
    }

    fun compile(index: Int, keys: Collection<R_Key>, indices: Collection<R_Index>): C_CompiledAttribute {
        val keyIndexKind = when {
            keys.isNotEmpty() -> R_KeyIndexKind.KEY
            indices.isNotEmpty() -> R_KeyIndexKind.INDEX
            else -> null
        }

        val (primaryDefs, secondaryDefs) = defs.partition { it.primary }

        if (sysAttr != null) {
            val ideKind = C_AttrUtils.getIdeSymbolKind(persistent, sysAttr.mutable, keyIndexKind)
            val rAttr = sysAttr.compile(index, persistent)
            processOtherDefs(primaryDefs, secondaryDefs, sysAttr.type, ideKind, rAttr.ideInfo)
            return C_CompiledAttribute(null, rAttr)
        }

        val mainDef = primaryDefs.firstOrNull() ?: secondaryDefs.first()
        val conflictDefs = primaryDefs.drop(1)
        val otherDefs = secondaryDefs.filter { it !== mainDef }

        val mutable = mainDef.attrDef.mutablePos != null
        val ideKind = C_AttrUtils.getIdeSymbolKind(persistent, mutable, keyIndexKind)

        val docSymLate = C_LateInit<DocSymbol?>(C_CompilerPass.DOCS, null)
        val ideData = C_GlobalAttrHeaderIdeData(IdeSymbolCategory.ATTRIBUTE, ideKind, null, docSymLate.getter)

        val mainHeader = mainDef.attrHeader.compile(defCtx, false, ideData)
        val type = mainHeader.type ?: R_CtErrorType

        checkAttrType(mainDef, type)
        processOtherDefs(conflictDefs, otherDefs, type, ideKind, mainHeader.ideInfo)

        if (defCtx.definitionType.isEntityOrObject()) {
            S_EntityDefinition.checkAttrNameLen(msgCtx, mainDef.name)
        }

        val comment = (primaryDefs + secondaryDefs).firstNotNullOfOrNull { it.comment }
        val exprGetter = processAttrExpr(mainDef.name, mainDef.attrDef.expr, type)

        defCtx.executor.onPass(C_CompilerPass.DOCS) {
            val docExpr = exprGetter?.get()?.vDocExpr
            val docDec = makeDocDeclaration(mainDef.name, type, mutable, docExpr, keys, indices)
            val docSym = makeDocSymbol(mainDef.name, docDec, comment)
            docSymLate.set(docSym)
        }

        val rAttr = R_Attribute(
            index,
            mainDef.name.rName,
            type,
            mutable = mutable,
            keyIndexKind = keyIndexKind,
            ideInfo = mainHeader.ideInfo,
            docSourcePos = mainDef.name.pos.toDocPos(),
            exprGetter = exprGetter?.transform { it.rDefaultValue },
        )

        return C_CompiledAttribute(mainDef.attrHeader.pos, rAttr)
    }

    private fun makeDocDeclaration(
        name: C_Name,
        type: R_Type,
        mutable: Boolean,
        vDocExpr: V_Expr?,
        keys: Collection<R_Key>,
        indices: Collection<R_Index>,
    ): DocDeclaration {
        var keyIndexKind: R_KeyIndexKind? = null
        var docKeys = keys
        var docIndices = indices

        when {
            keys.any { it.attribs.size == 1 } -> {
                keyIndexKind = R_KeyIndexKind.KEY
                docKeys = keys.filter { it.attribs.size != 1 }
            }
            indices.any { it.attribs.size == 1 } -> {
                keyIndexKind = R_KeyIndexKind.INDEX
                docIndices = indices.filter { it.attribs.size != 1 }
            }
        }

        val docType = L_TypeUtils.docType(type.mType)
        val docExpr = if (vDocExpr == null) null else C_DocUtils.docExpr(vDocExpr)

        return DocDeclaration_EntityAttribute(name.rName, docType, mutable, keyIndexKind, docExpr, docKeys, docIndices)
    }

    private fun makeDocSymbol(name: C_Name, docDec: DocDeclaration, comment: S_Comment?): DocSymbol {
        val docKind = when (defCtx.definitionType) {
            C_DefinitionType.STRUCT -> DocSymbolKind.STRUCT_ATTR
            C_DefinitionType.OBJECT -> DocSymbolKind.OBJECT_ATTR
            else -> DocSymbolKind.ENTITY_ATTR
        }

        val defName = defCtx.cDefName.toPath().subName(name.rName)

        return defCtx.symCtx.makeDocSymbol(
            kind = docKind,
            symbolName = DocSymbolName.global(defName.module.module, defName.qualifiedName.str()),
            declaration = docDec,
            comment = comment,
        )
    }

    private fun processOtherDefs(
        conflictDefs: List<AttrDef>,
        secondaryDefs: List<AttrDef>,
        type: R_Type,
        ideKind: IdeSymbolKind,
        mainIdeInfo: C_IdeSymbolInfo,
    ) {
        for (def in conflictDefs) {
            val ideData = C_GlobalAttrHeaderIdeData(IdeSymbolCategory.ATTRIBUTE, ideKind, null)
            val attrHeader = def.attrHeader.compile(defCtx, false, ideData)
            processAttrExpr(def.name, def.attrDef.expr, attrHeader.type)
            C_Errors.errDuplicateAttribute(msgCtx, def.name)
        }

        for (def in secondaryDefs) {
            processSecondaryDef(def, type, ideKind, mainIdeInfo)
        }
    }

    private fun processAttrExpr(name: C_Name, expr: S_Expr?, type: R_Type?): C_LateGetter<C_DefaultValue>? {
        if (expr == null) {
            return null
        }

        val exprType = type ?: R_CtErrorType
        val errValue = R_DefaultValue(C_ExprUtils.errorRExpr(exprType), false)
        val errDocExpr = C_ExprUtils.errorVExpr(defCtx.initExprCtx, expr.startPos, exprType)
        val late = C_LateInit(C_CompilerPass.EXPRESSIONS, C_DefaultValue(errValue, errDocExpr))

        defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
            val exprCtx = defCtx.initExprCtx
            val vExpr0 = expr.compile(exprCtx, C_ExprHint.ofType(exprType)).vExpr()
            val adapter = C_Types.adaptSafe(msgCtx, exprType, vExpr0.type, name.pos) {
                "attr_type:$name" toCodeMsg "Default value type mismatch for '$name'"
            }
            val vExpr = adapter.adaptExpr(exprCtx, vExpr0)
            val rExpr = vExpr.toRExpr()
            val rDefaultValue = R_DefaultValue(rExpr, vExpr0.info.hasDbModifications)
            late.set(C_DefaultValue(rDefaultValue, vExpr0))
        }

        return late.getter
    }

    private fun processSecondaryDef(
        def: AttrDef,
        rPrimaryType: R_Type,
        ideKind: IdeSymbolKind,
        mainIdeInfo: C_IdeSymbolInfo,
    ) {
        val ideData = C_GlobalAttrHeaderIdeData(IdeSymbolCategory.ATTRIBUTE, ideKind, mainIdeInfo)
        val header = def.attrHeader.compile(defCtx, true, ideData)

        if (header.type != null && header.type != rPrimaryType && header.isExplicitType) {
            C_Errors.errTypeMismatch(msgCtx, def.name.pos, header.type, rPrimaryType) {
                "entity:attr:type_diff" toCodeMsg
                        "Type of attribute '${def.name}' differs from the primary definition"
            }
        }

        if (def.attrDef.mutablePos != null) {
            msgCtx.error(def.attrDef.mutablePos, "entity:attr:mutable_not_primary:${def.name}",
                    "Mutability can be specified only in the primary definition of the attribute '${def.name}'")
        }

        if (def.attrDef.expr != null) {
            msgCtx.error(def.attrDef.expr.startPos, "entity:attr:expr_not_primary:${def.name}",
                    "Default value can be specified only in the primary definition of the attribute '${def.name}'")
        }

        checkAttrType(def, header.type)
        processAttrExpr(def.name, def.attrDef.expr, header.type)
    }

    private fun checkAttrType(attr: AttrDef, type: R_Type?) {
        val isEntity = defCtx.definitionType.isEntityOrObject()
        if (isEntity && type != null) {
            val allowed = type.sqlAdapter.isAllowedForEntityAttributes(defCtx.globalCtx.compilerOptions)
            if (!allowed) {
                val name = attr.name
                val typeStr = type.strCode()
                msgCtx.error(name.pos, "entity_attr_type:$name:$typeStr",
                    "Attribute '$name' has unallowed type: $typeStr")
            }
        }
    }

    class AttrDef(
        val attrDef: S_AttributeDefinition,
        val attrHeader: C_AttrHeaderHandle,
        val primary: Boolean,
        val comment: S_Comment?,
    ) {
        val name = attrHeader.name
    }

    private class C_DefaultValue(
        val rDefaultValue: R_DefaultValue,
        val vDocExpr: V_Expr,
    )
}

class C_EntityContext(
    val defCtx: C_DefinitionContext,
    private val entityName: String,
    private val logAnnotation: Boolean,
    sysAttributes: List<C_SysAttribute>,
    private val persistent: Boolean,
) {
    val msgCtx = defCtx.msgCtx

    private val attrMap = mutableMapOf<R_Name, C_EntityAttributeClause>()

    private val keys = mutableListOf<R_Key>()
    private val indices = mutableListOf<R_Index>()
    private val uniqueKeys = mutableSetOf<Set<R_Name>>()
    private val uniqueIndices = mutableSetOf<Set<R_Name>>()

    init {
        for (sysAttr in sysAttributes) {
            attrMap[sysAttr.name] = C_EntityAttributeClause(defCtx, sysAttr, persistent)
        }
    }

    fun addAttribute(
        attrDef: S_AttributeDefinition,
        attrHeader: C_AttrHeaderHandle,
        primary: Boolean,
        comment: S_Comment?,
    ) {
        validateAttr(attrDef, attrHeader)

        val clause = attrMap.computeIfAbsent(attrHeader.rName) { C_EntityAttributeClause(defCtx, null, persistent) }
        clause.addDefinition(C_EntityAttributeClause.AttrDef(attrDef, attrHeader, primary, comment))
    }

    private fun validateAttr(attrDef: S_AttributeDefinition, attrHeader: C_AttrHeaderHandle) {
        val nameStr = attrHeader.rName.str

        val defType = defCtx.definitionType
        if (defType.isEntityOrObject() && !C_EntityAttrRef.isAllowedRegularAttrName(nameStr)) {
            msgCtx.error(attrHeader.pos, "unallowed_attr_name:$nameStr", "Unallowed attribute name: '$nameStr'")
        }

        if (attrDef.mutablePos != null && logAnnotation) {
            val ann = C_Constants.LOG_ANNOTATION
            msgCtx.error(attrHeader.pos, "entity_attr_mutable_log:$entityName:$nameStr",
                    "Entity '$entityName' cannot have mutable attributes because of the '$ann' annotation")
        }

        if (defType == C_DefinitionType.OBJECT && attrDef.expr == null) {
            msgCtx.error(attrHeader.pos, "object_attr_novalue:$entityName:$nameStr",
                    "Object attribute '$entityName.$nameStr' must have a default value")
        }
    }

    fun addKey(pos: S_Pos, attrs: ImmList<R_Name>) {
        addUniqueKeyIndex(pos, uniqueKeys, attrs, R_KeyIndexKind.KEY)
        keys.add(R_Key(attrs))
    }

    fun addIndex(pos: S_Pos, attrs: ImmList<R_Name>) {
        addUniqueKeyIndex(pos, uniqueIndices, attrs, R_KeyIndexKind.INDEX)
        indices.add(R_Index(attrs))
    }

    fun createEntityBody(): R_EntityBody {
        val cAttributes = compileAttributes()
        val rAttributes = cAttributes.mapValuesToImmMap { it.value.rAttr }
        return R_EntityBody(keys.toImmList(), indices.toImmList(), rAttributes)
    }

    fun createStructBody(): Map<R_Name, C_CompiledAttribute> {
        return compileAttributes()
    }

    private fun compileAttributes(): Map<R_Name, C_CompiledAttribute> {
        val keyMap = keyIndexMap(keys)
        val indexMap = keyIndexMap(indices)

        val cAttrs = mutableListOf<C_CompiledAttribute>()

        for ((name, attr) in attrMap) {
            val attrKeys = keyMap.get(name)
            val attrIndices = indexMap.get(name)
            val compiledAttr = attr.compile(cAttrs.size, attrKeys, attrIndices)
            cAttrs.add(compiledAttr)
        }

        return cAttrs.associateByToImmMap { it.rAttr.rName }
    }

    private fun <T: R_KeyIndex> keyIndexMap(list: List<T>): Multimap<R_Name, T> {
        return list.flatMap { r -> r.attribs.map { attr -> attr to r } }.toImmMultimap()
    }

    private fun addUniqueKeyIndex(pos: S_Pos, set: MutableSet<Set<R_Name>>, names: List<R_Name>, kind: R_KeyIndexKind) {
        if (defCtx.definitionType == C_DefinitionType.OBJECT) {
            msgCtx.error(pos, "object:key_index:${entityName}:$kind", "Object cannot have ${kind.nameMsg.normal}")
            return
        }

        val nameSet = names.toSet()
        if (!set.add(nameSet)) {
            val nameLst = names.sorted()
            val errCode = "entity:key_index:dup_attr:$kind"
            val errMsg = "Duplicate ${kind.nameMsg.normal}"
            msgCtx.error(pos, "$errCode:${nameLst.joinToString(",")}", "$errMsg: ${nameLst.joinToString()}")
        }
    }
}
