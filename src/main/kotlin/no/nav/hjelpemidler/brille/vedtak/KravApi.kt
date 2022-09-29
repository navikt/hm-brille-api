package no.nav.hjelpemidler.brille.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr

private val log = KotlinLogging.logger {}

internal fun Route.kravApi(
    vedtakService: VedtakService,
    auditService: AuditService,
    slettVedtakService: SlettVedtakService,
) {
    route("/krav") {
        post {
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

        delete("/{id}") {
            val fnrInnsender = call.extractFnr()
            val vedtakId = call.parameters["id"]!!.toLong()
            try {
                slettVedtakService.slettVedtak(fnrInnsender, vedtakId, false)
                call.respond(HttpStatusCode.OK, "{}")
            } catch (e: SlettVedtakNotAuthorizedException) {
                call.respond(HttpStatusCode.Unauthorized, e.message!!)
            } catch (e: SlettVedtakConflictException) {
                call.respond(HttpStatusCode.Conflict, e.message!!)
            } catch (e: SlettVedtakInternalServerErrorException) {
                call.respond(HttpStatusCode.InternalServerError, e.message!!)
            } catch (e: SlettVedtakNotFoundException) {
                call.respond(HttpStatusCode.NotFound, e.message!!)
            }
        }
    }
}
