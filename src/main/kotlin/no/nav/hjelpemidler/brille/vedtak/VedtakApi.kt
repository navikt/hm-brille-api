package no.nav.hjelpemidler.brille.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr

fun Route.søknadApi(vedtakService: VedtakService, auditService: AuditService) {
    post("/soknad") {
        if (Configuration.prod) { // TODO: fjern før prodsetting
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val søknadDto = call.receive<SøknadDto>()
        val fnrInnsender = call.extractFnr()

        auditService.lagreOppslag(
            fnrInnlogget = fnrInnsender,
            fnrOppslag = søknadDto.vilkårsgrunnlag.fnrBruker,
            oppslagBeskrivelse = "[POST] /soknad - Innsending av søknad"
        )

        val vedtak = vedtakService.lagVedtak(søknadDto, fnrInnsender)

        call.respond(HttpStatusCode.Created, mapOf("vedtakId" to vedtak.id))
    }
}
