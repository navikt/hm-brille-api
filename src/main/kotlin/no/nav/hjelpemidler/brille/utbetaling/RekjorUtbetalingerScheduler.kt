package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class RekjorUtbetalingerScheduler(
    private val utbetalingService: UtbetalingService,
    private val databaseContext: DatabaseContext,
    leaderElection: LeaderElection,
    private val metricsConfig: MetricsConfig,
    delay: Duration = 5.minutes,
    onlyWorkHours: Boolean = true
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {

    companion object {
        private val LOG = LoggerFactory.getLogger(RekjorUtbetalingerScheduler::class.java)
    }

    override suspend fun action() {
        val utbetalinger =
            utbetalingService.hentUtbetalingerSomSkalRekjøres()
        LOG.info("Fant ${utbetalinger.size} utbetalinger som skal rekjøres.")
        if (utbetalinger.isNotEmpty()) {
            val utbetalingsBatchList = utbetalinger.toUtbetalingsBatchList()
            LOG.info("fordelt på ${utbetalingsBatchList.size} batch")
            utbetalingsBatchList.forEach {
                if (it.utbetalinger.size > 100)
                    LOG.warn("En batch ${it.batchId} har ${it.utbetalinger.size}} som er mer enn 100 utbetalinger!")
                val tssIdent = transaction(databaseContext) { ctx -> ctx.tssIdentStore.hentTssIdent(it.orgNr) }
                    ?: throw RuntimeException("ingen tss ident tilgjengelig for batch (skal ikke skje)")
                utbetalingService.rekjorBatchTilUtbetaling(it, tssIdent)
            }
        }
        this.metricsConfig.registry.counter("rekjor_utbetalinger", "type", "utbetalinger")
            .increment(utbetalinger.size.toDouble())
    }
}
