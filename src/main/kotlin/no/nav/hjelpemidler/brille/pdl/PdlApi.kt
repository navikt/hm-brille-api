package no.nav.hjelpemidler.brille.pdl

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr

fun Route.pdlApi(pdlService: PdlService, auditService: AuditService) {
    post("/hent-bruker") {
        data class Request(val fnr: String)
        data class Response(
            val fnr: String,
            val navn: String,
            val alder: Int
        )

        val fnrInnlogget = call.extractFnr()
        val fnrBruker = call.receive<Request>().fnr
        if (fnrBruker.count() != 11) error("Fnr er ikke gyldig (må være 11 siffre)")

        auditService.lagreOppslag(
            fnrInnlogget = fnrInnlogget,
            fnrOppslag = fnrBruker,
            "[POST] /hent-bruker - brukeroppslag mot PDL"
        )

        val personInformasjon = pdlService.hentPerson(fnrBruker)

        call.respond(
            Response(
                fnrBruker,
                "${personInformasjon.fornavn} ${personInformasjon.etternavn}",
                personInformasjon.alder!!
            )
        )
    }
}
