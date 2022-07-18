package no.nav.hjelpemidler.brille.innsender

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.extractFnr

private val log = KotlinLogging.logger {}

fun Route.innsenderApi(innsenderService: InnsenderService) {
    route("/innsendere") {
        // hent innsendere
        get {
            val fnrInnsender = call.extractFnr()
            val innsender = innsenderService.hentInnsender(fnrInnsender)
            call.respond(HttpStatusCode.OK, InnsenderDto(innsender.godtatt))
        }
        // opprett innsender
        post {
            val fnrInnsender = call.extractFnr()
            val innsender = innsenderService.godtaAvtale(fnrInnsender)
            call.respond(HttpStatusCode.Created, InnsenderDto(innsender.godtatt))
        }
    }
}

data class InnsenderDto(val godtatt: Boolean)
