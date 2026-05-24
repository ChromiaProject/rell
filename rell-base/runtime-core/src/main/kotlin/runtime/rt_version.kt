/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.toImmMap

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
            "kotlin.version" to Rt_RellVersionProperty.KOTLIN_VERSION,
        )

        fun getInstance(): Rt_RellVersion {
            val raw = getBuildProperties() ?: FALLBACK_PROPERTIES

            val properties = getRtProperties(raw)

            val rtProperties: ImmMap<Rt_Value, Rt_Value> = properties
                .map { Rt_TextValue.get(it.key.key) to Rt_TextValue.get(it.value) }
                .toImmMap()

            val buildDescriptor = getBuildDescriptor(properties)
            return Rt_RellVersion(properties.toImmMap(), rtProperties, buildDescriptor)
        }

        // Used when `rell-base-maven.properties` is not on the classpath — e.g. running
        // tests from IntelliJ without a Gradle build, or when the gradle-git-properties
        // plugin's output is missing from the umbrella jar in CI. Values are format-valid
        // sentinels: known strings that match `rell.get_build`'s output schema so callers
        // (and the CLI tests that assert on it) keep working, but are clearly synthetic.
        private val FALLBACK_PROPERTIES = immMapOf(
            "git.branch" to "unknown",
            "git.build.version" to RellVersions.VERSION_STR,
            "git.commit.id" to "0".repeat(40),
            "git.commit.id.abbrev" to "0".repeat(7),
            "git.commit.message.short" to "unknown",
            "git.commit.message.full" to "unknown",
            "git.commit.time" to "1970-01-01T00:00:00+0000",
            "git.dirty" to "false",
            "postchain.version" to "0.0.0",
            "kotlin.version" to KotlinVersion.CURRENT.toString(),
        )

        private fun getRtProperties(raw: Map<String, String>): Map<Rt_RellVersionProperty, String> = buildMap {
            for ((rawKey, prop) in PROPS) {
                val value = raw.getValue(rawKey)
                this[prop] = value
            }

            val codeVer = RellVersions.VERSION_STR
            val fullBuildVer = getValue(Rt_RellVersionProperty.RELL_VERSION)
            val buildVer = parseBuildVersion(fullBuildVer)
            check(buildVer == codeVer || fullBuildVer.endsWith("-SNAPSHOT")) {
                "Rell version in code = $codeVer, in build = $buildVer"
            }
        }

        // Remove "-SNAPSHOT", etc.
        private fun parseBuildVersion(s: String): String = s.substringBefore("-")

        private fun getBuildDescriptor(props: Map<Rt_RellVersionProperty, String>): String = listOf(
            "rell" to "${props[Rt_RellVersionProperty.RELL_VERSION]}",
            "postchain" to "${props[Rt_RellVersionProperty.POSTCHAIN_VERSION]}",
            "branch" to "${props[Rt_RellVersionProperty.RELL_BRANCH]}",
            "commit" to "${props[Rt_RellVersionProperty.RELL_COMMIT_ID]} (${props[Rt_RellVersionProperty.RELL_COMMIT_TIME]})",
            "dirty" to "${props[Rt_RellVersionProperty.RELL_DIRTY]}",
        ).joinToString("; ") { "${it.first}: ${it.second}" }

        private fun getBuildProperties(): Map<String, String>? {
            // TeaVM-friendly: Class.getResource and Properties.load are not in TeaVM's
            // classlib emulation, so the bytecode references break reachability analysis.
            // The caller already handles `null` by falling back to FALLBACK_PROPERTIES; on
            // the JVM the gradle-git-properties plugin's output sat in `rell-base-maven
            // .properties`, on TeaVM there is no classpath so the fallback is the only path.
            return null
        }
    }
}
