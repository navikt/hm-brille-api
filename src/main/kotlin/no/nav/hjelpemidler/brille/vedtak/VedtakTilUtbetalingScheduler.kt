package no.nav.hjelpemidler.brille.vedtak

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import no.nav.hjelpemidler.brille.slack.Slack
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.configuration.ClusterEnvironment
import no.nav.hjelpemidler.configuration.Environment
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger {}

class VedtakTilUtbetalingScheduler(
    private val vedtakService: VedtakService,
    leaderElection: LeaderElection,
    private val utbetalingService: UtbetalingService,
    private val enhetsregisteretService: EnhetsregisteretService,
    private val metricsConfig: MetricsConfig,
    delay: Duration = if (Environment.current.isDev) 2.minutes else 30.minutes,
    private val dager: Long = 14,
) : SimpleScheduler(leaderElection, delay, metricsConfig) {

    private var vedtakKo: Double = 0.0

    init {
        Gauge.builder("vedtak_til_utbetaling_ko", this) { this.vedtakKo }
            .register(metricsConfig.registry)
    }

    override suspend fun action() {
        vedtakKo = vedtakService.hentAntallVedtakIKø().toDouble()

        val vedtakList =
            vedtakService.hentVedtakForUtbetaling(opprettet = LocalDate.now().minusDays(dager).atStartOfDay())
        log.info { "fant ${vedtakList.size} vedtak for utbetaling" }

        val enhetsregisterCache = mutableMapOf<String, Boolean>()
        vedtakList.forEach {
            // Sjekk om organisasjon har blitt slettet
            val orgnr = it.orgnr
            val erSlettet = enhetsregisterCache[orgnr].let {
                if (it == null) enhetsregisterCache[orgnr] = enhetsregisteretService.organisasjonSlettet(orgnr)
                enhetsregisterCache[orgnr]!!
            }

            // ... hvis ikke, opprett ny utbetaling
            if (!erSlettet) {
                utbetalingService.opprettNyUtbetaling(it)
            }
        }

        // Rapporter til slack om alle orgnr med køet opp vedtak for utbetaling som er knyttet til en organisasjon som er slettet
        enhetsregisterCache.filter { it.value }.forEach { (orgnr, _) ->
            if (Environment.current is ClusterEnvironment) {
                Slack.post("VedtakTilUtbetalingScheduler: Kan ikke opprette utbetalinger for organisasjon som er slettet i enhetsregisteret (orgnr=$orgnr)")
            }
        }

        this.metricsConfig.registry.counter("vedtak_til_utbetaling", "type", "vedtak")
            .increment(vedtakList.size.toDouble())
    }
}
