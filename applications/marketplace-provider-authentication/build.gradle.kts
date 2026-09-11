plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":applications:credential-rotation-execution"))
    implementation(project(":applications:integration-control-plane"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(project(":platform:foundation:organization-context"))
    testImplementation(kotlin("test"))
}

val forbiddenProductionDependencies = configurations
    .getByName("implementation")
    .dependencies
    .filter {
        (it.group == "io.flooow" && it.name !in setOf(
            "credential-rotation-execution",
            "integration-control-plane"
        )) ||
            it.name.contains("connector-runtime", ignoreCase = true) ||
            it.name.contains("marketplace-operations", ignoreCase = true) ||
            it.name.contains("postgres", ignoreCase = true) ||
            it.name.contains("jdbc", ignoreCase = true) ||
            it.name.contains("ktor", ignoreCase = true)
    }

check(forbiddenProductionDependencies.isEmpty()) {
    "marketplace-provider-authentication must remain provider-edge and persistence-free"
}

val forbiddenTestProjectDependencies = configurations
    .getByName("testImplementation")
    .dependencies
    .filter {
        it.group == "io.flooow" &&
            it.name != "organization-context"
    }

check(forbiddenTestProjectDependencies.isEmpty()) {
    "marketplace-provider-authentication tests may use only organization-context as a direct Flooow fixture dependency"
}