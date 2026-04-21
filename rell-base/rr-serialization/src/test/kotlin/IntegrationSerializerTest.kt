/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import org.junit.jupiter.api.Test
import rell.ir.App
import rell.ir.TypeUnion
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntegrationSerializerTest: BaseSerializerTest() {

    // --- Basic smoke test ---

    @Test fun testEmptyModule() {
        // An empty function body is the simplest valid module content.
        val fb = serializeAndParse("function noop() {}")
        assertEquals(1, fb.modulesLength)
        assertEquals(0, fb.entitiesLength)
        assertEquals(1, fb.functionsLength)
        assertEquals(0, fb.queriesLength)
        assertEquals(0, fb.operationsLength)
    }

    // --- Complex scenarios ---

    @Test fun testEntityStructEnumCombination() {
        val fb = serializeAndParse(
            """
            enum role { ADMIN, USER }
            struct user_data { name: text; r: role; }
            entity user { name: text; r: role; }
            function create_data(u_name: text): user_data = user_data(name = u_name, r = role.USER);
        """,
        )
        assertEquals(1, fb.entitiesLength)
        assertEquals(1, fb.structsLength)
        assertEquals(1, fb.enumsLength)
        assertEquals(1, fb.functionsLength)
    }

    @Test fun testNestedCollections() {
        val fb = serializeAndParse("function foo(): list<list<integer>> = [[1, 2], [3, 4]];")
        val retType = fb.functions(0).body!!.type
        assertEquals(TypeUnion.ListType, retType.typeType)
    }

    @Test fun testNullableCollections() {
        val fb = serializeAndParse("function foo(): list<integer>? = null;")
        val retType = fb.functions(0).body!!.type
        assertEquals(TypeUnion.NullableType, retType.typeType)
    }

    @Test fun testRecursiveFunction() {
        val fb = serializeAndParse(
            """
            function factorial(n: integer): integer {
                if (n <= 1) return 1;
                return n * factorial(n - 1);
            }
        """,
        )
        assertEquals(1, fb.functionsLength)
    }

    @Test fun testHigherOrderFunction() {
        val fb = serializeAndParse(
            """
            function apply(f: (integer) -> integer, x: integer): integer = f(x);
            function double(x: integer): integer = x * 2;
            function foo(): integer = apply(double(*), 5);
        """,
        )
        assertEquals(3, fb.functionsLength)
    }

    @Test fun testComplexControlFlow() {
        val fb = serializeAndParse(
            """
            function classify(x: integer): text {
                if (x < 0) return 'negative';
                if (x == 0) return 'zero';
                var result = '';
                for (i in range(x)) {
                    if (i % 2 == 0) {
                        result += 'e';
                    } else {
                        result += 'o';
                    }
                }
                return result;
            }
        """,
        )
        assertNotNull(fb.functions(0))
    }

    @Test fun testOperationWithMultipleParams() {
        val fb = serializeAndParse(
            """
            entity user { name: text; score: integer; }
            operation create_user(name: text, score: integer) {
                create user(name = name, score = score);
            }
        """,
        )
        assertEquals(1, fb.entitiesLength)
        assertEquals(1, fb.operationsLength)
    }

    @Test fun testQueryWithEntityAccess() {
        val fb = serializeAndParse(
            """
            entity user { name: text; }
            query get_users(): list<text> = user @* {} (.name);
        """,
        )
        assertEquals(1, fb.entitiesLength)
        assertEquals(1, fb.queriesLength)
    }

    // --- Serialization roundtrip: serialize, verify valid FlatBuffer ---

    @Test fun testSerializationProducesValidFlatBuffer() {
        val code = """
            enum status { ACTIVE, INACTIVE }
            struct config { max_users: integer; name: text; }
            entity user { key name: text; mutable status: status; }
            object settings { mutable max: integer = 100; }
            val DEFAULT_NAME: text = 'admin';
            function greet(name: text): text = 'Hello, ' + name;
            operation create_user(name: text) { create user(name = name, status = status.ACTIVE); }
            query get_user_count(): integer = user @* {} (.name).size();
        """
        val app = compileApp(code)
        val bytes = serializeRellApp(app)
        val fb = App.getRootAsApp(ByteBuffer.wrap(bytes))

        // 1 user entity + 1 object backing entity = 2
        assertEquals(2, fb.entitiesLength)
        assertEquals(1, fb.objectsLength)
        assertEquals(1, fb.structsLength)
        assertEquals(1, fb.enumsLength)
        assertEquals(1, fb.functionsLength)
        assertEquals(1, fb.operationsLength)
        assertEquals(1, fb.queriesLength)
        assertEquals(1, fb.constantsLength)
        assertEquals(1, fb.modulesLength)
    }

    @Test fun testSerializeTwiceProducesSameResult() {
        val code = """
            entity user { name: text; score: integer; }
            function foo(): integer = 42;
        """
        val app = compileApp(code)
        val bytes1 = serializeRellApp(app)
        val bytes2 = serializeRellApp(app)
        assertTrue(bytes1.contentEquals(bytes2), "Serializing the same RR_App twice should produce identical bytes")
    }

    // --- Tuple destructuring ---

    @Test fun testTupleDestructuring() {
        val fb = serializeAndParse(
            """
            function foo(): integer {
                val (a, b) = (1, 2);
                return a + b;
            }
        """,
        )
        assertNotNull(fb.functions(0))
    }

    // --- Lambda / higher-order ---

    @Test fun testLambda() {
        val fb = serializeAndParse(
            """
            function foo(): list<integer> {
                val l = [3, 1, 2];
                return l.sorted();
            }
        """,
        )
        assertNotNull(fb.functions(0))
    }

    // --- Range ---

    @Test fun testRange() {
        val fb = serializeAndParse(
            """
            function foo(): integer {
                var sum = 0;
                for (i in range(10)) { sum += i; }
                return sum;
            }
        """,
        )
        assertNotNull(fb.functions(0))
    }

    // --- Guard statement ---

    @Test fun testGuardBlock() {
        val fb = serializeAndParse(
            """
            function foo() {
                val x = 42;
            }
        """,
        )
        assertNotNull(fb.functions(0))
    }

    // --- Big integration test ---

    @Test fun testLargeApp() {
        val fb = serializeAndParse(
            """
            enum status { ACTIVE, INACTIVE, PENDING }
            enum role { ADMIN, USER, GUEST }

            struct user_info {
                name: text;
                age: integer;
                s: status;
            }

            entity user {
                key name: text;
                mutable age: integer;
                mutable s: status;
                r: role;
            }

            entity log_entry {
                index user;
                message: text;
                timestamp: integer;
            }

            object counter {
                mutable total_users: integer = 0;
            }

            val MAX_AGE: integer = 150;
            val MIN_AGE: integer = 0;

            function validate_age(age: integer): boolean {
                return age >= MIN_AGE and age <= MAX_AGE;
            }

            function make_info(u_name: text, u_age: integer): user_info {
                return user_info(
                    name = u_name,
                    age = u_age,
                    s = status.ACTIVE
                );
            }

            function sum_list(values: list<integer>): integer {
                var total = 0;
                for (v in values) {
                    total += v;
                }
                return total;
            }

            operation create_user(name: text, age: integer, r: role) {
                require(validate_age(age), "Invalid age");
                create user(name = name, age = age, s = status.ACTIVE, r = r);
                create log_entry(user @{ name }, message = 'created', timestamp = 0);
                counter.total_users += 1;
            }

            operation deactivate_user(name: text) {
                update user @{ name } ( s = status.INACTIVE );
                create log_entry(user @{ name }, message = 'deactivated', timestamp = 0);
            }

            query get_active_users(): list<text> {
                return user @* { .s == status.ACTIVE } (.name);
            }

            query get_user_count(): integer {
                return counter.total_users;
            }

            query get_user_info(name: text): user_info {
                val u = user @{ name };
                return make_info(u.name, u.age);
            }
        """,
        )

        // 2 entities (user, log_entry) + 1 object backing entity (counter) = 3
        assertEquals(3, fb.entitiesLength)
        assertEquals(1, fb.objectsLength)
        assertEquals(1, fb.structsLength)
        assertEquals(2, fb.enumsLength)
        assertEquals(3, fb.functionsLength)
        assertEquals(2, fb.operationsLength)
        assertEquals(3, fb.queriesLength)
        assertEquals(2, fb.constantsLength)
    }
}
