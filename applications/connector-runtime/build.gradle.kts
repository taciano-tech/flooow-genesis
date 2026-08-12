plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":platform:foundation:organization-context"))
    testImplementation(kotlin("test"))
}

val forbiddenDependencies = configurations
    .flatMap { it.dependencies }
    .filter {
        (it.group == "io.flooow" && it.name !in setOf(
            "integration-control-plane",
            "organization-context"
        )) ||
            it.name.contains("ktor", ignoreCase = true) ||
            it.name.contains("jdbc", ignoreCase = true) ||
            it.name.contains("oauth", ignoreCase = true) ||
            it.name.contains("jackson", ignoreCase = true) ||
            it.name.contains("serialization", ignoreCase = true)
    }

check(forbiddenDependencies.isEmpty()) {
    "connector-runtime must remain provider-neutral and infrastructure-free"
}
