plugins { id("flooow.kotlin-conventions") }

dependencies {
    implementation(project(":platform:foundation:organization-context"))
    implementation(project(":applications:integration-control-plane"))
    implementation(project(":applications:connector-runtime"))
    testImplementation(kotlin("test"))
}