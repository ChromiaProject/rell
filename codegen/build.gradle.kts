plugins {
    id("net.postchain.rell.codegen.kotlin-library-conventions")
}

repositories {
    mavenCentral()
    maven("https://gitlab.com/api/v4/projects/32294340/packages/maven")
    maven("https://gitlab.com/api/v4/projects/32802097/packages/maven")
    maven("https://jcenter.bintray.com")
    maven("https://maven.emrld.io")
}

dependencies {
    implementation("net.postchain.rell:rell:0.10.10")
}

