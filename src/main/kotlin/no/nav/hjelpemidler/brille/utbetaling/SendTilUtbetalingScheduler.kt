package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SendTilUtbetalingScheduler(
    private val utbetalingService: UtbetalingService,
    leaderElection: LeaderElection,
    delay: Duration = 60.minutes,
    private val dager: Long = 8,
) : SimpleScheduler(leaderElection, delay) {

    companion object {
        private val LOG = LoggerFactory.getLogger(SendTilUtbetalingScheduler::class.java)
    }

    override suspend fun action() {
        LOG.info("Starter opp SendTilUtbetaling")
        val utbetalinger = utbetalingService.hentUtbetalingerMedStatusBatchDato(batchDato = LocalDate.now().minusDays(dager))
        if (utbetalinger.isNotEmpty()) {
            val utbetalingsBatchList = utbetalinger.toUtbetalingsBatchList()
            LOG.info("Skal sende ${utbetalinger.size} utbetalinger, fordelt på ${utbetalingsBatchList.size} batch")
            utbetalingsBatchList.forEach {
                if (it.utbetalinger.size > 100)
                    LOG.warn("En batch ${it.batchId} har ${it.utbetalinger.size}} som er mer enn 100 utbetalinger!")
                utbetalingService.sendBatchTilUtbetaling(it)
            }
        }
    }
}
