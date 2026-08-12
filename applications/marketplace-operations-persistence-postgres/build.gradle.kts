plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:marketplace-operations"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:connector-runtime"))
    implementation(project(":applications:inventory-source-ingestion"))
    implementation(project(":applications:inventory-identity-mapping"))
    implementation(project(":applications:inventory-canonical-observation"))
    implementation(project(":applications:inventory-source-acceptance"))
    implementation("org.jetbrains.exposed:exposed-core:1.4.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.4.0")
    implementation("org.jetbrains.exposed:exposed-java-time:1.4.0")
    implementation("org.flywaydb:flyway-core:13.2.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.2.0")
    implementation("org.postgresql:postgresql:42.7.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

val forbiddenDirectKernelDependencies = configurations
    .flatMap { it.dependencies }
    .filter { it.group == "io.flooow" && it.name == "kernel" }

check(forbiddenDirectKernelDependencies.isEmpty()) {
    "marketplace-operations-persistence-postgres must not depend directly on the Kernel"
}
