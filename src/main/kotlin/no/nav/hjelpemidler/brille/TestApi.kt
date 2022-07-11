package no.nav.hjelpemidler.brille

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnClient

private val log = KotlinLogging.logger { }

// FIXME: Remove eventually
@Deprecated("fjernes")
fun Route.testApi(
    altinnClient: AltinnClient,
) {
    route("/test") {
        get("/altinn/{...}") {
            val path = call.parameters.getAll("param")!!.joinToString("/")
            val data = altinnClient.get(path)
            call.respond(HttpStatusCode.OK, data)
        }
    }
}
