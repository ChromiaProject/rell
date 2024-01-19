group = "net.postchain.rell.toolbox"

plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("rell-language-server") {
            artifactId = "rell-language-server"
            setArtifacts(listOf("./language-server/build/libs/language-server-dev-all.jar"))
        }
    }

    repositories {
        maven {
            name = ("GitLab")
            url = uri("https://gitlab.com/api/v4/projects/51303085/packages/maven")
            credentials(HttpHeaderCredentials::class.java) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}
