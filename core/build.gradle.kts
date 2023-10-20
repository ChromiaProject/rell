plugins {
    id("java")
    kotlin("jvm") version "1.9.10"
    antlr
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
}
val koinVersion = "3.5.0"

dependencies {
    antlr("org.antlr:antlr4:4.13.1")

    implementation("org.antlr:antlr4:4.13.1")
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation(libs.bundles.logging)

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.27.0")
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testcontainers)
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf(
        "-visitor",
        "-long-messages",
        "-package", "net.postchain.rell.toolbox.core.parser"
    )
    outputDirectory = file("src/main/gen")
}


sourceSets.getByName("main") {
    java.srcDir("src/main/gen")
    java.srcDir("src/main/java")
    kotlin.srcDir("src/main/kotlin")
}

sourceSets.getByName("test") {
    output.setResourcesDir(file("build/classes/kotlin/test"))
}