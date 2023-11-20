package net.postchain.rell.toolbox.core

import net.postchain.rell.base.runtime.Rt_RellVersionProperty
import net.postchain.rell.base.utils.ide.IdeApi.getRellVersionInfo


data class RellAbout(val rellVersion: String, val aboutText: String)

object RellVersionInfo {

    fun getAbout() = RellAbout(getRellVersion(), getAboutText())

    private fun getAboutText(): String {
        val versionInfo = getRellVersionInfo() ?: throw IllegalStateException("Rell version info is not available")
        return """
            Rell ${versionInfo[Rt_RellVersionProperty.RELL_VERSION]}
            Postchain ${versionInfo[Rt_RellVersionProperty.POSTCHAIN_VERSION]}
            
            branch: ${versionInfo[Rt_RellVersionProperty.RELL_BRANCH]}
            commit: ${versionInfo[Rt_RellVersionProperty.RELL_COMMIT_ID]} (${versionInfo[Rt_RellVersionProperty.RELL_COMMIT_TIME]})
        """.trimIndent()
    }

    private fun getRellVersion(): String {
        val versionInfo = getRellVersionInfo()
        return versionInfo?.get(Rt_RellVersionProperty.RELL_VERSION)
            ?: throw IllegalStateException("Rell version info is not available")
    }
}