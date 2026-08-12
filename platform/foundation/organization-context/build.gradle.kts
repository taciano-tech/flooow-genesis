plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    testImplementation(kotlin("test"))
}

check(configurations.flatMap { it.dependencies }.none { it.group == "io.flooow" }) {
    "organization-context must remain independent from Kernel and applications"
}
