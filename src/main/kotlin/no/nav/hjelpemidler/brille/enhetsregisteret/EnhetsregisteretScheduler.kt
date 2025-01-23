package no.nav.hjelpemidler.brille.enhetsregisteret

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import no.nav.hjelpemidler.brille.slack.Slack
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class EnhetsregisteretScheduler(
    private val enhetsregisteretService: EnhetsregisteretService,
    leaderElection: LeaderElection,
    metricsConfig: MetricsConfig,
    delay: Duration = 1.hours,
    onlyWorkHours: Boolean = false,
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override suspend fun action() {
        // Prøv en til to ganger per natt, merk: Filen produseres hver natt, cirka klokken 05:00.
        val now = LocalDateTime.now(ZoneId.of("Europe/Oslo"))
        if (now.hour < 6 || now.hour > 8) return

        runCatching {
            enhetsregisteretService.oppdaterMirrorHvisUtdatert()
        }.getOrElse { e ->
            val uid = UUID.randomUUID()
            log.error(e) { "Feil under oppdatering av vår kopi av enhetsregisteret ($uid)" }
            Slack.post("Feil under oppdatering av vår kopi av enhetsregisteret (søk kibana: $uid)")
        }
    }
}
