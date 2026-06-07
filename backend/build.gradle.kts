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

val ktorVersion = "2.3.11"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // PDF text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.2")

    // Excel export
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")
}

// Configure max upload size at server level
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
}
