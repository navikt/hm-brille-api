package no.nav.hjelpemidler.brille.vedtak

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.kafka.KafkaService

private val log = KotlinLogging.logger {}

class SlettVedtakConflictException : RuntimeException("vedtaket er utbetalt")
class SlettVedtakInternalServerErrorException : RuntimeException("har ikke joarkref for krav")

class SlettVedtakService(
    private val databaseContext: DatabaseContext,
    private val joarkrefService: JoarkrefService,
    private val kafkaService: KafkaService,
) {
    suspend fun slettVedtak(vedtakId: Long, slettetAv: String, slettetAvType: SlettetAvType) {
        val joarkRef = joarkrefService.hentJoarkRef(vedtakId)?.journalpostId
            ?: throw SlettVedtakInternalServerErrorException()
        log.info { "JoarkRef funnet: $joarkRef" }

        transaction(databaseContext) { ctx ->
            ctx.slettVedtakStore.slettVedtak(vedtakId, slettetAv, slettetAvType)
            kafkaService.vedtakSlettet(vedtakId, slettetAvType)
            kafkaService.feilregistrerBarnebrillerIJoark(vedtakId, joarkRef)
        }
    }
}
