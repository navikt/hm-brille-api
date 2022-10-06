package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService

private val log = KotlinLogging.logger {}

class SlettVedtakNotAuthorizedException() : RuntimeException("Ikke autorisert")
class SlettVedtakConflictException() : RuntimeException("vedtaket er utbetalt")
class SlettVedtakInternalServerErrorException() : RuntimeException("har ikke joarkref for krav")
class SlettVedtakNotFoundException() : RuntimeException("ikke funnet")

class SlettVedtakService(
    private val vedtakService: VedtakService,
    private val auditService: AuditService,
    private val utbetalingService: UtbetalingService,
    private val joarkrefService: JoarkrefService,
    private val kafkaService: KafkaService,
    private val databaseContext: DatabaseContext,
) {

    suspend fun slettVedtak(fnrInnsender: String, vedtakId: Long, erAdmin: Boolean) {
        val vedtak = vedtakService.hentVedtak(vedtakId)
        if (vedtak != null) {
            if (!erAdmin && fnrInnsender != vedtak.fnrInnsender) {
                throw SlettVedtakNotAuthorizedException()
            } else if (utbetalingService.hentUtbetalingForVedtak(vedtakId) != null) {
                throw SlettVedtakConflictException()
            } else {
                if (!erAdmin) {
                    auditService.lagreOppslag(
                        fnrInnlogget = fnrInnsender,
                        fnrOppslag = vedtak.fnrBarn,
                        oppslagBeskrivelse = "[DELETE] /krav - Sletting av krav $vedtakId"
                    )
                } else {
                    log.info("Sletter vedtak med vedtakId=$vedtakId og adminId=$fnrInnsender")
                }

                val joarkRef = joarkrefService.hentJoarkRef(vedtakId)
                    ?: throw SlettVedtakInternalServerErrorException()

                log.info("JoarkRef funnet: $joarkRef")

                transaction(databaseContext) { ctx ->
                    ctx.slettVedtakStore.slettVedtak(vedtakId, fnrInnsender, if (erAdmin) SlettetAvType.NAV_ADMIN else SlettetAvType.INNSENDER)
                    kafkaService.vedtakSlettet(vedtakId)
                }

                kafkaService.feilregistrerBarnebrillerIJoark(vedtakId, joarkRef)
                return
            }
        } else throw SlettVedtakNotFoundException()
    }
}
