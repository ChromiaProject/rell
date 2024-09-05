plugins {
    id("java")
    id("net.postchain.rell.toolbox.kotlin-common-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":ast"))
    implementation(project(":indexer"))
    implementation(libs.rell.base)
    implementation(libs.java.diff.utils)
    implementation(libs.ec4j)
    
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
}

publishing {
    publications {
        create<MavenPublication>("rell-code-quality") {
            artifactId = "rell-code-quality"
        }
    }
}