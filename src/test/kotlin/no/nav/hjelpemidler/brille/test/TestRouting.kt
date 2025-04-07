package no.nav.hjelpemidler.brille.test

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.auth.authentication
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.hjelpemidler.brille.configure
import no.nav.hjelpemidler.brille.tilgang.InnloggetBruker
import no.nav.hjelpemidler.http.jackson
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.util.UUID

class TestRouting(private val configuration: Routing.() -> Unit) {
    internal val principal = InnloggetBruker.TokenX.Bruker("15084300133")

    internal fun test(block: suspend TestContext.() -> Unit) = testApplication {
        application {
            configure()
            authentication {
                provider("test") {
                    authenticate { context ->
                        context.principal(principal)
                    }
                }
                provider("test_azuread") {
                    authenticate { context ->
                        context.principal(
                            InnloggetBruker.AzureAd.Administrator(
                                objectId = UUID.fromString("21547b88-65da-49bf-8117-075fb40e6682"),
                                email = "example@example.com",
                                name = "E. X. Ample",
                                navIdent = "X123456",
                            ),
                        )
                    }
                }
            }

            routing(configuration)
        }

        val client = createClient {
            jackson(jsonMapper)
            defaultRequest {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
            }
        }

        TestContext(client).block()
    }
}

class TestContext(val client: HttpClient)
