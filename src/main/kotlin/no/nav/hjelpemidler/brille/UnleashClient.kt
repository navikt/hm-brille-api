package no.nav.hjelpemidler.brille

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.strategy.Strategy
import io.getunleash.util.UnleashConfig
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.configuration.GcpEnvironment
import no.nav.hjelpemidler.configuration.LocalEnvironment
import no.nav.hjelpemidler.configuration.NaisEnvironmentVariable

object UnleashClient {
    private val unleash: Unleash

    init {
        val miljø = Environment.current
        unleash = when (miljø) {
            GcpEnvironment.DEV, GcpEnvironment.PROD, LocalEnvironment -> DefaultUnleash(
                UnleashConfig.builder()
                    .appName(NaisEnvironmentVariable.NAIS_APP_NAME)
                    .unleashAPI("${Configuration.UNLEASH_SERVER_API_URL}/api")
                    .apiKey(Configuration.UNLEASH_SERVER_API_TOKEN)
                    .synchronousFetchOnInitialisation(true)
                    .build(),
            )

            else -> TODO()
        }
    }

    fun isEnabled(toggleKey: String) = unleash.isEnabled(toggleKey, false)
}

object UnleashToggleKeys

class ClusterStrategy(val miljø: Environment) : Strategy {
    override fun getName() = "byCluster"

    override fun isEnabled(parameters: MutableMap<String, String>): Boolean {
        val clustersParameter = parameters["cluster"] ?: return false
        val alleClustere = clustersParameter.split(",").map { it.trim() }.map { it.lowercase() }.toList()
        return alleClustere.contains(miljø.cluster.lowercase())
    }
}
