/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.*
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.fn.C_FunctionUtils
import net.postchain.rell.base.compiler.base.modifier.*
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.namespace.C_NamespacePropertyContext
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueEvalContext
import net.postchain.rell.base.lib.Lib_OpContext
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.*

sealed class S_AttrHeader: S_Node() {
    abstract fun discoverVar(): R_Name
    abstract fun compile(ctx: C_DefinitionContext): C_AttrHeaderHandle
    abstract fun ideOutlineTreeNodeName(): S_Name

    fun compile(ctx: C_DefinitionContext, canInferType: Boolean, ideData: C_AttrHeaderIdeData): C_AttrHeader {
        val handle = compile(ctx)
        return handle.compile(ctx, canInferType, ideData)
    }

    companion object {
        fun checkUnitType(msgCtx: C_MessageContext, pos: S_Pos, rType: R_Type, cName: C_Name): R_Type {
            return C_Types.checkNotUnit(msgCtx, pos, rType, cName.str) { "attr_var" toCodeMsg "attribute or variable" }
        }
    }
}

class S_NamedAttrHeader(private val name: S_Name, private val type: S_Type): S_AttrHeader() {
    override fun discoverVar(): R_Name {
        return name.getRNameSpecial()
    }

    override fun compile(ctx: C_DefinitionContext): C_AttrHeaderHandle {
        val nameHand = name.compile(ctx.symCtx, def = true)
        val rType = type.compileOpt(ctx) ?: R_CtErrorType
        val rResType = checkUnitType(ctx.msgCtx, type.pos, rType, nameHand.name)
        return C_NamedAttrHeaderHandle(nameHand, rResType)
    }

    override fun ideOutlineTreeNodeName() = name
}

class S_AnonAttrHeader(private val typeName: S_QualifiedName, private val nullable: Boolean): S_AttrHeader() {
    override fun discoverVar(): R_Name {
        return typeName.last.getRNameSpecial()
    }

    override fun compile(ctx: C_DefinitionContext): C_AttrHeaderHandle {
        val typeNameHand = typeName.compile(ctx.symCtx, def = true)
        return C_AnonAttrHeaderHandle(ctx.nsCtx, typeNameHand, nullable)
    }

    override fun ideOutlineTreeNodeName() = typeName.last
}

sealed class S_RelClause: S_Node() {
    abstract fun compile(ctx: C_EntityContext)
    abstract fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder)
}

class S_AttributeClause(private val attr: S_AttributeDefinition): S_RelClause() {
    override fun compile(ctx: C_EntityContext) {
        val attrHeader = attr.header.compile(ctx.defCtx)
        ctx.addAttribute(attr, attrHeader, true)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val name = attr.header.ideOutlineTreeNodeName()
        b.node(this, name, IdeOutlineNodeType.ATTRIBUTE)
    }
}

class S_KeyIndexClause(val pos: S_Pos, val kind: R_KeyIndexKind, val attrs: List<S_AttributeDefinition>): S_RelClause() {
    override fun compile(ctx: C_EntityContext) {
        val cAttrs = attrs.map {
            val cHeader = it.header.compile(ctx.defCtx)
            AttrRec(cHeader, it)
        }

        for (attr in cAttrs) {
            ctx.addAttribute(attr.sAttr, attr.header, false)
        }

        val nameSet = mutableSetOf<R_Name>()
        for (attr in cAttrs) {
            val name = attr.header.rName
            C_Errors.check(ctx.msgCtx, nameSet.add(name), attr.header.pos) {
                "entity_keyindex_dup:$name" toCodeMsg "Duplicate attribute: '$name'"
            }
        }

        if (cAttrs.size > 1) {
            cAttrs.all { it.sAttr.checkMultiAttrKeyIndex(ctx.msgCtx, kind, it.header.rName) }
        }

        val attrNames = cAttrs.map { it.header.rName }

        when (kind) {
            R_KeyIndexKind.KEY -> ctx.addKey(pos, attrNames)
            R_KeyIndexKind.INDEX -> ctx.addIndex(pos, attrNames)
        }
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        for (attr in attrs) {
            val name = attr.header.ideOutlineTreeNodeName()
            b.node(attr, name, IdeOutlineNodeType.KEY_INDEX)
        }
    }

