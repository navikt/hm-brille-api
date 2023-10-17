package no.nav.hjelpemidler.brille.enhetsregisteret

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class EnhetsregisteretScheduler(
    private val enhetsregisteretService: EnhetsregisteretService,
    leaderElection: LeaderElection,
    metricsConfig: MetricsConfig,
    delay: Duration = 1.hours,
    onlyWorkHours: Boolean = true,
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override suspend fun action() {
        runCatching {
            enhetsregisteretService.oppdaterMirrorHvisUtdatert()
        }.getOrElse { e ->
            log.error(e) { "Feil under oppdatering av vår kopi av enhetsregisteret" }
        }
    }
}
