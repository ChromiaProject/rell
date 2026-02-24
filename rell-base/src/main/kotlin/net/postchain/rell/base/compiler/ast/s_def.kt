/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import com.google.common.collect.Multimap
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
import java.util.EnumSet

sealed class S_AttrHeader: S_Node() {
    internal abstract fun discoverVar(): R_Name
    internal abstract fun compile(ctx: C_DefinitionContext): C_AttrHeaderHandle
    internal abstract fun ideOutlineTreeNodeName(): S_Name

    internal fun compile(ctx: C_DefinitionContext, canInferType: Boolean, ideData: C_AttrHeaderIdeData): C_AttrHeader {
        val handle = compile(ctx)
        return handle.compile(ctx, canInferType, ideData)
    }

    companion object {
        internal fun checkUnitType(msgCtx: C_MessageContext, pos: S_Pos, rType: R_Type, cName: C_Name): R_Type {
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
        val typeNameHand = typeName.compile(ctx.symCtx.nameCtx, def = true)
        return C_AnonAttrHeaderHandle(ctx.nsCtx, typeNameHand, nullable)
    }

    override fun ideOutlineTreeNodeName() = typeName.last
}

sealed class S_RelClause: S_Node() {
    internal abstract fun compile(ctx: C_EntityContext)
    internal abstract fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder)
}

class S_AttributeClause internal constructor(
    private val attr: S_AttributeDefinition,
    private val comment: S_Comment?,
): S_RelClause() {
    private companion object {
        private val sizeConstraintValidDefTypes: Set<C_DefinitionType> = EnumSet.of(
            C_DefinitionType.STRUCT,
            C_DefinitionType.ENTITY,
            C_DefinitionType.OBJECT,
        )
    }

    override fun compile(ctx: C_EntityContext) {
        val attrHeader = attr.header.compile(ctx.defCtx)
        val name = attrHeader.name

        val mods = C_ModifierValues(C_ModifierTargetType.ATTRIBUTE, name)
        mods.field(C_ModifierFields.DUMMY_ANNOTATION)
        mods.field(C_ModifierFields.MUTABLE)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val sizeHandler = C_SizeModifierHandler(ctx.defCtx, attrHeader, mods)
        attr.modifiers.compile(ctx.defCtx.mntCtx, mods)

        val sizeConstraint = sizeHandler.getSizeConstraint()
        if (ctx.defCtx.definitionType !in sizeConstraintValidDefTypes && sizeConstraint != null) {
            C_SizeConstraint.reportInvalidDefType(ctx.defCtx, attrHeader, sizeConstraint.annStrs)
        }
        val rawMount = modMount.value()?.process(false)
        val sqlMapping = rawMount?.calculateMountName(ctx.defCtx.msgCtx, R_MountName.EMPTY)?.str()
        if (sqlMapping != null) {
            S_EntityDefinition.checkAttrMountNameLen(ctx.defCtx.msgCtx, modMount.pos() ?: attrHeader.pos, sqlMapping)
        }

        ctx.addAttribute(attr, attrHeader, true, comment, sizeConstraint, sqlMapping)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val name = attr.header.ideOutlineTreeNodeName()
        b.node(this, name, IdeOutlineNodeType.ATTRIBUTE)
    }
}

