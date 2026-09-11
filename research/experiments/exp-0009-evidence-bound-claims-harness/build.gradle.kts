plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // Experimental model adapter only. Claim contracts and validation remain Flooow-owned.
    testImplementation("dev.langchain4j:langchain4j:1.20.0")
    testImplementation("dev.langchain4j:langchain4j-ollama:1.20.0")
}

tasks.withType<Test> {
    testLogging.showStandardStreams = true
}