    private class AttrRec(val header: C_AttrHeaderHandle, val sAttr: S_AttributeDefinition)
}

class S_AttributeDefinition(val mutablePos: S_Pos?, val header: S_AttrHeader, val expr: S_Expr?): S_Node() {
    fun checkMultiAttrKeyIndex(msgCtx: C_MessageContext, kind: R_KeyIndexKind, name: R_Name): Boolean {
        return if (mutablePos != null) {
            errKeyIndexTooComplex(msgCtx, kind, name, mutablePos, "mutable")
            false
        } else if (expr != null) {
            errKeyIndexTooComplex(msgCtx, kind, name, expr.startPos, "expr")
            false
        } else {
            true
        }
    }

    private fun errKeyIndexTooComplex(
            msgCtx: C_MessageContext,
            kind: R_KeyIndexKind,
            name: R_Name,
            pos: S_Pos,
            reasonCode: String
    ) {
        msgCtx.error(pos, "attr:key_index:too_complex:$name:$kind:$reasonCode",
                "${kind.nameMsg.capital} definition is too complex; write each attribute definition separately " +
                        "and use only attribute names in the index clause")
    }
}

abstract class S_Definition(val kwPos: S_Pos, val modifiers: S_Modifiers): S_Node() {
    val startPos = modifiers.pos ?: kwPos

    abstract fun compile(ctx: S_DefinitionContext): C_MidModuleMember?

    abstract fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder)

    open fun ideGetImportedModules(moduleName: R_ModuleName, res: MutableSet<R_ModuleName>) {
    }

    protected fun checkSysMountNameConflict(
            ctx: C_MountContext,
            pos: S_Pos,
            declType: C_DeclarationType,
            mountName: R_MountName,
            sysDefs: Set<R_MountName>
    ) {
        if (mountName in sysDefs) {
            ctx.msgCtx.error(pos, "mount:conflict:sys:$declType:$mountName",
                    "Mount name conflict: system ${declType.msg} '$mountName' exists")
        }
    }
}

abstract class S_BasicDefinition(pos: S_Pos, modifiers: S_Modifiers): S_Definition(pos, modifiers) {
    final override fun compile(ctx: S_DefinitionContext): C_MidModuleMember {
        return C_MidModuleMember_Basic(this)
    }

    abstract fun compileBasic(ctx: C_MountContext)
}

