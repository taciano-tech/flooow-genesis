plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":applications:connector-runtime"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:marketplace-provider-authentication"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
}

val forbiddenFlooowDependencies = configurations
    .flatMap { it.dependencies }
    .filter {
        it.group == "io.flooow" && it.name !in setOf(
            "connector-runtime",
            "integration-control-plane",
            "marketplace-provider-authentication"
        )
    }

check(forbiddenFlooowDependencies.isEmpty()) {
    "marketplace-economic-provider-ingestion must remain provider/infrastructure isolated"
}