plugins { id("flooow.kotlin-conventions") }

dependencies {
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":platform:foundation:organization-context"))
    testImplementation(kotlin("test"))
}

val forbiddenDependencies = configurations.flatMap { it.dependencies }.filter {
    (it.group == "io.flooow" && it.name !in setOf("integration-control-plane", "organization-context")) ||
        it.name.contains("ktor", true) || it.name.contains("jdbc", true) ||
        it.name.contains("postgres", true) || it.name.contains("oauth", true) ||
        it.name.contains("serialization", true)
}
check(forbiddenDependencies.isEmpty()) {
    "credential-rotation-execution must remain provider-neutral and infrastructure-free"
}