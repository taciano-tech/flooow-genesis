plugins {
    id("flooow.kotlin-conventions")
}

dependencies {
    testImplementation(project(":platform:foundation:kernel"))
    testImplementation(kotlin("test"))
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("research/experiments/fixtures/exp-0003"))
    }
}
