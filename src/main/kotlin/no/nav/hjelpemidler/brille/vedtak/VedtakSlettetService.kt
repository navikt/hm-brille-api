package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.kafka.KafkaService

class VedtakSlettetService(
    val databaseContext: DatabaseContext,
    private val kafkaService: KafkaService,
) {

    suspend fun slettVedtak(vedtakId: Long) {
        return transaction(databaseContext) { ctx ->
            ctx.vedtakSlettetStore.slettVedtak(vedtakId)
            kafkaService.vedtakSlettet(vedtakId)
        }
    }
}
