rootProject.name = "flooow-genesis"

pluginManagement {
    includeBuild("build-logic")
}

include(":platform:foundation:kernel")
include(":platform:foundation:organization-context")
include(":applications:marketplace-operations")
include(":applications:marketplace-operations-api")
include(":applications:marketplace-operations-persistence-postgres")
include(":applications:integration-control-plane")
include(":applications:connector-runtime")
include(":applications:inventory-source-ingestion")
include(":research:experiments:exp-0003-harness")

project(":platform:foundation:kernel").projectDir =
    file("platform/foundation/kernel")

project(":platform:foundation:organization-context").projectDir =
    file("platform/foundation/organization-context")

project(":applications:marketplace-operations").projectDir =
    file("applications/marketplace-operations")

project(":applications:marketplace-operations-api").projectDir =
    file("applications/marketplace-operations-api")

project(":applications:marketplace-operations-persistence-postgres").projectDir =
    file("applications/marketplace-operations-persistence-postgres")

project(":applications:integration-control-plane").projectDir =
    file("applications/integration-control-plane")

project(":applications:connector-runtime").projectDir =
    file("applications/connector-runtime")

project(":applications:inventory-source-ingestion").projectDir =
    file("applications/inventory-source-ingestion")

project(":research:experiments:exp-0003-harness").projectDir =
    file("research/experiments/exp-0003-harness")
