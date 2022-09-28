package no.nav.hjelpemidler.brille.tss

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import org.slf4j.LoggerFactory

class TssIdentService(val databaseContext: DatabaseContext) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TssIdentRiver::class.java)
    }

    suspend fun settTssIdent(orgnr: String, kontonr: String, tssIdent: String) {
        transaction(databaseContext) { ctx ->
            // Sjekk at ønsket kontonr fortsatt stemmer med kvitteringen (i tilfelle man endret flere ganger på rad, ungå race condition)
            val virksomhet = ctx.virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)
                ?: error("Mottok kvittering på tss-oppdatering fra ukjent orgnr") // TODO: Vurder feil-håndtering som ikke kaster og tar ned tjenesten

            if (virksomhet.kontonr != kontonr) {
                LOG.info("Mottok kvittering på tss-oppdatering som gjaldt utdatert kontonr, ignorerer")
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
