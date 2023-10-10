package no.nav.hjelpemidler.brille.admin

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.configuration.Environment
import org.slf4j.LoggerFactory

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AdminService(
    val databaseContext: DatabaseContext,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(VedtakService::class.java)
    }

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

    suspend fun lagreAvvisning(fnrBarn: String, fnrInnsender: String, orgnr: String, årsaker: List<String>) {
        return transaction(databaseContext) { ctx ->
            ctx.adminStore.lagreAvvisning(fnrBarn, fnrInnsender, orgnr, årsaker)
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
        // TODO: Fjern når testing er over
        if (Environment.current.tier.isDev) {
            return@transaction false
        }
        ctx.adminStore.harAvvisningDeSiste7DageneFor(fnrBarn, orgnr)
    }

    suspend fun hentUtbetalinger(utbetalingsRef: String): List<Utbetaling> {
        return transaction(databaseContext) { ctx ->
            ctx.adminStore.hentUtbetalinger(utbetalingsRef)
        }
    }
}
