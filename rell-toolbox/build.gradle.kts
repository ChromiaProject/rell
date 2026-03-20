subprojects {
    group = "net.postchain.rell.toolbox"

    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.withType<Test> {
            systemProperty("junit.jupiter.execution.parallel.enabled", "false")
            jvmArgs("-XX:+EnableDynamicAgentLoading")
        }
    }
}
