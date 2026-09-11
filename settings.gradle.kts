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
include(":applications:mvp-secure-runtime")
include(":applications:credential-rotation-execution")
include(":applications:marketplace-provider-authentication")
include(":applications:marketplace-economic-provider-ingestion")
include(":applications:marketplace-order-source-promotion")
include(":applications:marketplace-live-pipeline")
include(":applications:inventory-source-ingestion")
include(":applications:inventory-identity-mapping")
include(":applications:inventory-canonical-observation")
include(":applications:inventory-source-acceptance")
include(":applications:inventory-measure-selection")
include(":applications:inventory-candidate-snapshot")
include(":applications:inventory-candidate-comparison")
include(":applications:inventory-candidate-adjudication")
include(":applications:inventory-source-authority")
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

project(":applications:mvp-secure-runtime").projectDir =
    file("applications/mvp-secure-runtime")

project(":applications:credential-rotation-execution").projectDir =
    file("applications/credential-rotation-execution")

project(":applications:marketplace-provider-authentication").projectDir =
    file("applications/marketplace-provider-authentication")

project(":applications:marketplace-economic-provider-ingestion").projectDir =
    file("applications/marketplace-economic-provider-ingestion")

project(":applications:marketplace-order-source-promotion").projectDir =
    file("applications/marketplace-order-source-promotion")

project(":applications:marketplace-live-pipeline").projectDir =
    file("applications/marketplace-live-pipeline")

project(":applications:inventory-source-ingestion").projectDir =
    file("applications/inventory-source-ingestion")

project(":applications:inventory-identity-mapping").projectDir =
    file("applications/inventory-identity-mapping")

project(":applications:inventory-canonical-observation").projectDir =
    file("applications/inventory-canonical-observation")

project(":applications:inventory-source-acceptance").projectDir =
    file("applications/inventory-source-acceptance")

project(":applications:inventory-measure-selection").projectDir =
    file("applications/inventory-measure-selection")

project(":applications:inventory-candidate-snapshot").projectDir =
    file("applications/inventory-candidate-snapshot")

project(":applications:inventory-candidate-comparison").projectDir =
    file("applications/inventory-candidate-comparison")

project(":applications:inventory-candidate-adjudication").projectDir =
    file("applications/inventory-candidate-adjudication")

project(":applications:inventory-source-authority").projectDir =
    file("applications/inventory-source-authority")

project(":research:experiments:exp-0003-harness").projectDir =
    file("research/experiments/exp-0003-harness")
