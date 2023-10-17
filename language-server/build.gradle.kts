plugins {
    id("java")
    kotlin("jvm") version "1.9.10"
    application
}

group = "net.postchain.rell.toolbox"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}