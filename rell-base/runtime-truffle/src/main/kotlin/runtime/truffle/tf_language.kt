/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import net.postchain.rell.base.runtime.truffle.Tf_Language.Companion.LANG_ID
import java.util.concurrent.atomic.AtomicReference

/**
 * Polyglot-registered [TruffleLanguage] used purely as an identity key for the Truffle
 * Static Object Model (SOM) — `StaticShape.newBuilder(language)` queries
 * `getPolyglotLanguageInstance(language)` while generating the per-struct subclass and NPEs
 * when the language was hand-instantiated rather than created by the polyglot framework.
 *
 * Rell is not exposed as a polyglot guest language: [parse] throws and the Rell driver still
 * dispatches through [Tf_Backend] / [Tf_Driver] without going through `org.graalvm.polyglot`.
 * The registration exists solely so we can manufacture per-struct SOM shapes; one polyglot
 * `Engine`/`Context` is bootstrapped lazily and the framework-created language instance is
 * captured here for SOM use.
 *
 * The `@TruffleLanguage.Registration` annotation lives on the sibling `Tf_LanguageProvider`
 * class — Truffle 25.x's polyglot LanguageCache reflects on the provider, not the language
 * class, so the annotation here would be dead metadata.
 */
class Tf_Language: TruffleLanguage<Tf_Language.Context>() {
    /** Per-context state. Empty: SOM has no per-context dependencies. */
    class Context

    override fun createContext(env: Env): Context {
        // Capture the polyglot-managed instance the first time the framework constructs us.
        // Subsequent context creations on the same instance are a no-op (compareAndSet).
        POLYGLOT_INSTANCE.compareAndSet(null, this)
        return Context()
    }

    override fun parse(request: ParsingRequest): CallTarget = throw UnsupportedOperationException(
        "Tf_Language is not a polyglot guest language — Rell is dispatched directly by Tf_Backend.",
    )

    companion object {
        /** Stable language ID — must match the `Tf_LanguageProvider.getLanguageClassName()` target. */
        const val LANG_ID: String = "rell-tf"

        /**
         * Polyglot-managed [Tf_Language] instance, populated by [createContext] the first time
         * any polyglot `Context` for [LANG_ID] is created. SOM consumers
         * ([net.postchain.rell.base.runtime.truffle.values.Tf_StructShapeRegistry])
         * read this for `StaticShape.newBuilder(...)`. `null` until the bootstrap runs (or if
         * the polyglot framework cannot find the language at runtime — typically a packaging issue).
         */
        val POLYGLOT_INSTANCE: AtomicReference<Tf_Language?> = AtomicReference(null)
    }
}
