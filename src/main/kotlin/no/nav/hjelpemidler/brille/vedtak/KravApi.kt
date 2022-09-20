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
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService

private val log = KotlinLogging.logger {}

internal fun Route.kravApi(
    vedtakService: VedtakService,
    auditService: AuditService,
    utbetalingService: UtbetalingService,
    vedtakSlettetService: VedtakSlettetService,
    joarkrefService: JoarkrefService,
    kafkaService: KafkaService,
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
            val vedtakId = call.parameters["id"]!!.toLong()
            val fnrInnsender = call.extractFnr()
            val vedtak = vedtakService.hentVedtak(vedtakId)
            if (vedtak != null) {
                auditService.lagreOppslag(
                    fnrInnlogget = fnrInnsender,
                    fnrOppslag = vedtak.fnrBarn,
                    oppslagBeskrivelse = "[DELETE] /krav - Sletting av krav $vedtakId"
                )
                if (fnrInnsender != vedtak.fnrInnsender) {
                    call.respond(HttpStatusCode.Unauthorized, "Ikke autorisert")
                } else if (utbetalingService.hentUtbetalingForVedtak(vedtakId) != null) {
                    call.respond(HttpStatusCode.Conflict, "vedtaket er utbetalt")
                } else {
                    val joarkRef = joarkrefService.hentJoarkRef(vedtakId)
                        ?: return@delete call.respond(HttpStatusCode.InternalServerError, "har ikke joarkref for krav")
                    log.info("JoarkRef funnet: $joarkRef")

                    vedtakSlettetService.slettVedtak(vedtakId)
                    kafkaService.feilregistrerBarnebrillerIJoark(vedtakId, joarkRef)

                    call.respond(HttpStatusCode.OK, "{}")
                }
            } else call.respond(HttpStatusCode.NotFound, "ikke funnet")
        }
    }
}
