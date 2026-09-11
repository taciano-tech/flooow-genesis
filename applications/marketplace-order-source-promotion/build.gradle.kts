plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:marketplace-operations"))
    implementation(project(":applications:integration-control-plane"))

    testImplementation(kotlin("test"))
}

val allowedProductionProjects = setOf(
    "organization-context",
    "marketplace-operations",
    "integration-control-plane"
)

val forbiddenProductionDependencies = configurations
    .getByName("implementation")
    .dependencies
    .filter {
        it.group == "io.flooow" && it.name !in allowedProductionProjects
    }

check(forbiddenProductionDependencies.isEmpty()) {
    "marketplace-order-source-promotion must remain provider-neutral and persistence-free"
}