
rootProject.name = "rell-codegen"
include("rellgen")
findProject(":rellgen")?.name = "rellgen"
include("codegen")
include("codegen-kotlin")
include("plugin:rellgen-gradle-plugin")
findProject(":plugin:rellgen-gradle-plugin")?.name = "rellgen-gradle-plugin"
