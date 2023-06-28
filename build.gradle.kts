plugins {
    kotlin("multiplatform") version "1.8.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"
val ktorVersion: String by project

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
    linuxX64 {
        binaries {
            executable {
                entryPoint = "client.main"
            }
        }
    }
    sourceSets {
        val linuxX64Main by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
            }
        }
    }

}
