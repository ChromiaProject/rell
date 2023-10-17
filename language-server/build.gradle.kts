plugins {
    kotlin("jvm") version "1.9.10"
    application
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
}
val lsp4jVersion = "0.21.1"
dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:$lsp4jVersion")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.websocket:$lsp4jVersion")
    testImplementation(libs.bundles.testcontainers)
    implementation(libs.bundles.logging)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}