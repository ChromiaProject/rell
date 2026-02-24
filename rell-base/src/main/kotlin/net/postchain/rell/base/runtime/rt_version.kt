/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import com.google.common.io.Resources
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.toImmMap
import java.io.StringReader
import java.util.*

enum class Rt_RellVersionProperty(val key: String) {
    RELL_BRANCH("rell.branch"),
    RELL_VERSION("rell.version"),
    RELL_COMMIT_ID("rell.commit.id"),
    RELL_COMMIT_ID_FULL("rell.commit.id.full"),
    RELL_COMMIT_MESSAGE("rell.commit.message.short"),
    RELL_COMMIT_MESSAGE_FULL("rell.commit.message.full"),
    RELL_COMMIT_TIME("rell.commit.time"),
    RELL_DIRTY("rell.dirty"),
    POSTCHAIN_VERSION("postchain.version"),
    KOTLIN_VERSION("kotlin.version"),
}

class Rt_RellVersion private constructor(
    val properties: ImmMap<Rt_RellVersionProperty, String>,
    val rtProperties: ImmMap<Rt_Value, Rt_Value>,
    val buildDescriptor: String
) {
    companion object {
        private val PROPS = immMapOf(
                "git.branch" to Rt_RellVersionProperty.RELL_BRANCH,
                "git.build.version" to Rt_RellVersionProperty.RELL_VERSION,
                "git.commit.id.abbrev" to Rt_RellVersionProperty.RELL_COMMIT_ID,
                "git.commit.id" to Rt_RellVersionProperty.RELL_COMMIT_ID_FULL,
                "git.commit.message.short" to Rt_RellVersionProperty.RELL_COMMIT_MESSAGE,
                "git.commit.message.full" to Rt_RellVersionProperty.RELL_COMMIT_MESSAGE_FULL,
                "git.commit.time" to Rt_RellVersionProperty.RELL_COMMIT_TIME,
                "git.dirty" to Rt_RellVersionProperty.RELL_DIRTY,
                "postchain.version" to Rt_RellVersionProperty.POSTCHAIN_VERSION,
                "kotlin.version" to Rt_RellVersionProperty.KOTLIN_VERSION
        )

        fun getInstance(): Rt_RellVersion? {
            val raw = getBuildProperties()
            if (raw == null) return null

            val properties = getRtProperties(raw)

            val rtProperties = properties
                .map { Rt_TextValue.get(it.key.key) to Rt_TextValue.get(it.value) }
                .toImmMap()

            val buildDescriptor = getBuildDescriptor(properties)
            return Rt_RellVersion(properties, rtProperties, buildDescriptor)
        }

        private fun getRtProperties(raw: Map<String, String>): ImmMap<Rt_RellVersionProperty, String> {
            val ps = mutableMapOf<Rt_RellVersionProperty, String>()

            for ((rawKey, prop) in PROPS) {
                val value = raw.getValue(rawKey)
                ps[prop] = value
            }

            val codeVer = RellVersions.VERSION_STR
            val fullBuildVer = ps.getValue(Rt_RellVersionProperty.RELL_VERSION)
            val buildVer = parseBuildVersion(fullBuildVer)
            check(buildVer == codeVer || fullBuildVer.endsWith("-SNAPSHOT")) {
                "Rell version in code = $codeVer, in build = $buildVer"
            }

            return ps.toImmMap()
        }

        private fun parseBuildVersion(s: String): String {
            // Remove "-SNAPSHOT", etc.
            return s.substringBefore("-")
        }

        private fun getBuildDescriptor(props: Map<Rt_RellVersionProperty, String>): String {
            val parts = mapOf(
                    "rell" to "${props[Rt_RellVersionProperty.RELL_VERSION]}",
                    "postchain" to "${props[Rt_RellVersionProperty.POSTCHAIN_VERSION]}",
                    "branch" to "${props[Rt_RellVersionProperty.RELL_BRANCH]}",
                    "commit" to "${props[Rt_RellVersionProperty.RELL_COMMIT_ID]} (${props[Rt_RellVersionProperty.RELL_COMMIT_TIME]})",
                    "dirty" to "${props[Rt_RellVersionProperty.RELL_DIRTY]}"
            )
            return parts.entries.joinToString("; ") { "${it.key}: ${it.value}" }
        }

        private fun getBuildProperties(): Map<String, String>? {
            try {
                val url = Resources.getResource(Rt_Utils.javaClass, "/rell-base-maven.properties")
                val text = Resources.toString(url, Charsets.UTF_8)
                val props = Properties()
                props.load(StringReader(text))
                return props.stringPropertyNames().sorted().map { it to props.getProperty(it) }.toMap().toImmMap()
            } catch (_: Exception) {
                return null
            }
        }
    }
}
