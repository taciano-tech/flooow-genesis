plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:inventory-identity-mapping"))
    implementation(project(":applications:inventory-canonical-observation"))
    testImplementation(kotlin("test"))
}

check(configurations.flatMap { it.dependencies }.none {
    it.group == "io.flooow" && it.name !in setOf(
        "organization-context", "integration-control-plane",
        "inventory-identity-mapping", "inventory-canonical-observation"
    )
}) {
    "inventory-source-acceptance has an unauthorized project dependency"
}
