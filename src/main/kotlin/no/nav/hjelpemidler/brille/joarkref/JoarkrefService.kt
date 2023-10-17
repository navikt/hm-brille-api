package no.nav.hjelpemidler.brille.joarkref

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

class JoarkrefService(val databaseContext: DatabaseContext) {
    suspend fun hentJoarkRef(vedtakId: Long): JoarkRef? {
        return transaction(databaseContext) {
            val joarkref = it.joarkrefStore.hentJoarkRef(vedtakId) ?: return@transaction null
            JoarkRef(joarkref.first, joarkref.second)
        }
    }

    suspend fun lagreJoarkRef(vedtakId: Long, joarkRef: Long, dokumentIder: List<String>) {
        transaction(databaseContext) { ctx ->
            ctx.joarkrefStore.lagreJoarkRef(vedtakId, joarkRef, dokumentIder)
        }
    }
}

data class JoarkRef(
    val journalpostId: Long,
    val dokumentIder: List<String>,
)
