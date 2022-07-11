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
        get("/altinn/{fnr}/{etternavn}") {
            val fnr = call.parameters["fnr"] ?: error("mangler fnr")
            val etternavn = call.parameters["etternavn"] ?: error("mangler etternavn")
            val reportee = altinnClient.hentReportee(fnr, etternavn)
            if (reportee == null) {
                call.respond(HttpStatusCode.NotFound, "Fant ikke reportee")
                return@get
            }
            log.info { "Hentet reportee med reporteeId: ${reportee.reporteeId}" }
            val rightHolder = altinnClient.hentDelegations(reportee.reporteeId)
            if (rightHolder == null) {
                call.respond(HttpStatusCode.NotFound, "Fant ikke rightHolder")
                return@get
            }
            call.respond(HttpStatusCode.OK, rightHolder)
        }
    }
}
