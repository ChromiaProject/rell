/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.*

class RR_ResolveTest {
    private fun resolve(code: String): RR_App {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        assertTrue(cRes.errors.isEmpty(), "Compilation errors: ${cRes.errors.map { it.code }}")
        return cRes.rrApp!!
    }

    // --- Module structure ---

    @Test fun testEmptyModule() {
        val app = resolve("")
        assertEquals(1, app.modules.size)
        val mod = app.modules[0]
        assertEquals(ModuleName.EMPTY, mod.name)
        assertFalse(mod.test)
        assertFalse(mod.abstract)
        assertFalse(mod.external)
    }

    // --- Entity resolution ---

    @Test fun testEntity() {
        val app = resolve("entity user { name: text; score: integer; }")
        assertEquals(1, app.allEntities.size)

        val entity = app.allEntities[0]
        assertEquals("user", entity.base.simpleName)
        assertEquals(2, entity.attributes.size)

        val nameAttr = entity.strAttributes["name"]!!
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.TEXT), nameAttr.type)
        assertFalse(nameAttr.mutable)

        val scoreAttr = entity.strAttributes["score"]!!
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), scoreAttr.type)
    }

    @Test fun testEntityMutable() {
        val app = resolve("entity user { mutable name: text; }")
        val attr = app.allEntities[0].strAttributes["name"]!!
        assertTrue(attr.mutable)
    }

    @Test fun testEntityKeyIndex() {
        val app = resolve("entity user { key name: text; index score: integer; }")
        val entity = app.allEntities[0]
        assertEquals(1, entity.keys.size)
        assertEquals(1, entity.indexes.size)
    }

    @Test fun testEntityType() {
        val app = resolve("entity user { name: text; }")
        val entity = app.allEntities[0]
        assertEquals(RR_Type.Entity(0), entity.type)
    }

    // --- Struct resolution ---

    @Test fun testStruct() {
        val app = resolve("struct point { x: integer; y: integer; }")
        assertEquals(1, app.allStructs.size)

        val struct = app.allStructs[0].struct
        assertEquals("point", struct.name)
        assertEquals(2, struct.attributes.size)
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), struct.strAttributes["x"]!!.type)
    }

    // --- Enum resolution ---

    @Test fun testEnum() {
        val app = resolve("enum color { RED, GREEN, BLUE }")
        assertEquals(1, app.allEnums.size)
        val e = app.allEnums[0]
        assertEquals(3, e.attrs.size)
        assertEquals("RED", e.attrs[0].name.str)
    }

    // --- Object resolution ---

    @Test fun testObject() {
        val app = resolve("object config { mutable value: integer = 0; }")
        assertEquals(1, app.allObjects.size)
        // Objects have a backing entity.
        assertTrue(app.allEntities.isNotEmpty())
    }

    // --- Function resolution ---

    @Test fun testFunction() {
        val app = resolve("function add(a: integer, b: integer): integer = a + b;")
        assertEquals(1, app.allFunctions.size)

        val fn = app.allFunctions[0]
        assertEquals("add", fn.base.simpleName)
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), fn.fnBase.resultType)
        assertEquals(2, fn.fnBase.params.size)
        assertEquals("a", fn.fnBase.params[0].name.str)
        assertEquals("b", fn.fnBase.params[1].name.str)
    }

    @Test fun testFunctionBody() {
        val app = resolve("""
            function fib(n: integer): integer {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }
        """)
        val fn = app.allFunctions[0]
        assertIs<RR_Statement.Block>(fn.fnBase.body)
    }

    // --- Operation resolution ---

    @Test fun testOperation() {
        val app = resolve("operation create_user(name: text) {}")
        assertEquals(1, app.allOperations.size)

        val op = app.allOperations[0]
        assertEquals("create_user", op.base.simpleName)
        assertEquals(1, op.params.size)
        assertEquals("name", op.params[0].name.str)
        assertNotNull(op.mountName)
    }

    @Test fun testOperationMountNameLookup() {
        val app = resolve("operation my_op() {}")
        assertEquals(1, app.operations.size)
        val op = app.operations.values.first()
        assertEquals("my_op", op.base.simpleName)
        // The mount-name map should point to the same definition as the flat array.
        assertEquals(app.allOperations[0].base.defId, op.base.defId)
    }

    // --- Query resolution ---

    @Test fun testQuery() {
        val app = resolve("query get_value(): integer = 42;")
        assertEquals(1, app.allQueries.size)

        val query = app.allQueries[0]
        assertEquals("get_value", query.base.simpleName)
        assertIs<RR_UserQueryBody>(query.body)
    }

    @Test fun testQueryReturnType() {
        val app = resolve("query get_value(): integer = 42;")
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), app.allQueries[0].type())
    }

    // --- Global constant resolution ---

    @Test fun testConstant() {
        val app = resolve("val MAX: integer = 100;")
        assertEquals(1, app.allConstants.size)

        val c = app.allConstants[0]
        assertEquals("MAX", c.base.simpleName)
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), c.type)
        assertIs<RR_Expr.ConstantValue>(c.expr)
    }

    // --- Type resolution ---

    @Test fun testCollectionTypes() {
        val app = resolve("""
            function f_list(): list<integer> = [1];
            function f_set(): set<text> = set(['a']);
            function f_map(): map<text, integer> = ['a': 1];
        """)
        val types = app.allFunctions.map { it.fnBase.resultType }
        assertIs<RR_Type.List>(types[0])
        assertIs<RR_Type.Set>(types[1])
        assertIs<RR_Type.Map>(types[2])
    }

    @Test fun testNullableType() {
        val app = resolve("function foo(): integer? = null;")
        val retType = app.allFunctions[0].fnBase.resultType
        assertIs<RR_Type.Nullable>(retType)
        assertEquals(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), retType.value)
    }

    @Test fun testTupleType() {
        val app = resolve("function foo(): (integer, text) = (1, 'a');")
        val retType = app.allFunctions[0].fnBase.resultType
        assertIs<RR_Type.Tuple>(retType)
        assertEquals(2, retType.fields.size)
    }

    @Test fun testFunctionType() {
        val app = resolve("function apply(f: (integer) -> text, x: integer): text = f(x);")
        val paramType = app.allFunctions[0].fnBase.params[0].type
        assertIs<RR_Type.Function>(paramType)
    }

    @Test fun testEntityTypeReference() {
        val app = resolve("""
            entity user { name: text; }
            function get_name(u: user): text = u.name;
        """)
        val paramType = app.allFunctions[0].fnBase.params[0].type
        assertIs<RR_Type.Entity>(paramType)
        assertEquals(0, paramType.defIndex)
        // Verify the def index points to the correct entity.
        assertEquals("user", app.allEntities[paramType.defIndex].base.simpleName)
    }

    @Test fun testStructTypeReference() {
        val app = resolve("""
            struct point { x: integer; y: integer; }
            function origin(): point = point(x = 0, y = 0);
        """)
        val retType = app.allFunctions[0].fnBase.resultType
        assertIs<RR_Type.Struct>(retType)
        assertEquals("point", app.allStructs[retType.defIndex].struct.name)
    }

    // --- Cross-reference indices ---

    @Test fun testDefIdIndices() {
        val app = resolve("""
            entity user { name: text; }
            function foo(): integer = 1;
            operation my_op() {}
            val C: integer = 42;
        """)
        // Each definition's defId should be present in the corresponding index.
        for ((i, e) in app.allEntities.withIndex()) {
            assertEquals(i, app.entityDefIdIndex[e.base.defId])
        }
        for ((i, f) in app.allFunctions.withIndex()) {
            assertEquals(i, app.functionDefIdIndex[f.base.defId])
        }
        for ((i, o) in app.allOperations.withIndex()) {
            assertEquals(i, app.operationDefIdIndex[o.base.defId])
        }
        for ((i, c) in app.allConstants.withIndex()) {
            assertEquals(i, app.constantDefIdIndex[c.base.defId])
        }
    }

    // --- Module map ---

    @Test fun testModuleMap() {
        val app = resolve("""
            entity user { name: text; }
            function foo(): integer = 1;
            query bar(): text = 'x';
            operation baz() {}
        """)
        val mod = app.module(ModuleName.EMPTY)
        assertNotNull(mod)
        assertTrue(mod.entities.containsKey("user"))
        assertTrue(mod.functions.containsKey("foo"))
        assertTrue(mod.queries.containsKey("bar"))
        assertTrue(mod.operations.containsKey("baz"))
    }

    // --- Expression IR ---

    @Test fun testConstantExprResolved() {
        val app = resolve("function foo(): integer = 42;")
        // Expression-bodied functions produce a Return directly (no wrapping Block).
        val retExpr = when (val body = app.allFunctions[0].fnBase.body) {
            is RR_Statement.Return -> body.expr
            is RR_Statement.Block -> {
                val last = body.stmts.last()
                assertIs<RR_Statement.Return>(last)
                last.expr
            }
            else -> fail("Unexpected body type: ${body::class.simpleName}")
        }
        assertNotNull(retExpr)
        assertIs<RR_Expr.ConstantValue>(retExpr)
        val cv = retExpr.value
        assertIs<RR_ConstantValue.Int>(cv)
        assertEquals(42L, cv.value)
    }

    @Test fun testBinaryExprResolved() {
        val app = resolve("function foo(): integer = 1 + 2;")
        val retExpr = when (val body = app.allFunctions[0].fnBase.body) {
            is RR_Statement.Return -> body.expr!!
            is RR_Statement.Block -> ((body.stmts.last() as RR_Statement.Return).expr!!)
            else -> fail("Unexpected body type: ${body::class.simpleName}")
        }
        assertIs<RR_Expr.Binary>(retExpr)
    }

    @Test fun testIfExprResolved() {
        val app = resolve("function foo(x: integer): integer = if (x > 0) x else -x;")
        val retExpr = when (val body = app.allFunctions[0].fnBase.body) {
            is RR_Statement.Return -> body.expr!!
            is RR_Statement.Block -> ((body.stmts.last() as RR_Statement.Return).expr!!)
            else -> fail("Unexpected body type: ${body::class.simpleName}")
        }
        assertIs<RR_Expr.If>(retExpr)
    }

    // --- RR_App preserves RR_App.sqlDefs, externalChains, etc. ---

    @Test fun testSqlDefsPresent() {
        val app = resolve("entity user { name: text; }")
        assertNotNull(app.sqlDefs)
        assertTrue(app.sqlDefs.entities.isNotEmpty())
    }

    @Test fun testExternalChainsEmpty() {
        val rrApp = resolve("entity user { name: text; }")
        assertTrue(rrApp.externalChains.isEmpty())
    }

    // --- Type info map ---

    @Test fun testTypeResolution() {
        val app = resolve("function foo(): integer = 1;")
        val interpreter = RellTestUtils.forCompilation(app, emptyMap())
        val intType = RR_Type.Primitive(RR_PrimitiveKind.INTEGER)
        val rtType = interpreter.resolveType(intType)
        assertEquals("integer", rtType.name)
        assertEquals(intType, rtType.rrType)
    }
}
