package no.nav.hjelpemidler.brille.enhetsregisteret

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import no.nav.hjelpemidler.brille.slack.Slack
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
        runCatching {
            enhetsregisteretService.oppdaterMirrorHvisUtdatert()
        }.getOrElse { e ->
            val uid = UUID.randomUUID()
            log.error(e) { "Feil under oppdatering av vår kopi av enhetsregisteret ($uid)" }
            Slack.post("Feil under oppdatering av vår kopi av enhetsregisteret (søk kibana: $uid)")
        }
    }
}
