rootProject.name = "exp-0008-harness"

pluginManagement {
    includeBuild("../../../build-logic")
}

include(":platform:foundation:kernel")
include(":platform:foundation:organization-context")
include(":applications:marketplace-operations")

project(":platform").projectDir = file("../../../platform")
project(":platform:foundation").projectDir = file("../../../platform/foundation")
project(":applications").projectDir = file("../../../applications")

project(":platform:foundation:kernel").projectDir =
    file("../../../platform/foundation/kernel")

project(":platform:foundation:organization-context").projectDir =
    file("../../../platform/foundation/organization-context")

project(":applications:marketplace-operations").projectDir =
    file("../../../applications/marketplace-operations")
