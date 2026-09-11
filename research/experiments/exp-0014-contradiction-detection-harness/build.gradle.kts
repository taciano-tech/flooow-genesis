plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.0")
}

tasks.withType<Test> {
    testLogging.showStandardStreams = true
}
