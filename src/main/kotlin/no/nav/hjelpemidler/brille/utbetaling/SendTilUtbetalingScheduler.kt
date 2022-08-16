package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import org.slf4j.LoggerFactory

class SendTilUtbetalingScheduler(
    private val utbetalingService: UtbetalingService,
    leaderElection: LeaderElection,
    timeInMs: Long = 60000
) : SimpleScheduler(leaderElection, timeInMs) {

    companion object {
        private val LOG = LoggerFactory.getLogger(SendTilUtbetalingScheduler::class.java)
    }

    override suspend fun action() {
        LOG.info("Send melding til utbetaling")
    }
}
