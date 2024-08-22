import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.dokka") version "1.9.20" // Used to create a javadoc jar
    kotlin("plugin.serialization") version "1.9.22"
    `maven-publish`
    signing
    application
    id("jacoco")
    id("jacoco-report-aggregation") // Nothing to aggregate but added for consistency
}

group = "com.chromia.rell.dokka"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://gitlab.com/api/v4/projects/32294340/packages/maven")
    maven("https://gitlab.com/api/v4/projects/32802097/packages/maven")
    maven("https://gitlab.com/api/v4/projects/46288950/packages/maven")
    maven("https://gitlab.com/api/v4/projects/50818999/packages/maven")
    maven("https://jcenter.bintray.com")
    maven("https://maven.emrld.io")
}

val dependenciesToUnshade: Configuration by configurations.creating
val unshadedClassesTarget = layout.buildDirectory.dir("unshaded")

val dokkaVersion: String by project
val rellVersion: String by project
val jacksonVersion = "2.15.3"
dependencies {
    implementation(platform("net.postchain.rell:rell:$rellVersion"))
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("org.jetbrains.dokka:dokka-core:$dokkaVersion")
    implementation("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    implementation("org.jetbrains.dokka:analysis-markdown:$dokkaVersion")
    implementation("org.jetbrains.dokka:analysis-kotlin-api:$dokkaVersion")


    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    implementation("net.postchain.rell:rell-api-base:$rellVersion")
    implementation("net.postchain.rell:rell-base:$rellVersion")

    implementation("com.github.ajalt.clikt:clikt:3.5.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.10")
    testImplementation("com.willowtreeapps.assertk:assertk:0.28.0")
    testImplementation("org.jetbrains.dokka:dokka-test-api:$dokkaVersion")
    testImplementation("org.jetbrains.dokka:dokka-base-test-utils:$dokkaVersion")
    testImplementation("org.jsoup:jsoup:1.17.2")
    testImplementation("org.slf4j:slf4j-simple:1.7.36")

    // Unshading dependencies
    dependenciesToUnshade("org.jetbrains.dokka:analysis-kotlin-descriptors:$dokkaVersion")
    implementation(files(unshadedClassesTarget))
}

configurations.all {
    resolutionStrategy {
        force("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
        force("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    }
}

kotlin {
    jvmToolchain(21)
}

val copyShadedDependencyClasses by tasks.registering(Copy::class) {
    from(zipTree(dependenciesToUnshade.singleFile))
    include(
            "**/DocumentableSourceLanguageParser.class",
            "**/DocumentableLanguage.class",
            "**/DescriptorDocumentableSource.class",
            "**/DeclarationDescriptor.class",
    )
    into(unshadedClassesTarget)
}

tasks.compileKotlin {
    dependsOn(copyShadedDependencyClasses)
}

tasks.dokkaHtml {
    dependsOn(copyShadedDependencyClasses)
    moduleName.set("Rell Dokka Plugin")
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}

application {
    mainClass = "com.chromia.rell.dokka.cli.MainKt"
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaHtml)
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
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
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it).apply {
                exclude("**/cli/*")
            }
        }))
    }
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport"))
}

publishing {
    publications {
        repositories {
            maven {
                name = ("GitLab")
                url = uri("https://gitlab.com/api/v4/projects/55009888/packages/maven")
                credentials(HttpHeaderCredentials::class.java) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
        }
        val dokkaTemplatePlugin by creating(MavenPublication::class) {
            artifactId = project.name
            from(components["java"])
            artifact(javadocJar)

            pom {
                name.set("Rell dokka plugin")
                description.set("Generates documentation for a rell project")
                url.set("https://docs.chromia.com/")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
            }
        }
        signPublicationsIfKeyPresent(dokkaTemplatePlugin)
    }
}

fun Project.signPublicationsIfKeyPresent(publication: MavenPublication) {
    val signingKeyId: String? = System.getenv("SIGN_KEY_ID")
    val signingKey: String? = System.getenv("SIGN_KEY")
    val signingKeyPassphrase: String? = System.getenv("SIGN_KEY_PASSPHRASE")

    if (!signingKey.isNullOrBlank()) {
        extensions.configure<SigningExtension>("signing") {
            if (signingKeyId?.isNotBlank() == true) {
                useInMemoryPgpKeys(signingKeyId, signingKey, signingKeyPassphrase)
            } else {
                useInMemoryPgpKeys(signingKey, signingKeyPassphrase)
            }
            sign(publication)
        }
    }
}
