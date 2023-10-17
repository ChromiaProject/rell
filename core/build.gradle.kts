plugins {
    id("java")
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

sourceSets.getByName("main") {
    java.srcDir("src/main/gen")
    java.srcDir("src/main/java")
    java.srcDir("src/main/kotlin")
}