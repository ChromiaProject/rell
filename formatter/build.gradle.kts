plugins {
    id("java")
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
    application
}

dependencies {
    implementation(libs.antlr)
    implementation(libs.ec4j)
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.testing)
    implementation(project(":core"))
}

tasks.processTestResources {
    dependsOn(tasks.compileTestKotlin)
}

application {
    mainClass.set("net.postchain.rell.toolbox.formatter.MainKt")
}
