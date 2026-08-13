plugins {
    id("flooow.kotlin-conventions")
    `java-library`
}

dependencies {
    api(project(":platform:foundation:organization-context"))
    api(project(":applications:integration-control-plane"))
    api(project(":applications:inventory-identity-mapping"))
    api(project(":applications:inventory-canonical-observation"))
    api(project(":applications:inventory-source-acceptance"))
    api(project(":applications:inventory-measure-selection"))
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
