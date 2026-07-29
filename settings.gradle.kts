rootProject.name = "flooow-genesis"

pluginManagement {
    includeBuild("build-logic")
}

include(":platform:foundation:kernel")

project(":platform:foundation:kernel").projectDir =
    file("platform/foundation/kernel")
include(":applications:experiments:marketplace")

project(":applications:experiments:marketplace").projectDir =
    file("applications/experiments/marketplace")
