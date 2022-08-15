package no.nav.hjelpemidler.brille.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr

fun Route.kravApi(vedtakService: VedtakService, auditService: AuditService) {
    post("/krav") {
        val kravDto = call.receive<KravDto>()
        val fnrInnsender = call.extractFnr()

        auditService.lagreOppslag(
            fnrInnlogget = fnrInnsender,
            fnrOppslag = kravDto.vilkårsgrunnlag.fnrBarn,
            oppslagBeskrivelse = "[POST] /krav - Innsending av krav"
        )

        val vedtak = vedtakService.lagVedtak(fnrInnsender, kravDto)
        call.respond(
            HttpStatusCode.OK,
            vedtak.toDto()
        )
    }
}
