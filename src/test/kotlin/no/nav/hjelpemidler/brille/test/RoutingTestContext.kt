package no.nav.hjelpemidler.brille.test

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.hjelpemidler.brille.configure

interface RoutingTestContext {
    val client: HttpClient
}

fun setupTestApplication(configuration: Routing.() -> Unit, test: suspend RoutingTestContext.() -> Unit): Unit =
    testApplication {
        environment {
            config = MapApplicationConfig() // for at application.conf ikke skal leses
        }
        application {
            configure()
            routing(configuration)
        }
        test(object : RoutingTestContext {
            override val client: HttpClient = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
                defaultRequest {
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                }
            }
        })
    }
