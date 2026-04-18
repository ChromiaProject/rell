/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx;

import net.postchain.rell.api.nativ.RellNativeEnvironment;

@SuppressWarnings("unused")
public final class JavaNativeModule {
    public JavaNativeModule(RellNativeEnvironment env) {
    }

    // @native function f(a: integer, b: boolean?, c: text, d: text?): integer;
    public long f(long a, Boolean b, String c, String d) {
        return a * a;
    }
}
