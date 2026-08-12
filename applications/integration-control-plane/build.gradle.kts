plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    testImplementation(kotlin("test"))
}

val forbiddenDependencies = configurations
    .flatMap { it.dependencies }
    .filter {
        (it.group == "io.flooow" && it.name != "organization-context") ||
            it.name.contains("ktor", ignoreCase = true) ||
            it.name.contains("jdbc", ignoreCase = true) ||
            it.name.contains("oauth", ignoreCase = true)
    }

check(forbiddenDependencies.isEmpty()) {
    "integration-control-plane must remain independent from Kernel and infrastructure"
}
