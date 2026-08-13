plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":applications:inventory-candidate-snapshot"))
    testImplementation(kotlin("test"))
}

check(configurations.flatMap { it.dependencies }.none {
    it.group == "io.flooow" && it.name != "inventory-candidate-snapshot"
}) {
    "inventory-candidate-comparison has an unauthorized project dependency"
}
