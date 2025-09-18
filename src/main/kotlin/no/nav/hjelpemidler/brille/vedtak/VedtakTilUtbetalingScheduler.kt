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
import java.time.LocalDateTime
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
    private var vedtakKø: Double = 0.0
    private var sistRapportertTilSlack: MutableMap<String, LocalDateTime> = mutableMapOf()

    init {
        Gauge.builder("vedtak_til_utbetaling_ko", this) { this.vedtakKø }
            .register(metricsConfig.registry)
    }

    override suspend fun action() {
        vedtakKø = vedtakService.hentAntallVedtakIKø().toDouble()

        val vedtak = vedtakService.hentVedtakForUtbetaling(opprettet = LocalDate.now().minusDays(dager).atStartOfDay())
        log.info { "Fant ${vedtak.size} vedtak for utbetaling" }

        val enhetsregisterCache = mutableMapOf<String, Boolean>()
        vedtak.forEach {
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

        // Rapporter til Slack om alle orgnr med køet opp vedtak for utbetaling som er knyttet til en organisasjon som er slettet
        enhetsregisterCache.filter { it.value }.forEach { (orgnr, _) ->
            if (Environment.current is ClusterEnvironment) {
                if (
                    !sistRapportertTilSlack.containsKey(orgnr) ||
                    sistRapportertTilSlack[orgnr]!!.isBefore(LocalDateTime.now().minusHours(6))
                ) {
                    Slack.post("VedtakTilUtbetalingScheduler: Kan ikke opprette utbetalinger for organisasjon som er slettet i enhetsregisteret (orgnr: $orgnr)")
                    sistRapportertTilSlack[orgnr] = LocalDateTime.now()
                }
            }
        }

        metricsConfig.registry
            .counter("vedtak_til_utbetaling", "type", "vedtak")
            .increment(vedtak.size.toDouble())
    }
}
