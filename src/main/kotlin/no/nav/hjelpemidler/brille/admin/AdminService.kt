package no.nav.hjelpemidler.brille.admin

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

class AdminService(
    val databaseContext: DatabaseContext,
) {
    suspend fun hentVedtakListe(fnr: String): List<VedtakListe> {
        return transaction(databaseContext) { ctx ->
            ctx.adminStore.hentVedtakListe(fnr)
        }
    }

    suspend fun hentVedtak(vedtakId: Long): Vedtak? {
        return transaction(databaseContext) { ctx ->
            ctx.adminStore.hentVedtak(vedtakId)
        }
    }

    suspend fun lagreAvvisning(
        fnrBarn: String,
        fnrInnsender: String,
        orgnr: String,
        butikkId: String?,
        årsaker: List<String>,
    ) {
        return transaction(databaseContext) { ctx ->
            ctx.adminStore.lagreAvvisning(fnrBarn, fnrInnsender, orgnr, butikkId, årsaker)
        }
    }

    suspend fun hentAvvisning(fnrBarn: String, etterVedtak: VedtakListe?): Avvisning? {
        return transaction(databaseContext) { ctx ->
            ctx.adminStore.hentAvvisning(fnrBarn, etterVedtak)
        }
    }

    suspend fun harAvvisningDeSiste7DageneFor(
        fnrBarn: String,
        orgnr: String,
    ) = transaction(databaseContext) { ctx ->
        ctx.adminStore.harAvvisningDeSiste7DageneFor(fnrBarn, orgnr)
    }

    suspend fun hentUtbetalinger(utbetalingsRef: String): List<Utbetaling> {
        return transaction(databaseContext) { ctx ->
            ctx.adminStore.hentUtbetalinger(utbetalingsRef)
        }
    }
}
