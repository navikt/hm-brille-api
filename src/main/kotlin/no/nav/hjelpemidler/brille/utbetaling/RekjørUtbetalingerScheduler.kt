package no.nav.hjelpemidler.brille.utbetaling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger {}

class RekjørUtbetalingerScheduler(
    private val utbetalingService: UtbetalingService,
    private val databaseContext: DatabaseContext,
    leaderElection: LeaderElection,
    private val metricsConfig: MetricsConfig,
    delay: Duration = 5.minutes,
    onlyWorkHours: Boolean = true,
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {
    override suspend fun action() {
        val utbetalinger = utbetalingService.hentUtbetalingerSomSkalRekjøres()
        log.info { "Fant ${utbetalinger.size} utbetalinger som skal rekjøres" }
        if (utbetalinger.isNotEmpty()) {
            val batch = utbetalinger.toUtbetalingBatchList()
            log.info { "Fordelt på ${batch.size} batch" }
            batch.forEach {
                if (it.utbetalinger.size > 100) {
                    log.warn { "En batch ${it.batchId} har ${it.utbetalinger.size} som er mer enn 100 utbetalinger!" }
                }
                val tssIdent = transaction(databaseContext) { ctx -> ctx.tssIdentStore.hentTssIdent(it.orgNr) }
                    ?: error("ingen tss ident tilgjengelig for batch (skal ikke skje)")
                utbetalingService.rekjorBatchTilUtbetaling(it, tssIdent)
            }
        }
        metricsConfig.registry
            .counter("rekjor_utbetalinger", "type", "utbetalinger")
            .increment(utbetalinger.size.toDouble())
    }
}
