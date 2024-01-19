import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    id("jacoco")
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
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
    constraints {
        // Define dependency versions as constraints
        //implementation(catalog.findLibrary("commons-text").get())

        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    //implementation(catalog.findLibrary("rell").get())

    //testImplementation(catalog.findLibrary("assertk").get())

    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")


}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<Test> {
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

/*testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter("5.8.1")
        }
    }
}*/
