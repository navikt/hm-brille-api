package no.nav.hjelpemidler.brille.internal

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.tss.TssIdentRiver
import org.slf4j.LoggerFactory

class SelfTestService(val databaseContext: DatabaseContext) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TssIdentRiver::class.java)
    }

    suspend fun sjekkDatabaseKobling() = transaction(databaseContext) { ctx ->
        ctx.selfTestStore.sjekkDatabaseKobling()
    }
}
