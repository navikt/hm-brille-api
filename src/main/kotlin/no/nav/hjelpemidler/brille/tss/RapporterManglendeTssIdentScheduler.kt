package no.nav.hjelpemidler.brille.tss

import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import no.nav.hjelpemidler.brille.slack.Slack
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class RapporterManglendeTssIdentScheduler(
    private val tssIdentService: TssIdentService,
    private val enhetsregisteretService: EnhetsregisteretService,
    leaderElection: LeaderElection,
    private val metricsConfig: MetricsConfig,
    delay: Duration = 24.hours,
    onlyWorkHours: Boolean = false
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {

    companion object {
        private val LOG = LoggerFactory.getLogger(RapporterManglendeTssIdentScheduler::class.java)
    }

    override suspend fun action() {
        LOG.info("RapporterManglendeTssIdentScheduler: ser etter orgnr som mangler tss-ident")

        val orgnre = tssIdentService.hentAlleOrgnrSomManglerTssIdent()
        if (orgnre.isEmpty()) return

        data class Result(
            val orgnr: String,
            val slettet: LocalDate?,
        )

        // Sjekk om de er slettet i enhetsregisteret slik at vi kan merke de som det.
        val results = mutableListOf<Result>()
        for (orgnr in orgnre) {
            results.add(
                Result(
                    orgnr = orgnr,
                    slettet = enhetsregisteretService.organisasjonSlettetNår(orgnr),
                )
            )
        }

        // Post til slack
        val count = results.count()
        val resultString = results.joinToString { res ->
            if (res.slettet == null) res.orgnr
            else "${res.orgnr} (slettet: ${res.slettet})"
        }

        Slack.post(
            "RapporterManglendeTssIdentScheduler: Fant $count virksomheter med avtale som ikke har TSS-ident. Orgnr: $resultString."
        )
    }
}
