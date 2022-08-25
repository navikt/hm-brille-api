package no.nav.hjelpemidler.brille.innsender

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

class InnsenderService(private val databaseContext: DatabaseContext) {
    suspend fun godtaAvtale(fnrInnsender: String): Innsender {
        val innsender = Innsender(fnrInnsender = fnrInnsender, godtatt = true)
        transaction(databaseContext) { ctx -> ctx.innsenderStore.lagreInnsender(innsender) }
        return innsender
    }

    suspend fun hentInnsender(fnrInnsender: String): Innsender =
        transaction(databaseContext) { ctx ->
            ctx.innsenderStore.hentInnsender(fnrInnsender) ?: Innsender(fnrInnsender = fnrInnsender, godtatt = false)
        }
}
