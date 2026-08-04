rootProject.name = "flooow-genesis"

pluginManagement {
    includeBuild("build-logic")
}

include(":platform:foundation:kernel")
include(":applications:marketplace-operations")
include(":research:experiments:exp-0003-harness")

project(":platform:foundation:kernel").projectDir =
    file("platform/foundation/kernel")

project(":applications:marketplace-operations").projectDir =
    file("applications/marketplace-operations")

project(":research:experiments:exp-0003-harness").projectDir =
    file("research/experiments/exp-0003-harness")
