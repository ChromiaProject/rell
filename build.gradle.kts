version = "0.2.0-SNAPSHOT"
group = "net.postchain.rell"


val mavenPlugin = tasks.register<Exec>("buildMavenPlugin") {
    description = "Builds the maven plugin"
    group = "build"
    commandLine("mvn", "clean", "test", "package")
    workingDir("$projectDir/plugin/rellgen-maven-plugin")
}

val mavenPluginInstall = tasks.register<Exec>("publishMavenPluginToMavenLocal") {
    description = "Installs the maven plugin to local repository"
    group = "publishing"
    dependsOn("buildMavenPlugin")
    commandLine("mvn", "install")
    workingDir("$projectDir/plugin/rellgen-maven-plugin")
}

val mavenPluginDeploy by tasks.register<Exec>("publishMavenPlugin") {
    description = "Deploys maven plugin"
    group = "publishing"
    dependsOn("publishMavenPluginToMavenLocal")
    commandLine("mvn", "deploy:deploy")
    workingDir("$projectDir/plugin/rellgen-maven-plugin")
}
