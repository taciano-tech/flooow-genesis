plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    testImplementation(project(":platform:foundation:organization-context"))
    testImplementation(project(":applications:marketplace-operations"))
    testImplementation(project(":applications:marketplace-operations-persistence-postgres"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.flywaydb:flyway-core:13.2.0")
    testImplementation("org.flywaydb:flyway-database-postgresql:13.2.0")
    testImplementation("org.postgresql:postgresql:42.7.12")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

tasks.withType<Test> {
    testLogging.showStandardStreams = true
}
