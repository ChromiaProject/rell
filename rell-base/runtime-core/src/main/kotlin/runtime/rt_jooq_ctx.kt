/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import org.jooq.DSLContext
import org.jooq.QueryPart
import org.jooq.SQLDialect
import org.jooq.conf.*
import org.jooq.impl.DSL

/**
 * jOOQ render context shared across the runtime SQL emitter:
 * PostgreSQL dialect, always-quoted identifiers, uppercase keywords, no formatting,
 * indexed `?` placeholders. Generated SQL is rendered once per query and paired with the
 * parallel `Rt_Value` bind list tracked by `DbSqlGen` (in `runtime-interpreter`).
 */
val JOOQ_CTX: DSLContext = DSL.using(
    SQLDialect.POSTGRES,
    Settings()
        .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED)
        .withRenderKeywordCase(RenderKeywordCase.UPPER)
        .withRenderOptionalAsKeywordForTableAliases(RenderOptionalKeyword.OFF)
        .withRenderOptionalAsKeywordForFieldAliases(RenderOptionalKeyword.OFF)
        .withRenderFormatted(false)
        .withParamType(ParamType.INDEXED),
)

/** Renders a jOOQ [QueryPart] as a SQL string in the runtime's standard render context.
 *  Uses indexed `?` placeholders — pair with a parallel bind list. For DDL or any standalone
 *  SQL that must execute without separate binds, use [renderJooqInlined] instead. */
fun renderJooq(part: QueryPart): String = JOOQ_CTX.render(part)

/** Renders a jOOQ [QueryPart] with inline literals (no `?` placeholders). Use for DDL —
 *  PostgreSQL does not allow bind parameters in `CREATE TABLE`, `ALTER TABLE … CHECK (…)`,
 *  `CREATE INDEX`, etc. — and for standalone DML where binds are not tracked separately. */
fun renderJooqInlined(part: QueryPart): String = JOOQ_CTX.renderInlined(part)

/** Renders a quoted identifier (table or column name) for embedding in raw SQL strings. */
fun renderName(name: String): String = renderJooq(DSL.name(name))
