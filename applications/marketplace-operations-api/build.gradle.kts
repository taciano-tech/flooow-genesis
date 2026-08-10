plugins {
    id("flooow.kotlin-conventions")
    application
}

application {
    mainClass = "io.flooow.marketplace.api.ApplicationKt"
}

dependencies {
    implementation(project(":applications:marketplace-operations"))
    implementation("io.ktor:ktor-server-core-jvm:3.5.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.1")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.1")
}

val verifyApiDependencyBoundary by tasks.registering {
    group = "verification"
    description = "Verifies that the HTTP adapter has no direct Kernel dependency."

    doLast {
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filter { it.group == "io.flooow" && it.name == "kernel" }

        check(forbidden.isEmpty()) {
            "marketplace-operations-api must not depend directly on the Kernel"
        }
    }
}

tasks.check {
    dependsOn(verifyApiDependencyBoundary)
}