class S_EntityDefinition(
    pos: S_Pos,
    modifiers: S_Modifiers,
    val deprecatedKwPos: S_Pos?,
    val name: S_Name,
    val annotations: List<S_Name>,
    val body: List<S_RelClause>?,
): S_BasicDefinition(pos, modifiers) {
    override fun compileBasic(ctx: C_MountContext) {
        ctx.checkNotReplOrTest(name.pos, C_DeclarationType.ENTITY)

        if (deprecatedKwPos != null) {
            ctx.msgCtx.error(deprecatedKwPos, "deprecated_kw:class:entity",
                    "Keyword 'class' is deprecated, use 'entity' instead")
        }

        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        if (body == null) {
            compileHeader(ctx, nameHand)
            return
        }

        val mods = C_ModifierValues(C_ModifierTargetType.ENTITY, cName)
        val modExternal = mods.field(C_ModifierFields.EXTERNAL_CHAIN)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val modLog = mods.field(C_ModifierFields.LOG)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(ctx, mods)

        val extChain = ctx.externalChain(modExternal)
        val extChainRef = extChain?.ref
        val isExternalChain = extChainRef != null
        val rFlags = compileFlags(ctx, isExternalChain, modLog.hasValue())
        val mountName = ctx.mountName(modMount, cName)

        val cDefBase = ctx.defBase(
            nameHand,
            C_DefinitionType.ENTITY,
            IdeSymbolKind.DEF_ENTITY,
            mountName,
            extChain = extChain,
        )

        cDefBase.setDocDeclaration(DocDeclaration_Entity(docModifiers, cName.rName))

        val isExternalChainOrModule = isExternalChain || ctx.modCtx.external

        C_Errors.check(!isExternalChainOrModule || !ctx.mountName.isEmpty() || cName.str !in HEADER_ENTITIES, cName.pos) {
            "def_entity_external_unallowed:$cName" toCodeMsg
            "External entity '$cName' can be declared only without body (as entity header)"
        }

        C_Errors.check(!isExternalChainOrModule || rFlags.log, cName.pos) {
            "def_entity_external_nolog:${cDefBase.simpleName}" toCodeMsg
            "External entity '${cDefBase.simpleName}' must have '${C_Constants.LOG_ANNOTATION}' annotation"
        }

        val rMapping = if (extChainRef == null) {
            R_EntitySqlMapping_Regular(mountName)
        } else {
            R_EntitySqlMapping_External(mountName, extChainRef)
        }

        checkEntityMountNameLen(ctx.msgCtx, cName, mountName)

        val defCtx = cDefBase.defCtx(ctx)
        val defBase = cDefBase.rBase(defCtx.initFrameGetter)

        val rExternalEntity = if (extChainRef == null) null else R_ExternalEntity(extChainRef, true)

        val rEntity = C_Utils.createEntity(
            ctx.appCtx,
            C_DefinitionType.ENTITY,
            defBase,
            cName.rName,
            rFlags,
            rMapping,
            rExternalEntity,
        )

        ctx.appCtx.defsAdder.addEntity(C_Entity(cName.pos, rEntity))
        ctx.nsBuilder.addEntity(cDefBase.nsMemBase(modDeprecated), cName, rEntity)
        ctx.mntBuilder.addEntity(cName, rEntity)

        ctx.executor.onPass(C_CompilerPass.MEMBERS) {
            membersPass(defCtx, cName, extChain, rEntity, body)
        }
    }

    private fun compileHeader(ctx: C_MountContext, nameHand: C_NameHandle) {
        val cName = nameHand.name
        val mods = C_ModifierValues(C_ModifierTargetType.ENTITY, cName)
        val modExternal = mods.field(C_ModifierFields.EXTERNAL_CHAIN)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val modLog = mods.field(C_ModifierFields.LOG)
        val docModifiers = modifiers.compile(ctx, mods)

        checkHeaderNoModifier(ctx, modMount)
        checkHeaderNoModifier(ctx, modLog)

        if (annotations.isNotEmpty()) {
            ctx.msgCtx.error(name.pos, "def_entity_hdr_annotations:$name",
                    "Annotations not allowed for entity header '$name'")
        }

        var err = false

        val entGetter = HEADER_ENTITIES[cName.str]
        if (entGetter == null) {
            val entities = HEADER_ENTITIES.keys.joinToString()
            ctx.msgCtx.error(name.pos, "def_entity_hdr_name:$name",
                    "Entity header declarations allowed only for entities: $entities")
            err = true
        }

        val extChain = ctx.externalChain(modExternal)
        if (extChain == null && !ctx.modCtx.external) {
            ctx.msgCtx.error(name.pos, "def_entity_hdr_noexternal:$name", "Entity header must be declared as external")
            err = true
        }

        val sysDefs = extChain?.sysDefs?.common ?: ctx.modCtx.sysDefsCommon
        val rEntity = if (entGetter == null) null else entGetter(sysDefs)

        val mountName = rEntity?.mountName ?: ctx.mountName(modMount, cName)

        val cDefBase = ctx.defBase(
            nameHand,
            C_DefinitionType.ENTITY,
            IdeSymbolKind.DEF_ENTITY,
            mountName,
            extChain = extChain,
        )

        cDefBase.setDocDeclaration(DocDeclaration_Entity(docModifiers, cName.rName))

        if (!err && rEntity != null) {
            val cNsMemBase = cDefBase.nsMemBase(defName = rEntity.cDefName)
            ctx.nsBuilder.addEntity(cNsMemBase, cName, rEntity, addToModule = false)
        }
    }

    private fun checkHeaderNoModifier(ctx: C_MountContext, modValue: C_ModifierValue<*>) {
        val link = modValue.modLink()
        if (link != null) {
            val codeMsg = link.key.codeMsg()
            val code = "def_entity_hdr:modifier:${codeMsg.code}"
            val msg = "${codeMsg.msg.capitalizeEx()} not allowed for an entity header"
            ctx.msgCtx.error(link.pos, code, msg)
        }
    }

    private fun compileFlags(ctx: C_MountContext, externalChain: Boolean, modLog: Boolean): R_EntityFlags {
        val set = mutableSetOf<String>()
        var log = modLog

        if (log) {
            set.add(C_Constants.LOG_ANNOTATION)
        }

        for (ann in annotations) {
            val nameHand = ann.compile(ctx)
            val cAnn = nameHand.name
            ctx.msgCtx.warning(ann.pos, "ann:legacy:$ann", "Deprecated annotation syntax; use @$ann instead")

            if (!set.add(cAnn.str)) {
                ctx.msgCtx.error(ann.pos, "entity_ann_dup:$ann", "Duplicate annotation: '$ann'")
            }

            val ideKind: IdeSymbolKind

            if (cAnn.str == C_Constants.LOG_ANNOTATION) {
                log = true
                ideKind = IdeSymbolKind.MOD_ANNOTATION_LEGACY
            } else {
                ctx.msgCtx.error(ann.pos, "entity_ann_bad:$ann", "Invalid annotation: '$ann'")
                ideKind = IdeSymbolKind.UNKNOWN
            }

            nameHand.setIdeInfo(C_IdeSymbolInfo.get(ideKind))
        }

        return R_EntityFlags(
                isObject = false,
                canCreate = !externalChain,
                canUpdate = !log && !externalChain && !ctx.modCtx.external,
                canDelete = !log && !externalChain && !ctx.modCtx.external,
                gtv = true,
                log = log
        )
    }

    private fun membersPass(
            defCtx: C_DefinitionContext,
            cName: C_Name,
            extChain: C_ExternalChain?,
            rEntity: R_EntityDefinition,
            clauses: List<S_RelClause>
    ) {
        val sysAttrs = mutableListOf<C_SysAttribute>()
        val attrMaker = C_SysAttribute.Maker(rEntity.defName, defCtx.globalCtx.docFactory)

        if (rEntity.flags.log) {
            val sysDefs = extChain?.sysDefs?.common ?: defCtx.modCtx.sysDefsCommon
            val txType = sysDefs.transactionEntity.type
            val expr = if (extChain == null) {
                val propCtx = C_NamespacePropertyContext(defCtx.initExprCtx)
                Lib_OpContext.transactionRExpr(propCtx, cName.pos)
            } else {
                C_ExprUtils.errorRExpr(txType, "Trying to initialize transaction for external entity '${rEntity.appLevelName}'")
            }
            sysAttrs.add(attrMaker.make("transaction", txType, expr = expr, mutable = false, canSetInCreate = false))
        }

        val entCtx = C_EntityContext(defCtx, cName.str, rEntity.flags.log, sysAttrs, persistent = true)

        for (clause in clauses) {
            clause.compile(entCtx)
        }

        val body = entCtx.createEntityBody()
        C_Utils.setEntityBody(rEntity, body)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val sub = b.node(this, name, IdeOutlineNodeType.ENTITY)
        for (clause in body ?: listOf()) {
            clause.ideBuildOutlineTree(sub)
        }
    }

    companion object {
        private val HEADER_ENTITIES: Map<String, (C_SystemDefsCommon) -> R_EntityDefinition> = immMapOf(
                C_Constants.BLOCK_ENTITY to { sysDefs: C_SystemDefsCommon -> sysDefs.blockEntity },
                C_Constants.TRANSACTION_ENTITY to { sysDefs: C_SystemDefsCommon -> sysDefs.transactionEntity }
        )

        private val NAME_LEN_SWITCH = C_FeatureSwitch("0.12.0")
        private const val MAX_ENTITY_MOUNT_NAME_LEN = 58 // Postgres allows 63, minimal prefix is "c0.", i. e. 3 characters.
        private const val MAX_ATTR_NAME_LEN = 63

        fun checkEntityMountNameLen(msgCtx: C_MessageContext, name: C_Name, mountName: R_MountName) {
            val s = mountName.str()
            checkNameLen(msgCtx, name.pos, s, MAX_ENTITY_MOUNT_NAME_LEN, "entity", "Mount")
        }

        fun checkAttrNameLen(msgCtx: C_MessageContext, name: C_Name) {
            checkNameLen(msgCtx, name.pos, name.str, MAX_ATTR_NAME_LEN, "attr", "Attribute")
        }

        private fun checkNameLen(
            msgCtx: C_MessageContext,
            pos: S_Pos,
            name: String,
            max: Int,
            code: String,
            msg: String,
        ) {
            val n = name.length
            if (n > max && NAME_LEN_SWITCH.isActive(msgCtx.globalCtx)) {
                msgCtx.error(pos, "mount:too_long:$code:$max:$n:$name", "$msg name '$name' is too long: $n (max $max)")
            }
        }
    }
}

