plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:inventory-identity-mapping"))
    implementation(project(":applications:inventory-canonical-observation"))
    implementation(project(":applications:inventory-source-acceptance"))
    implementation(project(":applications:inventory-measure-selection"))
    testImplementation(kotlin("test"))
}

val authorizedProjects = setOf(
    "organization-context",
    "integration-control-plane",
    "inventory-identity-mapping",
    "inventory-canonical-observation",
    "inventory-source-acceptance",
    "inventory-measure-selection"
)

check(configurations.flatMap { it.dependencies }.none {
    it.group == "io.flooow" && it.name !in authorizedProjects
}) {
    "inventory-candidate-snapshot has an unauthorized project dependency"
}
