package com.chromia.rell.dokka.transformers

import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.transformers.documentation.PreMergeDocumentableTransformer

class NullPreMergeDocumentableTransformer: PreMergeDocumentableTransformer {
    override fun invoke(modules: List<DModule>): List<DModule> {
        return modules
    }
}