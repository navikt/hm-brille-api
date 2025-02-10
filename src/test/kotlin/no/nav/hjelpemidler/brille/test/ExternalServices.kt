package no.nav.hjelpemidler.brille.test

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.condition
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.ExternalServicesBuilder
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.serialization.jackson.jsonMapper

private val log = KotlinLogging.logger {}

/**
 * Lag en ktor-applikasjon som kan fungere som en stub av eksterne tjenester og returner en [HttpClientEngine] for å kommunisere med denne.
 */
fun externalServices(block: ExternalServicesBuilder.() -> Unit): HttpClientEngine {
    val builder = ApplicationTestBuilder().apply {
        environment { config = MapApplicationConfig() }
        externalServices { block() }
    }
    return builder.client.engine
}

/**
 * Lag stub for ekstern tjeneste.
 */
fun ExternalServicesBuilder.baseUrl(urlString: String, block: Routing.(baseUrl: String) -> Unit = {}) {
    val url = Url(urlString)
    val host = url.protocolWithAuthority
    val path = urlString.removePrefix(host)
    hosts(host) {
        install(Compression) {
            gzip {
                condition {
                    request.uri.endsWith("/lastned")
                }
                matchContentType(
                    ContentType.Application.Json,
                    EnhetsregisteretClient.CONTENT_TYPE_ENHET_GZIP,
                    EnhetsregisteretClient.CONTENT_TYPE_UNDERENHET_GZIP,
                )
            }
        }
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(jsonMapper, true))
        }
        install(StatusPages) {
            unhandled { log.warn { "Mangler svar for: '${it.request.httpMethod.value} ${it.request.uri}'" } }
        }
        routing { block(path) }
    }
}
