import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

plugins {
    application
    kotlin("jvm") version "1.7.0"
    id("com.diffplug.spotless") version "6.7.2"
}

application {
    applicationName = "hm-brille-api"
    mainClass.set("io.ktor.server.cio.EngineMain")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.9.1")

    // Database
    implementation("org.postgresql:postgresql:42.4.0")
    implementation("org.flywaydb:flyway-core:8.5.13")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.github.seratch:kotliquery:1.8.0")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.1.0")

    // Ktor Shared
    val ktorVersion = "2.0.2"
    fun ktor(name: String) = "io.ktor:ktor-$name:$ktorVersion"
    implementation(ktor("serialization-jackson"))

    // Ktor Server
    fun ktorServer(name: String) = "io.ktor:ktor-server-$name:$ktorVersion"
    implementation(ktorServer("core"))
    implementation(ktorServer("cio"))
    implementation(ktorServer("content-negotiation"))
    implementation(ktorServer("auth-jwt"))
    implementation(ktorServer("metrics-micrometer"))
    implementation(ktorServer("call-id"))
    implementation(ktorServer("status-pages"))

    // Ktor Client
    fun ktorClient(name: String) = "io.ktor:ktor-client-$name:$ktorVersion"
    implementation(ktorClient("core"))
    implementation(ktorClient("cio"))
    implementation(ktorClient("content-negotiation"))
    implementation(ktorClient("auth"))
    implementation(ktorClient("jackson"))

    // Jackson
    val jacksonVersion = "2.13.3"
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Logging
    implementation("io.github.microutils:kotlin-logging:2.1.23")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.2")
    runtimeOnly("ch.qos.logback:logback-classic:1.2.11")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(ktorServer("test-host"))
    testImplementation("io.mockk:mockk:1.12.4")
    testImplementation("org.testcontainers:postgresql:1.17.2")
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Wrapper> {
    gradleVersion = "7.4.2"
}

tasks.named("compileKotlin") {
    dependsOn("spotlessApply")
    dependsOn("spotlessCheck")
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass
    }
    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
}
