plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.antlr.runtime)
    implementation(libs.ec4j)
    implementation(libs.system.stubs.jupiter)
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellBase)

    implementation(libs.chromia.build.tools) {
        exclude(group = "com.chromia.rell.dokka")
        exclude(group = "net.postchain", module = "postchain-base")
        exclude(group = "net.postchain", module = "postchain-admin-service")
        exclude(group = "net.postchain", module = "postchain-gtv")
        exclude(group = "net.postchain.client", module = "chromia-client")
        exclude(group = "net.postchain.client", module = "postchain-client")
        exclude(group = "net.postchain.rell", module = "codegen")
        exclude(group = "org.eclipse.jgit")
    }

    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}
