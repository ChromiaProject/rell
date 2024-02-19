plugins {
    kotlin("jvm") version "1.9.10"
    id("org.jetbrains.dokka") version "1.9.10" // Used to create a javadoc jar
    `maven-publish`
    signing
    application
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

val dokkaVersion: String by project
dependencies {
    compileOnly("org.jetbrains.dokka:dokka-core:$dokkaVersion")
    implementation("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    implementation("org.jetbrains.dokka:dokka-cli:$dokkaVersion")
    implementation("org.jetbrains.dokka:analysis-kotlin-descriptors:$dokkaVersion")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    //runtimeOnly("org.freemarker:freemaker:2.3.31")
    implementation("net.postchain.rell:rell-api-base:0.13.6")

    implementation("com.github.ajalt.clikt:clikt:3.5.2")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.dokka:dokka-test-api:$dokkaVersion")
    testImplementation("org.jetbrains.dokka:dokka-base-test-utils:$dokkaVersion")
}

kotlin {
    jvmToolchain(17)
}

tasks.dokkaHtml {
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

publishing {
    publications {
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

                /*scm {
                    connection.set("scm:git:git://github.com/Kotlin/dokka-plugin-template.git")
                    url.set("https://github.com/Kotlin/dokka-plugin-template/tree/master")
                }*/
            }
        }
        signPublicationsIfKeyPresent(dokkaTemplatePlugin)
    }

    /*repositories {
        maven("https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
            credentials {
                username = System.getenv("SONATYPE_USER")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    }*/
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
