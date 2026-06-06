plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    application
    id("io.ktor.plugin") version "2.3.11"
}

group   = "com.ortuspro"
version = "1.0.0"

repositories { mavenCentral() }

application {
    mainClass.set("com.ortuspro.MainKt")
}

ktor {
    fatJar {
        archiveFileName.set("app.jar")
    }
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:2.3.11")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.11")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.11")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.11")
    implementation("io.ktor:ktor-server-http-redirect-jvm:2.3.11")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // PDF text extraction (tuned for AU bank statements)
    implementation("org.apache.pdfbox:pdfbox:3.0.2")

    // Excel export
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // OCR: uses system CLI calls to tesseract + pdftoppm (poppler)
    // installed via nixpacks.toml — no heavy JVM OCR library needed
}
