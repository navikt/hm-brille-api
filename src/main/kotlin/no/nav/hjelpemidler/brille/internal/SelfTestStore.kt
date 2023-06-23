package no.nav.hjelpemidler.brille.internal

import kotliquery.Session
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query

interface SelfTestStore : Store {
    fun sjekkDatabaseKobling(): Boolean
}

class SelfTestStorePostgres(private val sessionFactory: () -> Session) : SelfTestStore, TransactionalStore(sessionFactory) {
    override fun sjekkDatabaseKobling() = session {
        it.query(
            "SELECT 1 FROM vedtak_v1 LIMIT 1",
            mapOf(),
        ) {
            true
        } ?: false
    }
}
