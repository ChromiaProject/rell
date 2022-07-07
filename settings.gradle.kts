
rootProject.name = "rell-codegen"
include("rellgen")
settings.project(":rellgen").name = "rellgen"
include("codegen")
include("codegen-kotlin")