class S_ObjectDefinition(
        pos: S_Pos,
        modifiers: S_Modifiers,
        val name: S_Name,
        val attrs: List<S_AttributeDefinition>
): S_BasicDefinition(pos, modifiers) {
    override fun compileBasic(ctx: C_MountContext) {
        ctx.checkNotExternal(name.pos, C_DeclarationType.OBJECT)
        ctx.checkNotReplOrTest(name.pos, C_DeclarationType.OBJECT)

        val entityFlags = R_EntityFlags(
                isObject = true,
                canCreate = false,
                canUpdate = true,
                canDelete = false,
                gtv = false,
                log = false
        )

        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        val mods = C_ModifierValues(C_ModifierTargetType.OBJECT, cName)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(ctx, mods)

        val mountName = ctx.mountName(modMount, cName)
        val sqlMapping = R_EntitySqlMapping_Regular(mountName)
        S_EntityDefinition.checkEntityMountNameLen(ctx.msgCtx, cName, mountName)

        val cDefBase = ctx.defBase(nameHand, C_DefinitionType.OBJECT, IdeSymbolKind.DEF_OBJECT, mountName)
        val defCtx = cDefBase.defCtx(ctx)
        val rDefBase = cDefBase.rBase(defCtx.initFrameGetter)
        cDefBase.setDocDeclaration(DocDeclaration_Object(docModifiers, cName.rName))

        val rEntity = C_Utils.createEntity(
            ctx.appCtx,
            C_DefinitionType.OBJECT,
            rDefBase,
            cName.rName,
            entityFlags,
            sqlMapping,
            null,
        )

        val rObject = R_ObjectDefinition(rDefBase, rEntity)

        ctx.appCtx.defsAdder.addObject(rObject)
        ctx.nsBuilder.addObject(cDefBase.nsMemBase(modDeprecated), cName, rObject)
        ctx.mntBuilder.addObject(cName, rObject)

        ctx.executor.onPass(C_CompilerPass.MEMBERS) {
            membersPass(defCtx, cName, rObject)
        }
    }

    private fun membersPass(defCtx: C_DefinitionContext, cName: C_Name, rObject: R_ObjectDefinition) {
        val entCtx = C_EntityContext(defCtx, cName.str, false, listOf(), persistent = true)

        for (attr in attrs) {
            val attrHeader = attr.header.compile(defCtx)
            entCtx.addAttribute(attr, attrHeader, true)
        }

        val body = entCtx.createEntityBody()
        C_Utils.setEntityBody(rObject.rEntity, body)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val sub = b.node(this, name, IdeOutlineNodeType.OBJECT)
        for (attr in attrs) {
            val attrName = attr.header.ideOutlineTreeNodeName()
            sub.node(attr, attrName, IdeOutlineNodeType.ATTRIBUTE)
        }
    }
}

