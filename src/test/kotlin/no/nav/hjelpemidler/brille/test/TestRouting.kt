package no.nav.hjelpemidler.brille.test

import io.kotest.common.runBlocking
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.auth.authentication
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplication
import no.nav.hjelpemidler.brille.UserPrincipal
import no.nav.hjelpemidler.brille.UserPrincipalAdmin
import no.nav.hjelpemidler.brille.configure
import no.nav.hjelpemidler.http.jackson
import java.util.UUID

class TestRouting(configuration: Routing.() -> Unit) {
    private val application = TestApplication {
        environment {
            config = MapApplicationConfig() // for at application.conf ikke skal leses
        }
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
                            UserPrincipalAdmin(
                                UUID.fromString("21547b88-65da-49bf-8117-075fb40e6682"),
                                "example@example.com",
                                "Example some some"
                            )
                        )
                    }
                }
            }

            routing(configuration)
        }
    }

    internal val principal = UserPrincipal("15084300133")

    internal val client = application.createClient {
        jackson()
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
    }

    internal fun test(block: suspend TestRouting.() -> Unit) = runBlocking {
        block(this)
    }
}
