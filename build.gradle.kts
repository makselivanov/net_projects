plugins {
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.compose") version "1.4.3"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(compose.desktop.currentOs)
    implementation("io.ktor:ktor-network:2.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}