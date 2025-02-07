plugins {
    id("java")
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
}

dependencies {
    implementation(libs.antlr.runtime)
    implementation(libs.ec4j)
    implementation(libs.system.stubs.jupiter)
    implementation(libs.bundles.rell)

    implementation(libs.chromia.build.tools) {
        exclude(group = "com.chromia.rell.dokka")
        exclude(group = "net.postchain", module = "postchain-base")
        exclude(group = "net.postchain", module = "postchain-admin-service")
        exclude(group = "net.postchain", module = "postchain-gtv")
        exclude(group = "net.postchain", module = "chromia-client")
        exclude(group = "net.postchain.client", module = "postchain-client")
        exclude(group = "net.postchain.rell", module = "codegen")
        exclude(group = "org.eclipse.jgit")
    }

    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}
