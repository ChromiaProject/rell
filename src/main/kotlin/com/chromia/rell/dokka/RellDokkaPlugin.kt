package com.chromia.rell.dokka

import com.chromia.rell.dokka.signature.RellSignatureProvider
import com.chromia.rell.dokka.translator.RellSourceToDocumentableTranslator
import com.chromia.rell.dokka.translator.RellDocumentableToPageTranslator
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

class RellDokkaPlugin : DokkaPlugin() {
    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement

    val r: Extension<SourceToDocumentableTranslator, *, *> by extending {
        CoreExtensions.sourceToDocumentableTranslator with RellSourceToDocumentableTranslator
    }

    val myPageCreator by extending {
        with(plugin<DokkaBase>()) {
            CoreExtensions.documentableToPageTranslator providing ::RellDocumentableToPageTranslator override documentableToPageTranslator
        }

        /*(CoreExtensions.documentableToPageTranslator providing
                { MyPageTransformer(it) } override plugin<DokkaBase>().documentableToPageTranslator
                )*/
    }

    val mySignatureProvider by extending {
        with (plugin<DokkaBase>()) {
            signatureProvider providing ::RellSignatureProvider override kotlinSignatureProvider
        }
    }
}