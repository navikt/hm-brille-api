package no.nav.hjelpemidler.brille.vedtak

import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService

private val log = KotlinLogging.logger {}

class SlettVedtakService(
    private val vedtakService: VedtakService,
    private val auditService: AuditService,
    private val utbetalingService: UtbetalingService,
    private val joarkrefService: JoarkrefService,
    private val kafkaService: KafkaService,
    private val databaseContext: DatabaseContext,
) {

    suspend fun slettVedtak(fnrInnsender: String, vedtakId: Long, erAdmin: Boolean): Pair<HttpStatusCode, String> {
        val vedtak = vedtakService.hentVedtak(vedtakId)
        if (vedtak != null) {
            auditService.lagreOppslag(
                fnrInnlogget = fnrInnsender,
                fnrOppslag = vedtak.fnrBarn,
                oppslagBeskrivelse = "[DELETE] /krav - Sletting av krav $vedtakId"
            )
            if (!erAdmin && fnrInnsender != vedtak.fnrInnsender) {
                return Pair(HttpStatusCode.Unauthorized, "Ikke autorisert")
            } else if (utbetalingService.hentUtbetalingForVedtak(vedtakId) != null) {
                return Pair(HttpStatusCode.Conflict, "vedtaket er utbetalt")
            } else {
                val joarkRef = joarkrefService.hentJoarkRef(vedtakId)
                    ?: return Pair(HttpStatusCode.InternalServerError, "har ikke joarkref for krav")
                log.info("JoarkRef funnet: $joarkRef")

                transaction(databaseContext) { ctx ->
                    ctx.vedtakSlettetStore.slettVedtak(vedtakId)
                    kafkaService.vedtakSlettet(vedtakId)
                }

                kafkaService.feilregistrerBarnebrillerIJoark(vedtakId, joarkRef)

                return Pair(HttpStatusCode.OK, "{}")
            }
        } else return Pair(HttpStatusCode.NotFound, "ikke funnet")
    }
}
