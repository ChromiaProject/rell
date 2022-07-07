
plugins {
    id("net.postchain.rell.codegen.kotlin-library-conventions")
    `java-gradle-plugin`
}

dependencies {
    compileOnly(gradleApi())
    implementation(project(":rellgen")) { isTransitive = false }
    implementation(project(":codegen"))
    implementation(project(":codegen-kotlin"))
}

// Integration test section
val integrationTest by sourceSets.creating

dependencies {
    "integrationTestImplementation"(project)
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter-api:5.8.1")
    "integrationTestImplementation"("com.willowtreeapps.assertk:assertk:0.10")
}
val integrationTestTask = tasks.register<Test>("integrationTest") {
    useJUnitPlatform()
    description = "Runs the integration tests."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    mustRunAfter(tasks.test)
}
tasks.check {
    dependsOn(integrationTestTask)
}

// Plugin publication section
gradlePlugin {
    testSourceSets(integrationTest)
    plugins {
        create("rellGenPlugin") {
            id = "net.postchain.rell.rellgen"
            implementationClass = "net.postchain.rell.plugin.RellGenPlugin"
        }
    }
}
