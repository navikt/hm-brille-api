import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.graphql)
    alias(libs.plugins.spotless)
}

application {
    applicationName = "hm-brille-api"
    mainClass.set("no.nav.hjelpemidler.brille.ApplicationKt")
}

dependencies {
    // hotlibs
    implementation(platform(libs.hotlibs.platform))
    implementation(libs.hotlibs.http)
    implementation(libs.hotlibs.nare)
    implementation(libs.hotlibs.database) {
        capabilities {
            requireCapability("no.nav.hjelpemidler:database-postgresql")
        }
    }

    implementation(libs.konfig.deprecated)
    implementation(libs.micrometer.registry.prometheus)
    implementation("com.github.navikt:hm-rapids-and-rivers-v2-core:202410290928")

    // Database
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.kotliquery)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // Unleash
    implementation(libs.unleash)

    // Ktor Server
    implementation(libs.bundles.ktor.server)

    // GraphQL
    implementation(libs.graphql.ktor.client) {
        exclude(group = "com.expediagroup", module = "graphql-kotlin-client-serialization")
        exclude(group = "io.ktor", module = "ktor-client-serialization")
    }
    implementation(libs.graphql.client.jackson)

    // Jackson
    implementation(libs.bundles.jackson)

    // Logging
    implementation(libs.kotlin.logging.deprecated)
    runtimeOnly(libs.bundles.logging.runtime)
    runtimeOnly(libs.logback.syslog4j) // auditlog https://github.com/navikt/naudit

    // Redis
    implementation(libs.jedis)

    // Testing
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
    testImplementation(libs.nimbus.jose.jwt)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.hotlibs.database) {
        capabilities {
            requireCapability("no.nav.hjelpemidler:database-testcontainers")
        }
    }
}

spotless {
    kotlin {
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_enum-entry-name-case" to "disabled",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
                "ktlint_standard_value-argument-comment" to "disabled",
                "ktlint_standard_value-parameter-comment" to "disabled",
            ),
        )
        targetExclude("build/generated/source/**")
    }
    kotlinGradle {
        ktlint()
    }
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

tasks.test {
    useJUnitPlatform()
}

tasks.compileKotlin {
    // dependsOn("spotlessApply")
    // dependsOn("spotlessCheck")
}

tasks.shadowJar {
    mergeServiceFiles()
}

graphql {
    client {
        schemaFile = file("${project.projectDir}/src/main/resources/pdl/schema.graphql")
        queryFiles =
            listOf(
                file("${project.projectDir}/src/main/resources/pdl/hentPerson.graphql"),
                file("${project.projectDir}/src/main/resources/pdl/medlemskapHentBarn.graphql"),
            )
        customScalars =
            listOf(
                GraphQLScalar("Long", "kotlin.Long", "no.nav.hjelpemidler.brille.pdl.LongConverter"),
                GraphQLScalar("Date", "java.time.LocalDate", "no.nav.hjelpemidler.brille.pdl.DateConverter"),
                GraphQLScalar(
                    "DateTime",
                    "java.time.LocalDateTime",
                    "no.nav.hjelpemidler.brille.pdl.DateTimeConverter",
                ),
            )
        packageName = "no.nav.hjelpemidler.brille.pdl.generated"
    }
}

val graphqlIntrospectSchema by tasks.getting(GraphQLIntrospectSchemaTask::class) {
    endpoint.set("https://pdl-api.intern.dev.nav.no/graphql")
    outputFile.set(file("${project.projectDir}/src/main/resources/pdl/schema.graphql"))
}
