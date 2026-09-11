rootProject.name = "exp-0007-harness"

pluginManagement {
    includeBuild("../../../build-logic")
}

include(":platform:foundation:kernel")
include(":platform:foundation:organization-context")
include(":applications:marketplace-operations")
include(":applications:marketplace-operations-persistence-postgres")
include(":applications:integration-control-plane")
include(":applications:connector-runtime")
include(":applications:inventory-source-ingestion")
include(":applications:inventory-identity-mapping")
include(":applications:inventory-canonical-observation")
include(":applications:inventory-source-acceptance")
include(":applications:inventory-measure-selection")
include(":applications:inventory-candidate-snapshot")
include(":applications:inventory-candidate-comparison")
include(":applications:inventory-candidate-adjudication")

project(":platform").projectDir = file("../../../platform")
project(":platform:foundation").projectDir = file("../../../platform/foundation")
project(":applications").projectDir = file("../../../applications")

project(":platform:foundation:kernel").projectDir =
    file("../../../platform/foundation/kernel")

project(":platform:foundation:organization-context").projectDir =
    file("../../../platform/foundation/organization-context")

project(":applications:marketplace-operations").projectDir =
    file("../../../applications/marketplace-operations")

project(":applications:marketplace-operations-persistence-postgres").projectDir =
    file("../../../applications/marketplace-operations-persistence-postgres")

project(":applications:integration-control-plane").projectDir =
    file("../../../applications/integration-control-plane")

project(":applications:connector-runtime").projectDir =
    file("../../../applications/connector-runtime")

project(":applications:inventory-source-ingestion").projectDir =
    file("../../../applications/inventory-source-ingestion")

project(":applications:inventory-identity-mapping").projectDir =
    file("../../../applications/inventory-identity-mapping")

project(":applications:inventory-canonical-observation").projectDir =
    file("../../../applications/inventory-canonical-observation")

project(":applications:inventory-source-acceptance").projectDir =
    file("../../../applications/inventory-source-acceptance")

project(":applications:inventory-measure-selection").projectDir =
    file("../../../applications/inventory-measure-selection")

project(":applications:inventory-candidate-snapshot").projectDir =
    file("../../../applications/inventory-candidate-snapshot")

project(":applications:inventory-candidate-comparison").projectDir =
    file("../../../applications/inventory-candidate-comparison")

project(":applications:inventory-candidate-adjudication").projectDir =
    file("../../../applications/inventory-candidate-adjudication")
