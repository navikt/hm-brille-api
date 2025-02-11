package no.nav.hjelpemidler.brille.tss

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

private val log = KotlinLogging.logger {}

class TssIdentService(val databaseContext: DatabaseContext) {
    suspend fun settTssIdent(orgnr: String, kontonr: String, tssIdent: String) {
        transaction(databaseContext) { ctx ->
            // Sjekk at ønsket kontonr fortsatt stemmer med kvitteringen (i tilfelle man endret flere ganger på rad, ungå race condition)
            val virksomhet = ctx.virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)
                ?: error("Mottok kvittering på tss-oppdatering fra ukjent orgnr")

            if (virksomhet.kontonr != kontonr) {
                log.info { "Mottok kvittering på tss-oppdatering som gjaldt utdatert kontonr, ignorerer" }
                return@transaction
            }

            // Oppdater databasen med ny tss ident
            ctx.tssIdentStore.settTssIdent(orgnr, tssIdent)
        }
    }

    suspend fun hentAlleOrgnrSomManglerTssIdent(): List<String> {
        return transaction(databaseContext) { ctx ->
            ctx.tssIdentStore.hentAlleOrgnrSomManglerTssIdent()
        }
    }
}
