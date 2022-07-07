
rootProject.name = "rell-codegen"
include("rellgen")
findProject(":rellgen")?.name = "rellgen"
include("codegen")
include("codegen-kotlin")
include(":plugin")
include("plugin:rellgen-gradle-plugin")
