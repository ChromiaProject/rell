plugins {
    id("net.postchain.rell.codegen.kotlin-library-conventions")
}

dependencies {
    implementation(libs.rell)
}

val testConfiguration by configurations.creating

val testjar by tasks.register<Jar>("testJar") {
    from(sourceSets.test.get().output)
    archiveClassifier.set("test")
}

artifacts {
    add(testConfiguration.name, testjar)
}