/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine

/**
 * Lazily creates one polyglot [Engine] + [Context] for [Tf_Language.LANG_ID] so that
 * `Tf_Language.POLYGLOT_INSTANCE` is populated. The instance becomes the SOM identity key for
 * `StaticShape.newBuilder(...)`; without it, SOM's `getPolyglotLanguageInstance(language)` lookup
 * NPEs and the struct path falls back to [net.postchain.rell.base.runtime.Rt_HeapStruct].
 *
 * One Engine + one Context for the lifetime of the JVM is enough — the Context exists purely to
 * trigger `Tf_Language.createContext`, which captures `this` into `POLYGLOT_INSTANCE`. Rell
 * execution itself never enters this Context; the language is reused as a hosted SOM key.
 *
 * Idempotent and thread-safe via `AtomicReference.compareAndSet` in [Tf_Language.createContext].
 * Repeated calls after `POLYGLOT_INITIALISED` is set return immediately. Failures (missing
 * polyglot SDK, missing service registration, sandbox restrictions) flip the registry's sticky
 * `somAvailable` flag in [net.postchain.rell.base.runtime.truffle.values.Tf_StructShapeRegistry] and the struct path falls back gracefully.
 */
object Tf_PolyglotBootstrap {
    @Volatile
    private var initialised: Boolean = false

    /**
     * Build an [Engine] + [Context] for [Tf_Language.LANG_ID] and `enter()` once so polyglot
     * invokes [Tf_Language.createContext]. Returns the captured language instance, or `null` if
     * the bootstrap failed (caller should treat SOM as unavailable).
     */
    fun ensure(): Tf_Language? {
        Tf_Language.POLYGLOT_INSTANCE.get()?.let { return it }
        return synchronized(this) {
            Tf_Language.POLYGLOT_INSTANCE.get() ?: bootstrapLocked()
        }
    }

    private fun bootstrapLocked(): Tf_Language? {
        if (initialised) return Tf_Language.POLYGLOT_INSTANCE.get()
        initialised = true
        return try {
            // Engine + Context construction is the polyglot framework's discovery hook: it walks
            // the META-INF service for `TruffleLanguageProvider`, instantiates `Tf_Language`, and
            // calls `createContext` (which stashes `this` into `POLYGLOT_INSTANCE`).
            val engine = Engine.newBuilder(Tf_Language.LANG_ID).build()
            // `allowAllAccess(true)` keeps the host-side polyglot Context permissive enough to
            // initialise `Tf_Language` without sandbox restrictions interfering. The Context is
            // not used to execute Rell code (we only need it to trigger `createContext`), so the
            // permissive setting has no security impact — no untrusted polyglot source ever runs
            // through this Context.
            val context = Context.newBuilder(Tf_Language.LANG_ID)
                .engine(engine)
                .allowAllAccess(true)
                .build()

            // `initialize` is enough to cross the polyglot→language boundary and trigger
            // `createContext`; no `enter()` required since we never call into language code.
            context.initialize(Tf_Language.LANG_ID)
            Tf_Language.POLYGLOT_INSTANCE.get()
        } catch (e: Throwable) {
            lastFailure = e
            null
        }
    }

    /**
     * Last bootstrap failure, if any. Exposed for diagnostic tests so a regression in service
     * registration (missing `META-INF/services` entry, polyglot SDK absent, etc.) surfaces a
     * concrete error rather than a silent fallback.
     */
    @Volatile
    var lastFailure: Throwable? = null
        private set
}
