plugins {
    alias(libs.plugins.kotlin.jvm)
    antlr
}

description = "Rell frontend: compiler, model, type system, library descriptors"

sourceSets.main {
    java.srcDir(tasks.generateGrammarSource)
    // The ANTLR Gradle plugin mirrors the grammar's source-folder name (which contains dots) into
    // the generated path, which Gradle's standard Java source crawler skips. Add the output as a
    // Kotlin source dir so kotlinc parses the generated `.java` files for type resolution.
    kotlin.srcDir(tasks.generateGrammarSource)
}

dependencies {
    antlr(libs.antlr)
    api(libs.antlr.runtime)
    api(projects.rellBase.utils)
    api(projects.rellBase.rrTree)
    api(libs.postchain.gtv)
    api(libs.better.parse)
    api(libs.jooq)
    implementation(libs.jackson.databind)
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-long-messages")
    packageName = "net.postchain.rell.base.compiler.parser.antlr"
    outputDirectory = layout.buildDirectory.dir("generated/antlr").get().asFile
}

// Workaround excluding antlr "non-runtime" dependencies from jar.
// https://github.com/gradle/gradle/issues/820#issuecomment-288838412
configurations {
    api {
        setExtendsFrom(extendsFrom.filterNot { it == antlr.get() })
    }
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
