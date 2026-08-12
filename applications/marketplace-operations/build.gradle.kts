plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    implementation(project(":platform:foundation:kernel"))
    implementation(project(":platform:foundation:organization-context"))
    testImplementation(kotlin("test"))
}
