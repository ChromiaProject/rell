/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.provider.TruffleLanguageProvider;

import java.util.Collections;
import java.util.List;

/**
 * Manual {@link TruffleLanguageProvider} for {@link Tf_Language}.
 *
 * <p>Truffle's DSL annotation processor would normally generate this class from
 * {@code @TruffleLanguage.Registration} on the language class, but {@code rell-base/runtime-truffle}
 * is intentionally kapt-free (see {@code tf_nodes.kt}'s rationale for hand-rolled nodes), so we
 * write the provider by hand. The {@code META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider}
 * file alongside this source registers the class with Truffle's {@code ServiceLoader} discovery.
 *
 * <p>Java rather than Kotlin: {@link TruffleLanguageProvider}'s constructor and abstract methods
 * are {@code protected}, and Kotlin cannot easily implement them in another package without
 * exposing them publicly (which is forbidden — Truffle pins them as {@code protected} so polyglot
 * is the only legitimate caller).
 *
 * <p>The {@code @TruffleLanguage.Registration} annotation lives <em>on the provider</em>, not
 * the language: in Truffle 25.x the polyglot LanguageCache reads the annotation off the provider
 * class via reflection at discovery time. Without it, the language is silently filtered out with
 * the warning "Provider class ... is missing @Registration annotation".
 */
@TruffleLanguage.Registration(
        id = Tf_Language.LANG_ID,
        name = "Rell-Truffle",
        version = "0.0.1",
        characterMimeTypes = "application/x-rell",
        contextPolicy = TruffleLanguage.ContextPolicy.SHARED
)
public final class Tf_LanguageProvider extends TruffleLanguageProvider {
    @Override
    protected String getLanguageClassName() {
        return Tf_Language.class.getName();
    }

    @Override
    protected Object create() {
        return new Tf_Language();
    }

    @Override
    protected java.util.Collection<String> getServicesClassNames() {
        return Collections.emptyList();
    }

    @Override
    protected List<TruffleFile.FileTypeDetector> createFileTypeDetectors() {
        return Collections.emptyList();
    }
}
