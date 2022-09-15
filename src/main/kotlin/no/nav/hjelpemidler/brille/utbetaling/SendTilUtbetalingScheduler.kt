package no.nav.hjelpemidler.brille.utbetaling

import io.micrometer.core.instrument.Gauge
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SendTilUtbetalingScheduler(
    private val utbetalingService: UtbetalingService,
    private val databaseContext: DatabaseContext,
    leaderElection: LeaderElection,
    private val metricsConfig: MetricsConfig,
    delay: Duration = 10.minutes,
    private val dager: Long = 8,
    onlyWorkHours: Boolean = true
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {

    private var maxUtbetalinger: Double = 0.0
    private var tilUtbetaling: Double = 0.0
    init {
        Gauge.builder("utbetalingslinjer_max", this) { this.maxUtbetalinger }
            .register(metricsConfig.registry)
        Gauge.builder("til_utbetaling_ko", this) { tilUtbetaling }
            .register(metricsConfig.registry)
    }
    companion object {
        private val LOG = LoggerFactory.getLogger(SendTilUtbetalingScheduler::class.java)
    }

    override suspend fun action() {
        val utbetalinger =
            utbetalingService.hentUtbetalingerMedNyStatusBatchDato(batchDato = LocalDate.now().minusDays(dager))
        LOG.info("Fant ${utbetalinger.size} utbetalinger som skal sendes over.")
        if (utbetalinger.isNotEmpty()) {
            val utbetalingsBatchList = utbetalinger.toUtbetalingsBatchList()
            LOG.info("fordelt på ${utbetalingsBatchList.size} batch")
            utbetalingsBatchList.forEach {
                val tssIdent = transaction(databaseContext) { ctx -> ctx.tssIdentStore.hentTssIdent(it.orgNr) }
                    ?: throw RuntimeException("ingen tss ident tilgjengelig for batch (skal ikke skje)")
                utbetalingService.sendBatchTilUtbetaling(it, tssIdent)
                val antUtbetalinger = it.utbetalinger.size
                if (maxUtbetalinger < antUtbetalinger) {
                    maxUtbetalinger = antUtbetalinger.toDouble()
                }
                if (antUtbetalinger > 100) {
                    LOG.warn("En batch ${it.batchId} har $antUtbetalinger} som er mer enn 100 utbetalinger!")
                }
            }
        }
        metricsConfig.registry.counter("send_til_utbetaling", "type", "utbetalinger")
            .increment(utbetalinger.size.toDouble())
        tilUtbetaling = utbetalingService.hentAntallUtbetalingerMedStatus(UtbetalingStatus.TIL_UTBETALING).toDouble()
    }
}
