plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    //maven(url="https://clojars.org/repo")
}

dependencies {
    testImplementation(kotlin("test"))

    //implementation("jnetpcap:jnetpcap:1.5.r1457-1i")
    implementation("com.ardikars.pcap:pcap-spi:1.5.0")
    implementation("com.ardikars.pcap:pcap-common:1.5.0")
    implementation("com.ardikars.pcap:pcap-jdk7:1.5.0")
    implementation("com.ardikars.pcap:pcap-codec:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.lets-plot:lets-plot-batik:3.2.0")
    implementation("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:4.4.1")
    implementation("org.slf4j:slf4j-nop:2.0.7")
    implementation("org.slf4j:slf4j-api:2.0.7")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}