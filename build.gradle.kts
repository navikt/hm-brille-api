import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.9.21"
    id("io.ktor.plugin") version "2.3.6"
    id("com.diffplug.spotless") version "6.16.0"
    id("com.expediagroup.graphql") version "7.0.2"
}

application {
    applicationName = "hm-brille-api"
    mainClass.set("no.nav.hjelpemidler.brille.ApplicationKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    implementation("com.github.navikt:hm-rapids-and-rivers-v2-core:202210121657")

    // Database
    implementation("org.postgresql:postgresql:42.7.0")
    implementation("org.flywaydb:flyway-core:10.1.0")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:10.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.github.seratch:kotliquery:1.9.0")

    // HTTP
    implementation("no.nav.hjelpemidler:hm-http:0.1.9")

    // NARE
    implementation("no.nav.hjelpemidler:hm-nare:0.1.6")

    // Unleash
    implementation("io.getunleash:unleash-client-java:9.1.1")

    // Ktor Shared
    fun ktor(name: String) = "io.ktor:ktor-$name"
    implementation(ktor("serialization-jackson"))

    // Ktor Server
    fun ktorServer(name: String) = "io.ktor:ktor-server-$name"
    implementation(ktorServer("core"))
    implementation(ktorServer("cio"))
    implementation(ktorServer("content-negotiation"))
    implementation(ktorServer("auth-jwt"))
    implementation(ktorServer("metrics-micrometer"))
    implementation(ktorServer("call-id"))
    implementation(ktorServer("status-pages"))

    // GraphQL
    val graphQLVersion = "7.0.2"
    implementation("com.expediagroup:graphql-kotlin-ktor-client:$graphQLVersion") {
        exclude(group = "com.expediagroup", module = "graphql-kotlin-client-serialization")
        exclude(group = "io.ktor", module = "ktor-client-serialization")
    }
    implementation("com.expediagroup:graphql-kotlin-client-jackson:$graphQLVersion")

    // Jackson
    val jacksonVersion = "2.15.2"
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.4")
    runtimeOnly("com.papertrailapp:logback-syslog4j:1.0.0") // auditlog https://github.com/navikt/naudit
    runtimeOnly("ch.qos.logback:logback-classic:1.4.11")

    // Redis
    implementation("redis.clients:jedis:5.1.0")

    // Postgres from naisjob
    implementation("com.google.cloud.sql:postgres-socket-factory:1.15.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(ktorServer("test-host"))
    testImplementation("io.mockk:mockk:1.13.8")
    val kotestVersion = "5.8.0"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.35")
}

spotless {
    kotlin {
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_enum-entry-name-case" to "disabled",
                "ktlint_standard_filename" to "disabled",
            ),
        )
        targetExclude("build/generated/source/**")
    }
    kotlinGradle {
        ktlint()
    }
}

val jdkVersion = JavaLanguageVersion.of(17)
java { toolchain { languageVersion.set(jdkVersion) } }
kotlin { jvmToolchain { languageVersion.set(jdkVersion) } }

tasks.test {
    useJUnitPlatform()
}

tasks.compileKotlin {
    dependsOn("spotlessApply")
    dependsOn("spotlessCheck")
}

graphql {
    client {
        schemaFile = file("${project.projectDir}/src/main/resources/pdl/schema.graphql")
        queryFiles = listOf(
            file("${project.projectDir}/src/main/resources/pdl/hentPerson.graphql"),
            file("${project.projectDir}/src/main/resources/pdl/medlemskapHentBarn.graphql"),
        )
        customScalars = listOf(
            GraphQLScalar("Long", "kotlin.Long", "no.nav.hjelpemidler.brille.pdl.LongConverter"),
            GraphQLScalar("Date", "java.time.LocalDate", "no.nav.hjelpemidler.brille.pdl.DateConverter"),
            GraphQLScalar("DateTime", "java.time.LocalDateTime", "no.nav.hjelpemidler.brille.pdl.DateTimeConverter"),
        )
        packageName = "no.nav.hjelpemidler.brille.pdl.generated"
    }
}
