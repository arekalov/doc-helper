plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.arekalov.dochelper"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Testing
    testImplementation(kotlin("test"))
    
    // Ktor client for HTTP requests
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    
    // Kotlinx serialization for JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // SQLite JDBC for vector storage
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // Typesafe config for configuration
    implementation("com.typesafe:config:1.4.3")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Process execution for MCP
    implementation("org.zeroturnaround:zt-exec:1.12")
    
    // JSON-RPC for MCP communication
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
}

application {
    mainClass.set("com.arekalov.dochelper.AppKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

// Настройка для интерактивного ввода
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}