plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "net.polar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.1")

    implementation("net.kyori:adventure-text-minimessage:4.12.0")
}