package no.nav.hjelpemidler.brille.utbetaling

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

fun Route.utbetalingApi() {
    route("/utbetalinger") {
        get("/{orgnr}") {
            val orgnr = call.orgnr()
            // todo: hent faktiske utbetalinger fra db
            val utbetalinger = emptyList<String>()
            call.respond(HttpStatusCode.OK, utbetalinger)
        }
    }
}

private fun ApplicationCall.orgnr(): String = requireNotNull(parameters["orgnr"]) {
    "Mangler orgnr i URL"
}
