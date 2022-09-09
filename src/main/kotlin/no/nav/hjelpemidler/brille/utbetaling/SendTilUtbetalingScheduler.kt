package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SendTilUtbetalingScheduler(
    private val utbetalingService: UtbetalingService,
    leaderElection: LeaderElection,
    private val metricsConfig: MetricsConfig,
    delay: Duration = 2.minutes,
    private val dager: Long = 8,
    onlyWorkHours: Boolean = true
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {

    private var maxUtbetalinger: Int = 0

    init {
        metricsConfig.registry.gauge("send_til_utbetalinger_max", maxUtbetalinger)
    }
    companion object {
        private val LOG = LoggerFactory.getLogger(SendTilUtbetalingScheduler::class.java)
    }

    override suspend fun action() {
        val utbetalinger = utbetalingService.hentUtbetalingerMedStatusBatchDato(batchDato = LocalDate.now().minusDays(dager))
        LOG.info("Fant ${utbetalinger.size} utbetalinger som skal sendes over.")
        if (utbetalinger.isNotEmpty()) {
            val utbetalingsBatchList = utbetalinger.toUtbetalingsBatchList()
            LOG.info("fordelt på ${utbetalingsBatchList.size} batch")
            utbetalingsBatchList.forEach {
                val antUtbetalinger = it.utbetalinger.size
                if (maxUtbetalinger < antUtbetalinger) {
                    maxUtbetalinger = antUtbetalinger
                }
                if (it.utbetalinger.size > 100) {
                    LOG.warn("En batch ${it.batchId} har ${it.utbetalinger.size}} som er mer enn 100 utbetalinger!")
                }
                utbetalingService.sendBatchTilUtbetaling(it)
            }
        }
        metricsConfig.registry.counter("send_til_utbetaling", "type", "utbetalinger")
            .increment(utbetalinger.size.toDouble())
    }
}
