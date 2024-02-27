package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellConfig
import com.chromia.rell.dokka.renderers.html.RellHtmlRenderer
import com.chromia.rell.dokka.signature.RellSignatureProvider
import com.chromia.rell.dokka.translator.RellDocumentableToPageTranslator
import com.chromia.rell.dokka.translator.RellSourceToDocumentableTranslator
import com.chromia.rell.dokka.translator.RellSystemLibToDocumentableTranslator
import kotlinx.serialization.json.Json
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement

class RellDokkaPlugin : DokkaPlugin() {
    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement

    val sourceToDocumentableTranslator by extending {
        CoreExtensions.sourceToDocumentableTranslator providing {
            if (extractConfig(it)?.system == true) RellSystemLibToDocumentableTranslator  else RellSourceToDocumentableTranslator
        }
    }

    val pageCreator by extending {
        with(plugin<DokkaBase>()) {
            CoreExtensions.documentableToPageTranslator providing ::RellDocumentableToPageTranslator override documentableToPageTranslator
        }
    }

    val signatureProvider by extending {
        with (plugin<DokkaBase>()) {
            signatureProvider providing ::RellSignatureProvider override kotlinSignatureProvider
        }
    }

   val renderer by extending {
        with (plugin<DokkaBase>()) {
            CoreExtensions.renderer providing ::RellHtmlRenderer override htmlRenderer
        }
    }

    companion object {
        fun extractConfig(context: DokkaContext): RellConfig? {
            val pluginConfig = context.configuration.pluginsConfiguration.find { it.fqPluginName == RellDokkaPlugin::class.qualifiedName }
            return pluginConfig?.let {
                Json.decodeFromString<RellConfig>(it.values)
            }
        }
    }
}
