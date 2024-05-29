package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.doc.AliasDocTagProvider
import com.chromia.rell.dokka.moduledocs.RellModuleAndPackageDocumentationTransformer
import com.chromia.rell.dokka.navigation.RellNavigationPageInstaller
import com.chromia.rell.dokka.renderers.html.ChromiaAssetsInstaller
import com.chromia.rell.dokka.renderers.html.RellHtmlRenderer
import com.chromia.rell.dokka.renderers.html.RellSearchbarDataInstaller
import com.chromia.rell.dokka.signature.RellSignatureProvider
import com.chromia.rell.dokka.transformers.NullPageTransformer
import com.chromia.rell.dokka.translators.RellSourceToDocumentableTranslator
import com.chromia.rell.dokka.translators.RellSystemLibToDocumentableTranslator
import com.chromia.rell.dokka.translators.documentables.RellDocumentableToPageTranslator
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.transformers.pages.PageTransformer

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
                before(scriptsInstaller)
                after(rootCreator)
                after(stylesInstaller)
                after(assetsInstaller)
            } applyIf { !delayTemplateSubstitution }
        }
    }

    val rellSearchbarDataInstaller by extending {
        with (plugin<DokkaBase>()) {
            htmlPreprocessors providing ::RellSearchbarDataInstaller override baseSearchbarDataInstaller
        }
    }

    val renderer by extending {
         with (plugin<DokkaBase>()) {
             CoreExtensions.renderer providing ::RellHtmlRenderer override htmlRenderer
         }
     }

    // Copied implementations with minor tweaks to remove kotlin dependency
    val rellNavigationPageInstaller by extending {
        with (plugin<DokkaBase>()) {
            htmlPreprocessors providing ::RellNavigationPageInstaller override navigationPageInstaller
        }
    }

    val rellModuleAndPackageDocumentation by extending {
        with (plugin<DokkaBase>()) {
            preMergeDocumentableTransformer providing ::RellModuleAndPackageDocumentationTransformer override modulesAndPackagesDocumentation
        }
    }

    // Suppressed extensions that depends on kotlinAnalysis
    val nullPageTransformer by extending {
        with (plugin<DokkaBase>()) {
            CoreExtensions.pageTransformer with NullPageTransformer() override defaultSamplesTransformer
        }
    }

    companion object {
        private fun config(context: DokkaContext) = configuration<RellDokkaPlugin, RellDokkaPluginConfiguration>(context)
    }
}
