package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService

private val log = KotlinLogging.logger {}

class SlettVedtakConflictException() : RuntimeException("vedtaket er utbetalt")
class SlettVedtakInternalServerErrorException() : RuntimeException("har ikke joarkref for krav")

class SlettVedtakService(
    private val utbetalingService: UtbetalingService,
    private val joarkrefService: JoarkrefService,
    private val kafkaService: KafkaService,
    private val databaseContext: DatabaseContext,
) {

    suspend fun slettVedtak(vedtakId: Long, slettetAv: String, slettetAvType: SlettetAvType) {
        if (utbetalingService.hentUtbetalingForVedtak(vedtakId) != null) {
            throw SlettVedtakConflictException()
        } else {
            val joarkRef = joarkrefService.hentJoarkRef(vedtakId)
                ?: throw SlettVedtakInternalServerErrorException()

            log.info("JoarkRef funnet: $joarkRef")

            transaction(databaseContext) { ctx ->
                ctx.slettVedtakStore.slettVedtak(vedtakId, slettetAv, slettetAvType)
                kafkaService.vedtakSlettet(vedtakId)
                kafkaService.feilregistrerBarnebrillerIJoark(vedtakId, joarkRef)
            }
        }
    }
}
