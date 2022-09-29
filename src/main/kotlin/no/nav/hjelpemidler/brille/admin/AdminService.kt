package no.nav.hjelpemidler.brille.admin

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.vedtak.VedtakService
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
}
