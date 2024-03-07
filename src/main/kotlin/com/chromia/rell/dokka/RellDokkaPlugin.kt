package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.doc.AliasDocTagProvider
import com.chromia.rell.dokka.renderers.html.ChromiaAssetsInstaller
import com.chromia.rell.dokka.signature.RellSignatureProvider
import com.chromia.rell.dokka.translators.documentables.RellDocumentableToPageTranslator
import com.chromia.rell.dokka.translators.RellSourceToDocumentableTranslator
import com.chromia.rell.dokka.translators.RellSystemLibToDocumentableTranslator
import org.bouncycastle.pqc.legacy.math.linearalgebra.IntegerFunctions.order
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.DokkaDefaults.delayTemplateSubstitution
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.renderers.html.AssetsInstaller
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.transformers.pages.PageTransformer
import org.jetbrains.kotlin.metadata.deserialization.Flags.FlagField.after

/**
 * This plugin takes rell files and produces documentation nodes for each doc comment in the style of kdocs.
 * It can also produce documentation for the system library.
 */
class RellDokkaPlugin : DokkaPlugin() {
    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement

    val sourceToDocumentableTranslator by extending {
        CoreExtensions.sourceToDocumentableTranslator providing {
            when (config(it)?.system) {
                true -> RellSystemLibToDocumentableTranslator
                else -> RellSourceToDocumentableTranslator(it)
            }
        }
    }

    val signatureProvider by extending {
        with(plugin<DokkaBase>()) {
            signatureProvider providing ::RellSignatureProvider override kotlinSignatureProvider
        }
    }

    val documentableToPageTranslator by extending {
        with(plugin<DokkaBase>()) {
            CoreExtensions.documentableToPageTranslator providing ::RellDocumentableToPageTranslator override documentableToPageTranslator
        }
    }

    val aliasProvider by extending {
        with(plugin<DokkaBase>()) {
            customTagContentProvider with AliasDocTagProvider
        }
    }

    val chromiaAssetsInstaller: Extension<PageTransformer, *, *> by extending {
        with (plugin<DokkaBase>()) {
            htmlPreprocessors providing ::ChromiaAssetsInstaller order {
                before(customResourceInstaller)
                after(rootCreator)
            } applyIf { !delayTemplateSubstitution }
        }
    }


    /*val renderer by extending {
         with (plugin<DokkaBase>()) {
             CoreExtensions.renderer providing ::RellHtmlRenderer override htmlRenderer
         }
     }*/

    companion object {
        private fun config(context: DokkaContext) = configuration<RellDokkaPlugin, RellDokkaPluginConfiguration>(context)
    }
}
