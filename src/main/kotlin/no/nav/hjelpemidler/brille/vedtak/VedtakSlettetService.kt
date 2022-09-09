package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

class VedtakSlettetService(val databaseContext: DatabaseContext) {

    suspend fun slettVedtak(vedtakId: Long) {
        return transaction(databaseContext) { ctx ->
            ctx.vedtakSlettetStore.slettVedtak(vedtakId)
        }
    }
}
