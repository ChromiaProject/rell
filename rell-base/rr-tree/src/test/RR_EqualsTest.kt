/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.immSetOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Verifies that RR_ data classes have structural equality, even though some of the types
 * they embed (e.g. [Name], [MountName], [DefinitionId]) are not necessarily data classes.
 * If any field type loses its structural equals, these tests will catch it.
 */
class RR_EqualsTest {
    private fun makeName(s: String) = Name.of(s)
    private fun makeMountName(s: String) = MountName.of(s)

    private fun makeDefId() = DefinitionId("test", "foo")
    private fun makeDefName() = DefinitionName("test", "foo", "foo")

    private fun makeFrameBlock() = RR_FrameBlock(parentUid = null, uid = 1L, offset = 0, size = 2)
    private fun makeFrame() = RR_FrameDescriptor(size = 2, rootBlock = makeFrameBlock(), hasGuardBlock = false)
    private fun makeBase() = RR_DefinitionBase(makeDefId(), makeDefName(), makeFrame())

    @Test
    fun testRR_Type() {
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), RR_Type.Primitive(RR_PrimitiveKind.INTEGER))
        assertNotEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), RR_Type.Primitive(RR_PrimitiveKind.TEXT))
        assertEquals(RR_Type.Entity(0), RR_Type.Entity(0))
        assertEquals(
            RR_Type.Nullable(RR_Type.Primitive(RR_PrimitiveKind.TEXT)),
            RR_Type.Nullable(RR_Type.Primitive(RR_PrimitiveKind.TEXT)),
        )
        assertEquals(
            RR_Type.Tuple(immListOf(RR_TupleField("x", RR_Type.Primitive(RR_PrimitiveKind.INTEGER)))),
            RR_Type.Tuple(immListOf(RR_TupleField("x", RR_Type.Primitive(RR_PrimitiveKind.INTEGER)))),
        )
    }

    @Test
    fun testRR_DefinitionBase() {
        assertEquals(makeBase(), makeBase())
    }

    @Test
    fun testRR_EntityDefinition() {
        fun make() = RR_EntityDefinition(
            base = makeBase(),
            rName = makeName("my_entity"),
            flags = EntityFlags(
                isObject = false,
                canCreate = true,
                canUpdate = true,
                canDelete = true,
                gtv = true,
                log = false,
            ),
            sqlMapping = RR_EntitySqlMapping(
                mountName = makeMountName("test.my_entity"),
                metaName = "my_entity",
                rowidColumn = "rowid",
                autoCreateTable = true,
                isSystemEntity = false,
                kind = RR_EntitySqlMappingKind.REGULAR,
                externalChainIndex = -1,
            ),
            external = null,
            type = RR_Type.Entity(0),
            keys = immListOf(),
            indexes = immListOf(),
            attributes = immMapOf(makeName("x") to makeAttr("x", 0)),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_StructDefinition() {
        fun make() = RR_StructDefinition(
            base = makeBase(),
            struct = RR_Struct(
                name = "my_struct",
                attributes = immMapOf(makeName("a") to makeAttr("a", 0)),
                flags = RR_StructFlags(
                    typeFlags = TypeFlags(
                        pure = true, mutable = false,
                        gtv = GtvCompatibility.FULL,
                        virtualable = false, mixedTuple = false, hasTypeVariable = false,
                    ),
                    cyclic = false,
                    infinite = false,
                ),
            ),
            hasDefaultConstructor = true,
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_EnumDefinition() {
        fun make() = RR_EnumDefinition(
            base = makeBase(),
            attrs = immListOf(RR_EnumAttr(makeName("RED"), 0), RR_EnumAttr(makeName("GREEN"), 1)),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_FunctionDefinition() {
        fun make() = RR_FunctionDefinition(
            base = makeBase(),
            fnBase = RR_FunctionBase(
                defId = makeDefId(),
                defName = makeDefName(),
                params = immListOf(),
                resultType = RR_Type.Primitive(RR_PrimitiveKind.UNIT),
                paramVars = immListOf(),
                body = RR_Statement.Empty,
                frame = makeFrame(),
            ),
            isTest = false,
            disabled = false,
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_OperationDefinition() {
        fun make() = RR_OperationDefinition(
            base = makeBase(),
            mountName = makeMountName("test.my_op"),
            modifiers = OperationModifiers.getInstance(isCompound = false, isSingular = false),
            params = immListOf(),
            paramVars = immListOf(),
            body = RR_Statement.Empty,
            guardBody = null,
            frame = makeFrame(),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_QueryDefinition() {
        fun make() = RR_QueryDefinition(
            base = makeBase(),
            mountName = makeMountName("test.my_query"),
            body = RR_UserQueryBody(
                retType = RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
                params = immListOf(),
                paramVars = immListOf(),
                body = RR_Statement.Return(
                    RR_Expr.ConstantValue(
                        RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
                        RR_ConstantValue.Int(42),
                    ),
                ),
                frame = makeFrame(),
            ),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_GlobalConstantDefinition() {
        fun make() = RR_GlobalConstantDefinition(
            base = makeBase(),
            constId = GlobalConstantId(
                0,
                AppUid(1L),
                ModuleKey(ModuleName.of("test"), null),
                "test:MY_CONST",
                "MY_CONST",
            ),
            type = RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
            expr = RR_Expr.ConstantValue(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), RR_ConstantValue.Int(123)),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_Module() {
        fun make() = RR_Module(
            name = ModuleName.of("test"),
            directory = false,
            abstract = false,
            external = false,
            externalChain = null,
            test = false,
            disabled = false,
            selected = true,
            entities = immMapOf(),
            objects = immMapOf(),
            structs = immMapOf(),
            enums = immMapOf(),
            operations = immMapOf(),
            queries = immMapOf(),
            functions = immMapOf(),
            constants = immMapOf(),
            imports = immSetOf(),
            moduleArgs = null,
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_Attribute() {
        assertEquals(makeAttr("x", 0), makeAttr("x", 0))
        assertNotEquals(makeAttr("x", 0), makeAttr("y", 1))
    }

    @Test
    fun testRR_ConstantValue_ByteArray() {
        assertEquals(
            RR_ConstantValue.ByteArray(byteArrayOf(1, 2, 3)),
            RR_ConstantValue.ByteArray(byteArrayOf(1, 2, 3)),
        )
        assertNotEquals(
            RR_ConstantValue.ByteArray(byteArrayOf(1, 2, 3)),
            RR_ConstantValue.ByteArray(byteArrayOf(4, 5, 6)),
        )
    }

    @Test
    fun testRR_Expr() {
        fun make() = RR_Expr.Binary(
            type = RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
            op = "R_BinaryOp_Add_Integer",
            cmpInfo = null,
            left = RR_Expr.Var(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), RR_VarPtr(1L, 0), "x"),
            right = RR_Expr.ConstantValue(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), RR_ConstantValue.Int(1)),
            errPos = ErrorPos("test.rell", 1),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_Statement() {
        fun make() = RR_Statement.For(
            varDeclarator = RR_VarDeclarator.Simple(
                RR_VarPtr(1L, 0),
                RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
                null,
            ),
            expr = RR_Expr.Var(RR_Type.List(RR_Type.Primitive(RR_PrimitiveKind.INTEGER)), RR_VarPtr(1L, 1), "items"),
            iterableAdapter = RR_IterableAdapterKind.DIRECT,
            body = RR_Statement.Empty,
            frameBlock = makeFrameBlock(),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_DbExpr() {
        fun make() = RR_DbExpr.Attr(
            base = RR_DbExpr.Entity(entityDefIndex = 0, entityId = 0),
            attrName = "name",
            type = RR_Type.Primitive(RR_PrimitiveKind.TEXT),
        )
        assertEquals(make(), make())
    }

    @Test
    fun testRR_FunctionCall() {
        fun make() = RR_FunctionCall.Full(
            returnType = RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
            target = RR_FunctionCallTarget.RegularUser(fnDefIndex = 0),
            args = immListOf(
                RR_Expr.ConstantValue(
                    RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
                    RR_ConstantValue.Int(1),
                ),
            ),
            callPos = FilePos("test.rell", 10),
            mapping = immListOf(0),
        )
        assertEquals(make(), make())
    }

    private fun makeAttr(name: String, index: Int) = RR_Attribute(
        index = index,
        rName = makeName(name),
        type = RR_Type.Primitive(RR_PrimitiveKind.TEXT),
        mutable = false,
        keyIndexKind = null,
        canSetInCreate = true,
        sqlMapping = name,
        defaultExpr = null,
        isDbModification = false,
        sizeConstraint = null,
    )
}
