plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("dev.langchain4j:langchain4j:1.20.0")
    testImplementation("dev.langchain4j:langchain4j-ollama:1.20.0")
}

tasks.withType<Test> {
    testLogging.showStandardStreams = true
}
