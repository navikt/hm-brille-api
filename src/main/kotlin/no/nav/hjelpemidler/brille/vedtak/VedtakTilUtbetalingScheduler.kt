package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class VedtakTilUtbetalingScheduler(
    private val vedtakService: VedtakService,
    leaderElection: LeaderElection,
    private val utbetalingService: UtbetalingService,
    private val metricsConfig: MetricsConfig,
    delay: Duration = 5.minutes,
    private val dager: Long = 7,
) : SimpleScheduler(leaderElection, delay, metricsConfig) {

    companion object {
        private val LOG = LoggerFactory.getLogger(VedtakTilUtbetalingScheduler::class.java)
    }

    override suspend fun action() {
        val vedtakList = vedtakService.hentVedtakForUtbetaling(opprettet = LocalDateTime.now().minusDays(dager))
        LOG.info("fant ${vedtakList.size} vedtak for utbetaling")
        vedtakList.forEach {
            utbetalingService.opprettNyUtbetaling(it)
        }
        this.metricsConfig.registry.counter("vedtak_til_utbetaling", "type", "vedtak")
            .increment(vedtakList.size.toDouble())
    }
}
