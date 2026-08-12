plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":applications:connector-runtime"))
    testImplementation(kotlin("test"))
}

check(configurations.flatMap { it.dependencies }.none {
    it.group == "io.flooow" && it.name != "connector-runtime"
}) {
    "inventory-source-ingestion must depend only on connector-runtime"
}
