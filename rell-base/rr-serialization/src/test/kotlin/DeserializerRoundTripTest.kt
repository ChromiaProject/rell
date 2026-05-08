/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * True round-trip tests: `deserialize(serialize(RR_App))` produces a structurally equal [RR_App].
 *
 * Unlike [RoundTripSerializerTest] (which compares RR_ against raw FlatBuffer accessors),
 * these tests exercise the full `RR_App → bytes → RR_App` pipeline and assert equality
 * on the reconstructed data classes.
 */
class DeserializerRoundTripTest: BaseSerializerTest() {

    private fun roundTrip(code: String) {
        val original = compileApp(code)
        val bytes = serializeRellApp(original)
        val deserialized = deserializeRellApp(bytes)

        // Flat array sizes must match.
        assertEquals(original.allEntities.size, deserialized.allEntities.size, "entity count")
        assertEquals(original.allStructs.size, deserialized.allStructs.size, "struct count")
        assertEquals(original.allEnums.size, deserialized.allEnums.size, "enum count")
        assertEquals(original.allObjects.size, deserialized.allObjects.size, "object count")
        assertEquals(original.allOperations.size, deserialized.allOperations.size, "operation count")
        assertEquals(original.allQueries.size, deserialized.allQueries.size, "query count")
        assertEquals(original.allFunctions.size, deserialized.allFunctions.size, "function count")
        assertEquals(original.allConstants.size, deserialized.allConstants.size, "constant count")
        assertEquals(original.modules.size, deserialized.modules.size, "module count")
        assertEquals(original.externalChains.size, deserialized.externalChains.size, "external chain count")

        // Module names and structure.
        for (i in original.modules.indices) {
            val om = original.modules[i]
            val dm = deserialized.modules[i]
            assertEquals(om.name, dm.name, "module[$i].name")
            assertEquals(om.directory, dm.directory, "module[$i].directory")
            assertEquals(om.abstract, dm.abstract, "module[$i].abstract")
            assertEquals(om.external, dm.external, "module[$i].external")
            assertEquals(om.test, dm.test, "module[$i].test")
            assertEquals(om.entities.keys, dm.entities.keys, "module[$i].entities.keys")
            assertEquals(om.structs.keys, dm.structs.keys, "module[$i].structs.keys")
            assertEquals(om.enums.keys, dm.enums.keys, "module[$i].enums.keys")
            assertEquals(om.functions.keys, dm.functions.keys, "module[$i].functions.keys")
            assertEquals(om.operations.keys, dm.operations.keys, "module[$i].operations.keys")
            assertEquals(om.queries.keys, dm.queries.keys, "module[$i].queries.keys")
        }

        // Entity definitions.
        for (i in original.allEntities.indices) {
            val oe = original.allEntities[i]
            val de = deserialized.allEntities[i]
            assertEquals(oe.base.defId, de.base.defId, "entity[$i].defId")
            assertEquals(oe.base.defName.qualifiedName, de.base.defName.qualifiedName, "entity[$i].defName")
            assertEquals(oe.rName, de.rName, "entity[$i].rName")
            assertEquals(oe.flags, de.flags, "entity[$i].flags")
            assertEquals(oe.sqlMapping.mountName, de.sqlMapping.mountName, "entity[$i].sqlMapping.mountName")
            assertEquals(oe.sqlMapping.kind, de.sqlMapping.kind, "entity[$i].sqlMapping.kind")
            assertEquals(oe.attributes.keys, de.attributes.keys, "entity[$i].attributes.keys")
            for (attrName in oe.attributes.keys) {
                val oa = oe.attributes[attrName]!!
                val da = de.attributes[attrName]!!
                assertEquals(oa.type, da.type, "entity[$i].attr[$attrName].type")
                assertEquals(oa.mutable, da.mutable, "entity[$i].attr[$attrName].mutable")
            }
        }

        // Struct definitions.
        for (i in original.allStructs.indices) {
            val os = original.allStructs[i]
            val ds = deserialized.allStructs[i]
            assertEquals(os.base.defId, ds.base.defId, "struct[$i].defId")
            assertEquals(os.struct.name, ds.struct.name, "struct[$i].name")
            assertEquals(os.struct.attributes.keys, ds.struct.attributes.keys, "struct[$i].attributes.keys")
        }

        // Enum definitions.
        for (i in original.allEnums.indices) {
            val oe = original.allEnums[i]
            val de = deserialized.allEnums[i]
            assertEquals(oe.base.defId, de.base.defId, "enum[$i].defId")
            assertEquals(oe.attrs.size, de.attrs.size, "enum[$i].attrs.size")
            for (j in oe.attrs.indices) {
                assertEquals(oe.attrs[j].name, de.attrs[j].name, "enum[$i].attr[$j].name")
                assertEquals(oe.attrs[j].value, de.attrs[j].value, "enum[$i].attr[$j].value")
            }
        }

        // Query/operation mount names from allQueries/allOperations must survive round-trip.
        // Note: original.queries may contain extra system queries not in allQueries.
        val originalQueryMounts = original.allQueries.map { it.mountName }.toSet()
        val deserializedQueryMounts = deserialized.allQueries.map { it.mountName }.toSet()
        assertEquals(originalQueryMounts, deserializedQueryMounts, "query mount names (from flat array)")

        val originalOpMounts = original.allOperations.map { it.mountName }.toSet()
        val deserializedOpMounts = deserialized.allOperations.map { it.mountName }.toSet()
        assertEquals(originalOpMounts, deserializedOpMounts, "operation mount names (from flat array)")

        // Definition ID index maps must match.
        assertEquals(original.entityDefIdIndex, deserialized.entityDefIdIndex, "entityDefIdIndex")
        assertEquals(original.enumDefIdIndex, deserialized.enumDefIdIndex, "enumDefIdIndex")

        assertEquals(
            original.nativeFunctions.keys,
            deserialized.nativeFunctions.keys,
            "nativeFunctions.keys",
        )
        for (name in original.nativeFunctions.keys) {
            val oh = original.nativeFunctions.getValue(name)
            val dh = deserialized.nativeFunctions.getValue(name)
            assertEquals(oh.type, dh.type, "nativeFunctions[$name].type")
            assertEquals(oh.params.size, dh.params.size, "nativeFunctions[$name].params.size")
            for (i in oh.params.indices) {
                assertEquals(oh.params[i].name, dh.params[i].name, "nativeFunctions[$name].params[$i].name")
                assertEquals(oh.params[i].type, dh.params[i].type, "nativeFunctions[$name].params[$i].type")
            }
        }
    }

