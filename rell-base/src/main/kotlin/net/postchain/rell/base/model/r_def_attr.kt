package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_DefinitionType
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.lib.type.R_ByteArrayType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.MsgString
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.doc.DocSymbol
import java.util.Locale

class R_Attribute(
    val index: Int,
    val rName: R_Name,
    val type: R_Type,
    val mutable: Boolean,
    val keyIndexKind: R_KeyIndexKind?,
    val ideInfo: C_IdeSymbolInfo,
    val restrictions: C_MemberRestrictions = C_MemberRestrictions.NULL,
    val canSetInCreate: Boolean = true,
    val sqlMapping: String = rName.str,
    val validator: R_AttributeValidator? = null,
    override val docSourcePos: DocSourcePos? = null,
    private val exprGetter: C_LateGetter<R_DefaultValue>?,
): DocDefinition() {
    val ideName = R_IdeName(rName, ideInfo)
    val name = rName.str

    val expr: R_Expr? get() = exprGetter?.get()?.rExpr
    val isExprDbModification: Boolean get() = exprGetter?.get()?.isDbModification ?: false

    val hasExpr: Boolean get() = exprGetter != null

    override val docSymbol: DocSymbol get() = ideInfo.getIdeInfo().doc ?: DocSymbol.NONE

    fun toMetaGtv(): Gtv {
        return mapOf(
            "name" to name.toGtv(),
            "type" to type.toMetaGtv(),
            "mutable" to mutable.toGtv(),
        ).toGtv()
    }

    fun copy(mutable: Boolean, ideInfo: C_IdeSymbolInfo): R_Attribute {
        return R_Attribute(
            index = index,
            rName = rName,
            type = type,
            mutable = mutable,
            keyIndexKind = keyIndexKind,
            ideInfo = ideInfo,
            docSourcePos = docSourcePos,
            restrictions = restrictions,
            canSetInCreate = true,
            sqlMapping = sqlMapping,
            validator = validator,
            exprGetter = if (canSetInCreate) exprGetter else null, // Not copying default value e. g. for "transaction".
        )
    }

    override fun toString() = name
}

class R_DefaultValue(val rExpr: R_Expr, val isDbModification: Boolean)

enum class R_KeyIndexKind(val code: String) {
    KEY("key"),
    INDEX("index"),
    ;

    val nameMsg = MsgString(code)
}

abstract class R_AttributeValidator {
    abstract fun check(value: Rt_Value): Error?
    data class Error(val code: String, val msg: String)
}

internal data class R_AttributeMetadata(
    val attrName: R_Name,
    val attrType: R_Type,
    val ownerName: R_DefinitionName,
    val ownerDefType: C_DefinitionType,
)

internal class R_SizeAttributeValidator(
    private val min: Long?,
    private val max: Long?,
    private val sizeAdapter: R_SizeAdapter,
    private val metadata: R_AttributeMetadata,
): R_AttributeValidator() {
    init {
        require(min != null || max != null) { "min and max cannot both be null" }
    }

    override fun check(value: Rt_Value): Error? {
        val size = sizeAdapter.getSize(value)
        if (min != null && size < min) {
            val msg = buildTooSmallErrMsg(size)
            return Error(tooSmallErrCode, msg)
        }
        if (max != null && size > max) {
            val msg = buildTooLargeErrMsg(size)
            return Error(tooLargeErrCode, msg)
        }
        return null
    }

    private val defTypeStr: String by lazy {
        metadata.ownerDefType.name.lowercase(Locale.US)
    }

    private val tooSmallErrCode: String by lazy {
        "$defTypeStr:${metadata.ownerName.simpleName}:attribute:${metadata.attrName.str}:validator:size:too_small"
    }

    private fun buildTooSmallErrMsg(size: Int): String {
        val maxStr = if (max != null) "The specified maximum size is $max." else "No maximum is specified."
        val errMsgPrefix = "Attribute ${metadata.attrName.str} of $defTypeStr ${metadata.ownerName.simpleName}: "
        return "$errMsgPrefix size too small: specified minimum is $min (inclusive), got $size. $maxStr"
    }

    private val tooLargeErrCode: String by lazy {
        "$defTypeStr:${metadata.ownerName.simpleName}:attribute:${metadata.attrName.str}:validator:size:too_large"
    }

    private fun buildTooLargeErrMsg(size: Int): String {
        val minStr = if (min != null) "The specified minimum size is $min." else "No minimum is specified."
        val errMsgPrefix = "Attribute ${metadata.attrName.str} of $defTypeStr ${metadata.ownerName.simpleName}: "
        return "$errMsgPrefix size too large: specified maximum is $max (inclusive), got $size. $minStr"
    }

    companion object {
        fun getSizeAdapter(type: R_Type): R_SizeAdapter? {
            return when (type) {
                // is R_ListType -> R_ListSizeAdapter
                // is R_MapType -> R_MapSizeAdapter
                // is R_SetType -> R_SetSizeAdapter
                is R_ByteArrayType -> R_ByteArraySizeAdapter
                is R_TextType -> R_TextSizeAdapter
                // is R_JsonType -> R_JsonSizeAdapter
                else -> null
            }
        }
    }

    internal interface R_SizeAdapter {
        fun getSize(value: Rt_Value): Int
    }

    /*
    TODO: Support size on mutable collections. Might have to implement some kind of reference tracking, which could be
    tricky. Another option would be to support only immutable collections.
    private object R_ListSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asList().size
    }

    private object R_MapSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asMap().size
    }

    private object R_SetSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asSet().size
    }
    */

    private object R_ByteArraySizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asByteArray().size
    }

    private object R_TextSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asString().length
    }

    /*
    TODO: JSON also disabled since it's not clear what 'size' should mean for JSON. If it's the size of the internal
    text, one can use a workaround - the @size can be on a text attribute, and convert to JSON where required.
    private object R_JsonSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asJsonString().length
    }
    */
}