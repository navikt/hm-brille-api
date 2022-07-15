package no.nav.hjelpemidler.brille.utbetaling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

fun Route.utbetalingApi() {
        route("/utbetalinger") {
            get("/{orgnr}") {
                val orgnr = call.orgnr()
                //todo: hent faktiske utbetalinger fra db
                val utbetalinger = emptyList<String>()
                call.respond(HttpStatusCode.OK, utbetalinger)
            }
        }
}

private fun ApplicationCall.orgnr(): String = requireNotNull(parameters["orgnr"]) {
    "Mangler orgnr i URL"
}
