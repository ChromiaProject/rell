plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.oshai)
    implementation(libs.slf4j)
    implementation(projects.rellToolbox.ast)
    implementation(projects.rellToolbox.common)
    implementation(projects.rellBase)

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

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.bundles.toolbox.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.log4j.slf4j2.impl)
}
