package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class VedtakTilUtbetalingScheduler(
    private val vedtakService: VedtakService,
    leaderElection: LeaderElection,
    timeInMs: Long = 5 * 60 * 1000 // every 5 min
) : SimpleScheduler(leaderElection, timeInMs) {

    companion object {
        private val LOG = LoggerFactory.getLogger(VedtakTilUtbetalingScheduler::class.java)
    }

    override suspend fun action() {
        val vedtakList = vedtakService.hentVedtakIkkeRegistrertForUtbetaling(opprettet = LocalDateTime.now().minusDays(1))
        LOG.info("fant ${vedtakList.size} vedtak for registrering")
        // TODO innvilget vedtak som ikke finnes i utbetaling tabellen bør insertes her.
    }
}