class S_StructDefinition(
    pos: S_Pos,
    modifiers: S_Modifiers,
    val deprecatedKwPos: S_Pos?,
    val name: S_Name,
    val attrs: List<S_AttributeDefinition>,
): S_BasicDefinition(pos, modifiers) {
    override fun compileBasic(ctx: C_MountContext) {
        if (deprecatedKwPos != null) {
            ctx.msgCtx.error(deprecatedKwPos, "deprecated_kw:record:struct",
                    "Keyword 'record' is deprecated, use 'struct' instead")
        }

        ctx.checkNotExternal(name.pos, C_DeclarationType.STRUCT)

        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        val mods = C_ModifierValues(C_ModifierTargetType.STRUCT, cName)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(ctx, mods)

        val cDefBase = ctx.defBase(nameHand, C_DefinitionType.STRUCT, IdeSymbolKind.DEF_STRUCT, null)
        val defCtx = cDefBase.defCtx(ctx)
        val defBase = cDefBase.rBase(defCtx.initFrameGetter)

        cDefBase.setDocDeclaration(DocDeclaration_Struct(docModifiers, cName.rName))

        val rStruct = R_Struct(
            cDefBase.appLevelName,
            cDefBase.appLevelName.toGtv(),
            defBase.initFrameGetter,
            mirrorStructs = null,
        )

        val rStructDef = R_StructDefinition(defBase, rStruct)

        val attrsLate = C_LateInit<List<C_CompiledAttribute>>(C_CompilerPass.MEMBERS, immListOf())
        val cStruct = C_Struct(cName, cDefBase.ideRefInfo, rStructDef, attrsLate.getter)

        ctx.appCtx.defsAdder.addStruct(rStruct)
        ctx.nsBuilder.addStruct(cDefBase.nsMemBase(modDeprecated), cStruct)

        ctx.executor.onPass(C_CompilerPass.MEMBERS) {
            membersPass(defCtx, cName, cStruct, attrsLate)
        }
    }

    private fun membersPass(
            defCtx: C_DefinitionContext,
            cName: C_Name,
            cStruct: C_Struct,
            attrsLate: C_LateInit<List<C_CompiledAttribute>>
    ) {
        val entCtx = C_EntityContext(defCtx, cName.str, false, immListOf(), persistent = false)

        for (attr in attrs) {
            val attrHeader = attr.header.compile(defCtx)
            entCtx.addAttribute(attr, attrHeader, true)
        }

        val cAttributes = entCtx.createStructBody()
        attrsLate.set(cAttributes.map { it.value }.toImmList())

        val rAttributes = cAttributes.mapValues { it.value.rAttr }.toImmMap()
        cStruct.structDef.struct.setAttributes(rAttributes)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val sub = b.node(this, name, IdeOutlineNodeType.STRUCT)
        for (attr in attrs) {
            val attrName = attr.header.ideOutlineTreeNodeName()
            sub.node(attr, attrName, IdeOutlineNodeType.ATTRIBUTE)
        }
    }
}

