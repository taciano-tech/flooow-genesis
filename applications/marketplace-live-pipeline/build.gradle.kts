plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:connector-runtime"))
    implementation(project(":applications:marketplace-order-source-promotion"))
    implementation(project(":applications:marketplace-operations"))

    testImplementation(kotlin("test"))
}

val allowedProductionProjects = setOf(
    "organization-context",
    "integration-control-plane",
    "connector-runtime",
    "marketplace-order-source-promotion",
    "marketplace-operations"
)

val forbiddenProductionDependencies = configurations
    .getByName("implementation")
    .dependencies
    .filter {
        it.group == "io.flooow" && it.name !in allowedProductionProjects
    }

check(forbiddenProductionDependencies.isEmpty()) {
    "marketplace-live-pipeline must remain persistence-free and orchestration-only"
}