class S_KeyIndexClause(
    private val pos: S_Pos,
    private val kind: R_KeyIndexKind,
    private val attrs: ImmList<S_AttributeDefinition>,
    private val comment: S_Comment?,
): S_RelClause() {
    override fun compile(ctx: C_EntityContext) {
        val cAttrs = attrs.map {
            val cHeader = it.header.compile(ctx.defCtx)
            AttrRec(cHeader, it)
        }

        for (attr in cAttrs) {
            ctx.addAttribute(attr.sAttr, attr.header, false, comment, sqlMapping = null)
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

        val attrNames = cAttrs.mapToImmList { it.header.rName }

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

class S_AttributeDefinition(val modifiers: S_Modifiers, val header: S_AttrHeader, val expr: S_Expr?): S_Node() {
    fun checkMultiAttrKeyIndex(msgCtx: C_MessageContext, kind: R_KeyIndexKind, name: R_Name): Boolean {
        return if (modifiers.pos != null) {
            errKeyIndexTooComplex(msgCtx, kind, name, modifiers.pos, "modifiers")
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

    internal fun getMutableModifier(): S_Modifier? {
        return modifiers.modifiers.find { it.isKeywordModifier(S_KeywordModifierKind.MUTABLE) }
    }
}

class S_DefinitionBase(
    val kwPos: S_Pos,
    val modifiers: S_Modifiers,
    val comment: S_Comment?,
)

abstract class S_Definition(base: S_DefinitionBase): S_Node() {
    val kwPos = base.kwPos
    val modifiers = base.modifiers
    val comment = base.comment

    val startPos = modifiers.pos ?: kwPos

    internal abstract fun compile(ctx: S_DefinitionContext): C_MidModuleMember?

    internal abstract fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder)

    internal open fun ideGetImportedModules(moduleName: R_ModuleName, res: MutableSet<R_ModuleName>) {
    }
}

abstract class S_BasicDefinition(base: S_DefinitionBase): S_Definition(base) {
    final override fun compile(ctx: S_DefinitionContext): C_MidModuleMember {
        return C_MidModuleMember_Basic(this)
    }

    internal abstract fun compileBasic(ctx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>>
}

class   S_EntityDefinition(
    base: S_DefinitionBase,
    private val deprecatedKwPos: S_Pos?,
    private val name: S_Name,
    private val annotations: ImmList<S_Name>,
    private val body: ImmList<S_RelClause>?,
): S_BasicDefinition(base) {
    override fun compileBasic(ctx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        ctx.checkNotReplOrTest(name.pos, C_DeclarationType.ENTITY)

        if (deprecatedKwPos != null) {
            ctx.msgCtx.error(deprecatedKwPos, "deprecated_kw:class:entity",
                    "Keyword 'class' is deprecated, use 'entity' instead")
        }

        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        if (body == null) {
            compileHeader(ctx, nameHand)
            return C_LateGetter.const(immMultimapOf())
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
            commentProvider = ctx.symCtx.commentProvider(comment),
        )

        cDefBase.setDocDeclaration(DocDeclarationProto_Entity(docModifiers, cName.rName))

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

        return C_LateGetter.const(immMultimapOf())
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
            commentProvider = ctx.symCtx.commentProvider(comment),
        )

        cDefBase.setDocDeclaration(DocDeclarationProto_Entity(docModifiers, cName.rName))

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
        clauses: List<S_RelClause>,
    ) {
        val sysAttrs = mutableListOf<C_SysAttribute>()
        val attrMaker = C_SysAttribute.Maker(defCtx.symCtx.docSymbolFactory, rEntity.defName)

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
        for (clause in body.orEmpty()) {
            clause.ideBuildOutlineTree(sub)
        }
    }

    companion object {
        private val HEADER_ENTITIES: ImmMap<String, (C_SystemDefsCommon) -> R_EntityDefinition> = immMapOf(
                C_Constants.BLOCK_ENTITY to { sysDefs -> sysDefs.blockEntity },
                C_Constants.TRANSACTION_ENTITY to { sysDefs -> sysDefs.transactionEntity }
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

        fun checkAttrMountNameLen(msgCtx: C_MessageContext, pos: S_Pos, name: String) {
            checkNameLen(msgCtx, pos, name, MAX_ATTR_NAME_LEN, "attr_mount", "Attribute mount")
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
    base: S_DefinitionBase,
    private val name: S_Name,
    private val attrs: ImmList<S_AttributeClause>,
): S_BasicDefinition(base) {
    override fun compileBasic(ctx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        ctx.checkNotExternal(name.pos, C_DeclarationType.OBJECT)
        ctx.checkNotReplOrTest(name.pos, C_DeclarationType.OBJECT)

        val entityFlags = R_EntityFlags(
            isObject = true,
            canCreate = false,
            canUpdate = true,
            canDelete = false,
            gtv = false,
            log = false,
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

        val cDefBase = ctx.defBase(
            nameHand,
            C_DefinitionType.OBJECT,
            IdeSymbolKind.DEF_OBJECT,
            mountName,
            commentProvider = ctx.symCtx.commentProvider(comment),
        )

        val defCtx = cDefBase.defCtx(ctx)
        val rDefBase = cDefBase.rBase(defCtx.initFrameGetter)
        cDefBase.setDocDeclaration(DocDeclarationProto_Object(docModifiers, cName.rName))

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

        return C_LateGetter.const(immMultimapOf())
    }

    private fun membersPass(defCtx: C_DefinitionContext, cName: C_Name, rObject: R_ObjectDefinition) {
        val entCtx = C_EntityContext(defCtx, cName.str, false, listOf(), persistent = true)

        for (attr in attrs) {
            attr.compile(entCtx)
        }

        val body = entCtx.createEntityBody()
        C_Utils.setEntityBody(rObject.rEntity, body)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val sub = b.node(this, name, IdeOutlineNodeType.OBJECT)
        for (attr in attrs) {
            attr.ideBuildOutlineTree(sub)
        }
    }
}

class S_StructDefinition(
    base: S_DefinitionBase,
    private val deprecatedKwPos: S_Pos?,
    private val name: S_Name,
    private val attrs: ImmList<S_AttributeClause>,
): S_BasicDefinition(base) {
    override fun compileBasic(ctx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
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

        val cDefBase = ctx.defBase(
            nameHand,
            C_DefinitionType.STRUCT,
            IdeSymbolKind.DEF_STRUCT,
            mountName = null,
            commentProvider = ctx.symCtx.commentProvider(comment),
        )
        val defCtx = cDefBase.defCtx(ctx)
        val defBase = cDefBase.rBase(defCtx.initFrameGetter)

        cDefBase.setDocDeclaration(DocDeclarationProto_Struct(docModifiers, cName.rName))

        val rStruct = R_Struct(
            cDefBase.appLevelName,
            cDefBase.appLevelName.toGtv(),
            defBase,
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

        return C_LateGetter.const(immMultimapOf())
    }

    private fun membersPass(
        defCtx: C_DefinitionContext,
        cName: C_Name,
        cStruct: C_Struct,
        attrsLate: C_LateInit<List<C_CompiledAttribute>>,
    ) {
        val entCtx = C_EntityContext(defCtx, cName.str, false, immListOf(), persistent = false)

        for (attr in attrs) {
            attr.compile(entCtx)
        }

        val cAttributes = entCtx.createStructBody()
        attrsLate.set(cAttributes.mapToImmList { it.value })

        val rAttributes = cAttributes.mapValuesToImmMap { it.value.rAttr }
        cStruct.structDef.struct.setAttributes(rAttributes)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val sub = b.node(this, name, IdeOutlineNodeType.STRUCT)
        for (attr in attrs) {
            attr.ideBuildOutlineTree(sub)
        }
    }
}

class S_EnumValue(val name: S_Name, val comment: S_Comment?)

class S_EnumDefinition(
    base: S_DefinitionBase,
    private val name: S_Name,
    private val attrs: ImmList<S_EnumValue>,
): S_Definition(base) {
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
            mountName = null,
            ctx.docFactory,
            commentProvider = ctx.symCtx.commentProvider(comment),
        )

        val docDec = DocDeclarationProto_Enum(docModifiers, cName.rName)
        val docGetter = cDefBase.docGetter(C_LateGetter.const(docDec))
        val ideDef = cDefBase.ideDef(startPos, docGetter)

        nameHand.setIdeInfo(ideDef.defInfo)

        val attrsCompiler = AttrsCompiler(ctx)
        val rAttrs = attrsCompiler.compileAttrs(cDefBase, attrs)

        val docPos = cName.pos.toDocPos()
        val defBase = cDefBase.rBase(R_CallFrame.NONE_INIT_FRAME_GETTER, docPos, docGetter)
        val rEnum = R_EnumDefinition(defBase, rAttrs)
        attrsCompiler.finish(rEnum)

        val memBase = cDefBase.nsMemBase(deprecated = modDeprecated.value(), ideRefInfo = ideDef.refInfo)
        return C_MidModuleMember_Enum(cName, rEnum, memBase)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val sub = b.node(this, name, IdeOutlineNodeType.ENUM)
        for (attr in attrs) {
            sub.node(attr.name, attr.name, IdeOutlineNodeType.ENUM_ATTRIBUTE)
        }
    }

    private class AttrsCompiler(private val ctx: S_DefinitionContext) {
        private val set = mutableSetOf<String>()
        private val rAttrs = mutableListOf<R_EnumAttr>()
        private val attrDocDecInits = mutableListOf<Pair<R_Name, C_LateInit<DocDeclarationProto>>>()

        fun compileAttrs(cDefBase: C_CommonDefinitionBase, attrs: List<S_EnumValue>): ImmList<R_EnumAttr> {
            for (attr in attrs) {
                compileAttr(cDefBase, attr)
            }
            return rAttrs.toImmList()
        }

        private fun compileAttr(cDefBase: C_CommonDefinitionBase, attr: S_EnumValue) {
            val attrNameHand = attr.name.compile(ctx.symCtx)
            val attrName = attrNameHand.name

            val attrDocDecInit = C_LateInit(C_CompilerPass.NAMESPACES, DocDeclarationProto.NONE)
            attrDocDecInits.add(attrName.rName to attrDocDecInit)

            val attrIdeDef = cDefBase.memberIdeDef(
                attrNameHand.pos,
                IdeSymbolCategory.ENUM_VALUE,
                IdeSymbolKind.MEM_ENUM_VALUE,
                DocSymbolKind.ENUM_VALUE,
                attrNameHand.rName,
                attr.comment,
                attrDocDecInit.getter,
            )

            attrNameHand.setIdeInfo(attrIdeDef.defInfo)

            if (set.add(attrName.str)) {
                val docPos = attrName.pos.toDocPos()
                val rAttr = R_EnumAttr(attrName.rName, rAttrs.size, attrIdeDef.refInfo, docPos)
                rAttrs.add(rAttr)
            } else {
                ctx.msgCtx.error(attrName.pos, "enum_dup:$attrName", "Duplicate enum value: '$attrName'")
            }
        }

        fun finish(rEnum: R_EnumDefinition): ImmList<R_EnumAttr> {
            val docType = rEnum.type.docType()

            ctx.appCtx.executor.onPass(C_CompilerPass.NAMESPACES) {
                for ((rAttrName, docDecInit) in attrDocDecInits) {
                    docDecInit.set(DocDeclarationProto_EnumValue(rAttrName, docType))
                }
            }

            return rAttrs.toImmList()
        }
    }
}

class S_NamespaceDefinition(
    base: S_DefinitionBase,
    private val bodyPosRange: S_PosRange,
    private val qualifiedName: S_QualifiedName?,
    private val definitions: ImmList<S_Definition>,
): S_Definition(base) {
    override fun compile(ctx: S_DefinitionContext): C_MidModuleMember {
        val nameParts = mutableListOf<C_MidModuleMember_Namespace.NamePart>()
        var nsPath = ctx.namespacePath

        for (name in qualifiedName?.parts.orEmpty()) {
            val nameHand = name.compile(ctx.symCtx, def = true)

            val fullName = nsPath.qualifiedName(nameHand.rName)
            val docSymLate = C_LateInit<DocSymbol?>(C_CompilerPass.NAMESPACES, null)

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
        val rPath = C_RNamePath.of(midQualifiedName.mapToImmList { it.ideName.name.rName })

        val subCtx = ctx.namespace(rPath)
        val midMembers = definitions.mapNotNullToImmList { it.compile(subCtx) }

        return C_MidModuleMember_Namespace(modifiers, midQualifiedName, comment, bodyPosRange, midMembers)
    }

    override fun ideGetImportedModules(moduleName: R_ModuleName, res: MutableSet<R_ModuleName>) {
        for (def in definitions) {
            def.ideGetImportedModules(moduleName, res)
        }
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        var sub = b
        for (name in qualifiedName?.parts.orEmpty()) {
            sub = sub.node(this, name, IdeOutlineNodeType.NAMESPACE)
        }
        for (def in definitions) {
            def.ideBuildOutlineTree(sub)
        }
    }
}

class S_IncludeDefinition(base: S_DefinitionBase): S_Definition(base) {
    override fun compile(ctx: S_DefinitionContext): C_MidModuleMember? {
        ctx.msgCtx.error(kwPos, "include", "Include not supported since Rell ${RellVersions.MODULE_SYSTEM_VERSION_STR}")
        return null
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        // Do nothing.
    }
}

class S_GlobalConstantDefinition(
    base: S_DefinitionBase,
    val name: S_Name,
    val type: S_Type?,
    val expr: S_Expr,
): S_BasicDefinition(base) {
    override fun compileBasic(ctx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        val mods = C_ModifierValues(C_ModifierTargetType.CONSTANT, cName)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(ctx, mods)

        val cDefBase = ctx.defBase(
            nameHand,
            C_DefinitionType.CONSTANT,
            IdeSymbolKind.DEF_CONSTANT,
            mountName = null,
            commentProvider = ctx.symCtx.commentProvider(comment),
        )
        val defCtx = cDefBase.defCtx(ctx)
        val errorExpr = C_ExprUtils.errorVExpr(defCtx.initExprCtx, expr.startPos)

        val headerLate = C_LateInit(C_CompilerPass.MEMBERS, C_GlobalConstantHeader.ERROR)
        val bodyLate = C_LateInit(C_CompilerPass.EXPRESSIONS, R_GlobalConstantBody.ERROR)
        val exprLate = C_LateInit(C_CompilerPass.EXPRESSIONS, errorExpr)

        val cDef = ctx.appCtx.addConstant(ctx.modCtx.rModuleKey, cDefBase.defName) { constId ->
            val defBase = cDefBase.rBase(defCtx.initFrameGetter)
            val rDef = R_GlobalConstantDefinition(defBase, constId, bodyLate.getter)
            val typePos = type?.pos ?: cName.pos
            val varId = ctx.modCtx.nextConstVarUid(cDefBase.qualifiedName)
            C_GlobalConstantDefinition(rDef, typePos, varId, headerLate.getter, exprLate.getter)
        }

        ctx.nsBuilder.addConstant(cDefBase.nsMemBase(modDeprecated), cName, cDef)

        if (cName.str == "_") {
            ctx.msgCtx.error(cName.pos, "def:const:wildcard", "Name '$cName' is a wildcard, not allowed for constants")
        }

        ctx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = C_FunctionUtils.compileGlobalConstantHeader(defCtx, cName, type, expr, cDef.rDef.constId)
            headerLate.set(header)

            ctx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                val rType = header.deepHeader.returnType()
                val cBody = header.constBody
                val vExpr = cBody?.compile()
                val rExpr = vExpr?.toRExpr() ?: C_ExprUtils.errorRExpr()
                val rtValue = cBody?.constantValue(V_ConstantValueEvalContext())
                bodyLate.set(R_GlobalConstantBody(rType, rExpr, rtValue))
                exprLate.set(vExpr ?: errorExpr)

                val docType = rType.docType()
                val docValue = if (rtValue == null) null else C_DocUtils.docValue(rtValue)
                val docDec = DocDeclarationProto_Constant(docModifiers, cName.rName, docType, docValue)
                cDefBase.setDocDeclaration(docDec)
            }
        }

        return C_LateGetter.const(immMultimapOf())
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        b.node(this, name, IdeOutlineNodeType.CONSTANT)
    }
}