class S_EnumDefinition(
    pos: S_Pos,
    modifiers: S_Modifiers,
    val name: S_Name,
    val attrs: List<S_Name>,
): S_Definition(pos, modifiers) {
    // TODO Better design: compile external module in two steps, all definitions must be processed same way.
    override fun compile(ctx: S_DefinitionContext): C_MidModuleMember {
        val nameHand = name.compile(ctx.symCtx, def = true)
        val cName = nameHand.name

        val modifierCtx = C_ModifierContext(ctx.msgCtx, ctx.symCtx)
        val mods = C_ModifierValues(C_ModifierTargetType.ENUM, cName)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(modifierCtx, mods)

        val modKey = R_ModuleKey(ctx.moduleName, null)
        val fullName = C_StringQualifiedName.ofRNames(ctx.namespacePath.parts + cName.rName)
        val cDefBase = C_Utils.createDefBase(
            C_DefinitionType.ENUM,
            IdeSymbolKind.DEF_ENUM,
            modKey,
            fullName,
            null,
            ctx.docFactory,
        )

        val docDec = DocDeclaration_Enum(docModifiers, cName.rName)
        val docGetter = cDefBase.docGetter(C_LateGetter.const(docDec))
        val ideDef = cDefBase.ideDef(startPos, docGetter)

        nameHand.setIdeInfo(ideDef.defInfo)

        val set = mutableSetOf<String>()
        val rAttrs = mutableListOf<R_EnumAttr>()

        for (attr in attrs) {
            val attrNameHand = attr.compile(ctx.symCtx)
            val attrName = attrNameHand.name

            val attrIdeDef = cDefBase.memberIdeDef(
                attrNameHand.pos,
                IdeSymbolCategory.ENUM_VALUE,
                IdeSymbolKind.MEM_ENUM_VALUE,
                DocSymbolKind.ENUM_VALUE,
                attrNameHand.rName,
                DocDeclaration_EnumValue(attrName.rName),
            )

            attrNameHand.setIdeInfo(attrIdeDef.defInfo)

            if (set.add(attrName.str)) {
                rAttrs.add(R_EnumAttr(attrName.rName, rAttrs.size, attrIdeDef.refInfo))
            } else {
                ctx.msgCtx.error(attr.pos, "enum_dup:$attr", "Duplicate enum value: '$attrName'")
            }
        }

        val defBase = cDefBase.rBase(R_CallFrame.NONE_INIT_FRAME_GETTER, docGetter)
        val rEnum = R_EnumDefinition(defBase, rAttrs.toList())
        val memBase = cDefBase.nsMemBase(deprecated = modDeprecated.value(), ideRefInfo = ideDef.refInfo)
        return C_MidModuleMember_Enum(cName, rEnum, memBase)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val sub = b.node(this, name, IdeOutlineNodeType.ENUM)
        for (attr in attrs) {
            sub.node(attr, attr, IdeOutlineNodeType.ENUM_ATTRIBUTE)
        }
    }
}

