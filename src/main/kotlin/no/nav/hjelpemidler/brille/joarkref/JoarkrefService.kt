package no.nav.hjelpemidler.brille.joarkref

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

class JoarkrefService(val databaseContext: DatabaseContext) {
    suspend fun hentJoarkRef(vedtakId: Long): Pair<Long, List<String>>? {
        return transaction(databaseContext) {
            it.joarkrefStore.hentJoarkRef(vedtakId)
        }
    }

    suspend fun lagreJoarkRef(vedtakId: Long, joarkRef: Long, dokumentIder: List<String>) {
        transaction(databaseContext) { ctx ->
            ctx.joarkrefStore.lagreJoarkRef(vedtakId, joarkRef, dokumentIder)
        }
    }
}