    // --- Tests ---

    @Test fun testEmpty() = roundTrip("")

    @Test fun testSimpleQuery() = roundTrip("query q() = 42;")

    @Test fun testSimpleFunction() = roundTrip("function f(): integer = 1 + 2;")

    @Test fun testEntity() = roundTrip("entity user { name: text; score: integer; }")

    @Test fun testEntityWithKeys() = roundTrip(
        """
        entity user {
            key name: text;
            index score: integer;
            mutable bio: text;
        }
    """,
    )

    @Test fun testStruct() = roundTrip("struct point { x: integer; y: integer; }")

    @Test fun testEnum() = roundTrip("enum color { RED, GREEN, BLUE }")

    @Test fun testObject() = roundTrip(
        """
        object config {
            mutable max_size: integer = 100;
        }
    """,
    )

    @Test fun testOperation() = roundTrip(
        """
        entity user { name: text; }
        operation create_user(name: text) {
            create user(name);
        }
    """,
    )

    @Test fun testQueryWithParams() = roundTrip(
        """
        query greet(name: text, loud: boolean): text {
            return if (loud) name.upper_case() else name;
        }
    """,
    )

    @Test fun testMultipleDefinitions() = roundTrip(
        """
        entity user { name: text; }
        struct user_dto { name: text; }
        enum role { ADMIN, USER }
        function get_name(u: user): text = u.name;
        query all_users() = user @* {};
        operation add_user(name: text) { create user(name); }
    """,
    )

    @Test fun testConstants() = roundTrip(
        """
        val MAX_SIZE = 100;
        val GREETING = "hello";
        function f() = MAX_SIZE;
    """,
    )

    @Test fun testFunctionWithBody() = roundTrip(
        """
        function factorial(n: integer): integer {
            if (n <= 1) return 1;
            return n * factorial(n - 1);
        }
    """,
    )

    @Test fun testCollectionTypes() = roundTrip(
        """
        function f(): list<integer> = [1, 2, 3];
        function g(): set<text> = set(["a", "b"]);
        function h(): map<text, integer> = ["a": 1];
    """,
    )

    @Test fun testNullableTypes() = roundTrip(
        """
        function f(x: integer?): integer = x ?: 0;
    """,
    )

    @Test fun testTupleTypes() = roundTrip(
        """
        function f(): (integer, text) = (1, "hello");
        function g(): (x: integer, y: integer) = (x = 1, y = 2);
    """,
    )

    @Test fun testWhenExpression() = roundTrip(
        """
        function f(x: integer): text = when (x) {
            1 -> "one";
            2 -> "two";
            else -> "other";
        };
    """,
    )

    @Test fun testForLoop() = roundTrip(
        """
        function sum(xs: list<integer>): integer {
            var total = 0;
            for (x in xs) total += x;
            return total;
        }
    """,
    )

    @Test fun testStructCreate() = roundTrip(
        """
        struct point { x: integer; y: integer; }
        function origin() = point(x = 0, y = 0);
    """,
    )

    @Test fun testNativeFunctions() = roundTrip(
        """
        @native function my_add(x: integer, y: integer): integer;
        @native function my_greet(name: text): text;
        @native function my_notify(message: text);
    """,
    )
}