class S_NamespaceDefinition(
    pos: S_Pos,
    modifiers: S_Modifiers,
    private val qualifiedName: S_QualifiedName?,
    private val definitions: List<S_Definition>,
): S_Definition(pos, modifiers) {
    override fun compile(ctx: S_DefinitionContext): C_MidModuleMember {
        val nameParts = mutableListOf<C_MidModuleMember_Namespace.NamePart>()
        var nsPath = ctx.namespacePath

        for (name in qualifiedName?.parts ?: immListOf()) {
            val nameHand = name.compile(ctx.symCtx, def = true)

            val fullName = nsPath.qualifiedName(nameHand.rName)
            val docSymLate = C_LateInit(C_CompilerPass.NAMESPACES, Nullable.of<DocSymbol>())

            val ideId = ctx.fileCtx.addNamespaceName(nameHand, fullName, docSymLate.getter)
            val ideLink = IdeGlobalSymbolLink(IdeSymbolGlobalId(name.pos.idePath(), ideId))

            val refIdeInfo = C_IdeSymbolInfo.late(
                IdeSymbolKind.DEF_NAMESPACE,
                defId = null,
                link = ideLink,
                docGetter = docSymLate.getter,
            )

            val ideName = C_IdeName(nameHand.name, refIdeInfo)
            nameParts.add(C_MidModuleMember_Namespace.NamePart(ideName, fullName, docSymLate))
            nsPath = nsPath.append(nameHand.rName)
        }

        val midQualifiedName = nameParts.toImmList()
        val rPath = C_RNamePath.of(midQualifiedName.map { it.ideName.name.rName })

        val subCtx = ctx.namespace(rPath)
        val midMembers = definitions.mapNotNull { it.compile(subCtx) }

        return C_MidModuleMember_Namespace(modifiers, midQualifiedName, midMembers)
    }

    override fun ideGetImportedModules(moduleName: R_ModuleName, res: MutableSet<R_ModuleName>) {
        for (def in definitions) {
            def.ideGetImportedModules(moduleName, res)
        }
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        var sub = b
        for (name in qualifiedName?.parts ?: immListOf()) {
            sub = sub.node(this, name, IdeOutlineNodeType.NAMESPACE)
        }
        for (def in definitions) {
            def.ideBuildOutlineTree(sub)
        }
    }
}

