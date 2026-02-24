/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dependency.check)
}

group = "net.postchain.rell"
version = "0.16.0-SNAPSHOT"

// Opt-in flag to generate Rell test cases (replacement for Maven profile generate-test-cases)
val generateTestCases by extra(providers.gradleProperty("generateTestCases").isPresent)

apiValidation {
    ignoredProjects += listOf("rell-base", "rell-gtx", "rell-tools")
}

dependencyCheck {
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 0f
    suppressionFiles = listOf(
        "https://gitlab.com/chromaway/chromia-parent/-/raw/dev/common-dependencies-suppression.xml?ref_type=heads",
        "dependencies-suppression.xml"
    )
    analyzers.assemblyEnabled = false
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "jacoco")
        apply(plugin = "maven-publish")

        dependencies {
            "implementation"(platform(rootProject.libs.postchain.bom))
            "testImplementation"(platform(rootProject.libs.junit.bom))
            "testImplementation"(platform(rootProject.libs.testcontainers.bom))
        }

        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(21)
            withSourcesJar()
        }

        tasks.withType<JavaCompile> {
            options.release = 21
            options.encoding = "UTF-8"
        }

        tasks.withType<KotlinCompile> {
            compilerOptions.jvmTarget = JvmTarget.JVM_21
        }

        tasks.withType<Test> {
            useJUnitPlatform()
            // Include integration-test style classes as Maven Failsafe did
            include("**/*Test.*", "**/*Tests.*", "**/*TestCase.*", "**/*IT.*")
            systemProperty("java.awt.headless", "true")

            // Memory settings for test JVM - matches Maven Surefire configuration
            jvmArgs("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError")

            // JUnit parallel test execution
            systemProperty("junit.jupiter.execution.parallel.enabled", "true")
            systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")

            // maxParallelForks = 1: Only one test worker JVM within this task.
            // forkEvery = 0: Reuse the same JVM for all tests (no per-class forking).
            //
            // Tests within a single worker can run in parallel via JUnit's parallel
            // execution mode configured above. Cross-process database isolation is
            // handled by SqlSchemaUtils (PID-scoped schemas + advisory locks).
            maxParallelForks = 1
            forkEvery = 0

            // Forward locale overrides to test JVM when passed as Gradle project properties,
            // e.g. ./gradlew check -Puser.language=tr -Puser.country=TR
            providers.gradleProperty("user.language").orNull?.let { systemProperty("user.language", it) }
            providers.gradleProperty("user.country").orNull?.let { systemProperty("user.country", it) }

            if (generateTestCases) {
                systemProperty("test.snippets.recorder.enabled", "true")
                systemProperty(
                    "test.snippets.recorder.target",
                    layout.buildDirectory.dir("rell-test-cases").get().asFile.absolutePath
                )
                systemProperty("test.snippets.recorder.zipfile", "false")
            }

            testLogging {
                events("passed", "skipped", "failed")
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }

            finalizedBy(tasks.named("jacocoTestReport"))
        }

        tasks.withType<JacocoReport> {
            dependsOn(tasks.withType<Test>())
            reports {
                xml.required = true
                html.required = true
            }
        }

        tasks.withType<Jar>().configureEach {
            manifest {
                attributes(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                    "Specification-Title" to project.name,
                    "Specification-Version" to project.version
                )
            }
        }

        extensions.configure<PublishingExtension> {
            repositories {
                mavenLocal()

                val gitlabToken = providers.environmentVariable("CI_JOB_TOKEN")
                    .orElse(providers.environmentVariable("GITLAB_CI_TOKEN"))
                    .orElse(providers.environmentVariable("GITLAB_TOKEN"))
                    .orNull

                if (gitlabToken != null) {
                    maven {
                        name = "gitlab"
                        url = uri("https://gitlab.com/api/v4/projects/32802097/packages/maven")

                        credentials(HttpHeaderCredentials::class) {
                            name = "Job-Token"
                            value = gitlabToken
                        }

                        authentication {
                            create<HttpHeaderAuthentication>("header")
                        }
                    }
                }
            }

            publications.create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }
    }
}

