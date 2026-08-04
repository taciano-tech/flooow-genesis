rootProject.name = "flooow-genesis"

pluginManagement {
    includeBuild("build-logic")
}

include(":platform:foundation:kernel")
include(":applications:marketplace-operations")

project(":platform:foundation:kernel").projectDir =
    file("platform/foundation/kernel")

project(":applications:marketplace-operations").projectDir =
    file("applications/marketplace-operations")
