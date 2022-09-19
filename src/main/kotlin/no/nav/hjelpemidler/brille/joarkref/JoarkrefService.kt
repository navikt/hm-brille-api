package no.nav.hjelpemidler.brille.joarkref

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

class JoarkrefService(val databaseContext: DatabaseContext) {
    suspend fun hentJoarkRef(vedtakId: Long): Long? {
        return transaction(databaseContext) {
            it.joarkrefStore.hentJoarkRef(vedtakId)
        }
    }

    suspend fun lagreJoarkRef(vedtakId: Long, joarkRef: Long) {
        transaction(databaseContext) { ctx ->
            ctx.joarkrefStore.lagreJoarkRef(vedtakId, joarkRef)
        }
    }
}
