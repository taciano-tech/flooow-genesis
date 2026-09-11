plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    testImplementation(project(":platform:foundation:organization-context"))
    testImplementation(project(":applications:marketplace-operations"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test> {
    testLogging.showStandardStreams = true
}
