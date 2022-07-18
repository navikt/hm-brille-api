package no.nav.hjelpemidler.brille.rapportering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

fun Route.rapportApi(rapportService: RapportService) {
    route("/kravlinjer") {
        get("/{orgnr}") {
            val orgnr = call.orgnr()
            val kravlinjer = rapportService.hentKravlinjer(orgnr)
            call.respond(HttpStatusCode.OK, kravlinjer)
        }
    }
}

private fun ApplicationCall.orgnr(): String = requireNotNull(parameters["orgnr"]) {
    "Mangler orgnr i URL"
}
