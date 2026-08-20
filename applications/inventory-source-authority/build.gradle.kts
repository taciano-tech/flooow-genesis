plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:inventory-identity-mapping"))
    implementation(project(":applications:inventory-measure-selection"))
    testImplementation(project(":applications:inventory-canonical-observation"))
    testImplementation(project(":applications:inventory-source-acceptance"))
    testImplementation(kotlin("test"))
}

val acceptedProductionProjects = setOf(
    "organization-context",
    "integration-control-plane",
    "inventory-identity-mapping",
    "inventory-measure-selection"
)
val acceptedTestProjects = setOf(
    "inventory-canonical-observation",
    "inventory-source-acceptance"
)

check(
    configurations.named("implementation").get().dependencies
        .filter { it.group == "io.flooow" }
        .map { it.name }
        .toSet() == acceptedProductionProjects
) {
    "inventory-source-authority production dependencies differ from the accepted contract"
}
check(
    configurations.named("testImplementation").get().dependencies
        .filter { it.group == "io.flooow" }
        .map { it.name }
        .toSet() == acceptedTestProjects
) {
    "inventory-source-authority test dependencies differ from the accepted contract"
}
check(configurations.flatMap { it.dependencies }.none {
    it.group == "io.flooow" && it.name !in acceptedProductionProjects + acceptedTestProjects
}) {
    "inventory-source-authority has an unauthorized project dependency"
}
