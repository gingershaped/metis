plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":lang"))
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
}

application {
    mainClass.set("io.github.seggan.metis.app.MainKt")
}