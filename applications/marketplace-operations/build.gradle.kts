plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:kernel"))
    testImplementation(kotlin("test"))
}
