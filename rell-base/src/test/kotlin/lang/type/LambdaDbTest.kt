/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

/**
 * Database access inside a lambda body. The body is lifted into a synthetic function, so these tests
 * pin down that it inherits the enclosing definition's database context rather than opening a hole in
 * it. A read at-expression works; a write executes when the lambda ultimately runs in an operation,
 * and is rejected at runtime when it runs in a read-only (query) context - exactly as a db write in
 * any ordinary function is. Consensus-critical: a lambda must not let a query modify the database.
 */
class LambdaDbTest: BaseRellTest(useSql = true) {
    // Entity plus the higher-order takers, all in one def list: calling def() would replace this list
    // rather than extend it, so the fixtures live here together.
    override fun entityDefs() = listOf(
        "entity user { name; mutable score: integer; }",
        "function pick(f: () -> integer): integer = f();",
        "function pickAll(f: () -> list<user>): list<user> = f();",
        "function run(f: () -> unit) { f(); }",
        "function runInt(f: () -> unit): integer { f(); return 0; }",
    )

    private fun insertUsers() {
        chkOp("create user(name = 'Bob', score = 10); create user(name = 'Alice', score = 20);")
    }

    @Test fun testDbReadInLambdaExprBody() {
        insertUsers()
        chk("pick(() -> user @ { .name == 'Bob' } ( .score ))", "int[10]")
        chk("pick(() -> user @ { .name == 'Alice' } ( .score ))", "int[20]")
    }

    @Test fun testDbReadInLambdaBlockBody() {
        insertUsers()
        chk("pick(() -> { val u = user @ { .name == 'Bob' }; u.score * 2 })", "int[20]")
    }

    @Test fun testDbReadWithCapturedFilter() {
        // A captured local flows into the at-expression's where-clause inside the lifted function.
        insertUsers()
        chkEx("{ val minScore = 15; return pickAll(() -> user @* { .score >= minScore }); }", "list<user>[user[2]]")
    }

    @Test fun testDbWriteInLambdaRunsInOperation() {
        // Operation context: the write inside the lifted lambda function executes and is committed.
        insertUsers()
        chkOp("run(() -> { update user @ { .name == 'Bob' } ( score = 99 ); });")
        chk("user @ { .name == 'Bob' } ( .score )", "int[99]")
    }

    @Test fun testDbWriteInLambdaRejectedInQuery() {
        // Read-only (query) context: the lambda body is compiled in the query's own definition context,
        // so the compiler statically sees the read-only context and rejects the db write - even more
        // strictly than a named function called from a query, which only fails at runtime (rt_err:n:def).
        // The lambda inherits its lexical enclosing db-context and is not a hole for a query to write.
        insertUsers()
        chk("runInt(() -> { update user @ { .name == 'Bob' } ( score = 99 ); })", "ct_err:no_db_update:query")
        chk("user @ { .name == 'Bob' } ( .score )", "int[10]")
    }
}
