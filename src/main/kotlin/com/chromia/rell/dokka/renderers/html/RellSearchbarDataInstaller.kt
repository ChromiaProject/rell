package com.chromia.rell.dokka.renderers.html

import com.chromia.rell.dokka.dri.escapeAnonymousFunctionName
import org.jetbrains.dokka.base.renderers.html.SearchRecord
import org.jetbrains.dokka.base.renderers.html.SearchbarDataInstaller
import org.jetbrains.dokka.plugability.DokkaContext

class RellSearchbarDataInstaller(dokkaContext: DokkaContext): SearchbarDataInstaller(dokkaContext) {
    override fun createSearchRecord(
        name: String,
        description: String?,
        location: String,
        searchKeys: List<String>
    ): SearchRecord =
        SearchRecord(name, description, location.escapeAnonymousFunctionName(), searchKeys)
}
