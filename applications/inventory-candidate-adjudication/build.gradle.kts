plugins {
    id("flooow.kotlin-conventions")
    `java-library`
}

dependencies {
    api(project(":platform:foundation:organization-context"))
    api(project(":applications:inventory-candidate-snapshot"))
    api(project(":applications:inventory-candidate-comparison"))
    testImplementation(kotlin("test"))
}

val authorizedProjects = setOf(
    "organization-context",
    "inventory-candidate-snapshot",
    "inventory-candidate-comparison"
)

check(configurations.flatMap { it.dependencies }.none {
    it.group == "io.flooow" && it.name !in authorizedProjects
}) {
    "inventory-candidate-adjudication has an unauthorized project dependency"
}
