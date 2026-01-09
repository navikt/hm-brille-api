import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    implementation(libs.hotlibs.logging)
    implementation(libs.hotlibs.nare)
    implementation(libs.hotlibs.serialization)

    implementation(libs.hotlibs.database) {
        capabilities {
            requireCapability("no.nav.hjelpemidler:database-postgresql")
        }
    }

    // Ktor
    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.server.compression)

    // other
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.hmRapidsAndRiversV2Core)
    implementation(libs.valkey)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.unleash)
    runtimeOnly(libs.logback.syslog4j) // auditlog https://github.com/navikt/naudit

    // GraphQL
    implementation(libs.graphql.ktor.client) {
        exclude(group = "com.expediagroup", module = "graphql-kotlin-client-serialization")
        exclude(group = "io.ktor", module = "ktor-client-serialization")
    }
    implementation(libs.graphql.client.jackson)
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

@Suppress("UnstableApiUsage")
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useKotlinTest(libs.versions.kotlin.asProvider())
            dependencies {
                implementation(libs.handlebars)
                implementation(libs.hotlibs.test)
                implementation(libs.jackson.dataformat.yaml)
                implementation(libs.kotest.assertions.json)
                implementation(libs.ktor.server.test.host)
                implementation(libs.nimbus.jose.jwt)

                implementation(libs.hotlibs.database) {
                    capabilities {
                        requireCapability("no.nav.hjelpemidler:database-testcontainers")
                    }
                }
            }
        }
    }
}

tasks.shadowJar {
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
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

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}
