/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.RR_App
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * True round-trip tests: `deserialize(serialize(RR_App))` produces a structurally equal [RR_App].
 *
 * The whole RR_ tree consists of data classes referring to definitions by index rather than by
 * reference, so plain equality is a complete check: anything the serializer drops or mangles
 * shows up as a mismatch.
 */
class DeserializerRoundTripTest: BaseSerializerTest() {
    private fun roundTrip(@Language("Rell") code: String) {
        val original = compileApp(code)
        val bytes = serializeRellApp(original)
        val deserialized = deserializeRellApp(bytes)
        assertEquals(original, deserialized)
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

    @Test fun testDefaultParameterValues() = roundTrip(
        """
        function f(x: integer = 42, y: text = "hi"): text = y + x;
    """,
    )

    @Test fun testAttributeDefaultValues() = roundTrip(
        """
        entity user { name: text = "anon"; score: integer = 0; }
        struct point { x: integer = 1; y: integer = 2; }
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
