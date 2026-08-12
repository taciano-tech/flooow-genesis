plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:inventory-source-ingestion"))
    testImplementation(kotlin("test"))
}

check(configurations.flatMap { it.dependencies }.none {
    it.group == "io.flooow" && it.name !in setOf(
        "organization-context", "integration-control-plane", "inventory-source-ingestion"
    )
}) {
    "inventory-identity-mapping has an unauthorized project dependency"
}
