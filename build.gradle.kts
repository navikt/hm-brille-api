import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

plugins {
    application
    kotlin("jvm") version "1.7.0"
    id("com.diffplug.spotless") version "6.4.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

apply {
    plugin("com.diffplug.spotless")
}

application {
    applicationName = "hm-brille-api"
    mainClass.set("no.nav.hjelpemidler.brille.ApplicationKt")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // Used for hm-docs
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.navikt:hm-docs:21.350.104114")

    // Database
    implementation("org.postgresql:postgresql:42.3.6")
    implementation("org.flywaydb:flyway-core:8.5.12")
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
    implementation(ktorServer("netty"))
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
    implementation(ktorClient("jackson"))

    // Jackson
    val jacksonVersion = "2.13.3"
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic:1.3.0-alpha14") // alpha pga 1.3.0 fikser stackoverflow error https://youtrack.jetbrains.com/issue/KTOR-2040
    implementation("org.slf4j:slf4j-api:2.0.0-alpha6") // trengs av logback-classic 1.3.0
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")

    // Prometheus and InfluxDB
    implementation("io.micrometer:micrometer-registry-prometheus:1.9.0")

    // Utils
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("com.github.tomakehurst:wiremock-jre8-standalone:2.33.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(ktorServer("test-host"))
    val junitJupiterVersion = "5.8.2"
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("io.mockk:mockk:1.12.4")
    testImplementation("org.testcontainers:junit-jupiter:1.17.2")
    testImplementation("com.playtika.testcontainers:embedded-postgresql:2.2.2")
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
    testLogging {
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true
        outputs.upToDateWhen { false }
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "7.4.2"
}

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("jar") {
    dependsOn("test")
}

tasks.named("compileKotlin") {
    dependsOn("spotlessApply")
    dependsOn("spotlessCheck")
}
