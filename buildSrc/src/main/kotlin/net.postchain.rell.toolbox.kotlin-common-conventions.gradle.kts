
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.apache.tools.ant.DirectoryScanner

DirectoryScanner.removeDefaultExclude("**/.gitignore")

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    id("jacoco")
    id("org.jetbrains.dokka")
    id("io.gitlab.arturbosch.detekt")
    id("se.bjurr.violations.violations-gradle-plugin")
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
    maven {
        name = "bintray"
        url = uri("https://jcenter.bintray.com")
    }
    maven {
        name = "etherjar"
        url = uri("https://maven.emrld.io")
    }
    maven {
        name = "Rell GitLab Registry"
        url = uri("https://gitlab.com/api/v4/projects/32802097/packages/maven")
    }
    maven {
        name = "Postchain GitLab Registry"
        url = uri("https://gitlab.com/api/v4/projects/32294340/packages/maven")
    }
    maven {
        name = "Chromia parent GitLab Registry"
        url = uri("https://gitlab.com/api/v4/projects/50818999/packages/maven")
    }
    maven {
        name = "Chromia Build Tools Registry"
        url = uri("https://gitlab.com/api/v4/projects/39844192/packages/maven")
    }
    maven {
        name = "Detekt Gitlab Plugin Registry"
        url = uri("https://gitlab.com/api/v4/projects/25796063/packages/maven")
    }
}

val catalog: VersionCatalog = versionCatalogs.named("libs")
publishing {
    repositories {
        maven {
            name = ("GitLab")
            url = uri("https://gitlab.com/api/v4/projects/51303085/packages/maven")
            credentials(HttpHeaderCredentials::class.java) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(catalog.findLibrary("oshai").get())
    implementation(catalog.findLibrary("slf4j").get())

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
    detektPlugins("com.gitlab.cromefire:detekt-gitlab-report:0.3.3")
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

tasks {
    withType<Test> {
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

tasks.withType<JacocoReport> {
    dependsOn(tasks.test)
    afterEvaluate {
        classDirectories.setFrom(
            files(
                classDirectories.files.map {
                    fileTree(it).apply {
                        exclude("**/core/RellVersionInfo.*")
                        exclude("**/core/RellAbout.*")
                        exclude("net/postchain/rell/toolbox/parser/**")
                        exclude("net/postchain/rell/lsp/grammar/**")
                    }
                }
            )
        )
    }
}
tasks.test {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("../detekt.yml"))
    basePath = rootDir.toString()
    source.setFrom(
        files(
            rootDir.resolve("common/src"),
            rootDir.resolve("language-server/src"),
            rootDir.resolve("indexer/src"),
            rootDir.resolve("ast/src"),
            rootDir.resolve("code-quality/src"),
        )
    )
}

tasks {
    withType<Detekt> {
        reports {
            custom {
                reportId = "DetektGitlabReport"
                // This tells detekt, where it should write the report to,
                // you have to specify this file in the gitlab pipeline config.
                outputLocation.set(file("${rootDir.path}/build/reports/detekt/code-climate.json"))
            }
        }
    }
}