class S_IncludeDefinition(pos: S_Pos): S_Definition(pos, S_Modifiers(listOf())) {
    override fun compile(ctx: S_DefinitionContext): C_MidModuleMember? {
        ctx.msgCtx.error(kwPos, "include", "Include not supported since Rell ${RellVersions.MODULE_SYSTEM_VERSION_STR}")
        return null
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        // Do nothing.
    }
}

class S_GlobalConstantDefinition(
        pos: S_Pos,
        modifiers: S_Modifiers,
        val name: S_Name,
        val type: S_Type?,
        val expr: S_Expr
): S_BasicDefinition(pos, modifiers) {
    override fun compileBasic(ctx: C_MountContext) {
        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        val mods = C_ModifierValues(C_ModifierTargetType.CONSTANT, cName)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(ctx, mods)

        val cDefBase = ctx.defBase(nameHand, C_DefinitionType.CONSTANT, IdeSymbolKind.DEF_CONSTANT, null)
        val defCtx = cDefBase.defCtx(ctx)
        val errorExpr = C_ExprUtils.errorVExpr(defCtx.initExprCtx, expr.startPos)

        val headerLate = C_LateInit(C_CompilerPass.MEMBERS, C_GlobalConstantFunctionHeader.ERROR)
        val bodyLate = C_LateInit(C_CompilerPass.EXPRESSIONS, R_GlobalConstantBody.ERROR)
        val exprLate = C_LateInit(C_CompilerPass.EXPRESSIONS, errorExpr)

        val cDef = ctx.appCtx.addConstant(ctx.modCtx.rModuleKey, cDefBase.defName) { constId ->
            val filePos = cName.pos.toFilePos()
            val defBase = cDefBase.rBase(defCtx.initFrameGetter)
            val rDef = R_GlobalConstantDefinition(defBase, constId, filePos, bodyLate.getter)
            val typePos = type?.pos ?: cName.pos
            val varUid = ctx.modCtx.nextConstVarUid(cDefBase.qualifiedName)
            C_GlobalConstantDefinition(rDef, typePos, varUid, headerLate.getter, exprLate.getter)
        }

        ctx.nsBuilder.addConstant(cDefBase.nsMemBase(modDeprecated), cName, cDef)

        if (cName.str == "_") {
            ctx.msgCtx.error(cName.pos, "def:const:wildcard", "Name '$cName' is a wildcard, not allowed for constants")
        }

        ctx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = C_FunctionUtils.compileGlobalConstantHeader(defCtx, cName, cDefBase.defName, type, expr, cDef.rDef.constId)
            headerLate.set(header)

            ctx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                val rType = header.returnType()
                val cBody = header.constBody
                val vExpr = cBody?.compile()
                val rExpr = vExpr?.toRExpr() ?: C_ExprUtils.errorRExpr()
                val rtValue = cBody?.constantValue(V_ConstantValueEvalContext())
                bodyLate.set(R_GlobalConstantBody(rType, rExpr, rtValue))
                exprLate.set(vExpr ?: errorExpr)

                val docType = L_TypeUtils.docType(rType.mType)
                val docValue = if (rtValue == null) null else C_DocUtils.docValue(rtValue)
                val doc = DocDeclaration_Constant(docModifiers, cName.rName, docType, docValue)
                cDefBase.setDocDeclaration(doc)
            }
        }
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        b.node(this, name, IdeOutlineNodeType.CONSTANT)
    }
}
