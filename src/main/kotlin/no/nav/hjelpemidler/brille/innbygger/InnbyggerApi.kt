package no.nav.hjelpemidler.brille.innbygger

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.pdl.PdlClientException
import no.nav.hjelpemidler.brille.pdl.PdlService

fun Route.innbyggerApi(pdlService: PdlService, auditService: AuditService) {
    route("/innbyggere") {
        post("/sok") {
            data class Request(val fnr: String)
            data class Response(
                val fnr: String,
                val navn: String,
                val alder: Int? = null,
            )

            val fnrInnlogget = call.extractFnr()
            val fnrBruker = call.receive<Request>().fnr
            if (fnrBruker.count() != 11) error("Fnr er ikke gyldig (må være 11 siffre)")

            auditService.lagreOppslag(
                fnrInnlogget = fnrInnlogget,
                fnrOppslag = fnrBruker,
                "[POST] /innbyggere/sok - personoppslag mot PDL"
            )

            val message = try {
                pdlService.hentPerson(fnrBruker)?.let {
                    Response(
                        fnr = fnrBruker,
                        navn = "${it.fornavn} ${it.etternavn}",
                        alder = it.alder
                    )
                } ?: Response(fnr = "", navn = "")
            } catch (e: PdlClientException) {
                // fixme
                Response(fnr = "", navn = "")
            }

            call.respond(message)
        }
    }
}
