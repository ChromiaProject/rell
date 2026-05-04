/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle;

import com.oracle.truffle.api.nodes.DirectCallNode;
import org.jetbrains.annotations.Contract;

/**
 * Static unchecked-cast helpers used on Truffle hot paths.
 *
 * <p>Kotlin's {@code as Type} on a non-null target compiles to
 * {@code Intrinsics.checkNotNull(value, "null cannot be cast to non-null type ...")} followed
 * by {@code CHECKCAST}. The {@code checkNotNull} slow path constructs an
 * {@code IllegalStateException} which, when partial-evaluated, drags in the JDK's
 * {@code Throwable.setStackTrace} → {@code Locale} → {@code SecurityManager} →
 * {@code AccessControlContext} recursion cycle and overflows Graal's inliner-depth budget.
 *
 * <p>Routing the cast through this Java helper emits a single {@code CHECKCAST} opcode.
 * The JVM still throws a {@code ClassCastException} on type mismatch (which never happens for our internal call sites
 * where the runtime always passes the expected type as the sole CallTarget argument).
 */
public final class Tf_Unchecked {
    private Tf_Unchecked() {}

    @Contract(value = "_ -> param1", pure = true)
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object o) {
        return (T) o;
    }

    /**
     * Pass {@code arguments} as the call target's argument array without any spread/copy
     * overhead.
     *
     * <p>Kotlin's {@code call.call(*args)} spread on a Java {@code Object...} varargs method may
     * emit an {@code Arrays.copyOf} per call site (the compiler can't always prove the array
     * isn't escape-stored), which adds an unnecessary heap allocation on the wave-3 inner
     * function-call hot path. In Java, passing an {@code Object[]} to {@code call(Object...)}
     * uses the array as-is (no copy).
     */
    public static Object callDirect(DirectCallNode call, Object[] arguments) {
        return call.call(arguments);
    }
}
